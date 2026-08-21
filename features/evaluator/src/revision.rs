use std::collections::hash_map::DefaultHasher;
use std::hash::{Hash, Hasher};
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, RwLock};
use std::time::{SystemTime, UNIX_EPOCH};

use serde::{Deserialize, Serialize};

use crate::config::EvaluatorDef;

/// Monotonically increasing revision counter shared across a single server lifetime.
static NEXT_REVISION: AtomicU64 = AtomicU64::new(1);

/// Maximum number of revisions kept in memory when no persistence backend is
/// configured. Oldest revisions beyond this limit are silently dropped.
const DEFAULT_MAX_HISTORY: usize = 50;

/// Unique identifier for an evaluator configuration revision.
pub type RevisionId = u64;

/// Where a configuration revision originated.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum RevisionOrigin {
    /// Loaded from `wanaku.yaml` at server startup.
    Startup,
    /// Submitted through the management API.
    Api,
}

/// Whether a revision was successfully activated.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum ActivationStatus {
    /// The revision is currently active.
    Active,
    /// The revision was once active but has been replaced by a newer one.
    Superseded,
    /// Activation was rejected because validation or compilation failed.
    Rejected,
}

/// Metadata attached to every configuration revision.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RevisionMetadata {
    pub id: RevisionId,
    pub created_at: String,
    pub activated_at: Option<String>,
    pub status: ActivationStatus,
    pub checksum: String,
    pub origin: RevisionOrigin,
    pub actor: Option<String>,
    pub failure_reason: Option<String>,
}

/// An immutable evaluator configuration revision.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Revision {
    pub metadata: RevisionMetadata,
    pub evaluators: Vec<EvaluatorDef>,
}

/// Errors that can occur during revision operations.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum RevisionError {
    /// The caller supplied an expected revision that does not match the current
    /// active revision. Another update was applied in between.
    Conflict {
        expected: RevisionId,
        actual: RevisionId,
    },
    /// The requested revision was not found in history.
    NotFound(RevisionId),
    /// Validation or compilation of the evaluator configuration failed.
    ValidationFailed(String),
}

impl std::fmt::Display for RevisionError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::Conflict { expected, actual } => {
                write!(
                    f,
                    "conflict: expected active revision {expected}, but current is {actual}"
                )
            }
            Self::NotFound(id) => write!(f, "revision {id} not found"),
            Self::ValidationFailed(reason) => {
                write!(f, "validation failed: {reason}")
            }
        }
    }
}

/// In-memory store for evaluator configuration revisions.
///
/// All revisions are kept in a bounded ring: when the history exceeds
/// `max_history`, the oldest entry is removed.
#[derive(Clone)]
pub struct RevisionStore {
    inner: Arc<RwLock<RevisionStoreInner>>,
}

struct RevisionStoreInner {
    revisions: Vec<Revision>,
    active_id: Option<RevisionId>,
    max_history: usize,
}

impl RevisionStore {
    /// Create a new empty revision store with the default history limit.
    #[must_use]
    pub fn new() -> Self {
        Self::with_max_history(DEFAULT_MAX_HISTORY)
    }

    /// Create a new empty revision store with a custom history limit.
    #[must_use]
    pub fn with_max_history(max_history: usize) -> Self {
        Self {
            inner: Arc::new(RwLock::new(RevisionStoreInner {
                revisions: Vec::new(),
                active_id: None,
                max_history,
            })),
        }
    }

    /// Return the currently active revision, if any.
    pub fn active_revision(&self) -> Option<Revision> {
        let guard = self.inner.read().ok()?;
        let active_id = guard.active_id?;
        guard
            .revisions
            .iter()
            .find(|r| r.metadata.id == active_id)
            .cloned()
    }

    /// Return the ID of the currently active revision, if any.
    pub fn active_revision_id(&self) -> Option<RevisionId> {
        self.inner.read().ok().and_then(|g| g.active_id)
    }

    /// List all stored revisions, newest first.
    pub fn list_revisions(&self) -> Vec<RevisionMetadata> {
        self.inner
            .read()
            .map(|guard| {
                let mut metas: Vec<_> =
                    guard.revisions.iter().map(|r| r.metadata.clone()).collect();
                metas.sort_by_key(|m| std::cmp::Reverse(m.id));
                metas
            })
            .unwrap_or_default()
    }

    /// Retrieve a specific revision by ID.
    pub fn get_revision(&self, id: RevisionId) -> Option<Revision> {
        self.inner.read().ok().and_then(|guard| {
            guard
                .revisions
                .iter()
                .find(|r| r.metadata.id == id)
                .cloned()
        })
    }

