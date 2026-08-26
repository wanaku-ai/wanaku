use std::path::PathBuf;
use std::sync::Arc;

use wanaku_types::persistence::{PersistenceBackend, PersistenceError, RegistrySnapshot};

pub struct FilePersistence {
    path: PathBuf,
}

impl FilePersistence {
    pub fn new(path: impl Into<PathBuf>) -> Self {
        Self { path: path.into() }
    }

    pub fn from_config() -> Option<Arc<dyn PersistenceBackend>> {
        let persist = wanaku_types::config::ENV.persist.as_ref()?;
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
        Ok(serde_json::from_str(&content)?)
    }

    fn save(&self, snapshot: &RegistrySnapshot) -> Result<(), PersistenceError> {
        if let Some(parent) = self.path.parent() {
            std::fs::create_dir_all(parent)?;
        }
        let content = serde_json::to_string_pretty(snapshot)?;

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

    use wanaku_types::registry::ToolEntry;

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
            }],
            ..RegistrySnapshot::default()
        };

        assert!(backend.save(&snapshot).is_ok(), "save should succeed");
        let loaded = backend.load();
        assert!(loaded.is_ok(), "load should succeed");
        if let Ok(s) = loaded {
            assert_eq!(s.tools.len(), 1);
        }

        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn load_missing_file_returns_empty() {
        let backend = FilePersistence::new("/tmp/wanaku-nonexistent-path/registry.json");
        let snapshot = backend.load();
        assert!(snapshot.is_ok(), "loading missing file should return Ok");
        if let Ok(s) = snapshot {
            assert_eq!(s.tools.len(), 0);
        }
    }
}
