#![allow(clippy::large_stack_frames, reason = "utoipa::ToSchema derive generates large stack frames")]

use std::collections::HashMap;
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};

use dashmap::DashMap;
use serde::{Deserialize, Serialize};

use crate::persistence::{PersistenceBackend, RegistrySnapshot};

#[derive(Debug, Clone, Serialize, Deserialize, utoipa::ToSchema)]
pub struct ToolEntry {
    pub name: String,
    pub description: String,
    pub uri: String,
    #[serde(rename = "type")]
    #[schema(rename = "type")]
    pub type_: String,
    #[serde(rename = "inputSchema", alias = "input_schema")]
    pub input_schema: serde_json::Value,
    #[serde(default)]
    pub labels: HashMap<String, String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub id: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub namespace: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none", rename = "configurationURI", alias = "configuration_uri")]
    pub configuration_uri: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none", rename = "secretsURI", alias = "secrets_uri")]
    pub secrets_uri: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, utoipa::ToSchema)]
pub struct ResourceEntry {
    pub name: String,
    #[serde(default)]
    pub description: String,
    pub location: String,
    #[serde(rename = "type")]
    #[schema(rename = "type")]
    pub type_: String,
    #[serde(default, rename = "mimeType", alias = "mime_type")]
    pub mime_type: String,
    #[serde(default)]
    pub labels: HashMap<String, String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub id: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub namespace: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none", rename = "configurationURI", alias = "configuration_uri")]
    pub configuration_uri: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none", rename = "secretsURI", alias = "secrets_uri")]
    pub secrets_uri: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, utoipa::ToSchema)]
pub struct PromptArgument {
    pub name: String,
    #[serde(default)]
    pub description: String,
    #[serde(default)]
    pub required: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize, utoipa::ToSchema)]
#[serde(rename_all = "lowercase")]
pub enum PromptRole {
    User,
    Assistant,
    System,
}

#[derive(Debug, Clone, Serialize, Deserialize, utoipa::ToSchema)]
pub struct PromptMessage {
    pub role: PromptRole,
    pub content: serde_json::Value,
}

#[derive(Debug, Clone, Serialize, Deserialize, utoipa::ToSchema)]
pub struct PromptEntry {
    pub name: String,
    #[serde(default)]
    pub description: String,
    #[serde(default)]
    pub arguments: Vec<PromptArgument>,
    #[serde(default)]
    pub messages: Vec<PromptMessage>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub id: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub namespace: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none", rename = "configurationURI", alias = "configuration_uri")]
    pub configuration_uri: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, utoipa::ToSchema)]
pub struct McpServerInfo {
    #[serde(rename = "serverName", alias = "server_name")]
    pub server_name: String,
    pub version: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub description: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none", rename = "websiteUrl", alias = "website_url")]
    pub website_url: Option<String>,
    #[serde(default)]
    pub capabilities: Vec<String>,
    #[serde(default)]
    pub extensions: Vec<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub instructions: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, utoipa::ToSchema)]
pub struct ForwardEntry {
    pub name: String,
    pub address: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub namespace: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none", rename = "serverInfo", alias = "server_info")]
    pub server_info: Option<McpServerInfo>,
    #[serde(default)]
    pub labels: HashMap<String, String>,
    #[serde(default = "default_available")]
    pub available: bool,
    #[serde(default, skip_serializing_if = "Option::is_none", rename = "statusMessage", alias = "status_message")]
    pub status_message: Option<String>,
}

fn default_available() -> bool {
    true
}

#[derive(Debug, Clone, Serialize, Deserialize, utoipa::ToSchema)]
pub struct NamespaceEntry {
    #[serde(alias = "path")]
    pub name: String,
    #[serde(default)]
    pub labels: HashMap<String, String>,
    #[serde(default, skip_serializing_if = "Option::is_none", rename = "authRequired", alias = "auth_required")]
    pub auth_required: Option<bool>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub audience: Option<String>,
}

/// Validates that a namespace name follows DNS-label conventions:
/// lowercase alphanumeric and hyphens, 1-63 chars, must start and end
/// with an alphanumeric character. The name doubles as the URL path
/// segment and unique identifier.
pub fn validate_namespace_name(name: &str) -> Result<(), String> {
    if name.is_empty() {
        return Err("namespace name must not be empty".to_owned());
    }

    if name.len() > 63 {
        return Err(format!(
            "namespace name must be at most 63 characters, got {}",
            name.len()
        ));
    }

    if !name.bytes().all(|b| b.is_ascii_lowercase() || b.is_ascii_digit() || b == b'-') {
        return Err(
            "namespace name must contain only lowercase alphanumeric characters and hyphens"
                .to_owned(),
        );
    }

    if name.starts_with('-') || name.ends_with('-') {
        return Err("namespace name must not start or end with a hyphen".to_owned());
    }

    Ok(())
}

