use std::sync::{Arc, Mutex, RwLock};

use crate::revision::{
    ActionPolicyRevision, RecordRevisionParams, RevisionError, RevisionId, RevisionMetadata,
    RevisionOrigin, RevisionStore, policy_checksum,
};
use crate::{ActionPolicy, CompiledPolicy};

/// Runtime policy state that one request captures and uses for evaluation and audit.
#[derive(Clone)]
pub enum PolicySnapshot {
    Unconfigured,
    Valid(Arc<ActivePolicy>),
    Invalid,
}

impl PolicySnapshot {
    /// Revision ID of the captured policy. Only a valid snapshot has one.
    #[must_use]
    pub fn revision_id(&self) -> Option<RevisionId> {
        match self {
            Self::Valid(active) => Some(active.revision_id),
            Self::Unconfigured | Self::Invalid => None,
        }
    }
}

/// Compiled policy bound to the exact revision it was compiled from.
pub struct ActivePolicy {
    revision_id: RevisionId,
    checksum: String,
    policy: CompiledPolicy,
}

impl ActivePolicy {
    fn new(metadata: &RevisionMetadata, policy: CompiledPolicy) -> Self {
        Self {
            revision_id: metadata.id,
            checksum: metadata.checksum.clone(),
            policy,
        }
    }

    #[must_use]
    pub const fn revision_id(&self) -> RevisionId {
        self.revision_id
    }

    #[must_use]
    pub fn checksum(&self) -> &str {
        &self.checksum
    }

    #[must_use]
    pub const fn policy(&self) -> &CompiledPolicy {
        &self.policy
    }
}

#[derive(Clone)]
pub struct ActionPolicyState {
    snapshot: Arc<RwLock<PolicySnapshot>>,
    revisions: RevisionStore,
    activation: Arc<Mutex<()>>,
}

pub struct ActivatePolicyParams {
    pub origin: RevisionOrigin,
    pub actor: Option<String>,
    pub expected_revision: Option<u64>,
}

impl ActionPolicyState {
    #[must_use]
    pub fn new() -> Self {
        Self {
            snapshot: Arc::new(RwLock::new(PolicySnapshot::Unconfigured)),
            revisions: RevisionStore::new(),
            activation: Arc::new(Mutex::new(())),
        }
    }

    #[must_use]
    pub fn with_revision_persistence(
        mut self,
        persistence: Arc<dyn crate::revision_persistence::RevisionPersistence>,
    ) -> Self {
        self.revisions = RevisionStore::with_persistence(persistence);
        if self.revisions.active_revision().is_some() {
            self.reinstall_active_or_invalid();
        }
        self
    }

    #[must_use]
    pub fn snapshot(&self) -> PolicySnapshot {
        self.snapshot
            .read()
            .map_or(PolicySnapshot::Invalid, |snapshot| snapshot.clone())
    }

    #[must_use]
    pub const fn revision_store(&self) -> &RevisionStore {
        &self.revisions
    }

    pub fn try_activate(
        &self,
        policy: ActionPolicy,
        params: ActivatePolicyParams,
    ) -> Result<ActionPolicyRevision, RevisionError> {
        let compiled = match policy.clone().compile() {
            Ok(compiled) => compiled,
            Err(error) => return self.reject(policy, params, error.to_string()),
        };
        self.activate_compiled(policy, compiled, params)
    }

    fn activate_compiled(
        &self,
        policy: ActionPolicy,
        compiled: CompiledPolicy,
        params: ActivatePolicyParams,
    ) -> Result<ActionPolicyRevision, RevisionError> {
        let _guard = self.activation.lock().map_err(|_| lock_error())?;
        let revision = self.revisions.commit(&RecordRevisionParams {
            policy,
            origin: params.origin,
            actor: params.actor,
            expected_revision: params.expected_revision,
            activate: true,
            failure_reason: None,
        })?;
        let active = ActivePolicy::new(&revision.metadata, compiled);
        self.install(PolicySnapshot::Valid(Arc::new(active)))?;
        self.revisions.persist();
        Ok(revision)
    }

    fn reject(
        &self,
        policy: ActionPolicy,
        params: ActivatePolicyParams,
        reason: String,
    ) -> Result<ActionPolicyRevision, RevisionError> {
        let _guard = self.activation.lock().map_err(|_| lock_error())?;
        self.revisions.commit(&RecordRevisionParams {
            policy,
            origin: params.origin,
            actor: params.actor,
            expected_revision: params.expected_revision,
            activate: false,
            failure_reason: Some(reason.clone()),
        })?;
        self.revisions.persist();
        if self.revisions.active_revision().is_none() {
            self.install(PolicySnapshot::Invalid)?;
        }
        Err(RevisionError::ValidationFailed(reason))
    }

    pub fn rollback(
        &self,
        source_id: u64,
        expected_revision: Option<u64>,
    ) -> Result<ActionPolicyRevision, RevisionError> {
        let policy = self
            .revisions
            .restore_policy(source_id, expected_revision)?;
        self.try_activate(
            policy,
            ActivatePolicyParams {
                origin: RevisionOrigin::Api,
                actor: None,
                expected_revision,
            },
        )
    }

