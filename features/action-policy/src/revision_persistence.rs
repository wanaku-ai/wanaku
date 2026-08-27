use std::io::Write;
use std::path::PathBuf;
use std::sync::Arc;

use wanaku_types::persistence::PersistenceError;

use crate::revision::ActionPolicyRevision;

pub type RevisionsSnapshot = wanaku_types::revision::RevisionSnapshot<ActionPolicyRevision>;

pub trait RevisionPersistence: Send + Sync {
    fn load(&self) -> Result<RevisionsSnapshot, PersistenceError>;
    fn save(&self, snapshot: &RevisionsSnapshot) -> Result<(), PersistenceError>;
}

pub struct FileRevisionPersistence {
    path: PathBuf,
}

impl FileRevisionPersistence {
    pub fn new(path: impl Into<PathBuf>) -> Self {
        Self { path: path.into() }
    }

    pub fn from_config() -> Option<Arc<dyn RevisionPersistence>> {
        let persist = wanaku_types::config::ENV.persist.as_ref()?;
        Some(Arc::new(Self::new(
            persist.dir.join("action-policy-revisions.json"),
        )))
    }
}

impl RevisionPersistence for FileRevisionPersistence {
    fn load(&self) -> Result<RevisionsSnapshot, PersistenceError> {
        if !self.path.exists() {
            return Ok(RevisionsSnapshot::default());
        }
        Ok(serde_json::from_str(&std::fs::read_to_string(&self.path)?)?)
    }

    fn save(&self, snapshot: &RevisionsSnapshot) -> Result<(), PersistenceError> {
        if let Some(parent) = self.path.parent() {
            std::fs::create_dir_all(parent)?;
        }
        let content = serde_json::to_string_pretty(snapshot)?;
        let temporary = self.path.with_extension("json.tmp");
        {
            let mut file = std::fs::File::create(&temporary)?;
            file.write_all(content.as_bytes())?;
            file.sync_all()?;
        }
        std::fs::rename(temporary, &self.path)?;
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn missing_file_is_empty_and_snapshot_round_trips() {
        let directory = std::env::temp_dir().join(format!(
            "wanaku-action-policy-revisions-{}",
            std::process::id()
        ));
        let path = directory.join("action-policy-revisions.json");
        let backend = FileRevisionPersistence::new(&path);
        let empty = backend.load().expect("missing file");
        assert!(empty.revisions.is_empty());

        let snapshot = RevisionsSnapshot {
            revisions: Vec::new(),
            active_id: Some(7),
            next_id: 8,
        };
        backend.save(&snapshot).expect("save");
        let loaded = backend.load().expect("load");
        assert_eq!(loaded.active_id, Some(7));
        assert_eq!(loaded.next_id, 8);
        let _ = std::fs::remove_dir_all(directory);
    }
}