pub const MCP_FORWARD_TYPE: &str = "mcp-forward";

pub const FORWARD_ADDRESS_LABEL: &str = "wanaku.forward_address";
pub const IS_TEMPLATE_LABEL: &str = "wanaku.is_template";
pub const FORWARD_HEADERS_LABEL: &str = "wanaku.forward_headers";

impl ToolEntry {
    pub fn is_mcp_forward(&self) -> bool {
        self.type_ == MCP_FORWARD_TYPE
    }

    pub fn forward_headers(&self) -> Vec<String> {
        self.labels
            .get(FORWARD_HEADERS_LABEL)
            .map(|v| {
                v.split(',')
                    .map(|s| s.trim().to_lowercase())
                    .filter(|s| !s.is_empty())
                    .collect()
            })
            .unwrap_or_default()
    }
}

impl ResourceEntry {
    pub fn is_mcp_forward(&self) -> bool {
        self.type_ == MCP_FORWARD_TYPE
    }

    pub fn is_template(&self) -> bool {
        self.labels.get(IS_TEMPLATE_LABEL).is_some_and(|v| v == "true")
    }

    pub fn forward_address(&self) -> Option<&str> {
        self.labels.get(FORWARD_ADDRESS_LABEL).map(std::string::String::as_str)
    }
}

pub const DEFAULT_NAMESPACE: &str = "default";

fn inject_request_id_arg(schema: &mut serde_json::Value) {
    let arg = crate::correlation::REQUEST_ID_ARG;

    if let Some(props) = schema.get_mut("properties").and_then(|p| p.as_object_mut()) {
        props.entry(arg).or_insert_with(|| {
            serde_json::json!({
                "type": "string",
                "description": "Conversation tracking ID provided in the system prompt"
            })
        });
    }

    if let Some(required) = schema.get_mut("required").and_then(|r| r.as_array_mut()) {
        if !required.iter().any(|v| v.as_str() == Some(arg)) {
            required.push(serde_json::Value::String(arg.to_owned()));
        }
    } else {
        if let Some(obj) = schema.as_object_mut() {
            obj.insert(
                "required".to_owned(),
                serde_json::json!([arg]),
            );
        }
    }
}

pub trait ToolRegistry: Send + Sync {
    fn list_tools(&self) -> Vec<ToolEntry>;
    fn list_tools_in_namespace(&self, namespace: &str) -> Vec<ToolEntry>;
    fn get_tool(&self, name: &str) -> Option<ToolEntry>;
    fn get_tool_in_namespace(&self, namespace: &str, name: &str) -> Option<ToolEntry>;
    fn register_tool(&self, tool: ToolEntry);
    fn register_tools_batch(&self, tools: Vec<ToolEntry>);
    fn remove_tool(&self, name: &str) -> bool;
    fn remove_tools_batch(&self, names: &[String]) -> usize;
    fn tool_count(&self) -> usize;
}

pub trait ResourceRegistry: Send + Sync {
    fn list_resources(&self) -> Vec<ResourceEntry>;
    fn list_resources_in_namespace(&self, namespace: &str) -> Vec<ResourceEntry>;
    fn get_resource(&self, name: &str) -> Option<ResourceEntry>;
    fn get_resource_in_namespace(&self, namespace: &str, name: &str) -> Option<ResourceEntry>;
    fn register_resource(&self, resource: ResourceEntry);
    fn register_resources_batch(&self, resources: Vec<ResourceEntry>);
    fn remove_resource(&self, name: &str) -> bool;
    fn remove_resources_batch(&self, names: &[String]) -> usize;
    fn resource_count(&self) -> usize;
}

pub trait PromptRegistry: Send + Sync {
    fn list_prompts(&self) -> Vec<PromptEntry>;
    fn list_prompts_in_namespace(&self, namespace: &str) -> Vec<PromptEntry>;
    fn get_prompt(&self, name: &str) -> Option<PromptEntry>;
    fn get_prompt_in_namespace(&self, namespace: &str, name: &str) -> Option<PromptEntry>;
    fn register_prompt(&self, prompt: PromptEntry);
    fn register_prompts_batch(&self, prompts: Vec<PromptEntry>);
    fn remove_prompt(&self, name: &str) -> bool;
    fn remove_prompts_batch(&self, names: &[String]) -> usize;
    fn prompt_count(&self) -> usize;
}

