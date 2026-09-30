//! Durable recording of credential brokerage audit records.
//!
//! The credential subsystem emits a redacted [`CredentialAuditRecord`] for every
//! brokerage attempt through the [`CredentialAuditSink`] trait. This module owns
//! the single mapping point from that shape to the audit subsystem's
//! [`AuditEvent`] schema, and routes each event into the shared audit store.
//!
//! Known limitation: a credential audit event carries no correlation or request
//! identifier. The [`CredentialAuditRecord`] does not propagate the triggering
//! request context, so a credential event cannot yet be joined to the tool call
//! or governance decision that triggered it. Correlation is a follow-up.

use wanaku_infra::credentials::{CredentialAuditRecord, CredentialAuditSink, ResolutionOutcome};
use wanaku_types::audit::{
    AuditCategory, AuditDecision, AuditEvent, AuditStore, InMemoryAuditStore,
};

/// The filter name recorded on credential brokerage audit events.
const CREDENTIAL_FILTER: &str = "wanaku_credentials";

/// A [`CredentialAuditSink`] backed by the durable audit store.
///
/// Wraps the shared [`InMemoryAuditStore`] and adapts each redacted
/// [`CredentialAuditRecord`] into an [`AuditEvent`] before recording it.
pub struct CredentialAuditRecorder {
    store: InMemoryAuditStore,
}

impl CredentialAuditRecorder {
    /// Build a recorder that writes into `store`.
    #[must_use]
    pub const fn new(store: InMemoryAuditStore) -> Self {
        Self { store }
    }
}

impl CredentialAuditSink for CredentialAuditRecorder {
    fn record_credential_audit(&self, record: &CredentialAuditRecord) {
        self.store.record(credential_audit_event(record));
    }
}

/// Map a redacted credential brokerage record into a durable audit event.
///
/// The decision mapping is: `Resolved` and `CacheHit` map to `Allow`, `Denied`
/// maps to `Block`, and `Failed` maps to `Error`. The operation marker is
/// `credential/<purpose>` so discovery and invocation records stay distinct. The
/// function copies only non-secret metadata; the record never carries a secret
/// reference path or a resolved credential value.
#[expect(
    clippy::too_many_lines,
    reason = "flat mapping of each non-secret record field onto the audit event"
)]
fn credential_audit_event(record: &CredentialAuditRecord) -> AuditEvent {
    let decision = match record.outcome {
        ResolutionOutcome::Resolved | ResolutionOutcome::CacheHit => AuditDecision::Allow,
        ResolutionOutcome::Denied => AuditDecision::Block,
        ResolutionOutcome::Failed => AuditDecision::Error,
    };
    let reason_code = record
        .failure_reason
        .clone()
        .unwrap_or_else(|| record.outcome.as_str().to_owned());
    let explanation = format!(
        "credential brokerage {} for forward {}",
        record.outcome.as_str(),
        record.forward_id
    );

    let mut event = AuditEvent::new(
        AuditCategory::Decision,
        decision,
        format!("credential/{}", record.purpose),
        reason_code,
        explanation,
    );
    event.filter = Some(CREDENTIAL_FILTER.to_owned());
    event.target = Some(record.forward_id.clone());
    event.target_type = Some("forward".to_owned());
    event.upstream_id = Some(record.upstream_origin.clone());

    event.attributes.insert(
        "binding_id".to_owned(),
        serde_json::Value::String(record.binding_id.clone()),
    );
    event.attributes.insert(
        "binding_revision".to_owned(),
        serde_json::Value::from(record.binding_revision),
    );
    event.attributes.insert(
        "resolver_types".to_owned(),
        serde_json::Value::from(record.resolver_types.clone()),
    );
    event.attributes.insert(
        "mechanism".to_owned(),
        serde_json::Value::String(record.mechanism.clone()),
    );
    event.attributes.insert(
        "purpose".to_owned(),
        serde_json::Value::String(record.purpose.to_string()),
    );
    event.attributes.insert(
        "outcome".to_owned(),
        serde_json::Value::String(record.outcome.as_str().to_owned()),
    );
    event.attributes.insert(
        "expiry_category".to_owned(),
        serde_json::Value::String(record.expiry_category.as_str().to_owned()),
    );
    event
}

#[cfg(test)]
mod tests {
    use super::*;
    use wanaku_infra::credentials::ExpiryCategory;
    use wanaku_types::audit::{AuditQuery, DEFAULT_AUDIT_CAPACITY};
    use wanaku_types::credentials::CredentialPurpose;

    fn record(outcome: ResolutionOutcome, purpose: CredentialPurpose) -> CredentialAuditRecord {
        CredentialAuditRecord {
            binding_id: "b1".to_owned(),
            binding_revision: 3,
            resolver_types: vec!["fake".to_owned()],
            outcome,
            mechanism: "bearer".to_owned(),
            forward_id: "fwd-a".to_owned(),
            upstream_origin: "https://api.example.com:443".to_owned(),
            purpose,
            expiry_category: ExpiryCategory::NotCached,
            failure_reason: None,
        }
    }

    #[test]
    fn maps_resolved_to_allow_with_distinct_operation() {
        let event = credential_audit_event(&record(
            ResolutionOutcome::Resolved,
            CredentialPurpose::Invocation,
        ));

        assert_eq!(event.decision, AuditDecision::Allow);
        assert_eq!(event.category, AuditCategory::Decision);
        assert_eq!(event.operation, "credential/invocation");
        assert_eq!(event.reason_code, "resolved");
        assert_eq!(event.filter.as_deref(), Some(CREDENTIAL_FILTER));
        assert_eq!(event.target.as_deref(), Some("fwd-a"));
        assert_eq!(event.target_type.as_deref(), Some("forward"));
    }

    #[test]
    fn maps_cache_hit_to_allow() {
        let event = credential_audit_event(&record(
            ResolutionOutcome::CacheHit,
            CredentialPurpose::Discovery,
        ));

        assert_eq!(event.decision, AuditDecision::Allow);
        assert_eq!(event.operation, "credential/discovery");
    }

    #[test]
    fn maps_denied_to_block_with_failure_reason() {
        let mut denied = record(ResolutionOutcome::Denied, CredentialPurpose::Discovery);
        denied.failure_reason = Some("origin_mismatch".to_owned());

        let event = credential_audit_event(&denied);

        assert_eq!(event.decision, AuditDecision::Block);
        assert_eq!(event.reason_code, "origin_mismatch");
    }

    #[test]
    fn maps_failed_to_error() {
        let event = credential_audit_event(&record(
            ResolutionOutcome::Failed,
            CredentialPurpose::Invocation,
        ));

        assert_eq!(event.decision, AuditDecision::Error);
    }

    #[test]
    fn recorder_writes_event_into_store() {
        let store = InMemoryAuditStore::new(DEFAULT_AUDIT_CAPACITY);
        let recorder = CredentialAuditRecorder::new(store.clone());

        recorder.record_credential_audit(&record(
            ResolutionOutcome::Resolved,
            CredentialPurpose::Discovery,
        ));

        let page = store.query(&AuditQuery {
            limit: 10,
            ..AuditQuery::default()
        });
        assert_eq!(page.total, 1);
        assert_eq!(page.events[0].operation, "credential/discovery");
    }
}
