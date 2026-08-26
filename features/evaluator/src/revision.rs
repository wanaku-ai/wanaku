use std::collections::hash_map::DefaultHasher;
use std::hash::{Hash, Hasher};
use std::sync::{Arc, RwLock};

use serde::{Deserialize, Serialize};
use wanaku_types::time::iso_now;

use crate::config::EvaluatorDef;
use crate::revision_persistence::{RevisionPersistence, RevisionsSnapshot};

/// Maximum number of revisions kept, in memory and on disk. Oldest revisions
/// beyond this limit are silently dropped.
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

/// Parameters for recording a new revision.
pub struct RecordRevisionParams {
    pub evaluators: Vec<EvaluatorDef>,
    pub origin: RevisionOrigin,
    pub actor: Option<String>,
    pub expected_revision: Option<RevisionId>,
    pub activate: bool,
    pub failure_reason: Option<String>,
}

/// Store for evaluator configuration revisions.
///
/// All revisions are kept in a bounded ring: when the history exceeds
/// `max_history`, the oldest entry is removed. When a
/// [`RevisionPersistence`] backend is configured, the full history is written
/// after every change and reloaded at startup, so revision history and
/// rollback survive a restart.
#[derive(Clone)]
pub struct RevisionStore {
    inner: Arc<RwLock<RevisionStoreInner>>,
    persistence: Option<Arc<dyn RevisionPersistence>>,
}

struct RevisionStoreInner {
    revisions: Vec<Revision>,
    active_id: Option<RevisionId>,
    max_history: usize,
    /// The next revision ID to assign. Instance-scoped (not a process global)
    /// so it can be seeded from persisted history at startup, keeping IDs
    /// monotonic across restarts.
    next_id: RevisionId,
}

impl RevisionStoreInner {
    fn check_concurrency(
        &self,
        expected: Option<RevisionId>,
    ) -> Result<(), RevisionError> {
        if let Some(expected) = expected {
            let actual = self.active_id.unwrap_or(0);
            if actual != expected {
                return Err(RevisionError::Conflict { expected, actual });
            }
        }
        Ok(())
    }

    fn mark_previous_superseded(&mut self) {
        if let Some(prev_id) = self.active_id
            && let Some(prev) = self
                .revisions
                .iter_mut()
                .find(|r| r.metadata.id == prev_id)
        {
            prev.metadata.status = ActivationStatus::Superseded;
        }
    }