pub trait NamespaceRegistry: Send + Sync {
    fn list_namespaces(&self) -> Vec<NamespaceEntry>;
    fn get_namespace(&self, path: &str) -> Option<NamespaceEntry>;
    fn register_namespace(&self, namespace: NamespaceEntry);
    fn remove_namespace(&self, path: &str) -> bool;
}

pub trait ForwardRegistry: Send + Sync {
    fn list_forwards(&self) -> Vec<ForwardEntry>;
    fn get_forward(&self, name: &str) -> Option<ForwardEntry>;
    fn register_forward(&self, forward: ForwardEntry);
    fn remove_forward(&self, name: &str) -> bool;
}

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
    fn resource_is_mcp_forward() {
        let mut resource = sample_resource();
        assert!(!resource.is_mcp_forward());

        resource.type_ = MCP_FORWARD_TYPE.to_owned();
        assert!(resource.is_mcp_forward());
    }

    #[test]
    fn resource_forward_address_from_labels() {
        let mut resource = sample_resource();
        assert!(resource.forward_address().is_none());

        resource.labels.insert(
            FORWARD_ADDRESS_LABEL.to_owned(),
            "http://remote:8080/mcp".to_owned(),
        );
        assert_eq!(resource.forward_address(), Some("http://remote:8080/mcp"));
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
    fn tool_forward_headers_from_label() {
        let mut tool = sample_tool();
        assert!(tool.forward_headers().is_empty());

        tool.labels.insert(
            FORWARD_HEADERS_LABEL.to_owned(),
            "Authorization, DPoP".to_owned(),
        );
        let headers = tool.forward_headers();
        assert_eq!(headers.len(), 2);
        assert!(headers.contains(&"authorization".to_owned()));
        assert!(headers.contains(&"dpop".to_owned()));
    }

    #[test]
    fn tool_forward_headers_empty_value() {
        let mut tool = sample_tool();
        tool.labels.insert(FORWARD_HEADERS_LABEL.to_owned(), String::new());
        assert!(tool.forward_headers().is_empty());
    }

    #[test]
    fn inject_request_id_adds_property_and_required() {
        let mut schema = serde_json::json!({
            "type": "object",
            "properties": {
                "message": {"type": "string"}
            }
        });
        super::inject_request_id_arg(&mut schema);

        let props = schema["properties"].as_object().map(|m| m.len());
        assert_eq!(props, Some(2));
        assert!(schema["properties"]["x-request-id"].is_object());
        assert_eq!(schema["required"], serde_json::json!(["x-request-id"]));
    }

    #[test]
    fn inject_request_id_appends_to_existing_required() {
        let mut schema = serde_json::json!({
            "type": "object",
            "properties": {
                "message": {"type": "string"}
            },
            "required": ["message"]
        });
        super::inject_request_id_arg(&mut schema);

        let required = schema["required"].as_array().map(|a| a.len());
        assert_eq!(required, Some(2));
    }

    #[test]
    fn inject_request_id_does_not_duplicate() {
        let mut schema = serde_json::json!({
            "type": "object",
            "properties": {
                "x-request-id": {"type": "string"}
            },
            "required": ["x-request-id"]
        });
        super::inject_request_id_arg(&mut schema);

        let props = schema["properties"].as_object().map(|m| m.len());
        assert_eq!(props, Some(1));
        let required = schema["required"].as_array().map(|a| a.len());
        assert_eq!(required, Some(1));
    }

    #[test]
    fn inject_request_id_handles_empty_object_schema() {
        let mut schema = serde_json::json!({"type": "object"});
        super::inject_request_id_arg(&mut schema);

        assert_eq!(schema["required"], serde_json::json!(["x-request-id"]));
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

    #[test]
    fn mcp_server_info_serde_round_trip() {
        let info = McpServerInfo {
            server_name: "apache-camel".to_owned(),
            version: "4.22.0".to_owned(),
            description: Some("Apache Camel MCP server".to_owned()),
            website_url: Some("https://camel.apache.org".to_owned()),
            capabilities: vec!["tools".to_owned(), "resources".to_owned()],
            extensions: vec!["io.apache.camel/routes".to_owned()],
            instructions: Some("A Camel MCP server".to_owned()),
        };

        let json = serde_json::to_string(&info).expect("serialize");
        let parsed: McpServerInfo = serde_json::from_str(&json).expect("deserialize");

        assert_eq!(parsed.server_name, "apache-camel");
        assert_eq!(parsed.version, "4.22.0");
        assert_eq!(parsed.description.as_deref(), Some("Apache Camel MCP server"));
        assert_eq!(parsed.website_url.as_deref(), Some("https://camel.apache.org"));
        assert_eq!(parsed.capabilities.len(), 2);
        assert_eq!(parsed.extensions.len(), 1);
        assert_eq!(parsed.instructions.as_deref(), Some("A Camel MCP server"));
    }

    #[test]
    fn mcp_server_info_camel_case_keys() {
        let info = McpServerInfo {
            server_name: "test".to_owned(),
            version: "1.0".to_owned(),
            description: None,
            website_url: Some("https://example.com".to_owned()),
            capabilities: Vec::new(),
            extensions: Vec::new(),
            instructions: None,
        };

        let json = serde_json::to_string(&info).expect("serialize");
        assert!(json.contains("\"serverName\""), "expected camelCase key");
        assert!(!json.contains("\"server_name\""), "unexpected snake_case key");
        assert!(json.contains("\"websiteUrl\""), "expected camelCase websiteUrl");
        assert!(!json.contains("\"website_url\""), "unexpected snake_case website_url");
    }

    #[test]
    fn forward_entry_with_server_info_round_trip() {
        let json = r#"{
            "name": "camel-prod",
            "address": "http://localhost:8180/mcp",
            "serverInfo": {
                "serverName": "apache-camel",
                "version": "4.22.0",
                "capabilities": ["tools"]
            },
            "labels": {"type": "camel"}
        }"#;

        let entry: ForwardEntry = serde_json::from_str(json).expect("deserialize");
        assert_eq!(entry.name, "camel-prod");
        assert!(entry.server_info.is_some());
        let si = entry.server_info.as_ref().unwrap();
        assert_eq!(si.server_name, "apache-camel");
        assert_eq!(entry.labels.get("type").map(|s| s.as_str()), Some("camel"));
    }

    #[test]
    fn forward_entry_without_server_info_backward_compat() {
        let json = r#"{"name": "legacy", "address": "http://x:1"}"#;
        let entry: ForwardEntry = serde_json::from_str(json).expect("deserialize");
        assert_eq!(entry.name, "legacy");
        assert!(entry.server_info.is_none());
        assert!(entry.labels.is_empty());
    }

    #[test]
    fn validate_namespace_name_valid() {
        assert!(validate_namespace_name("finance").is_ok());
        assert!(validate_namespace_name("my-ns").is_ok());
        assert!(validate_namespace_name("team-42").is_ok());
        assert!(validate_namespace_name("a").is_ok());
        assert!(validate_namespace_name("default").is_ok());
    }

    #[test]
    fn validate_namespace_name_empty() {
        assert!(validate_namespace_name("").is_err());
    }

    #[test]
    fn validate_namespace_name_too_long() {
        let long = "a".repeat(64);
        assert!(validate_namespace_name(&long).is_err());
        let max = "a".repeat(63);
        assert!(validate_namespace_name(&max).is_ok());
    }

    #[test]
    fn validate_namespace_name_invalid_chars() {
        assert!(validate_namespace_name("Finance").is_err());
        assert!(validate_namespace_name("my ns").is_err());
        assert!(validate_namespace_name("my_ns").is_err());
        assert!(validate_namespace_name("a.b").is_err());
        assert!(validate_namespace_name("ns!").is_err());
    }

    #[test]
    fn validate_namespace_name_leading_trailing_hyphen() {
        assert!(validate_namespace_name("-start").is_err());
        assert!(validate_namespace_name("end-").is_err());
        assert!(validate_namespace_name("-both-").is_err());
    }

    #[test]
    fn namespace_backward_compat_path_alias() {
        let json = r#"{"path": "finance"}"#;
        let ns: NamespaceEntry = serde_json::from_str(json).expect("should deserialize with path alias");
        assert_eq!(ns.name, "finance");
    }

    #[test]
    fn namespace_name_field() {
        let json = r#"{"name": "finance"}"#;
        let ns: NamespaceEntry = serde_json::from_str(json).expect("deserialize");
        assert_eq!(ns.name, "finance");
    }
}
