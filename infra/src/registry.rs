use std::collections::HashMap;
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};

use dashmap::DashMap;
use wanaku_types::persistence::{PersistenceBackend, RegistrySnapshot};
use wanaku_types::registry::{
    DEFAULT_NAMESPACE, ForwardEntry, ForwardRegistry, NamespaceEntry, NamespaceRegistry,
    PromptEntry, PromptRegistry, ResourceEntry, ResourceRegistry, ToolEntry, ToolRegistry,
    inject_request_id_arg,
};

#[derive(Clone)]
pub struct InMemoryRegistry {
    tools: Arc<DashMap<String, ToolEntry>>,
    resources: Arc<DashMap<String, ResourceEntry>>,
    prompts: Arc<DashMap<String, PromptEntry>>,
    forwards: Arc<DashMap<String, ForwardEntry>>,
    namespaces: Arc<DashMap<String, NamespaceEntry>>,
    persistence: Option<Arc<dyn PersistenceBackend>>,
    inject_request_id: Arc<AtomicBool>,
}

impl InMemoryRegistry {
    pub fn new() -> Self {
        let namespaces = DashMap::new();
        namespaces.insert(
            DEFAULT_NAMESPACE.to_owned(),
            NamespaceEntry {
                name: DEFAULT_NAMESPACE.to_owned(),
                labels: HashMap::new(),
                auth_required: None,
                audience: None,
            },
        );

        Self {
            tools: Arc::new(DashMap::new()),
            resources: Arc::new(DashMap::new()),
            prompts: Arc::new(DashMap::new()),
            forwards: Arc::new(DashMap::new()),
            namespaces: Arc::new(namespaces),
            persistence: None,
            inject_request_id: Arc::new(AtomicBool::new(false)),
        }
    }

    pub fn enable_request_id_injection(&self) {
        self.inject_request_id.store(true, Ordering::Relaxed);
    }

    pub fn with_persistence(backend: Arc<dyn PersistenceBackend>) -> Self {
        Self {
            persistence: Some(backend),
            ..Self::new()
        }
    }

    /// Load all entries from the persistence backend into memory.
    ///
    /// Inserts directly into the DashMaps to avoid triggering
    /// `persist()` on every entry (the data already came from disk).
    #[expect(clippy::too_many_lines, reason = "sequential loading of all registry types")]
    pub fn load_persisted(&self) {
        let Some(backend) = &self.persistence else {
            return;
        };

        let snapshot = match backend.load() {
            Ok(s) => s,
            Err(e) => {
                tracing::warn!(error = %e, "failed to load persisted registry");
                return;
            }
        };

        for mut tool in snapshot.tools {
            if tool.namespace.is_none() {
                tool.namespace = Some(DEFAULT_NAMESPACE.to_owned());
            }
            inject_request_id_arg(&mut tool.input_schema);
            self.tools.insert(tool.name.clone(), tool);
        }
        for mut resource in snapshot.resources {
            if resource.namespace.is_none() {
                resource.namespace = Some(DEFAULT_NAMESPACE.to_owned());
            }
            self.resources.insert(resource.name.clone(), resource);
        }
        for mut prompt in snapshot.prompts {
            if prompt.namespace.is_none() {
                prompt.namespace = Some(DEFAULT_NAMESPACE.to_owned());
            }
            self.prompts.insert(prompt.name.clone(), prompt);
        }
        for forward in snapshot.forwards {
            self.forwards.insert(forward.name.clone(), forward);
        }
        for namespace in snapshot.namespaces {
            self.namespaces.insert(namespace.name.clone(), namespace);
        }

        tracing::info!("loaded registry from persistence backend");
    }

    fn snapshot(&self) -> RegistrySnapshot {
        RegistrySnapshot {
            tools: self.list_tools(),
            resources: self.list_resources(),
            prompts: self.list_prompts(),
            forwards: self.list_forwards(),
            namespaces: self.list_namespaces(),
        }
    }

    fn persist(&self) {
        if let Some(backend) = &self.persistence {
            let snapshot = self.snapshot();
            let backend = Arc::clone(backend);
            let save = move || {
                if let Err(e) = backend.save(&snapshot) {
                    tracing::warn!(error = %e, "failed to persist registry");
                }
            };
            if tokio::runtime::Handle::try_current().is_ok() {
                tokio::task::spawn_blocking(save);
            } else {
                save();
            }
        }
    }
}

impl Default for InMemoryRegistry {
    fn default() -> Self {
        Self::new()
    }
}