    /// Record a new revision with the given evaluator definitions and origin.
    ///
    /// If `expected_revision` is `Some`, the call will fail with
    /// [`RevisionError::Conflict`] when the current active revision does not
    /// match the expected value.
    ///
    /// The `activate` flag controls whether the revision becomes active
    /// immediately. When `false` (e.g., a failed compilation), the revision is
    /// stored as rejected.
    #[expect(
        clippy::too_many_arguments,
        clippy::too_many_lines,
        reason = "revision creation with concurrency check, metadata, and history trimming"
    )]
    pub fn record_revision(
        &self,
        evaluators: &[EvaluatorDef],
        origin: RevisionOrigin,
        actor: Option<String>,
        expected_revision: Option<RevisionId>,
        activate: bool,
        failure_reason: Option<String>,
    ) -> Result<Revision, RevisionError> {
        let Ok(mut guard) = self.inner.write() else {
            return Err(RevisionError::ValidationFailed(
                "internal lock error".to_owned(),
            ));
        };

        // Optimistic concurrency check.
        if let Some(expected) = expected_revision {
            let actual = guard.active_id.unwrap_or(0);
            if actual != expected {
                return Err(RevisionError::Conflict { expected, actual });
            }
        }

        let id = NEXT_REVISION.fetch_add(1, Ordering::Relaxed);
        let now = iso_now();
        let checksum = compute_checksum(evaluators);

        let status = if activate {
            ActivationStatus::Active
        } else {
            ActivationStatus::Rejected
        };

        let activated_at = if activate { Some(now.clone()) } else { None };

        let revision = Revision {
            metadata: RevisionMetadata {
                id,
                created_at: now,
                activated_at,
                status,
                checksum,
                origin,
                actor,
                failure_reason,
            },
            evaluators: evaluators.to_vec(),
        };

        // Mark the previously active revision as superseded.
        if activate {
            if let Some(prev_id) = guard.active_id
                && let Some(prev) = guard
                    .revisions
                    .iter_mut()
                    .find(|r| r.metadata.id == prev_id)
            {
                prev.metadata.status = ActivationStatus::Superseded;
            }
            guard.active_id = Some(id);
        }

        guard.revisions.push(revision.clone());

        // Trim oldest revisions if we exceed the limit.
        while guard.revisions.len() > guard.max_history {
            guard.revisions.remove(0);
        }

        Ok(revision)
    }

    /// Restore a previous revision by creating a new revision with the same
    /// evaluator definitions. The new revision receives a fresh ID.
    ///
    /// Returns [`RevisionError::NotFound`] if the source revision does not
    /// exist. Returns [`RevisionError::Conflict`] if `expected_revision` does
    /// not match the current active revision.
    pub fn restore_revision(
        &self,
        source_id: RevisionId,
        expected_revision: Option<RevisionId>,
    ) -> Result<Vec<EvaluatorDef>, RevisionError> {
        let defs = self
            .get_revision(source_id)
            .ok_or(RevisionError::NotFound(source_id))?
            .evaluators;

        // Verify the optimistic concurrency precondition before the caller
        // tries to activate.
        if let Some(expected) = expected_revision {
            let actual = self.active_revision_id().unwrap_or(0);
            if actual != expected {
                return Err(RevisionError::Conflict { expected, actual });
            }
        }

        Ok(defs)
    }
}

