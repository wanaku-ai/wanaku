use std::sync::Arc;

use serde::{Deserialize, Serialize};
use wanaku_types::revision::{RevisionHistory, RevisionHistoryError, RevisionRecord};
use wanaku_types::time::iso_now;

pub use wanaku_types::revision::{ActivationStatus, RevisionId, RevisionMetadata, RevisionOrigin};

use crate::config::EvaluatorDef;
use crate::revision_persistence::{RevisionPersistence, RevisionsSnapshot};

/// Maximum number of revisions kept, in memory and on disk. Oldest revisions
/// beyond this limit are silently dropped.
const DEFAULT_MAX_HISTORY: usize = 50;

/// An immutable evaluator configuration revision.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Revision {
    pub metadata: RevisionMetadata,
    pub evaluators: Vec<EvaluatorDef>,
}

impl RevisionRecord for Revision {
    fn metadata(&self) -> &RevisionMetadata {
        &self.metadata
    }

    fn metadata_mut(&mut self) -> &mut RevisionMetadata {
        &mut self.metadata
    }
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
    history: RevisionHistory<Revision>,
    persistence: Option<Arc<dyn RevisionPersistence>>,
}

struct PersistenceAdapter<'a>(&'a dyn RevisionPersistence);

impl wanaku_types::revision::RevisionPersistence<Revision> for PersistenceAdapter<'_> {
    type Error = wanaku_types::persistence::PersistenceError;

    fn load(&self) -> Result<RevisionsSnapshot, Self::Error> {
        self.0.load()
    }

    fn save(&self, snapshot: &RevisionsSnapshot) -> Result<(), Self::Error> {
        self.0.save(snapshot)
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
            history: RevisionHistory::new(max_history),
            persistence: None,
        }
    }

    /// Create a revision store backed by the given persistence backend and load
    /// any previously persisted history. The next revision ID is seeded above
    /// the highest persisted ID so IDs stay monotonic across restarts.
    #[must_use]
    pub fn with_persistence(backend: Arc<dyn RevisionPersistence>) -> Self {
        Self::with_max_history_and_persistence(DEFAULT_MAX_HISTORY, backend)
    }

    fn with_max_history_and_persistence(
        max_history: usize,
        backend: Arc<dyn RevisionPersistence>,
    ) -> Self {
        let store = Self {
            history: RevisionHistory::new(max_history),
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
        let adapter = PersistenceAdapter(backend.as_ref());
        let snapshot = match wanaku_types::revision::RevisionPersistence::load(&adapter) {
            Ok(s) => s,
            Err(e) => {
                tracing::error!(error = %e, "failed to load persisted evaluator revisions; starting empty");
                return;
            }
        };
        let count = snapshot.revisions.len();
        let active_id = snapshot.active_id;
        let next_id = snapshot.next_id;
        if self.history.restore(snapshot).is_err() {
            tracing::warn!("revision store lock poisoned; persisted revisions not loaded");
            return;
        }
        tracing::info!(
            count,
            active = ?active_id,
            next_id,
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
        let Ok(snapshot) = self.history.snapshot() else {
            tracing::warn!("revision store lock poisoned; skipping persist");
            return;
        };
        let adapter = PersistenceAdapter(backend.as_ref());
        if let Err(e) = wanaku_types::revision::RevisionPersistence::save(&adapter, &snapshot) {
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
        self.history.active_revision()
    }

    /// Return the ID of the currently active revision, if any.
    pub fn active_revision_id(&self) -> Option<RevisionId> {
        self.history.active_revision_id()
    }

    /// List all stored revisions, newest first.
    pub fn list_revisions(&self) -> Vec<RevisionMetadata> {
        self.history.list_metadata()
    }

    /// Retrieve a specific revision by ID.
    pub fn get_revision(&self, id: RevisionId) -> Option<Revision> {
        self.history.get(id)
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
        self.history
            .commit(params.expected_revision, params.activate, |id| {
                build_revision(id, params)
            })
            .map_err(map_history_error)
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
        self.history
            .check_concurrency(expected_revision)
            .map_err(RevisionError::from)?;

        Ok(defs)
    }
}

fn map_history_error(error: RevisionHistoryError<RevisionError>) -> RevisionError {
    match error {
        RevisionHistoryError::Conflict { expected, actual } => {
            RevisionError::Conflict { expected, actual }
        }
        RevisionHistoryError::Build(error) => error,
        RevisionHistoryError::Lock => {
            RevisionError::ValidationFailed("internal lock error".to_owned())
        }
    }
}

impl From<RevisionHistoryError<()>> for RevisionError {
    fn from(error: RevisionHistoryError<()>) -> Self {
        match error {
            RevisionHistoryError::Conflict { expected, actual } => {
                Self::Conflict { expected, actual }
            }
            RevisionHistoryError::Build(()) | RevisionHistoryError::Lock => {
                Self::ValidationFailed("internal lock error".to_owned())
            }
        }
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
    wanaku_types::revision::checksum(&defs).map_err(|e| {
        RevisionError::ValidationFailed(format!("failed to serialize evaluator config: {e}"))
    })
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
            let snapshot =
                guard
                    .as_ref()
                    .map_or_else(RevisionsSnapshot::default, |s| RevisionsSnapshot {
                        revisions: s.revisions.clone(),
                        active_id: s.active_id,
                        next_id: s.next_id,
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
        let store = RevisionStore::with_max_history_and_persistence(3, backend.clone());
        for i in 0..5 {
            store
                .record_revision(&active_params(
                    vec![test_evaluator(&format!("eval-{i}"))],
                    RevisionOrigin::Api,
                ))
                .unwrap();
        }
        let highest = store.list_revisions().iter().map(|m| m.id).max().unwrap();

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
