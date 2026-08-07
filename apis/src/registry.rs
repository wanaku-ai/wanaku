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
    #[serde(alias = "inputSchema")]
    pub input_schema: serde_json::Value,
    #[serde(default)]
    pub labels: HashMap<String, String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub id: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub namespace: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none", alias = "configurationURI")]
    pub configuration_uri: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none", alias = "secretsURI")]
    pub secrets_uri: Option<String>,
    #[serde(default, skip_serializing_if = "std::ops::Not::not", alias = "skipSafetyCheck")]
    pub skip_safety_check: bool,
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
    #[serde(default, alias = "mimeType")]
    pub mime_type: String,
    #[serde(default)]
    pub labels: HashMap<String, String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub id: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub namespace: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none", alias = "configurationURI")]
    pub configuration_uri: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none", alias = "secretsURI")]
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
    #[serde(default, skip_serializing_if = "Option::is_none", alias = "configurationURI")]
    pub configuration_uri: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, utoipa::ToSchema)]
pub struct ServiceEntry {
    pub name: String,
    pub address: String,
    pub service_type: String,
}

#[derive(Debug, PartialEq, Eq, thiserror::Error)]
pub enum RegistryError {
    #[error("tool not found: {0}")]
    ToolNotFound(String),

    #[error("no service available for tool type '{tool_type}' with service type '{service_type}'")]
    ServiceNotFound {
        tool_type: String,
        service_type: String,
    },
}

#[derive(Debug, Clone, Serialize, Deserialize, utoipa::ToSchema)]
pub struct ForwardEntry {
    pub name: String,
    pub address: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub namespace: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, utoipa::ToSchema)]
pub struct NamespaceEntry {
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub id: Option<String>,
    pub name: String,
    #[serde(default)]
    pub path: String,
    #[serde(default)]
    pub labels: HashMap<String, String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub auth_required: Option<bool>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub audience: Option<String>,
}

pub const MCP_FORWARD_TYPE: &str = "mcp-forward";

impl ToolEntry {
    pub fn is_mcp_forward(&self) -> bool {
        self.type_ == MCP_FORWARD_TYPE
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
    fn remove_resource(&self, name: &str) -> bool;
    fn resource_count(&self) -> usize;
}

pub trait PromptRegistry: Send + Sync {
    fn list_prompts(&self) -> Vec<PromptEntry>;
    fn list_prompts_in_namespace(&self, namespace: &str) -> Vec<PromptEntry>;
    fn get_prompt(&self, name: &str) -> Option<PromptEntry>;
    fn get_prompt_in_namespace(&self, namespace: &str, name: &str) -> Option<PromptEntry>;
    fn register_prompt(&self, prompt: PromptEntry);
    fn remove_prompt(&self, name: &str) -> bool;
    fn prompt_count(&self) -> usize;
}

pub trait NamespaceRegistry: Send + Sync {
    fn list_namespaces(&self) -> Vec<NamespaceEntry>;
    fn get_namespace(&self, name: &str) -> Option<NamespaceEntry>;
    fn register_namespace(&self, namespace: NamespaceEntry);
    fn remove_namespace(&self, name: &str) -> bool;
}

pub trait ForwardRegistry: Send + Sync {
    fn list_forwards(&self) -> Vec<ForwardEntry>;
    fn get_forward(&self, name: &str) -> Option<ForwardEntry>;
    fn register_forward(&self, forward: ForwardEntry);
    fn remove_forward(&self, name: &str) -> bool;
}

pub trait ServiceRegistry: Send + Sync {
    fn list_services(&self) -> Vec<ServiceEntry>;
    fn get_service(&self, name: &str, service_type: &str) -> Option<ServiceEntry>;

    fn resolve_service(
        &self,
        tool_type: &str,
        service_type: &str,
    ) -> Result<ServiceEntry, RegistryError>;

