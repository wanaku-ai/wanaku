use std::collections::hash_map::DefaultHasher;
use std::hash::{Hash, Hasher};
use std::sync::{Arc, RwLock};

use serde::{Deserialize, Serialize};

pub type RevisionId = u64;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
#[serde(rename_all = "snake_case")]
pub enum RevisionOrigin {
    Startup,
    Api,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
#[serde(rename_all = "snake_case")]
pub enum ActivationStatus {
    Active,
    Superseded,
    Rejected,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
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

pub trait RevisionRecord: Clone {
    fn metadata(&self) -> &RevisionMetadata;
    fn metadata_mut(&mut self) -> &mut RevisionMetadata;
}

#[derive(Debug, Serialize, Deserialize)]
#[serde(bound(serialize = "R: Serialize", deserialize = "R: Deserialize<'de>"))]
pub struct RevisionSnapshot<R> {
    #[serde(default)]
    pub revisions: Vec<R>,
    #[serde(default)]
    pub active_id: Option<RevisionId>,
    #[serde(default)]
    pub next_id: RevisionId,
}

impl<R> Default for RevisionSnapshot<R> {
    fn default() -> Self {
        Self {
            revisions: Vec::new(),
            active_id: None,
            next_id: 0,
        }
    }
}

/// Persistence contract for one independent revision stream.
pub trait RevisionPersistence<R>: Send + Sync {
    type Error;

    fn load(&self) -> Result<RevisionSnapshot<R>, Self::Error>;
    fn save(&self, snapshot: &RevisionSnapshot<R>) -> Result<(), Self::Error>;
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum RevisionHistoryError<E> {
    Conflict {
        expected: RevisionId,
        actual: RevisionId,
    },
    Build(E),
    Lock,
}

#[derive(Clone)]
pub struct RevisionHistory<R> {
    inner: Arc<RwLock<RevisionHistoryInner<R>>>,
}

struct RevisionHistoryInner<R> {
    revisions: Vec<R>,
    active_id: Option<RevisionId>,
    max_history: usize,
    next_id: RevisionId,
}

impl<R: RevisionRecord> RevisionHistory<R> {
    #[must_use]
    pub fn new(max_history: usize) -> Self {
        Self {
            inner: Arc::new(RwLock::new(RevisionHistoryInner {
                revisions: Vec::new(),
                active_id: None,
                max_history,
                next_id: 1,
            })),
        }
    }

    pub fn restore(&self, snapshot: RevisionSnapshot<R>) -> Result<(), RevisionHistoryError<()>> {
        let mut guard = self.inner.write().map_err(|_| RevisionHistoryError::Lock)?;
        let max_id = snapshot
            .revisions
            .iter()
            .map(|revision| revision.metadata().id)
            .max()
            .unwrap_or(0);
        guard.next_id = snapshot.next_id.max(max_id.saturating_add(1)).max(1);
        guard.revisions = snapshot.revisions;
        guard.active_id = snapshot.active_id;
        trim_history(&mut guard);
        Ok(())
    }

    pub fn snapshot(&self) -> Result<RevisionSnapshot<R>, RevisionHistoryError<()>> {
        let guard = self.inner.read().map_err(|_| RevisionHistoryError::Lock)?;
        Ok(RevisionSnapshot {
            revisions: guard.revisions.clone(),
            active_id: guard.active_id,
            next_id: guard.next_id,
        })
    }

    pub fn commit<E>(
        &self,
        expected: Option<RevisionId>,
        activate: bool,
        build: impl FnOnce(RevisionId) -> Result<R, E>,
    ) -> Result<R, RevisionHistoryError<E>> {
        let mut guard = self.inner.write().map_err(|_| RevisionHistoryError::Lock)?;
        check_concurrency(&guard, expected)?;
        let id = guard.next_id;
        let mut revision = build(id).map_err(RevisionHistoryError::Build)?;
        revision.metadata_mut().id = id;
        revision.metadata_mut().status = if activate {
            ActivationStatus::Active
        } else {
            ActivationStatus::Rejected
        };
        guard.next_id = id.saturating_add(1);
        if activate {
            mark_previous_superseded(&mut guard);
            guard.active_id = Some(id);
        }
        guard.revisions.push(revision.clone());
        trim_history(&mut guard);
        Ok(revision)
    }

