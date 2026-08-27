use std::sync::Arc;

use serde::{Deserialize, Serialize};
use wanaku_types::revision::{RevisionHistory, RevisionHistoryError, RevisionRecord};
use wanaku_types::time::iso_now;

pub use wanaku_types::revision::{ActivationStatus, RevisionId, RevisionMetadata, RevisionOrigin};

use crate::ActionPolicy;
use crate::revision_persistence::RevisionPersistence;

const DEFAULT_MAX_HISTORY: usize = 50;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ActionPolicyRevision {
    pub metadata: RevisionMetadata,
    pub policy: ActionPolicy,
}

impl RevisionRecord for ActionPolicyRevision {
    fn metadata(&self) -> &RevisionMetadata {
        &self.metadata
    }
    fn metadata_mut(&mut self) -> &mut RevisionMetadata {
        &mut self.metadata
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum RevisionError {
    Conflict {
        expected: RevisionId,
        actual: RevisionId,
    },
    NotFound(RevisionId),
    ValidationFailed(String),
}

impl std::fmt::Display for RevisionError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::Conflict { expected, actual } => write!(
                f,
                "conflict: expected active revision {expected}, but current is {actual}"
            ),
            Self::NotFound(id) => write!(f, "revision {id} not found"),
            Self::ValidationFailed(reason) => write!(f, "validation failed: {reason}"),
        }
    }
}

pub struct RecordRevisionParams {
    pub policy: ActionPolicy,
    pub origin: RevisionOrigin,
    pub actor: Option<String>,
    pub expected_revision: Option<RevisionId>,
    pub activate: bool,
    pub failure_reason: Option<String>,
}

#[derive(Clone)]
pub struct RevisionStore {
    history: RevisionHistory<ActionPolicyRevision>,
    persistence: Option<Arc<dyn RevisionPersistence>>,
}

impl RevisionStore {
    #[must_use]
    pub fn new() -> Self {
        Self {
            history: RevisionHistory::new(DEFAULT_MAX_HISTORY),
            persistence: None,
        }
    }

    #[must_use]
    pub fn with_persistence(persistence: Arc<dyn RevisionPersistence>) -> Self {
        let store = Self {
            history: RevisionHistory::new(DEFAULT_MAX_HISTORY),
            persistence: Some(persistence),
        };
        store.load();
        store
    }

    fn load(&self) {
        let Some(persistence) = &self.persistence else {
            return;
        };
        match persistence.load() {
            Ok(snapshot) => {
                if self.history.restore(snapshot).is_err() {
                    tracing::error!("action policy revision history lock is unavailable");
                }
            }
            Err(error) => tracing::error!(error = %error, "failed to load action policy revisions"),
        }
    }

    pub(crate) fn persist(&self) {
        let Some(persistence) = &self.persistence else {
            return;
        };
        let Ok(snapshot) = self.history.snapshot() else {
            tracing::error!("action policy revision history lock is unavailable");
            return;
        };
        if let Err(error) = persistence.save(&snapshot) {
            tracing::error!(error = %error, "failed to persist action policy revisions");
        }
    }

    pub fn active_revision(&self) -> Option<ActionPolicyRevision> {
        self.history.active_revision()
    }
    pub fn active_revision_id(&self) -> Option<RevisionId> {
        self.history.active_revision_id()
    }
    pub fn list_revisions(&self) -> Vec<RevisionMetadata> {
        self.history.list_metadata()
    }
    pub fn get_revision(&self, id: RevisionId) -> Option<ActionPolicyRevision> {
        self.history.get(id)
    }

    pub(crate) fn commit(
        &self,
        params: &RecordRevisionParams,
    ) -> Result<ActionPolicyRevision, RevisionError> {
        self.history
            .commit(params.expected_revision, params.activate, |id| {
                build_revision(id, params)
            })
            .map_err(map_error)
    }

    pub fn restore_policy(
        &self,
        id: RevisionId,
        expected: Option<RevisionId>,
    ) -> Result<ActionPolicy, RevisionError> {
        let policy = self
            .get_revision(id)
            .ok_or(RevisionError::NotFound(id))?
            .policy;
        self.history
            .check_concurrency(expected)
            .map_err(|error| map_unit_error(&error))?;
        Ok(policy)
    }
}

impl Default for RevisionStore {
    fn default() -> Self {
        Self::new()
    }
}

fn build_revision(
    id: RevisionId,
    params: &RecordRevisionParams,
) -> Result<ActionPolicyRevision, RevisionError> {
    let now = iso_now();
    let checksum = wanaku_types::revision::checksum(&params.policy).map_err(|error| {
        RevisionError::ValidationFailed(format!("failed to serialize policy: {error}"))
    })?;
    Ok(ActionPolicyRevision {
        metadata: RevisionMetadata {
            id,
            created_at: now.clone(),
            activated_at: params.activate.then_some(now),
            status: if params.activate {
                ActivationStatus::Active
            } else {
                ActivationStatus::Rejected
            },
            checksum,
            origin: params.origin.clone(),
            actor: params.actor.clone(),
            failure_reason: params.failure_reason.clone(),
        },
        policy: params.policy.clone(),
    })
}

fn map_error(error: RevisionHistoryError<RevisionError>) -> RevisionError {
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

fn map_unit_error(error: &RevisionHistoryError<()>) -> RevisionError {
    match error {
        RevisionHistoryError::Conflict { expected, actual } => RevisionError::Conflict {
            expected: *expected,
            actual: *actual,
        },
        RevisionHistoryError::Build(()) | RevisionHistoryError::Lock => {
            RevisionError::ValidationFailed("internal lock error".to_owned())
        }
    }
}

#[must_use]
pub fn policy_checksum(policy: &ActionPolicy) -> Option<String> {
    wanaku_types::revision::checksum(policy).ok()
}