    fn register_service(&self, service: ServiceEntry);
    fn remove_service(&self, name: &str, service_type: &str) -> bool;
}

fn service_key(name: &str, service_type: &str) -> String {
    format!("{name}:{service_type}")
}

#[derive(Clone)]
pub struct InMemoryRegistry {
    tools: Arc<DashMap<String, ToolEntry>>,
    resources: Arc<DashMap<String, ResourceEntry>>,
    prompts: Arc<DashMap<String, PromptEntry>>,
    forwards: Arc<DashMap<String, ForwardEntry>>,
    namespaces: Arc<DashMap<String, NamespaceEntry>>,
    services: Arc<DashMap<String, ServiceEntry>>,
    persistence: Option<Arc<dyn PersistenceBackend>>,
    inject_request_id: Arc<AtomicBool>,
}

impl InMemoryRegistry {
    pub fn new() -> Self {
        Self {
            tools: Arc::new(DashMap::new()),
            resources: Arc::new(DashMap::new()),
            prompts: Arc::new(DashMap::new()),
            forwards: Arc::new(DashMap::new()),
            namespaces: Arc::new(DashMap::new()),
            services: Arc::new(DashMap::new()),
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
    pub fn load_persisted(&self) {
        let backend = match &self.persistence {
            Some(b) => b,
            None => return,
        };

        let snapshot = match backend.load() {
            Ok(s) => s,
            Err(e) => {
                tracing::warn!(error = %e, "failed to load persisted registry");
                return;
            }
        };

        for tool in snapshot.tools {
            self.register_tool(tool);
        }
        for resource in snapshot.resources {
            self.register_resource(resource);
        }
        for prompt in snapshot.prompts {
            self.register_prompt(prompt);
        }
        for forward in snapshot.forwards {
            self.register_forward(forward);
        }
        for namespace in snapshot.namespaces {
            self.register_namespace(namespace);
        }
        for service in snapshot.services {
            self.register_service(service);
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
            services: self.services.iter().map(|e| e.value().clone()).collect(),
        }
    }

    fn persist(&self) {
        if let Some(backend) = &self.persistence {
            if let Err(e) = backend.save(&self.snapshot()) {
                tracing::warn!(error = %e, "failed to persist registry");
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

    fn remove_resource(&self, name: &str) -> bool {
        let removed = self.resources.remove(name).is_some();
        if removed {
            self.persist();
        }
        removed
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

    fn remove_prompt(&self, name: &str) -> bool {
        let removed = self.prompts.remove(name).is_some();
        if removed {
            self.persist();
        }
        removed
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

    fn register_namespace(&self, mut namespace: NamespaceEntry) {
        if namespace.id.is_none() {
            namespace.id = Some(namespace.name.clone());
        }
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

    fn register_forward(&self, forward: ForwardEntry) {
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

impl ServiceRegistry for InMemoryRegistry {
    fn list_services(&self) -> Vec<ServiceEntry> {
        self.services.iter().map(|e| e.value().clone()).collect()
    }

    fn get_service(&self, name: &str, service_type: &str) -> Option<ServiceEntry> {
        let key = service_key(name, service_type);
        self.services.get(&key).map(|e| e.value().clone())
    }

    fn resolve_service(
        &self,
        tool_type: &str,
        service_type: &str,
    ) -> Result<ServiceEntry, RegistryError> {
        let key = service_key(tool_type, service_type);
        self.services
            .get(&key)
            .map(|entry| entry.value().clone())
            .ok_or_else(|| RegistryError::ServiceNotFound {
                tool_type: tool_type.to_owned(),
                service_type: service_type.to_owned(),
            })
    }

    fn register_service(&self, service: ServiceEntry) {
        let key = service_key(&service.name, &service.service_type);
        self.services.insert(key, service);
        self.persist();
    }

    fn remove_service(&self, name: &str, service_type: &str) -> bool {
        let key = service_key(name, service_type);
        let removed = self.services.remove(&key).is_some();
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
            skip_safety_check: false,
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

    fn sample_service() -> ServiceEntry {
        ServiceEntry {
            name: "http".to_owned(),
            address: "localhost:9090".to_owned(),
            service_type: "tool-invoker".to_owned(),
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
    fn resolve_service_by_type() {
        let registry = InMemoryRegistry::new();
        registry.register_service(sample_service());
        let svc = registry.resolve_service("http", "tool-invoker");
        assert!(svc.is_ok());
        assert_eq!(svc.as_ref().map(|s| s.address.as_str()), Ok("localhost:9090"));
    }

    #[test]
    fn resolve_missing_service_returns_error() {
        let registry = InMemoryRegistry::new();
        let result = registry.resolve_service("nonexistent", "tool-invoker");
        assert!(result.is_err());
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
    fn different_service_types_coexist() {
        let registry = InMemoryRegistry::new();
        registry.register_service(ServiceEntry {
            name: "http".to_owned(),
            address: "localhost:9090".to_owned(),
            service_type: "tool-invoker".to_owned(),
        });
        registry.register_service(ServiceEntry {
            name: "http".to_owned(),
            address: "localhost:9091".to_owned(),
            service_type: "resource-provider".to_owned(),
        });
        let tool_svc = registry.resolve_service("http", "tool-invoker");
        let res_svc = registry.resolve_service("http", "resource-provider");
        assert!(tool_svc.is_ok());
        assert!(res_svc.is_ok());
        assert_eq!(tool_svc.map(|s| s.address), Ok("localhost:9090".to_owned()));
        assert_eq!(res_svc.map(|s| s.address), Ok("localhost:9091".to_owned()));
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
            skip_safety_check: false,
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
            skip_safety_check: false,
            labels: std::collections::HashMap::new(),
        };
        registry.register_tool(tool);
        let stored = registry.get_tool("test").expect("tool should exist");
        let props = stored.input_schema["properties"].as_object().expect("has properties");
        assert_eq!(props.len(), 2, "x-request-id should be injected when flag is enabled");
        assert!(props.contains_key("x-request-id"));
    }
}
