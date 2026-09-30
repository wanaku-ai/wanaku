//! Credential resolution for the forward discovery path.
//!
//! Discovery runs outside the request filter pipeline: at startup, in the
//! management API, and in the background reconnect loop. This helper is the
//! single point where a forward's discovery credential binding turns into
//! outbound headers for `discover_forward`.
//!
//! The helper fails closed. When a forward configures a discovery binding, any
//! missing binding, brokerage error, or invalid header value returns an error
//! and no discovery request is sent. Secret values are never logged; resolved
//! values are carried as sensitive [`HeaderValue`]s.

use std::collections::HashMap;

use http::{HeaderName, HeaderValue};
use tracing::{trace, warn};

use wanaku_types::credentials::CredentialPurpose;
use wanaku_types::credentials::binding::UseScope;
use wanaku_types::registry::{BindingRegistry, ForwardEntry};

use super::audit_sink::{CredentialAuditSink, record_audit};
use super::broker::{BrokerError, CredentialAuditRecord, CredentialBroker, ResolutionOutcome};
use super::redaction::CredentialRedactor;
use crate::registry::InMemoryRegistry;

/// The outbound discovery headers plus the redactor for the discovery response.
///
/// Mirrors the request-pipeline `ForwardExchange`: the headers authenticate the
/// discovery call, and the redactor strips any injected secret the upstream
/// echoes back into the discovered tools, resources, prompts, or server identity
/// before that metadata is persisted and served to agents.
#[derive(Debug)]
pub struct DiscoveryExchange {
    /// Headers to send on the discovery call (may include a brokered credential).
    pub headers: HashMap<HeaderName, HeaderValue>,
    /// Redactor for the discovery response content.
    pub redactor: CredentialRedactor,
}

/// A discovery credential resolution failure. Every variant fails closed: the
/// caller must not run discovery against the upstream when this is returned.
#[derive(Debug, thiserror::Error)]
pub enum DiscoveryCredentialError {
    /// The forward references a discovery binding that is not registered.
    #[error("credential binding not found")]
    BindingNotFound,
    /// The broker denied or failed the discovery brokerage attempt.
    #[error("credential brokerage failed")]
    Brokerage(#[from] BrokerError),
    /// A brokered credential produced a header value the transport rejects.
    #[error("invalid brokered credential header value")]
    InvalidHeaderValue,
}

/// Resolve the outbound discovery exchange for a forward.
///
/// Returns an empty exchange (no headers, no-op redactor) when the forward has no
/// discovery binding (discovery proceeds unauthenticated). Returns brokered,
/// sensitive headers and a redactor for the injected secret when a binding is
/// configured and brokerage succeeds. Returns an error (fail closed) when a
/// configured binding cannot be brokered.
///
/// When `audit_sink` is present, every fail-closed denial records exactly one
/// redacted audit record through it: a configured binding that is not registered
/// records a `Denied` record, a brokerage failure records the broker's record,
/// and a success records the brokerage record after the headers are built. Only
/// the no-binding early return performs no brokerage and records nothing.
pub async fn resolve_discovery_headers(
    broker: &CredentialBroker,
    registry: &InMemoryRegistry,
    forward: &ForwardEntry,
    audit_sink: Option<&dyn CredentialAuditSink>,
) -> Result<DiscoveryExchange, DiscoveryCredentialError> {
    let mut headers = HashMap::new();

    let Some(binding_id) = forward.binding_for(CredentialPurpose::Discovery) else {
        // No discovery binding configured: discover unauthenticated and redact
        // nothing.
        return Ok(DiscoveryExchange {
            headers,
            redactor: CredentialRedactor::default(),
        });
    };

    let Some(binding) = registry.get_binding(binding_id) else {
        // Fail closed and audit the denial: a forward that references a binding
        // that is not registered never reaches the broker.
        record_audit(
            audit_sink,
            &CredentialAuditRecord::denial(
                binding_id,
                forward.forward_id(),
                CredentialPurpose::Discovery,
                CredentialAuditRecord::REASON_BINDING_NOT_FOUND,
            ),
        );
        warn!(
            forward_id = %forward.forward_id(),
            binding_id = %binding_id,
            "discovery credential binding referenced by forward not found"
        );
        return Err(DiscoveryCredentialError::BindingNotFound);
    };

    // Discovery is a forward-level operation with no governed item or per-call
    // identity. The scope still carries the forward namespace for the resolver.
    let scope = UseScope {
        namespace: forward.namespace.as_deref(),
        governed_item: None,
        operation: None,
        identity: None,
    };

    let brokered = match broker
        .broker(
            &binding,
            forward.forward_id(),
            &forward.address,
            CredentialPurpose::Discovery,
            &scope,
        )
        .await
    {
        Ok(brokered) => brokered,
        Err(e) => {
            // Fail closed. The audit record is redacted (no secret material).
            record_audit(audit_sink, &e.audit);
            warn!(
                forward_id = %forward.forward_id(),
                binding_id = %e.audit.binding_id,
                outcome = %e.audit.outcome.as_str(),
                reason = ?e.audit.failure_reason,
                "discovery credential brokerage failed; skipping discovery"
            );
            return Err(DiscoveryCredentialError::Brokerage(e));
        }
    };

    for header in &brokered.headers {
        let Ok(mut value) = HeaderValue::from_bytes(header.expose_value().expose_bytes()) else {
            // The brokerage succeeded but the resolved value is not a valid header
            // value. Record the failure so a denied discovery is never audited as
            // allowed, then fail closed.
            let mut audit = brokered.audit.clone();
            audit.outcome = ResolutionOutcome::Failed;
            audit.failure_reason =
                Some(CredentialAuditRecord::REASON_INVALID_HEADER_VALUE.to_owned());
            record_audit(audit_sink, &audit);
            warn!(
                forward_id = %forward.forward_id(),
                header = %header.name(),
                "brokered discovery credential produced an invalid header value"
            );
            return Err(DiscoveryCredentialError::InvalidHeaderValue);
        };
        value.set_sensitive(true);
        headers.insert(header.header_name().clone(), value);
    }

    // The headers are built, so record the brokerage outcome now. Recording after
    // the header loop keeps a header-rejection denial from being audited as an
    // allowed brokerage.
    record_audit(audit_sink, &brokered.audit);

    trace!(
        forward_id = %forward.forward_id(),
        binding_id = %brokered.audit.binding_id,
        revision = brokered.audit.binding_revision,
        outcome = %brokered.audit.outcome.as_str(),
        mechanism = %brokered.audit.mechanism,
        "resolved brokered discovery credential headers"
    );

    // Carry a redactor for the injected secret so the caller can strip it from
    // the discovery response before persisting and serving it to agents.
    let redactor = CredentialRedactor::from_brokered(&brokered);
    Ok(DiscoveryExchange { headers, redactor })
}

#[cfg(test)]
mod tests {
    use std::collections::HashMap;
    use std::sync::Arc;

