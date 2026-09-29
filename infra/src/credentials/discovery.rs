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

use super::broker::{BrokerError, CredentialBroker};
use crate::registry::InMemoryRegistry;

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

/// Resolve the outbound discovery headers for a forward.
///
/// Returns an empty map when the forward has no discovery binding (discovery
/// proceeds unauthenticated). Returns brokered, sensitive headers when a binding
/// is configured and brokerage succeeds. Returns an error (fail closed) when a
/// configured binding cannot be brokered.
pub async fn resolve_discovery_headers(
    broker: &CredentialBroker,
    registry: &InMemoryRegistry,
    forward: &ForwardEntry,
) -> Result<HashMap<HeaderName, HeaderValue>, DiscoveryCredentialError> {
    let mut headers = HashMap::new();

    let Some(binding_id) = forward.binding_for(CredentialPurpose::Discovery) else {
        // No discovery binding configured: discover unauthenticated.
        return Ok(headers);
    };

    let Some(binding) = registry.get_binding(binding_id) else {
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

    trace!(
        forward_id = %forward.forward_id(),
        binding_id = %brokered.audit.binding_id,
        revision = brokered.audit.binding_revision,
        outcome = %brokered.audit.outcome.as_str(),
        mechanism = %brokered.audit.mechanism,
        "resolved brokered discovery credential headers"
    );

    Ok(headers)
}