    pub(crate) fn reconcile_startup(&self, value: Option<&serde_yaml::Value>) {
        let startup = match parse_startup(value) {
            Ok(startup) => startup,
            Err(error) => {
                tracing::error!(error = %error, "action policy configuration is invalid");
                self.reinstall_active_or_invalid();
                return;
            }
        };
        match startup {
            Some(policy) if self.matches_active(&policy) => self.reinstall_active_or_invalid(),
            Some(policy) => self.activate_startup(policy),
            None if self.revisions.active_revision().is_some() => {
                self.reinstall_active_or_invalid();
            }
            None => {
                let _ = self.install(PolicySnapshot::Unconfigured);
            }
        }
    }

    fn activate_startup(&self, policy: ActionPolicy) {
        let result = self.try_activate(
            policy,
            ActivatePolicyParams {
                origin: RevisionOrigin::Startup,
                actor: None,
                expected_revision: None,
            },
        );
        if let Err(error) = result {
            tracing::error!(error = %error, "startup action policy rejected");
            if self.revisions.active_revision().is_none() {
                let _ = self.install(PolicySnapshot::Invalid);
            } else {
                self.reinstall_active_or_invalid();
            }
        }
    }

    fn matches_active(&self, policy: &ActionPolicy) -> bool {
        self.revisions.active_revision().is_some_and(|revision| {
            policy_checksum(policy).is_some_and(|checksum| checksum == revision.metadata.checksum)
        })
    }

    fn reinstall_active_or_invalid(&self) {
        let Ok(_guard) = self.activation.lock() else {
            tracing::error!("action policy activation lock is unavailable");
            return;
        };
        let Some(revision) = self.revisions.active_revision() else {
            let _ = self.install(PolicySnapshot::Invalid);
            return;
        };
        let ActionPolicyRevision { metadata, policy } = revision;
        match policy.compile() {
            Ok(compiled) => {
                let active = ActivePolicy::new(&metadata, compiled);
                let _ = self.install(PolicySnapshot::Valid(Arc::new(active)));
            }
            Err(error) => {
                tracing::error!(revision_id = metadata.id, error = %error, "persisted action policy revision is invalid");
                let _ = self.install(PolicySnapshot::Invalid);
            }
        }
    }

    fn install(&self, snapshot: PolicySnapshot) -> Result<(), RevisionError> {
        let mut active = self.snapshot.write().map_err(|_| lock_error())?;
        *active = snapshot;
        Ok(())
    }
}

fn lock_error() -> RevisionError {
    RevisionError::ValidationFailed("internal lock error".to_owned())
}

fn parse_startup(
    value: Option<&serde_yaml::Value>,
) -> Result<Option<ActionPolicy>, serde_yaml::Error> {
    value
        .map(|value| serde_yaml::from_value(value.clone()))
        .transpose()
}
impl Default for ActionPolicyState {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    fn policy(id: &str) -> ActionPolicy {
        serde_json::from_value(serde_json::json!({"rules": [{"id": id, "effect": "deny", "selectors": {"operation": "tools/call"}}]})).expect("valid policy")
    }

    /// Assert that the valid snapshot carries the exact metadata of `revision`.
    fn assert_snapshot_matches(state: &ActionPolicyState, revision: &ActionPolicyRevision) {
        let active = match state.snapshot() {
            PolicySnapshot::Valid(active) => Some(active),
            PolicySnapshot::Unconfigured | PolicySnapshot::Invalid => None,
        }
        .expect("valid snapshot");
        assert_eq!(active.revision_id(), revision.metadata.id);
        assert_eq!(active.checksum(), revision.metadata.checksum);
        assert_eq!(active.policy().rules().len(), revision.policy.rules.len());
    }

    fn activate(
        state: &ActionPolicyState,
        policy: ActionPolicy,
        expected_revision: Option<u64>,
    ) -> Result<ActionPolicyRevision, RevisionError> {
        state.try_activate(
            policy,
            ActivatePolicyParams {
                origin: RevisionOrigin::Api,
                actor: None,
                expected_revision,
            },
        )
    }

    #[test]
    fn rejection_and_conflict_preserve_active_revision() {
        let state = ActionPolicyState::new();
        let first = activate(&state, policy("first"), None).expect("activate");
        assert!(matches!(
            activate(&state, policy("second"), Some(99)),
            Err(RevisionError::Conflict { .. })
        ));
        let invalid: ActionPolicy = serde_json::from_value(
            serde_json::json!({"rules": [{"id": "bad", "effect": "deny", "selectors": {}}]}),
        )
        .expect("schema");
        assert!(matches!(
            activate(&state, invalid, Some(first.metadata.id)),
            Err(RevisionError::ValidationFailed(_))
        ));
        assert_eq!(
            state.revision_store().active_revision_id(),
            Some(first.metadata.id)
        );
        assert_snapshot_matches(&state, &first);
        assert_eq!(state.revision_store().list_revisions().len(), 2);
    }

