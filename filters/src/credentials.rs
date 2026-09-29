//! Just-in-time credential injection for forwarded MCP calls.
//!
//! This helper is the single point where the filter pipeline turns a forward's
//! credential binding into outbound headers. It runs only after the governance
//! filters have allowed the request, and it fails closed: any missing broker,
//! missing binding, header collision, or brokerage error denies the request and
//! injects no credential.
//!
//! Secret values are never logged. Only header names and redacted audit metadata
//! are emitted. Resolved values are carried as sensitive [`HeaderValue`]s so the
//! transport layer redacts them.

use std::collections::HashMap;
use std::sync::Arc;

use http::{HeaderName, HeaderValue};
use praxis_filter::{FilterAction, HttpFilterContext};
use tracing::{trace, warn};
use wanaku_infra::credentials::CredentialBroker;
use wanaku_infra::registry::InMemoryRegistry;
use wanaku_types::credentials::CredentialPurpose;
use wanaku_types::credentials::binding::UseScope;
use wanaku_types::credentials::injection::detect_collisions;
use wanaku_types::registry::{BindingRegistry, ForwardEntry};

/// The scope-bearing inputs for one forward credential brokerage request.
///
/// Grouping these keeps [`inject_forward_credentials`] under the argument-count
/// threshold and mirrors the `HttpContext`/`McpContext` context-struct pattern.
pub struct ForwardCredentialRequest<'a> {
    /// The owning forward (provides `forwardId` and the binding reference).
    pub forward: &'a ForwardEntry,
    /// The already resolved and validated upstream address.
    pub address: &'a str,
    /// The credential purpose for this call.
    pub purpose: CredentialPurpose,
    /// The scope dimensions checked against the binding restrictions.
    pub scope: UseScope<'a>,
    /// The JSON-RPC id used to build a fail-closed error response.
    pub json_rpc_id: &'a serde_json::Value,
}

/// Resolve the credential binding for the request's forward and purpose, then
/// merge the brokered headers into `forward_headers`.
///
/// Returns `Ok(())` when there is nothing to inject (the forward has no binding
/// for this purpose) or when injection succeeds. Returns `Err(FilterAction)`
/// with a ready JSON-RPC error when the request must be denied. On any error
/// path no credential header is added.
#[expect(
    clippy::too_many_lines,
    reason = "fail-closed credential brokerage with distinct error paths"
)]
pub async fn inject_forward_credentials(
    ctx: &HttpFilterContext<'_>,
    request: &ForwardCredentialRequest<'_>,
    forward_headers: &mut HashMap<HeaderName, HeaderValue>,
) -> Result<(), FilterAction> {
    let forward = request.forward;
    let json_rpc_id = request.json_rpc_id;

    let Some(binding_id) = forward.binding_for(request.purpose) else {
        // No credential binding configured for this purpose: forward unchanged.
        return Ok(());
    };

    // Once a binding is configured the request must fail closed if any part of
    // the brokerage path is unavailable.
    let Some(broker) = ctx.extensions.get::<Arc<CredentialBroker>>().cloned() else {
        warn!(
            forward_id = %forward.forward_id(),
            binding_id = %binding_id,
            "credential broker unavailable; denying credentialed request"
        );
        return Err(internal_error(json_rpc_id, "credential broker unavailable"));
    };

    let Some(registry) = ctx.extensions.get::<InMemoryRegistry>() else {
        warn!("registry unavailable while resolving credential binding");
        return Err(internal_error(
            json_rpc_id,
            "internal error: registry unavailable",
        ));
    };

    let Some(binding) = registry.get_binding(binding_id) else {
        warn!(
            forward_id = %forward.forward_id(),
            binding_id = %binding_id,
            "credential binding referenced by forward not found"
        );
        return Err(internal_error(json_rpc_id, "credential binding not found"));
    };

    // A client-forwarded header must never collide with a broker-managed header,
    // or the caller could observe or influence a managed credential.
    let client_names: Vec<String> = forward_headers
        .keys()
        .map(|name| name.as_str().to_owned())
        .collect();
    if let Err(e) = detect_collisions(&binding.mechanism, client_names.iter().map(String::as_str)) {
        warn!(
            forward_id = %forward.forward_id(),
            error = %e,
            "client header collides with a managed credential header; denying request"
        );
        return Err(internal_error(
            json_rpc_id,
            "client header collides with a managed credential header",
        ));
    }

    match broker
        .broker(
            &binding,
            forward.forward_id(),
            request.address,
            request.purpose,
            &request.scope,
        )
        .await
    {
        Ok(brokered) => {
            for header in &brokered.headers {
                // Never log the value. Build a sensitive HeaderValue so header
                // debuggers redact the secret at the transport boundary.
                let Ok(mut value) = HeaderValue::from_bytes(header.expose_value().expose_bytes())
                else {
                    warn!(
                        header = %header.name(),
                        "brokered credential produced an invalid header value; denying request"
                    );
                    return Err(internal_error(
                        json_rpc_id,
                        "invalid brokered credential header value",
                    ));
                };
                value.set_sensitive(true);
                forward_headers.insert(header.header_name().clone(), value);
            }
            trace!(
                forward_id = %forward.forward_id(),
                binding_id = %brokered.audit.binding_id,
                revision = brokered.audit.binding_revision,
                outcome = %brokered.audit.outcome.as_str(),
                mechanism = %brokered.audit.mechanism,
                "injected brokered credential headers"
            );
            Ok(())
        }
        Err(e) => {
            // Fail closed. The audit record is redacted (no secret material).
            warn!(
                forward_id = %forward.forward_id(),
                binding_id = %e.audit.binding_id,
                outcome = %e.audit.outcome.as_str(),
                reason = ?e.audit.failure_reason,
                "credential brokerage failed; denying request"
            );
            Err(internal_error(json_rpc_id, "credential brokerage failed"))
        }
    }
}

fn internal_error(json_rpc_id: &serde_json::Value, message: &str) -> FilterAction {
    crate::response::json_rpc_error(json_rpc_id, crate::response::JSONRPC_INTERNAL_ERROR, message)
}