    use super::*;
    use wanaku_types::credentials::binding::{
        BindingRestrictions, CacheRules, CredentialBinding, NormalizedOrigin,
    };
    use wanaku_types::credentials::fake_resolver::FakeResolver;
    use wanaku_types::credentials::injection::InjectionMechanism;
    use wanaku_types::credentials::resolver::{ResolverRegistry, SecretRef};
    use wanaku_types::registry::BindingRegistry;

    fn broker() -> CredentialBroker {
        let resolvers = ResolverRegistry::new()
            .with_resolver(Arc::new(FakeResolver::new().with_value("token", "s3cr3t")));
        CredentialBroker::new(resolvers)
    }

    fn binding(id: &str) -> CredentialBinding {
        CredentialBinding {
            id: id.to_owned(),
            forward_id: "fwd-a".to_owned(),
            origin: NormalizedOrigin::from_address("https://api.example.com").unwrap(),
            mechanism: InjectionMechanism::Bearer,
            secret_refs: vec![SecretRef::parse("fake:token").unwrap()],
            allowed_purposes: vec![CredentialPurpose::Discovery],
            restrictions: BindingRestrictions::default(),
            cache: CacheRules::default(),
            revision: 1,
        }
    }

    fn forward(address: &str, binding_id: Option<&str>) -> ForwardEntry {
        let mut credential_bindings = HashMap::new();
        if let Some(id) = binding_id {
            credential_bindings.insert(CredentialPurpose::Discovery, id.to_owned());
        }
        ForwardEntry {
            name: "fwd-a".to_owned(),
            address: address.to_owned(),
            namespace: None,
            server_info: None,
            labels: HashMap::new(),
            available: true,
            status_message: None,
            credential_bindings,
        }
    }

    #[tokio::test]
    async fn no_binding_returns_empty() {
        let broker = broker();
        let registry = InMemoryRegistry::new();
        let forward = forward("https://api.example.com/mcp", None);

        let exchange = resolve_discovery_headers(&broker, &registry, &forward, None)
            .await
            .unwrap();

        assert!(exchange.headers.is_empty());
        // With no binding there is nothing to redact.
        assert!(exchange.redactor.is_empty());
    }

    #[tokio::test]
    async fn missing_binding_fails_closed() {
        let broker = broker();
        let registry = InMemoryRegistry::new();
        let forward = forward("https://api.example.com/mcp", Some("missing"));

        let err = resolve_discovery_headers(&broker, &registry, &forward, None)
            .await
            .unwrap_err();

        assert!(matches!(err, DiscoveryCredentialError::BindingNotFound));
    }

    #[tokio::test]
    async fn brokerage_error_fails_closed() {
        let broker = broker();
        let registry = InMemoryRegistry::new();
        registry.register_binding(binding("b1"));
        // The forward address origin does not match the binding origin.
        let forward = forward("https://evil.example.com/mcp", Some("b1"));

        let err = resolve_discovery_headers(&broker, &registry, &forward, None)
            .await
            .unwrap_err();

        assert!(matches!(err, DiscoveryCredentialError::Brokerage(_)));
    }

