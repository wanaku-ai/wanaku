use std::io::Write;
use std::path::PathBuf;
use std::sync::{Arc, Mutex};

use wanaku_types::audit::{AuditEvent, AuditPersistence};

pub struct FileAuditPersistence {
    path: PathBuf,
    write_lock: Mutex<()>,
}

impl FileAuditPersistence {
    #[must_use]
    pub fn new(path: impl Into<PathBuf>) -> Self {
        Self {
            path: path.into(),
            write_lock: Mutex::new(()),
        }
    }

    pub fn from_config() -> Option<Arc<dyn AuditPersistence>> {
        let persist = wanaku_types::config::ENV.persist.as_ref()?;
        Some(Arc::new(Self::new(persist.dir.join("audit-events.json"))))
    }
}

impl AuditPersistence for FileAuditPersistence {
    fn load(&self) -> Result<Vec<AuditEvent>, String> {
        if !self.path.exists() {
            return Ok(Vec::new());
        }
        let content = std::fs::read_to_string(&self.path).map_err(|error| error.to_string())?;
        serde_json::from_str(&content).map_err(|error| error.to_string())
    }

    fn save(&self, events: &[AuditEvent]) -> Result<(), String> {
        let _guard = match self.write_lock.lock() {
            Ok(guard) => guard,
            Err(error) => {
                tracing::error!(error = %error, "audit persistence write lock poisoned");
                error.into_inner()
            }
        };
        if let Some(parent) = self.path.parent() {
            std::fs::create_dir_all(parent).map_err(|error| error.to_string())?;
        }
        let content = serde_json::to_vec_pretty(events).map_err(|error| error.to_string())?;
        let temporary = self.path.with_extension("json.tmp");
        {
            let mut file = std::fs::File::create(&temporary).map_err(|error| error.to_string())?;
            file.write_all(&content)
                .map_err(|error| error.to_string())?;
            file.sync_all().map_err(|error| error.to_string())?;
        }
        std::fs::rename(temporary, &self.path).map_err(|error| error.to_string())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use wanaku_types::audit::{AuditCategory, AuditDecision, AuditPersistence};

    #[test]
    fn events_survive_file_round_trip() {
        let path = std::env::temp_dir().join(format!("wanaku-audit-{}.json", std::process::id()));
        let backend = FileAuditPersistence::new(&path);
        let event = wanaku_types::audit::AuditEvent::new(
            AuditCategory::Decision,
            AuditDecision::Allow,
            "tools/call",
            "allowed",
            "Allowed",
        );
        assert!(backend.save(std::slice::from_ref(&event)).is_ok());
        let loaded = backend.load();
        assert!(loaded.is_ok());
        if let Ok(events) = loaded {
            assert_eq!(events, vec![event]);
        }
        let _ = std::fs::remove_file(path);
    }

    #[test]
    fn save_recovers_the_write_lock_after_poisoning() {
        let path = std::env::temp_dir().join(format!(
            "wanaku-audit-poisoned-lock-{}.json",
            std::process::id()
        ));
        let _ = std::fs::remove_file(&path);
        let backend = Arc::new(FileAuditPersistence::new(&path));
        let poison_target = backend.clone();
        let poisoned = std::thread::Builder::new()
            .spawn(move || {
                let _guard = poison_target.write_lock.lock().expect("write lock");
                std::panic::resume_unwind(Box::new("poison audit persistence"));
            })
            .expect("poison thread")
            .join();
        assert!(poisoned.is_err());

        let event = AuditEvent::new(
            AuditCategory::Decision,
            AuditDecision::Allow,
            "tools/call",
            "allowed",
            "Allowed",
        );
        assert!(backend.save(&[event]).is_ok());
        assert!(path.exists());
        let _ = std::fs::remove_file(path);
    }
}