    #[test]
    fn rollback_creates_a_new_active_revision() {
        let state = ActionPolicyState::new();
        let first = activate(&state, policy("first"), None).expect("first");
        let second = activate(&state, policy("second"), Some(first.metadata.id)).expect("second");
        let restored = state
            .rollback(first.metadata.id, Some(second.metadata.id))
            .expect("rollback");
        assert!(restored.metadata.id > second.metadata.id);
        assert_eq!(restored.policy.rules[0].id, "first");
        assert_eq!(restored.metadata.checksum, first.metadata.checksum);
        assert_snapshot_matches(&state, &restored);
    }

    #[test]
    fn startup_reconciliation_preserves_active_policy() {
        let state = ActionPolicyState::new();
        let first = activate(&state, policy("first"), None).expect("activate");
        let invalid = serde_yaml::from_str::<serde_yaml::Value>("rules: invalid").expect("yaml");
        state.reconcile_startup(Some(&invalid));
        assert!(matches!(state.snapshot(), PolicySnapshot::Valid(_)));
        let rejected = serde_yaml::from_str::<serde_yaml::Value>(
            "rules:\n  - id: rejected\n    effect: deny\n    selectors: {}",
        )
        .expect("yaml");
        state.reconcile_startup(Some(&rejected));
        assert!(matches!(state.snapshot(), PolicySnapshot::Valid(_)));
        assert_eq!(state.revision_store().list_revisions().len(), 2);
        state.reconcile_startup(None);
        assert_snapshot_matches(&state, &first);
        let startup = serde_yaml::from_str::<serde_yaml::Value>(
            "rules:\n  - id: startup\n    effect: deny\n    selectors:\n      operation: tools/call",
        )
        .expect("yaml");
        state.reconcile_startup(Some(&startup));
        let active = state
            .revision_store()
            .active_revision()
            .expect("startup revision");
        assert_eq!(active.metadata.origin, RevisionOrigin::Startup);
        assert_snapshot_matches(&state, &active);
    }

    #[test]
    fn unconfigured_snapshot_has_no_revision() {
        let state = ActionPolicyState::new();
        state.reconcile_startup(None);
        assert!(matches!(state.snapshot(), PolicySnapshot::Unconfigured));
        assert_eq!(state.snapshot().revision_id(), None);
    }

    #[test]
    fn persistence_restart_restores_an_independent_active_stream() {
        let directory =
            std::env::temp_dir().join(format!("wanaku-action-policy-state-{}", std::process::id()));
        let path = directory.join("action-policy-revisions.json");
        let persistence: Arc<dyn crate::revision_persistence::RevisionPersistence> = Arc::new(
            crate::revision_persistence::FileRevisionPersistence::new(&path),
        );
        let state = ActionPolicyState::new().with_revision_persistence(persistence.clone());
        let revision = activate(&state, policy("persisted"), None).expect("activate");

        let restored = ActionPolicyState::new().with_revision_persistence(persistence);
        assert_eq!(
            restored.revision_store().active_revision_id(),
            Some(revision.metadata.id)
        );
        assert_snapshot_matches(&restored, &revision);
        restored.reconcile_startup(None);
        assert_snapshot_matches(&restored, &revision);
        let next = activate(&restored, policy("next"), Some(revision.metadata.id)).expect("next");
        assert!(next.metadata.id > revision.metadata.id);
        let _ = std::fs::remove_dir_all(directory);
    }

    #[test]
    fn first_invalid_policy_sets_invalid_snapshot() {
        let state = ActionPolicyState::new();
        let invalid: ActionPolicy = serde_json::from_value(
            serde_json::json!({"rules": [{"id": "bad", "effect": "deny", "selectors": {}}]}),
        )
        .expect("schema");
        assert!(matches!(
            activate(&state, invalid, None),
            Err(RevisionError::ValidationFailed(_))
        ));
        assert!(matches!(state.snapshot(), PolicySnapshot::Invalid));
        assert_eq!(state.snapshot().revision_id(), None);
        assert!(state.revision_store().active_revision().is_none());
        assert_eq!(state.revision_store().list_revisions().len(), 1);
    }

    #[test]
    #[expect(
        clippy::disallowed_methods,
        reason = "synchronous test needs OS threads to verify activation locking"
    )]
    fn concurrent_updates_with_one_expected_revision_do_not_both_activate() {
        let state = ActionPolicyState::new();
        let first = activate(&state, policy("first"), None).expect("first");
        let left = state.clone();
        let right = state.clone();
        let expected = first.metadata.id;
        let left = std::thread::spawn(move || activate(&left, policy("left"), Some(expected)));
        let right = std::thread::spawn(move || activate(&right, policy("right"), Some(expected)));
        let results = [
            left.join().expect("left thread"),
            right.join().expect("right thread"),
        ];
        assert_eq!(results.iter().filter(|result| result.is_ok()).count(), 1);
        assert_eq!(
            results
                .iter()
                .filter(|result| matches!(result, Err(RevisionError::Conflict { .. })))
                .count(),
            1
        );
    }
}