fn effective_namespace(ns: &Option<String>) -> &str {
    ns.as_deref().unwrap_or(DEFAULT_NAMESPACE)
}

impl ToolRegistry for InMemoryRegistry {
    fn list_tools(&self) -> Vec<ToolEntry> {
        self.tools.iter().map(|entry| entry.value().clone()).collect()
    }

    fn list_tools_in_namespace(&self, namespace: &str) -> Vec<ToolEntry> {
        self.tools
            .iter()
            .filter(|entry| effective_namespace(&entry.value().namespace) == namespace)
            .map(|entry| entry.value().clone())
            .collect()
    }

    fn get_tool(&self, name: &str) -> Option<ToolEntry> {
        self.tools.get(name).map(|entry| entry.value().clone())
    }

    fn get_tool_in_namespace(&self, namespace: &str, name: &str) -> Option<ToolEntry> {
        self.tools
            .get(name)
            .map(|entry| entry.value().clone())
            .filter(|tool| effective_namespace(&tool.namespace) == namespace)
    }

    fn register_tool(&self, mut tool: ToolEntry) {
        if tool.namespace.is_none() {
            tool.namespace = Some(DEFAULT_NAMESPACE.to_owned());
        }
        if self.inject_request_id.load(Ordering::Relaxed) {
            inject_request_id_arg(&mut tool.input_schema);
        }
        self.tools.insert(tool.name.clone(), tool);
        self.persist();
    }

    fn register_tools_batch(&self, tools: Vec<ToolEntry>) {
        if tools.is_empty() {
            return;
        }
        let inject = self.inject_request_id.load(Ordering::Relaxed);
        let default_ns = DEFAULT_NAMESPACE.to_owned();
        for mut tool in tools {
            if tool.namespace.is_none() {
                tool.namespace = Some(default_ns.clone());
            }
            if inject {
                inject_request_id_arg(&mut tool.input_schema);
            }
            self.tools.insert(tool.name.clone(), tool);
        }
        self.persist();
    }

    fn remove_tool(&self, name: &str) -> bool {
        let removed = self.tools.remove(name).is_some();
        if removed {
            self.persist();
        }
        removed
    }

    fn remove_tools_batch(&self, names: &[String]) -> usize {
        let mut count = 0;
        for name in names {
            if self.tools.remove(name.as_str()).is_some() {
                count += 1;
            }
        }
        if count > 0 {
            self.persist();
        }
        count
    }

    fn tool_count(&self) -> usize {
        self.tools.len()
    }
}

impl ResourceRegistry for InMemoryRegistry {
    fn list_resources(&self) -> Vec<ResourceEntry> {
        self.resources.iter().map(|entry| entry.value().clone()).collect()
    }

    fn list_resources_in_namespace(&self, namespace: &str) -> Vec<ResourceEntry> {
        self.resources
            .iter()
            .filter(|entry| effective_namespace(&entry.value().namespace) == namespace)
            .map(|entry| entry.value().clone())
            .collect()
    }

    fn get_resource(&self, name: &str) -> Option<ResourceEntry> {
        self.resources.get(name).map(|entry| entry.value().clone())
    }

    fn get_resource_in_namespace(&self, namespace: &str, name: &str) -> Option<ResourceEntry> {
        self.resources
            .get(name)
            .map(|entry| entry.value().clone())
            .filter(|res| effective_namespace(&res.namespace) == namespace)
    }

    fn register_resource(&self, mut resource: ResourceEntry) {
        if resource.namespace.is_none() {
            resource.namespace = Some(DEFAULT_NAMESPACE.to_owned());
        }
        self.resources.insert(resource.name.clone(), resource);
        self.persist();
    }

    fn register_resources_batch(&self, resources: Vec<ResourceEntry>) {
        if resources.is_empty() {
            return;
        }
        let default_ns = DEFAULT_NAMESPACE.to_owned();
        for mut resource in resources {
            if resource.namespace.is_none() {
                resource.namespace = Some(default_ns.clone());
            }
            self.resources.insert(resource.name.clone(), resource);
        }
        self.persist();
    }

    fn remove_resource(&self, name: &str) -> bool {
        let removed = self.resources.remove(name).is_some();
        if removed {
            self.persist();
        }
        removed
    }

    fn remove_resources_batch(&self, names: &[String]) -> usize {
        let mut count = 0;
        for name in names {
            if self.resources.remove(name.as_str()).is_some() {
                count += 1;
            }
        }
        if count > 0 {
            self.persist();
        }
        count
    }

