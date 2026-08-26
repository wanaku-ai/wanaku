use serde::{Deserialize, Serialize};

use crate::registry::{
    ForwardEntry, NamespaceEntry, PromptEntry, ResourceEntry, ToolEntry,
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
}

#[derive(Debug, thiserror::Error)]
pub enum PersistenceError {
    #[error("I/O error: {0}")]
    Io(#[from] std::io::Error),

    #[error("serialization error: {0}")]
    Serialization(#[from] serde_json::Error),
}

pub trait PersistenceBackend: Send + Sync {
    fn load(&self) -> Result<RegistrySnapshot, PersistenceError>;
    fn save(&self, snapshot: &RegistrySnapshot) -> Result<(), PersistenceError>;
}