    fn trim_history(&mut self) {
        while self.revisions.len() > self.max_history {
            let is_active =
                self.active_id == Some(self.revisions[0].metadata.id);
            if is_active && self.revisions.len() <= self.max_history + 1 {
                break;
            }
            if is_active {
                self.revisions.remove(1);
            } else {
                self.revisions.remove(0);
            }
        }
    }
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
                next_id: 1,
            })),
            persistence: None,
        }
    }

    /// Create a revision store backed by the given persistence backend and load
    /// any previously persisted history. The next revision ID is seeded above
    /// the highest persisted ID so IDs stay monotonic across restarts.
    #[must_use]
    pub fn with_persistence(backend: Arc<dyn RevisionPersistence>) -> Self {
        let store = Self {
            inner: Arc::new(RwLock::new(RevisionStoreInner {
                revisions: Vec::new(),
                active_id: None,
                max_history: DEFAULT_MAX_HISTORY,
                next_id: 1,
            })),
            persistence: Some(backend),
        };
        store.load_persisted();
        store
    }

    /// Load persisted revisions into the store, replacing any current state.
    /// Best-effort: a load failure leaves the store empty and is logged, so a
    /// corrupt or unreadable file never blocks startup.
    fn load_persisted(&self) {
        let Some(backend) = self.persistence.as_ref() else {
            return;
        };
        let snapshot = match backend.load() {
            Ok(s) => s,
            Err(e) => {
                tracing::error!(error = %e, "failed to load persisted evaluator revisions; starting empty");
                return;
            }
        };
        let Ok(mut guard) = self.inner.write() else {
            tracing::warn!("revision store lock poisoned; persisted revisions not loaded");
            return;
        };
        let max_id = snapshot
            .revisions
            .iter()
            .map(|r| r.metadata.id)
            .max()
            .unwrap_or(0);
        guard.next_id = snapshot.next_id.max(max_id.saturating_add(1)).max(1);
        guard.revisions = snapshot.revisions;
        guard.active_id = snapshot.active_id;
        guard.trim_history();
        tracing::info!(
            count = guard.revisions.len(),
            active = ?guard.active_id,
            next_id = guard.next_id,
            "loaded persisted evaluator revisions"
        );
    }

    /// Persist the current history. Best-effort: a save failure is logged but
    /// does not fail the operation, so a full or read-only disk degrades to
    /// in-memory-only rather than rejecting valid activations. Callers hold the
    /// activation lock, so saves are serialized and cannot interleave.
    pub(crate) fn persist(&self) {
        let Some(backend) = self.persistence.as_ref() else {
            return;
        };
        let snapshot = {
            let Ok(guard) = self.inner.read() else {
                tracing::warn!("revision store lock poisoned; skipping persist");
                return;
            };
            RevisionsSnapshot {
                revisions: guard.revisions.clone(),
                active_id: guard.active_id,
                next_id: guard.next_id,
            }
        };
        if let Err(e) = backend.save(&snapshot) {
            tracing::error!(error = %e, "failed to persist evaluator revisions");
        }
    }

    /// Checksum of the active revision's configuration, if any. Used to detect
    /// whether a startup configuration matches the already-active revision.
    #[must_use]
    pub fn active_checksum(&self) -> Option<String> {
        self.active_revision().map(|r| r.metadata.checksum)
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
    pub fn record_revision(
        &self,
        params: &RecordRevisionParams,
    ) -> Result<Revision, RevisionError> {
        let revision = self.commit_revision(params)?;
        self.persist();
        Ok(revision)
    }

    /// Commit a revision to the in-memory history WITHOUT persisting it. The
    /// caller MUST call [`Self::persist`] afterward to make the change durable.
    ///
    /// Kept separate from [`Self::record_revision`] so an activation can install
    /// the runtime snapshot between the in-memory commit and the disk write.
    /// This keeps the disk write out of the window between "the store reports the
    /// new active revision" and "the runtime enforces it", so a slow disk cannot
    /// widen that inconsistency window. Callers must hold the activation lock so
    /// the commit, the snapshot install, and the persist stay serialized.
    pub(crate) fn commit_revision(
        &self,
        params: &RecordRevisionParams,
    ) -> Result<Revision, RevisionError> {
        let Ok(mut guard) = self.inner.write() else {
            return Err(RevisionError::ValidationFailed(
                "internal lock error".to_owned(),
            ));
        };

        guard.check_concurrency(params.expected_revision)?;

        // Allocate the ID before committing it, so a checksum failure in
        // build_revision leaves next_id untouched (no gaps).
        let id = guard.next_id;
        let revision = build_revision(id, params)?;
        guard.next_id = id.saturating_add(1);

        if params.activate {
            guard.mark_previous_superseded();
            guard.active_id = Some(id);
        }

        guard.revisions.push(revision.clone());
        guard.trim_history();
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

fn build_revision(
    id: RevisionId,
    params: &RecordRevisionParams,
) -> Result<Revision, RevisionError> {
    let now = iso_now();
    let checksum = compute_checksum(&params.evaluators)?;

    let (status, activated_at) = if params.activate {
        (ActivationStatus::Active, Some(now.clone()))
    } else {
        (ActivationStatus::Rejected, None)
    };

    Ok(Revision {
        metadata: RevisionMetadata {
            id,
            created_at: now,
            activated_at,
            status,
            checksum,
            origin: params.origin.clone(),
            actor: params.actor.clone(),
            failure_reason: params.failure_reason.clone(),
        },
        evaluators: params.evaluators.clone(),
    })
}

/// Checksum of a candidate evaluator configuration, using the same encoding as
/// recorded revisions. Returns `None` if the configuration cannot be
/// serialized. Used to compare a startup configuration against the active
/// revision without recording a new one.
#[must_use]
pub fn config_checksum(defs: &[EvaluatorDef]) -> Option<String> {
    compute_checksum(defs).ok()
}

/// Compute a hex-encoded hash of the serialized evaluator definitions.
///
/// `DefaultHasher`'s algorithm is not guaranteed stable across Rust releases,
/// so a persisted checksum may not match one recomputed by a binary built with
/// a different toolchain. The only consequence is that startup dedup can miss
/// once after a toolchain upgrade, recording one extra startup revision; the
/// bounded history absorbs it and checksums are stable again thereafter. A
/// stronger hash is not worth a new dependency for this use.
fn compute_checksum(defs: &[EvaluatorDef]) -> Result<String, RevisionError> {
    let json = serde_json::to_string(defs).map_err(|e| {
        RevisionError::ValidationFailed(format!("failed to serialize evaluator config: {e}"))
    })?;
    let mut hasher = DefaultHasher::new();
    json.hash(&mut hasher);
    Ok(format!("{:016x}", hasher.finish()))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::config::{
        ErrorPolicy, EvaluatorDef, LlmDef, LlmOperation, ProcessorRef, TriggerDef,
    };
    use std::path::PathBuf;

    fn active_params(defs: Vec<EvaluatorDef>, origin: RevisionOrigin) -> RecordRevisionParams {
        RecordRevisionParams {
            evaluators: defs,
            origin,
            actor: None,
            expected_revision: None,
            activate: true,
            failure_reason: None,
        }
    }

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
                connection: "test-connection".to_owned(),
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
            .record_revision(&active_params(defs, RevisionOrigin::Startup))
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
            .record_revision(&active_params(defs1, RevisionOrigin::Startup))
            .unwrap();
        let rev2 = store
            .record_revision(&active_params(defs2, RevisionOrigin::Api))
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
            .record_revision(&active_params(defs_good, RevisionOrigin::Startup))
            .unwrap();

        let bad = store
            .record_revision(&RecordRevisionParams {
                evaluators: defs_bad,
                origin: RevisionOrigin::Api,
                actor: None,
                expected_revision: None,
                activate: false,
                failure_reason: Some("compilation failed".to_owned()),
            })
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
            .record_revision(&active_params(defs.clone(), RevisionOrigin::Startup))
            .unwrap();

        // Try to update with wrong expected revision.
        let result = store.record_revision(&RecordRevisionParams {
            evaluators: defs,
            origin: RevisionOrigin::Api,
            actor: None,
            expected_revision: Some(rev1.metadata.id + 999),
            activate: true,
            failure_reason: None,
        });

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
            .record_revision(&active_params(defs, RevisionOrigin::Startup))
            .unwrap();

        let defs2 = vec![test_evaluator("eval-2")];
        let rev2 = store
            .record_revision(&RecordRevisionParams {
                evaluators: defs2,
                origin: RevisionOrigin::Api,
                actor: None,
                expected_revision: Some(rev1.metadata.id),
                activate: true,
                failure_reason: None,
            })
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
                .record_revision(&active_params(defs, RevisionOrigin::Api))
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
            .record_revision(&active_params(defs1, RevisionOrigin::Startup))
            .unwrap();
        store
            .record_revision(&active_params(defs2, RevisionOrigin::Api))
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
            .record_revision(&active_params(defs.clone(), RevisionOrigin::Startup))
            .unwrap();
        store
            .record_revision(&active_params(defs, RevisionOrigin::Api))
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
                .record_revision(&active_params(defs, RevisionOrigin::Api))
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
            .record_revision(&active_params(defs1, RevisionOrigin::Api))
            .unwrap();
        let rev2 = store
            .record_revision(&active_params(defs2, RevisionOrigin::Api))
            .unwrap();

        assert_ne!(rev1.metadata.checksum, rev2.metadata.checksum);
    }

    #[test]
    fn checksum_stable_for_same_config() {
        let defs = vec![test_evaluator("eval-1")];
        let c1 = compute_checksum(&defs).unwrap();
        let c2 = compute_checksum(&defs).unwrap();
        assert_eq!(c1, c2);
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

    // --- Persistence -------------------------------------------------------

    use crate::revision_persistence::{RevisionPersistence, RevisionsSnapshot};
    use std::sync::Mutex;
    use wanaku_types::persistence::PersistenceError;

    /// In-memory persistence backend for tests: emulates a durable store that
    /// survives a "restart" (a new `RevisionStore` reading the same backend).
    #[derive(Default)]
    struct MemoryPersistence {
        saved: Mutex<Option<RevisionsSnapshot>>,
    }

    impl RevisionPersistence for MemoryPersistence {
        fn load(&self) -> Result<RevisionsSnapshot, PersistenceError> {
            let guard = self.saved.lock().unwrap();
            let snapshot = guard.as_ref().map_or_else(RevisionsSnapshot::default, |s| {
                RevisionsSnapshot {
                    revisions: s.revisions.clone(),
                    active_id: s.active_id,
                    next_id: s.next_id,
                }
            });
            Ok(snapshot)
        }

        fn save(&self, snapshot: &RevisionsSnapshot) -> Result<(), PersistenceError> {
            *self.saved.lock().unwrap() = Some(RevisionsSnapshot {
                revisions: snapshot.revisions.clone(),
                active_id: snapshot.active_id,
                next_id: snapshot.next_id,
            });
            Ok(())
        }
    }

    #[test]
    fn history_survives_restart() {
        let backend = Arc::new(MemoryPersistence::default());

        let store = RevisionStore::with_persistence(backend.clone());
        store
            .record_revision(&active_params(
                vec![test_evaluator("eval-1")],
                RevisionOrigin::Startup,
            ))
            .unwrap();
        let rev2 = store
            .record_revision(&active_params(
                vec![test_evaluator("eval-2")],
                RevisionOrigin::Api,
            ))
            .unwrap();

        // "Restart": a fresh store reading the same backend.
        let restored = RevisionStore::with_persistence(backend);
        assert_eq!(restored.list_revisions().len(), 2);
        assert_eq!(
            restored.active_revision().unwrap().metadata.id,
            rev2.metadata.id
        );
        assert_eq!(
            restored.active_revision().unwrap().evaluators[0].name,
            "eval-2"
        );
    }

    #[test]
    fn next_id_stays_monotonic_across_restart() {
        let backend = Arc::new(MemoryPersistence::default());

        let store = RevisionStore::with_persistence(backend.clone());
        let rev1 = store
            .record_revision(&active_params(
                vec![test_evaluator("eval-1")],
                RevisionOrigin::Api,
            ))
            .unwrap();

        let restored = RevisionStore::with_persistence(backend);
        let rev2 = restored
            .record_revision(&active_params(
                vec![test_evaluator("eval-2")],
                RevisionOrigin::Api,
            ))
            .unwrap();

        assert!(
            rev2.metadata.id > rev1.metadata.id,
            "new revision after restart must get a higher ID"
        );
    }

    #[test]
    fn next_id_seeds_above_trimmed_history() {
        let backend = Arc::new(MemoryPersistence::default());

        // Record more than the history bound so early IDs are trimmed away.
        let store = {
            let s = RevisionStore::with_persistence(backend.clone());
            // Shrink the bound so the test stays small.
            if let Ok(mut g) = s.inner.write() {
                g.max_history = 3;
            }
            s
        };
        for i in 0..5 {
            store
                .record_revision(&active_params(
                    vec![test_evaluator(&format!("eval-{i}"))],
                    RevisionOrigin::Api,
                ))
                .unwrap();
        }
        let highest = store
            .list_revisions()
            .iter()
            .map(|m| m.id)
            .max()
            .unwrap();

        // Restart and record again: the new ID must exceed the highest ever
        // assigned, not just the highest still in the trimmed history.
        let restored = RevisionStore::with_persistence(backend);
        let next = restored
            .record_revision(&active_params(
                vec![test_evaluator("eval-new")],
                RevisionOrigin::Api,
            ))
            .unwrap();
        assert!(next.metadata.id > highest);
    }

    #[test]
    fn rejected_revisions_are_persisted() {
        let backend = Arc::new(MemoryPersistence::default());

        let store = RevisionStore::with_persistence(backend.clone());
        store
            .record_revision(&RecordRevisionParams {
                evaluators: vec![test_evaluator("bad")],
                origin: RevisionOrigin::Api,
                actor: None,
                expected_revision: None,
                activate: false,
                failure_reason: Some("compilation failed".to_owned()),
            })
            .unwrap();

        let restored = RevisionStore::with_persistence(backend);
        let list = restored.list_revisions();
        assert_eq!(list.len(), 1);
        assert_eq!(list[0].status, ActivationStatus::Rejected);
        assert!(restored.active_revision().is_none());
    }

    #[test]
    fn active_checksum_matches_config_checksum() {
        let store = RevisionStore::new();
        let defs = vec![test_evaluator("eval-1")];
        store
            .record_revision(&active_params(defs.clone(), RevisionOrigin::Startup))
            .unwrap();

        assert_eq!(store.active_checksum(), config_checksum(&defs));
        assert_ne!(
            store.active_checksum(),
            config_checksum(&[test_evaluator("other")])
        );
    }

    #[test]
    fn actor_is_stored() {
        let store = RevisionStore::new();
        let defs = vec![test_evaluator("eval-1")];
        let rev = store
            .record_revision(&RecordRevisionParams {
                evaluators: defs,
                origin: RevisionOrigin::Api,
                actor: Some("admin@example.com".to_owned()),
                expected_revision: None,
                activate: true,
                failure_reason: None,
            })
            .unwrap();
        assert_eq!(rev.metadata.actor.as_deref(), Some("admin@example.com"));
    }
}