    fn resource_count(&self) -> usize {
        self.resources.len()
    }
}

impl PromptRegistry for InMemoryRegistry {
    fn list_prompts(&self) -> Vec<PromptEntry> {
        self.prompts.iter().map(|entry| entry.value().clone()).collect()
    }

    fn list_prompts_in_namespace(&self, namespace: &str) -> Vec<PromptEntry> {
        self.prompts
            .iter()
            .filter(|entry| effective_namespace(&entry.value().namespace) == namespace)
            .map(|entry| entry.value().clone())
            .collect()
    }

    fn get_prompt(&self, name: &str) -> Option<PromptEntry> {
        self.prompts.get(name).map(|entry| entry.value().clone())
    }

    fn get_prompt_in_namespace(&self, namespace: &str, name: &str) -> Option<PromptEntry> {
        self.prompts
            .get(name)
            .map(|entry| entry.value().clone())
            .filter(|prompt| effective_namespace(&prompt.namespace) == namespace)
    }

    fn register_prompt(&self, mut prompt: PromptEntry) {
        if prompt.namespace.is_none() {
            prompt.namespace = Some(DEFAULT_NAMESPACE.to_owned());
        }
        self.prompts.insert(prompt.name.clone(), prompt);
        self.persist();
    }

    fn register_prompts_batch(&self, prompts: Vec<PromptEntry>) {
        if prompts.is_empty() {
            return;
        }
        let default_ns = DEFAULT_NAMESPACE.to_owned();
        for mut prompt in prompts {
            if prompt.namespace.is_none() {
                prompt.namespace = Some(default_ns.clone());
            }
            self.prompts.insert(prompt.name.clone(), prompt);
        }
        self.persist();
    }

    fn remove_prompt(&self, name: &str) -> bool {
        let removed = self.prompts.remove(name).is_some();
        if removed {
            self.persist();
        }
        removed
    }

    fn remove_prompts_batch(&self, names: &[String]) -> usize {
        let mut count = 0;
        for name in names {
            if self.prompts.remove(name.as_str()).is_some() {
                count += 1;
            }
        }
        if count > 0 {
            self.persist();
        }
        count
    }

    fn prompt_count(&self) -> usize {
        self.prompts.len()
    }
}

impl NamespaceRegistry for InMemoryRegistry {
    fn list_namespaces(&self) -> Vec<NamespaceEntry> {
        self.namespaces.iter().map(|entry| entry.value().clone()).collect()
    }

    fn get_namespace(&self, name: &str) -> Option<NamespaceEntry> {
        self.namespaces.get(name).map(|entry| entry.value().clone())
    }

    fn register_namespace(&self, namespace: NamespaceEntry) {
        self.namespaces.insert(namespace.name.clone(), namespace);
        self.persist();
    }

    fn remove_namespace(&self, name: &str) -> bool {
        let removed = self.namespaces.remove(name).is_some();
        if removed {
            self.persist();
        }
        removed
    }
}

impl ForwardRegistry for InMemoryRegistry {
    fn list_forwards(&self) -> Vec<ForwardEntry> {
        self.forwards.iter().map(|entry| entry.value().clone()).collect()
    }

    fn get_forward(&self, name: &str) -> Option<ForwardEntry> {
        self.forwards.get(name).map(|entry| entry.value().clone())
    }

    fn register_forward(&self, mut forward: ForwardEntry) {
        if forward.namespace.is_none() {
            forward.namespace = Some(DEFAULT_NAMESPACE.to_owned());
        }
        self.forwards.insert(forward.name.clone(), forward);
        self.persist();
    }

