//! Persistence for evaluator configuration revisions.
//!
//! The [`RevisionStore`](crate::revision::RevisionStore) keeps revision history
//! in memory. When a persistence backend is configured, the store writes its
//! full bounded history, the active revision ID, and the next revision counter
//! to durable storage after every change, and reloads them at startup. This
//! lets revision history and rollback survive a restart.
//!
//! The file backend mirrors
//! [`wanaku_apis::persistence::FilePersistence`]: an atomic write to a
//! temporary file followed by a rename.

use std::io::Write;
use std::path::PathBuf;
use std::sync::Arc;

use serde::{Deserialize, Serialize};
use wanaku_apis::persistence::PersistenceError;

use crate::revision::{Revision, RevisionId};

/// The persistent form of a revision store: the full history plus the active
/// revision ID and the next revision counter needed to resume ID assignment
/// after a restart.
#[derive(Debug, Default, Serialize, Deserialize)]
pub struct RevisionsSnapshot {
    #[serde(default)]
    pub revisions: Vec<Revision>,
    #[serde(default)]
    pub active_id: Option<RevisionId>,
    /// The next revision ID to assign. Persisted so IDs stay monotonic across
    /// restarts even after history trimming removes the highest-numbered
    /// revisions from `revisions`.
    #[serde(default)]
    pub next_id: RevisionId,
}

/// A backend that loads and stores evaluator revision history.
pub trait RevisionPersistence: Send + Sync {
    fn load(&self) -> Result<RevisionsSnapshot, PersistenceError>;
    fn save(&self, snapshot: &RevisionsSnapshot) -> Result<(), PersistenceError>;
}

/// File-backed revision persistence. Writes a single JSON document with an
/// atomic temporary-file-and-rename to avoid leaving a partial file if the
/// process dies mid-write.
pub struct FileRevisionPersistence {
    path: PathBuf,
}

impl FileRevisionPersistence {
    pub fn new(path: impl Into<PathBuf>) -> Self {
        Self { path: path.into() }
    }

    /// Build a file backend from the process persistence configuration.
    /// Returns `None` when persistence is disabled, so the store stays purely
    /// in memory. Uses the same directory as the registry snapshot.
    pub fn from_config() -> Option<Arc<dyn RevisionPersistence>> {
        let persist = wanaku_apis::config::ENV.persist.as_ref()?;
        let path = persist.dir.join("evaluator-revisions.json");
        Some(Arc::new(Self::new(path)))
    }
}

impl RevisionPersistence for FileRevisionPersistence {
    fn load(&self) -> Result<RevisionsSnapshot, PersistenceError> {
        if !self.path.exists() {
            return Ok(RevisionsSnapshot::default());
        }
        let content = std::fs::read_to_string(&self.path)?;
        Ok(serde_json::from_str(&content)?)
    }

    fn save(&self, snapshot: &RevisionsSnapshot) -> Result<(), PersistenceError> {
        if let Some(parent) = self.path.parent() {
            std::fs::create_dir_all(parent)?;
        }
        let content = serde_json::to_string_pretty(snapshot)?;

        // Write, flush, and fsync the temporary file before the rename. Without
        // the fsync the rename can become durable before the file contents do,
        // so a crash right after the rename can leave a truncated or empty final
        // file that silently wipes revision history on the next load.
        let tmp = self.path.with_extension("json.tmp");
        {
            let mut file = std::fs::File::create(&tmp)?;
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
    fn load_missing_file_returns_empty() {
        let backend =
            FileRevisionPersistence::new("/tmp/wanaku-nonexistent-revisions/evaluator-revisions.json");
        let snapshot = backend.load();
        assert!(snapshot.is_ok(), "loading missing file should return Ok");
        if let Ok(s) = snapshot {
            assert!(s.revisions.is_empty());
            assert!(s.active_id.is_none());
        }
    }

    #[test]
    fn file_round_trip() {
        let dir = std::env::temp_dir().join("wanaku-test-revision-persist");
        let _ = std::fs::remove_dir_all(&dir);
        let path = dir.join("evaluator-revisions.json");
        let backend = FileRevisionPersistence::new(&path);

        let snapshot = RevisionsSnapshot {
            revisions: Vec::new(),
            active_id: Some(7),
            next_id: 8,
        };

        assert!(backend.save(&snapshot).is_ok(), "save should succeed");
        let loaded = backend.load();
        assert!(loaded.is_ok(), "load should succeed");
        if let Ok(s) = loaded {
            assert_eq!(s.active_id, Some(7));
            assert_eq!(s.next_id, 8);
        }

        let _ = std::fs::remove_dir_all(&dir);
    }
}