    #[tokio::test]
    async fn success_marks_headers_sensitive() {
        let broker = broker();
        let registry = InMemoryRegistry::new();
        registry.register_binding(binding("b1"));
        let forward = forward("https://api.example.com/mcp", Some("b1"));

        let exchange = resolve_discovery_headers(&broker, &registry, &forward, None)
            .await
            .unwrap();

        let value = exchange
            .headers
            .get(&HeaderName::from_static("authorization"))
            .unwrap();
        assert_eq!(value.to_str().unwrap(), "Bearer s3cr3t");
        // Discovery credentials must be marked sensitive for transport redaction.
        assert!(value.is_sensitive());
        // The redactor must strip both the raw secret and the full injected
        // header value from any echo in the discovery response.
        assert!(!exchange.redactor.is_empty());
        assert_eq!(exchange.redactor.redact("saw s3cr3t"), "saw <redacted>");
        assert_eq!(
            exchange.redactor.redact("Authorization: Bearer s3cr3t"),
            "Authorization: <redacted>"
        );
    }

    #[derive(Default)]
    struct RecordingSink {
        records: std::sync::Mutex<Vec<super::super::CredentialAuditRecord>>,
    }

    impl super::super::CredentialAuditSink for RecordingSink {
        fn record_credential_audit(&self, record: &super::super::CredentialAuditRecord) {
            if let Ok(mut records) = self.records.lock() {
                records.push(record.clone());
            }
        }
    }

    #[tokio::test]
    async fn successful_discovery_emits_one_audit_record() {
        let broker = broker();
        let registry = InMemoryRegistry::new();
        registry.register_binding(binding("b1"));
        let forward = forward("https://api.example.com/mcp", Some("b1"));
        let sink = RecordingSink::default();

        resolve_discovery_headers(&broker, &registry, &forward, Some(&sink))
            .await
            .unwrap();

        let records = sink.records.lock().unwrap();
        assert_eq!(records.len(), 1);
        assert_eq!(
            records[0].outcome,
            super::super::ResolutionOutcome::Resolved
        );
        assert_eq!(records[0].purpose, CredentialPurpose::Discovery);
        // The record must never carry secret material.
        assert!(!format!("{:?}", records[0]).contains("s3cr3t"));
    }

    #[tokio::test]
    async fn failed_discovery_emits_one_audit_record() {
        let broker = broker();
        let registry = InMemoryRegistry::new();
        registry.register_binding(binding("b1"));
        // The forward address origin does not match the binding origin.
        let forward = forward("https://evil.example.com/mcp", Some("b1"));
        let sink = RecordingSink::default();

        let err = resolve_discovery_headers(&broker, &registry, &forward, Some(&sink))
            .await
            .unwrap_err();

        assert!(matches!(err, DiscoveryCredentialError::Brokerage(_)));
        let records = sink.records.lock().unwrap();
        assert_eq!(records.len(), 1);
        assert_eq!(records[0].outcome, super::super::ResolutionOutcome::Denied);
        assert_eq!(
            records[0].failure_reason.as_deref(),
            Some("origin_mismatch")
        );
    }

    #[tokio::test]
    async fn missing_binding_emits_denial_record() {
        let broker = broker();
        let registry = InMemoryRegistry::new();
        // The forward references a binding that is not registered.
        let forward = forward("https://api.example.com/mcp", Some("missing"));
        let sink = RecordingSink::default();

        let err = resolve_discovery_headers(&broker, &registry, &forward, Some(&sink))
            .await
            .unwrap_err();

        assert!(matches!(err, DiscoveryCredentialError::BindingNotFound));
        let records = sink.records.lock().unwrap();
        assert_eq!(records.len(), 1);
        assert_eq!(records[0].outcome, super::super::ResolutionOutcome::Denied);
        assert_eq!(
            records[0].failure_reason.as_deref(),
            Some(CredentialAuditRecord::REASON_BINDING_NOT_FOUND)
        );
    }

    #[tokio::test]
    async fn unusable_resolved_value_records_failure_not_allow() {
        // A resolved value with a control character cannot form a valid header.
        // The broker rejects it during header construction, so the recorded
        // outcome must be a failure, never an allow.
        let resolvers = ResolverRegistry::new().with_resolver(Arc::new(
            FakeResolver::new().with_value("token", "bad\nvalue"),
        ));
        let broker = CredentialBroker::new(resolvers);
        let registry = InMemoryRegistry::new();
        registry.register_binding(binding("b1"));
        let forward = forward("https://api.example.com/mcp", Some("b1"));
        let sink = RecordingSink::default();

        let err = resolve_discovery_headers(&broker, &registry, &forward, Some(&sink))
            .await
            .unwrap_err();

        assert!(matches!(err, DiscoveryCredentialError::Brokerage(_)));
        let records = sink.records.lock().unwrap();
        assert_eq!(records.len(), 1);
        // The denied discovery must never be audited as allowed.
        assert_eq!(records[0].outcome, super::super::ResolutionOutcome::Failed);
        assert_eq!(
            records[0].failure_reason.as_deref(),
            Some("injection_failure")
        );
    }
}
