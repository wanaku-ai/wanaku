#![deny(unsafe_code)]

use std::path::PathBuf;
use std::sync::Arc;

use serde::{Deserialize, Serialize};

use crate::registry::{
    ForwardEntry, NamespaceEntry, PromptEntry, ResourceEntry, ServiceEntry, ToolEntry,
};

#[derive(Debug, Default, Serialize, Deserialize)]
pub struct RegistrySnapshot {
    #[serde(default)]
    pub tools: Vec<ToolEntry>,
    #[serde(default)]
    pub resources: Vec<ResourceEntry>,
    #[serde(default)]
    pub prompts: Vec<PromptEntry>,
    #[serde(default)]
    pub forwards: Vec<ForwardEntry>,
    #[serde(default)]
    pub namespaces: Vec<NamespaceEntry>,
    #[serde(default)]
    pub services: Vec<ServiceEntry>,
}

#[derive(Debug, thiserror::Error)]
pub enum PersistenceError {
    #[error("I/O error: {0}")]
    Io(#[from] std::io::Error),

    #[error("serialization error: {0}")]
    Serialization(String),
}

pub trait PersistenceBackend: Send + Sync {
    fn load(&self) -> Result<RegistrySnapshot, PersistenceError>;
    fn save(&self, snapshot: &RegistrySnapshot) -> Result<(), PersistenceError>;
}

pub struct FilePersistence {
    path: PathBuf,
}

impl FilePersistence {
    pub fn new(path: impl Into<PathBuf>) -> Self {
        Self { path: path.into() }
    }

    pub fn from_config() -> Option<Arc<dyn PersistenceBackend>> {
        let persist = crate::config::ENV.persist.as_ref()?;
        let path = persist.dir.join("registry.json");
        Some(Arc::new(Self::new(path)))
    }
}

impl PersistenceBackend for FilePersistence {
    fn load(&self) -> Result<RegistrySnapshot, PersistenceError> {
        if !self.path.exists() {
            return Ok(RegistrySnapshot::default());
        }
        let content = std::fs::read_to_string(&self.path)?;
        serde_json::from_str(&content)
            .map_err(|e| PersistenceError::Serialization(e.to_string()))
    }

    fn save(&self, snapshot: &RegistrySnapshot) -> Result<(), PersistenceError> {
        if let Some(parent) = self.path.parent() {
            std::fs::create_dir_all(parent)?;
        }
        let content = serde_json::to_string_pretty(snapshot)
            .map_err(|e| PersistenceError::Serialization(e.to_string()))?;

        let tmp = self.path.with_extension("json.tmp");
        std::fs::write(&tmp, content)?;
        std::fs::rename(&tmp, &self.path)?;
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::HashMap;

    #[test]
    fn file_round_trip() {
        let dir = std::env::temp_dir().join("wanaku-test-persist");
        let _ = std::fs::remove_dir_all(&dir);
        let path = dir.join("registry.json");
        let backend = FilePersistence::new(&path);

        let snapshot = RegistrySnapshot {
            tools: vec![ToolEntry {
                name: "test".to_owned(),
                description: "desc".to_owned(),
                uri: "test://uri".to_owned(),
                type_: "test-type".to_owned(),
                input_schema: serde_json::json!({"type": "object"}),
                labels: HashMap::new(),
                id: None,
                namespace: None,
                configuration_uri: None,
                secrets_uri: None,
                skip_safety_check: false,
            }],
            services: vec![ServiceEntry {
                name: "svc".to_owned(),
                address: "localhost:9000".to_owned(),
                service_type: "tool-invoker".to_owned(),
            }],
            ..RegistrySnapshot::default()
        };

        backend.save(&snapshot).ok();
        let loaded = backend.load().ok();
        assert!(loaded.is_some());
        let loaded = loaded.map(|s| (s.tools.len(), s.services.len()));
        assert_eq!(loaded, Some((1, 1)));

        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn load_missing_file_returns_empty() {
        let backend = FilePersistence::new("/tmp/wanaku-nonexistent-path/registry.json");
        let snapshot = backend.load();
        assert!(snapshot.is_ok());
        let loaded = snapshot.ok().map(|s| s.tools.len());
        assert_eq!(loaded, Some(0));
    }
}