impl Default for RevisionStore {
    fn default() -> Self {
        Self::new()
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/// Compute a hex-encoded hash of the serialized evaluator definitions.
fn compute_checksum(defs: &[EvaluatorDef]) -> String {
    let mut hasher = DefaultHasher::new();
    // Serialize to a canonical JSON string for deterministic hashing.
    if let Ok(json) = serde_json::to_string(defs) {
        json.hash(&mut hasher);
    }
    format!("{:016x}", hasher.finish())
}

/// Return the current wall-clock time as an ISO 8601 string (UTC).
fn iso_now() -> String {
    let duration = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default();
    let secs = duration.as_secs();

    // Manual UTC formatting: YYYY-MM-DDTHH:MM:SSZ
    // This avoids adding a chrono dependency.
    let days = secs / 86400;
    let time_of_day = secs % 86400;
    let hours = time_of_day / 3600;
    let minutes = (time_of_day % 3600) / 60;
    let seconds = time_of_day % 60;

    // Compute year/month/day from days since epoch (1970-01-01).
    let (year, month, day) = days_to_ymd(days);

    format!("{year:04}-{month:02}-{day:02}T{hours:02}:{minutes:02}:{seconds:02}Z")
}

/// Convert days since Unix epoch to (year, month, day).
const fn days_to_ymd(days: u64) -> (u64, u64, u64) {
    // Algorithm adapted from Howard Hinnant's civil_from_days.
    let z = days + 719_468;
    let era = z / 146_097;
    let doe = z - era * 146_097;
    let yoe = (doe - doe / 1460 + doe / 36524 - doe / 146_096) / 365;
    let y = yoe + era * 400;
    let doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    let mp = (5 * doy + 2) / 153;
    let d = doy - (153 * mp + 2) / 5 + 1;
    let m = if mp < 10 { mp + 3 } else { mp - 9 };
    let y = if m <= 2 { y + 1 } else { y };
    (y, m, d)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::config::{
        ErrorPolicy, EvaluatorDef, LlmDef, LlmOperation, ProcessorRef, TriggerDef,
    };
    use std::path::PathBuf;

    fn test_evaluator(name: &str) -> EvaluatorDef {
        EvaluatorDef {
            name: name.to_owned(),
            trigger: TriggerDef {
                method: "tools/call".to_owned(),
                namespace: None,
            },
            llm: LlmDef {
                operation: LlmOperation::Classify,
                prompt: "test".to_owned(),
                model: "test-model".to_owned(),
                url: "http://localhost".to_owned(),
                api_key: String::new(),
                result_schema: None,
            },
            processor: ProcessorRef {
                path: PathBuf::from("/test.wasm"),
            },
            on_error: ErrorPolicy::Continue,
        }
    }

    #[test]
    fn record_and_retrieve_revision() {
        let store = RevisionStore::new();
        let defs = vec![test_evaluator("eval-1")];

        let rev = store
            .record_revision(&defs, RevisionOrigin::Startup, None, None, true, None)
            .unwrap();

        assert_eq!(rev.metadata.status, ActivationStatus::Active);
        assert_eq!(rev.metadata.origin, RevisionOrigin::Startup);
        assert!(rev.metadata.failure_reason.is_none());
        assert_eq!(rev.evaluators.len(), 1);

        let active = store.active_revision().unwrap();
        assert_eq!(active.metadata.id, rev.metadata.id);
    }

    #[test]
    fn supersedes_previous_on_new_active() {
        let store = RevisionStore::new();
        let defs1 = vec![test_evaluator("eval-1")];
        let defs2 = vec![test_evaluator("eval-2")];

        let rev1 = store
            .record_revision(&defs1, RevisionOrigin::Startup, None, None, true, None)
            .unwrap();
        let rev2 = store
            .record_revision(&defs2, RevisionOrigin::Api, None, None, true, None)
            .unwrap();

        // rev1 should be superseded now.
        let stored_rev1 = store.get_revision(rev1.metadata.id).unwrap();
        assert_eq!(stored_rev1.metadata.status, ActivationStatus::Superseded);

        // rev2 should be active.
        let active = store.active_revision().unwrap();
        assert_eq!(active.metadata.id, rev2.metadata.id);
        assert_eq!(active.metadata.status, ActivationStatus::Active);
    }

    #[test]
    fn rejected_revision_does_not_become_active() {
        let store = RevisionStore::new();
        let defs_good = vec![test_evaluator("good")];
        let defs_bad = vec![test_evaluator("bad")];

        let good = store
            .record_revision(&defs_good, RevisionOrigin::Startup, None, None, true, None)
            .unwrap();

        let bad = store
            .record_revision(
                &defs_bad,
                RevisionOrigin::Api,
                None,
                None,
                false,
                Some("compilation failed".to_owned()),
            )
            .unwrap();

        assert_eq!(bad.metadata.status, ActivationStatus::Rejected);
        assert!(bad.metadata.failure_reason.is_some());

        // Active should still be the good revision.
        let active = store.active_revision().unwrap();
        assert_eq!(active.metadata.id, good.metadata.id);
    }

    #[test]
    fn optimistic_concurrency_conflict() {
        let store = RevisionStore::new();
        let defs = vec![test_evaluator("eval-1")];

        let rev1 = store
            .record_revision(&defs, RevisionOrigin::Startup, None, None, true, None)
            .unwrap();

        // Try to update with wrong expected revision.
        let result = store.record_revision(
            &defs,
            RevisionOrigin::Api,
            None,
            Some(rev1.metadata.id + 999),
            true,
            None,
        );

        assert!(result.is_err());
        if let Err(RevisionError::Conflict { expected, actual }) = result {
            assert_eq!(actual, rev1.metadata.id);
            assert_eq!(expected, rev1.metadata.id + 999);
        }
    }

    #[test]
    fn optimistic_concurrency_success() {
        let store = RevisionStore::new();
        let defs = vec![test_evaluator("eval-1")];

        let rev1 = store
            .record_revision(&defs, RevisionOrigin::Startup, None, None, true, None)
            .unwrap();

        let defs2 = vec![test_evaluator("eval-2")];
        let rev2 = store
            .record_revision(
                &defs2,
                RevisionOrigin::Api,
                None,
                Some(rev1.metadata.id),
                true,
                None,
            )
            .unwrap();

        assert_eq!(
            store.active_revision().unwrap().metadata.id,
            rev2.metadata.id
        );
    }

    #[test]
    fn list_revisions_newest_first() {
        let store = RevisionStore::new();

        for i in 0..5 {
            let defs = vec![test_evaluator(&format!("eval-{i}"))];
            store
                .record_revision(&defs, RevisionOrigin::Api, None, None, true, None)
                .unwrap();
        }

        let list = store.list_revisions();
        assert_eq!(list.len(), 5);
        // Newest first.
        assert!(list[0].id > list[1].id);
    }

    #[test]
    fn get_revision_not_found() {
        let store = RevisionStore::new();
        assert!(store.get_revision(999).is_none());
    }

    #[test]
    fn restore_revision_creates_defs() {
        let store = RevisionStore::new();
        let defs1 = vec![test_evaluator("eval-1")];
        let defs2 = vec![test_evaluator("eval-2")];

        let rev1 = store
            .record_revision(&defs1, RevisionOrigin::Startup, None, None, true, None)
            .unwrap();
        store
            .record_revision(&defs2, RevisionOrigin::Api, None, None, true, None)
            .unwrap();

        let restored_defs = store.restore_revision(rev1.metadata.id, None).unwrap();
        assert_eq!(restored_defs.len(), 1);
        assert_eq!(restored_defs[0].name, "eval-1");
    }

    #[test]
    fn restore_nonexistent_revision_fails() {
        let store = RevisionStore::new();
        let result = store.restore_revision(999, None);
        assert!(matches!(result, Err(RevisionError::NotFound(999))));
    }

    #[test]
    fn restore_with_stale_expected_revision_fails() {
        let store = RevisionStore::new();
        let defs = vec![test_evaluator("eval-1")];

        let rev1 = store
            .record_revision(&defs, RevisionOrigin::Startup, None, None, true, None)
            .unwrap();
        store
            .record_revision(&defs, RevisionOrigin::Api, None, None, true, None)
            .unwrap();

        // Try to restore rev1 but claim we expect rev1 as active (rev2 is).
        let result = store.restore_revision(rev1.metadata.id, Some(rev1.metadata.id));
        assert!(matches!(
            result,
            Err(RevisionError::Conflict {
                expected: _,
                actual: _
            })
        ));
    }

    #[test]
    fn bounded_history_trims_oldest() {
        let store = RevisionStore::with_max_history(3);

        for i in 0..5 {
            let defs = vec![test_evaluator(&format!("eval-{i}"))];
            store
                .record_revision(&defs, RevisionOrigin::Api, None, None, true, None)
                .unwrap();
        }

        let list = store.list_revisions();
        assert_eq!(list.len(), 3);
    }

    #[test]
    fn checksum_changes_with_config() {
        let store = RevisionStore::new();
        let defs1 = vec![test_evaluator("eval-1")];
        let defs2 = vec![test_evaluator("eval-2")];

        let rev1 = store
            .record_revision(&defs1, RevisionOrigin::Api, None, None, true, None)
            .unwrap();
        let rev2 = store
            .record_revision(&defs2, RevisionOrigin::Api, None, None, true, None)
            .unwrap();

        assert_ne!(rev1.metadata.checksum, rev2.metadata.checksum);
    }

    #[test]
    fn checksum_stable_for_same_config() {
        let defs = vec![test_evaluator("eval-1")];
        let c1 = compute_checksum(&defs);
        let c2 = compute_checksum(&defs);
        assert_eq!(c1, c2);
    }

    #[test]
    fn iso_now_format() {
        let ts = iso_now();
        assert!(ts.ends_with('Z'), "timestamp should end with Z: {ts}");
        assert!(ts.contains('T'), "timestamp should contain T: {ts}");
        assert_eq!(ts.len(), 20, "ISO 8601 UTC should be 20 chars: {ts}");
    }

    #[test]
    fn revision_error_display() {
        let conflict = RevisionError::Conflict {
            expected: 1,
            actual: 2,
        };
        assert!(conflict.to_string().contains("conflict"));

        let not_found = RevisionError::NotFound(42);
        assert!(not_found.to_string().contains("42"));

        let validation = RevisionError::ValidationFailed("bad config".to_owned());
        assert!(validation.to_string().contains("bad config"));
    }

    #[test]
    fn empty_store_has_no_active() {
        let store = RevisionStore::new();
        assert!(store.active_revision().is_none());
        assert!(store.active_revision_id().is_none());
        assert!(store.list_revisions().is_empty());
    }

    #[test]
    fn actor_is_stored() {
        let store = RevisionStore::new();
        let defs = vec![test_evaluator("eval-1")];
        let rev = store
            .record_revision(
                &defs,
                RevisionOrigin::Api,
                Some("admin@example.com".to_owned()),
                None,
                true,
                None,
            )
            .unwrap();
        assert_eq!(rev.metadata.actor.as_deref(), Some("admin@example.com"));
    }
}