    pub fn check_concurrency(
        &self,
        expected: Option<RevisionId>,
    ) -> Result<(), RevisionHistoryError<()>> {
        let guard = self.inner.read().map_err(|_| RevisionHistoryError::Lock)?;
        check_concurrency(&guard, expected)
    }

    #[must_use]
    pub fn active_revision(&self) -> Option<R> {
        let guard = self.inner.read().ok()?;
        let active_id = guard.active_id?;
        guard
            .revisions
            .iter()
            .find(|revision| revision.metadata().id == active_id)
            .cloned()
    }

    #[must_use]
    pub fn active_revision_id(&self) -> Option<RevisionId> {
        self.inner.read().ok().and_then(|guard| guard.active_id)
    }

    #[must_use]
    pub fn list_metadata(&self) -> Vec<RevisionMetadata> {
        self.inner
            .read()
            .map(|guard| {
                let mut metadata: Vec<_> = guard
                    .revisions
                    .iter()
                    .map(|revision| revision.metadata().clone())
                    .collect();
                metadata.sort_by_key(|entry| std::cmp::Reverse(entry.id));
                metadata
            })
            .unwrap_or_default()
    }

    #[must_use]
    pub fn get(&self, id: RevisionId) -> Option<R> {
        self.inner.read().ok().and_then(|guard| {
            guard
                .revisions
                .iter()
                .find(|revision| revision.metadata().id == id)
                .cloned()
        })
    }
}

fn check_concurrency<R, E>(
    history: &RevisionHistoryInner<R>,
    expected: Option<RevisionId>,
) -> Result<(), RevisionHistoryError<E>> {
    if let Some(expected) = expected {
        let actual = history.active_id.unwrap_or(0);
        if actual != expected {
            return Err(RevisionHistoryError::Conflict { expected, actual });
        }
    }
    Ok(())
}

fn mark_previous_superseded<R: RevisionRecord>(history: &mut RevisionHistoryInner<R>) {
    if let Some(active_id) = history.active_id
        && let Some(previous) = history
            .revisions
            .iter_mut()
            .find(|revision| revision.metadata().id == active_id)
    {
        previous.metadata_mut().status = ActivationStatus::Superseded;
    }
}

fn trim_history<R: RevisionRecord>(history: &mut RevisionHistoryInner<R>) {
    while history.revisions.len() > history.max_history {
        let active_is_oldest = history.active_id == Some(history.revisions[0].metadata().id);
        if active_is_oldest && history.revisions.len() <= history.max_history.saturating_add(1) {
            break;
        }
        if active_is_oldest {
            history.revisions.remove(1);
        } else {
            history.revisions.remove(0);
        }
    }
}

/// Compute the checksum used by all revision streams.
pub fn checksum<T: Serialize>(value: &T) -> Result<String, serde_json::Error> {
    let json = serde_json::to_string(value)?;
    let mut hasher = DefaultHasher::new();
    json.hash(&mut hasher);
    Ok(format!("{:016x}", hasher.finish()))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[derive(Clone)]
    struct TestRevision {
        metadata: RevisionMetadata,
        value: String,
    }

    impl RevisionRecord for TestRevision {
        fn metadata(&self) -> &RevisionMetadata {
            &self.metadata
        }

        fn metadata_mut(&mut self) -> &mut RevisionMetadata {
            &mut self.metadata
        }
    }

    fn build(id: RevisionId, value: &str, active: bool) -> TestRevision {
        TestRevision {
            metadata: RevisionMetadata {
                id,
                created_at: String::new(),
                activated_at: None,
                status: if active {
                    ActivationStatus::Active
                } else {
                    ActivationStatus::Rejected
                },
                checksum: value.to_owned(),
                origin: RevisionOrigin::Api,
                actor: None,
                failure_reason: None,
            },
            value: value.to_owned(),
        }
    }

    #[test]
    fn commit_is_bounded_and_supersedes_previous() {
        let history = RevisionHistory::new(2);
        for value in ["one", "two", "three"] {
            assert!(
                history
                    .commit(None, true, |id| Ok::<_, ()>(build(id, value, true)))
                    .is_ok()
            );
        }
        let entries = history.list_metadata();
        assert_eq!(entries.len(), 2);
        assert_eq!(entries[0].status, ActivationStatus::Active);
        assert_eq!(entries[1].status, ActivationStatus::Superseded);
    }

    #[test]
    fn concurrency_and_restore_keep_ids_monotonic() {
        let history = RevisionHistory::new(5);
        let first = history
            .commit(None, true, |id| Ok::<_, ()>(build(id, "one", true)))
            .ok();
        let Some(first) = first else {
            return;
        };
        assert!(matches!(
            history.check_concurrency(Some(first.metadata.id + 1)),
            Err(RevisionHistoryError::Conflict { .. })
        ));
        let snapshot = history.snapshot().ok();
        let Some(snapshot) = snapshot else {
            return;
        };
        let restored = RevisionHistory::new(5);
        assert!(restored.restore(snapshot).is_ok());
        let next = restored
            .commit(None, true, |id| Ok::<_, ()>(build(id, "two", true)))
            .ok();
        assert!(next.is_some_and(|entry| entry.metadata.id > first.metadata.id));
    }

    #[test]
    fn lookup_and_checksum_are_stable() {
        let history = RevisionHistory::new(5);
        let revision = history
            .commit(None, true, |id| Ok::<_, ()>(build(id, "value", true)))
            .ok();
        let Some(revision) = revision else {
            return;
        };
        assert_eq!(
            history.get(revision.metadata.id).map(|entry| entry.value),
            Some("value".to_owned())
        );
        let first_checksum = checksum(&vec!["value"]);
        let second_checksum = checksum(&vec!["value"]);
        assert!(matches!(
            (first_checksum, second_checksum),
            (Ok(first), Ok(second)) if first == second
        ));
    }

    #[test]
    fn commit_corrects_builder_owned_id_and_status() {
        let history = RevisionHistory::new(5);
        let active = history
            .commit(None, true, |id| {
                let mut revision = build(id + 99, "active", false);
                revision.metadata.status = ActivationStatus::Superseded;
                Ok::<_, ()>(revision)
            })
            .ok();
        assert!(active.is_some_and(|revision| {
            revision.metadata.id == 1 && revision.metadata.status == ActivationStatus::Active
        }));

        let rejected = history
            .commit(None, false, |id| {
                Ok::<_, ()>(build(id + 99, "rejected", true))
            })
            .ok();
        assert!(rejected.is_some_and(|revision| {
            revision.metadata.id == 2 && revision.metadata.status == ActivationStatus::Rejected
        }));
    }

    #[test]
    fn histories_have_independent_ids_and_active_state() {
        let first = RevisionHistory::new(5);
        let second = RevisionHistory::new(5);
        let first_revision = first
            .commit(None, true, |id| Ok::<_, ()>(build(id, "first", true)))
            .ok();
        let second_revision = second
            .commit(None, false, |id| Ok::<_, ()>(build(id, "second", false)))
            .ok();

        assert_eq!(first_revision.map(|revision| revision.metadata.id), Some(1));
        assert_eq!(
            second_revision.map(|revision| revision.metadata.id),
            Some(1)
        );
        assert_eq!(first.active_revision_id(), Some(1));
        assert_eq!(second.active_revision_id(), None);
    }
}