    fn remove_forward(&self, name: &str) -> bool {
        let removed = self.forwards.remove(name).is_some();
        if removed {
            self.persist();
        }
        removed
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn sample_tool() -> ToolEntry {
        ToolEntry {
            name: "test-tool".to_owned(),
            description: "A test tool".to_owned(),
            uri: "camel:http://example.com".to_owned(),
            type_: "http".to_owned(),
            input_schema: serde_json::json!({"type": "object"}),
            labels: HashMap::new(),
            id: None,
            namespace: None,
            configuration_uri: None,
            secrets_uri: None,
        }
    }

    fn sample_resource() -> ResourceEntry {
        ResourceEntry {
            name: "test-resource".to_owned(),
            description: "A test resource".to_owned(),
            location: "/tmp/test.txt".to_owned(),
            type_: "file".to_owned(),
            mime_type: "text/plain".to_owned(),
            labels: HashMap::new(),
            id: None,
            namespace: None,
            configuration_uri: None,
            secrets_uri: None,
        }
    }

    #[test]
    fn register_and_list_tools() {
        let registry = InMemoryRegistry::new();
        registry.register_tool(sample_tool());
        let tools = registry.list_tools();
        assert_eq!(tools.len(), 1);
        assert_eq!(tools[0].name, "test-tool");
    }

    #[test]
    fn get_tool_by_name() {
        let registry = InMemoryRegistry::new();
        registry.register_tool(sample_tool());
        let tool = registry.get_tool("test-tool");
        assert!(tool.is_some());
        assert_eq!(tool.as_ref().map(|t| t.uri.as_str()), Some("camel:http://example.com"));
    }

    #[test]
    fn get_missing_tool_returns_none() {
        let registry = InMemoryRegistry::new();
        assert!(registry.get_tool("nonexistent").is_none());
    }

    #[test]
    fn remove_tool() {
        let registry = InMemoryRegistry::new();
        registry.register_tool(sample_tool());
        assert!(registry.remove_tool("test-tool"));
        assert!(registry.get_tool("test-tool").is_none());
    }

    #[test]
    fn register_and_list_resources() {
        let registry = InMemoryRegistry::new();
        registry.register_resource(sample_resource());
        let resources = registry.list_resources();
        assert_eq!(resources.len(), 1);
        assert_eq!(resources[0].name, "test-resource");
    }

    #[test]
    fn get_resource_by_name() {
        let registry = InMemoryRegistry::new();
        registry.register_resource(sample_resource());
        let res = registry.get_resource("test-resource");
        assert!(res.is_some());
        assert_eq!(res.as_ref().map(|r| r.location.as_str()), Some("/tmp/test.txt"));
    }

    #[test]
    fn remove_resource() {
        let registry = InMemoryRegistry::new();
        registry.register_resource(sample_resource());
        assert!(registry.remove_resource("test-resource"));
        assert!(registry.get_resource("test-resource").is_none());
    }

    #[test]
    fn remove_resources_batch() {
        let registry = InMemoryRegistry::new();
        registry.register_resource(ResourceEntry {
            name: "r1".to_owned(),
            ..sample_resource()
        });
        registry.register_resource(ResourceEntry {
            name: "r2".to_owned(),
            ..sample_resource()
        });
        registry.register_resource(ResourceEntry {
            name: "r3".to_owned(),
            ..sample_resource()
        });

        let removed = registry.remove_resources_batch(&["r1".to_owned(), "r3".to_owned()]);
        assert_eq!(removed, 2);
        assert!(registry.get_resource("r1").is_none());
        assert!(registry.get_resource("r2").is_some());
        assert!(registry.get_resource("r3").is_none());
    }

    #[test]
    fn register_tool_skips_injection_when_disabled() {
        let registry = InMemoryRegistry::new();
        let tool = ToolEntry {
            name: "test".to_owned(),
            description: "test tool".to_owned(),
            uri: "test://uri".to_owned(),
            type_: "test".to_owned(),
            input_schema: serde_json::json!({
                "type": "object",
                "properties": {
                    "msg": {"type": "string"}
                }
            }),
            namespace: None,
            id: None,
            configuration_uri: None,
            secrets_uri: None,
            labels: std::collections::HashMap::new(),
        };
        registry.register_tool(tool);
        let stored = registry.get_tool("test").expect("tool should exist");
        let props = stored.input_schema["properties"].as_object().expect("has properties");
        assert_eq!(props.len(), 1, "x-request-id should not be injected when flag is disabled");
    }

    #[test]
    fn register_tool_injects_when_enabled() {
        let registry = InMemoryRegistry::new();
        registry.enable_request_id_injection();
        let tool = ToolEntry {
            name: "test".to_owned(),
            description: "test tool".to_owned(),
            uri: "test://uri".to_owned(),
            type_: "test".to_owned(),
            input_schema: serde_json::json!({
                "type": "object",
                "properties": {
                    "msg": {"type": "string"}
                }
            }),
            namespace: None,
            id: None,
            configuration_uri: None,
            secrets_uri: None,
            labels: std::collections::HashMap::new(),
        };
        registry.register_tool(tool);
        let stored = registry.get_tool("test").expect("tool should exist");
        let props = stored.input_schema["properties"].as_object().expect("has properties");
        assert_eq!(props.len(), 2, "x-request-id should be injected when flag is enabled");
        assert!(props.contains_key("x-request-id"));
    }
}
