use std::collections::HashMap;
use std::sync::Arc;

use dashmap::DashMap;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ToolEntry {
    pub name: String,
    pub description: String,
    pub uri: String,
    #[serde(rename = "type")]
    pub type_: String,
    pub input_schema: serde_json::Value,
    #[serde(default)]
    pub labels: HashMap<String, String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
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

pub trait ToolRegistry: Send + Sync {
    fn list_tools(&self) -> Vec<ToolEntry>;
    fn get_tool(&self, name: &str) -> Option<ToolEntry>;
    fn register_tool(&self, tool: ToolEntry);
    fn remove_tool(&self, name: &str) -> bool;
}

pub trait ServiceRegistry: Send + Sync {
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
    services: Arc<DashMap<String, ServiceEntry>>,
}

impl InMemoryRegistry {
    pub fn new() -> Self {
        Self {
            tools: Arc::new(DashMap::new()),
            services: Arc::new(DashMap::new()),
        }
    }
}

impl Default for InMemoryRegistry {
    fn default() -> Self {
        Self::new()
    }
}

impl ToolRegistry for InMemoryRegistry {
    fn list_tools(&self) -> Vec<ToolEntry> {
        self.tools.iter().map(|entry| entry.value().clone()).collect()
    }

    fn get_tool(&self, name: &str) -> Option<ToolEntry> {
        self.tools.get(name).map(|entry| entry.value().clone())
    }

    fn register_tool(&self, tool: ToolEntry) {
        self.tools.insert(tool.name.clone(), tool);
    }

    fn remove_tool(&self, name: &str) -> bool {
        self.tools.remove(name).is_some()
    }
}

impl ServiceRegistry for InMemoryRegistry {
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
    }

    fn remove_service(&self, name: &str, service_type: &str) -> bool {
        let key = service_key(name, service_type);
        self.services.remove(&key).is_some()
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
}
