//! The audit sink abstraction for credential brokerage records.
//!
//! The broker, filter, and discovery paths route every redacted
//! [`CredentialAuditRecord`] to a durable audit sink through this trait. The
//! trait keeps this module independent of any concrete audit store: the server
//! wires a store-backed implementation, and tests can supply a lightweight fake.

use super::broker::CredentialAuditRecord;

/// A durable sink for redacted credential brokerage audit records.
///
/// Implementations receive only non-secret metadata: the record never carries a
/// secret reference path or a resolved credential value. Recording must not
/// panic and must not block the brokerage path.
pub trait CredentialAuditSink: Send + Sync {
    /// Record one redacted credential brokerage audit record.
    fn record_credential_audit(&self, record: &CredentialAuditRecord);
}

/// Record a credential brokerage audit record when a sink is configured.
///
/// The request-pipeline filter and the forward discovery path both hold an
/// optional sink and must record on every brokerage outcome. This helper keeps
/// that call site identical in both crates.
pub fn record_audit(sink: Option<&dyn CredentialAuditSink>, record: &CredentialAuditRecord) {
    if let Some(sink) = sink {
        sink.record_credential_audit(record);
    }
}
