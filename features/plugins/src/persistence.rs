use std::collections::HashMap;
use std::path::PathBuf;
use std::sync::Arc;

use wanaku_types::persistence::PersistenceError;

use crate::api::PluginServiceTarget;

pub type PluginsConfigSnapshot = HashMap<String, HashMap<String, PluginServiceTarget>>;

pub trait PluginConfigPersistence: Send + Sync {
    fn load(&self) -> Result<PluginsConfigSnapshot, PersistenceError>;
    fn save(&self, snapshot: &PluginsConfigSnapshot) -> Result<(), PersistenceError>;
}

pub struct FilePluginConfigPersistence {
    path: PathBuf,
}

impl FilePluginConfigPersistence {
    #[must_use]
    pub fn new(path: impl Into<PathBuf>) -> Self {
        Self { path: path.into() }
    }

    #[must_use]
    pub fn from_config() -> Option<Arc<dyn PluginConfigPersistence>> {
        let persist = wanaku_types::config::ENV.persist.as_ref()?;
        let path = persist.dir.join("plugins-config.json");
        Some(Arc::new(Self::new(path)))
    }
}

impl PluginConfigPersistence for FilePluginConfigPersistence {
    fn load(&self) -> Result<PluginsConfigSnapshot, PersistenceError> {
        if !self.path.exists() {
            return Ok(HashMap::new());
        }
        let content = std::fs::read_to_string(&self.path)?;
        Ok(serde_json::from_str(&content)?)
    }

    fn save(&self, snapshot: &PluginsConfigSnapshot) -> Result<(), PersistenceError> {
        if let Some(parent) = self.path.parent() {
            std::fs::create_dir_all(parent)?;
        }
        let content = serde_json::to_string_pretty(snapshot)?;

        let tmp = self.path.with_extension("json.tmp");
        {
            let mut file = std::fs::File::create(&tmp)?;
            use std::io::Write;
            file.write_all(content.as_bytes())?;
            file.sync_all()?;
        }
        std::fs::rename(&tmp, &self.path)?;
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn persistence_round_trip() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("plugins-config.json");
        let persistence = FilePluginConfigPersistence::new(&path);

        let mut snapshot: PluginsConfigSnapshot = HashMap::new();
        let mut services = HashMap::new();
        services.insert(
            "chat".to_owned(),
            PluginServiceTarget {
                target: "http://localhost:8080".to_owned(),
            },
        );
        snapshot.insert("my-plugin".to_owned(), services);

        assert!(persistence.save(&snapshot).is_ok());

        let loaded = persistence.load().unwrap();
        assert_eq!(
            loaded.get("my-plugin").and_then(|s| s.get("chat")).map(|t| t.target.as_str()),
            Some("http://localhost:8080")
        );
    }

    #[test]
    fn load_nonexistent_returns_empty() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("nonexistent.json");
        let persistence = FilePluginConfigPersistence::new(&path);
        let loaded = persistence.load().unwrap();
        assert!(loaded.is_empty());
    }
}
