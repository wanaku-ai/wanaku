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
//! transport layer redacts them. Injection also returns a [`CredentialRedactor`]
//! that carries the injected secret material so the caller can strip it from
//! upstream responses and errors before they reach the agent or the logs.

use std::collections::HashMap;
use std::sync::Arc;

use http::{HeaderName, HeaderValue};
use praxis_filter::{FilterAction, HttpFilterContext};
use tracing::{trace, warn};
use wanaku_infra::credentials::{
    CredentialAuditRecord, CredentialAuditSink, CredentialBroker, ResolutionOutcome, record_audit,
};
use wanaku_infra::registry::InMemoryRegistry;

// The redactor is shared with the forward discovery path, so it lives in
// `wanaku-infra`. Re-export it here so filter code refers to it as
// `crate::credentials::CredentialRedactor`.
pub use wanaku_infra::credentials::CredentialRedactor;
use wanaku_types::credentials::CredentialPurpose;
use wanaku_types::credentials::binding::UseScope;
use wanaku_types::credentials::injection::detect_collisions;
use wanaku_types::registry::{BindingRegistry, ForwardEntry};

/// The transport inputs and output sanitizer for one forwarded MCP call.
///
/// Groups the outbound headers to send upstream with the [`CredentialRedactor`]
/// that strips any injected secret from the upstream response and errors. Passing
/// this as one value keeps the forwarding handlers under the argument-count
/// threshold and mirrors the `HttpContext`/`McpContext` context-struct pattern.
pub struct ForwardExchange {
    /// Headers to send on the forwarded call (may include brokered credentials).
    pub headers: HashMap<HeaderName, HeaderValue>,
    /// Redactor for the upstream response content and error text.
    pub redactor: CredentialRedactor,
}

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
/// Returns a [`CredentialRedactor`] on success. The redactor is empty (a no-op)
/// when there is nothing to inject (the forward has no binding for this
/// purpose); otherwise it carries the injected secret material so the caller can
/// strip it from upstream responses and errors. Returns `Err(FilterAction)` with
/// a ready JSON-RPC error when the request must be denied. On any error path no
/// credential header is added.
pub async fn inject_forward_credentials(
    ctx: &HttpFilterContext<'_>,
    request: &ForwardCredentialRequest<'_>,
    forward_headers: &mut HashMap<HeaderName, HeaderValue>,
) -> Result<CredentialRedactor, FilterAction> {
    // Extract the brokerage dependencies from the request extensions and defer
    // to the pure core so the fail-closed paths stay unit-testable without a
    // full filter context.
    let broker = ctx.extensions.get::<Arc<CredentialBroker>>().cloned();
    let registry = ctx.extensions.get::<InMemoryRegistry>();
    let audit_sink = ctx
        .extensions
        .get::<Arc<dyn CredentialAuditSink>>()
        .map(AsRef::as_ref);
    inject_forward_credentials_inner(
        broker.as_ref(),
        registry,
        audit_sink,
        request,
        forward_headers,
    )
    .await
}

/// The pure brokerage core, independent of the filter context.
///
/// `broker` and `registry` are the dependencies resolved from the request
/// extensions. Both must be present once a binding is configured; a missing
/// dependency fails closed.
#[expect(
    clippy::too_many_lines,
    reason = "fail-closed credential brokerage with distinct error paths"
)]
async fn inject_forward_credentials_inner(
    broker: Option<&Arc<CredentialBroker>>,
    registry: Option<&InMemoryRegistry>,
    audit_sink: Option<&dyn CredentialAuditSink>,
    request: &ForwardCredentialRequest<'_>,
    forward_headers: &mut HashMap<HeaderName, HeaderValue>,
) -> Result<CredentialRedactor, FilterAction> {
    let forward = request.forward;
    let json_rpc_id = request.json_rpc_id;

    let Some(binding_id) = forward.binding_for(request.purpose) else {
        // No credential binding configured for this purpose: forward unchanged
        // and nothing to redact.
        return Ok(CredentialRedactor::default());
    };

    // Once a binding is configured the request must fail closed if any part of
    // the brokerage path is unavailable.
    let Some(broker) = broker else {
        record_audit(
            audit_sink,
            &CredentialAuditRecord::denial(
                binding_id,
                forward.forward_id(),
                request.purpose,
                CredentialAuditRecord::REASON_BROKER_UNAVAILABLE,
            ),
        );
        warn!(
            forward_id = %forward.forward_id(),
            binding_id = %binding_id,
            "credential broker unavailable; denying credentialed request"
        );
        return Err(internal_error(json_rpc_id, "credential broker unavailable"));
    };

    let Some(registry) = registry else {
        record_audit(
            audit_sink,
            &CredentialAuditRecord::denial(
                binding_id,
                forward.forward_id(),
                request.purpose,
                CredentialAuditRecord::REASON_REGISTRY_UNAVAILABLE,
            ),
        );
        warn!("registry unavailable while resolving credential binding");
        return Err(internal_error(
            json_rpc_id,
            "internal error: registry unavailable",
        ));
    };

    let Some(binding) = registry.get_binding(binding_id) else {
        record_audit(
            audit_sink,
            &CredentialAuditRecord::denial(
                binding_id,
                forward.forward_id(),
                request.purpose,
                CredentialAuditRecord::REASON_BINDING_NOT_FOUND,
            ),
        );
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
        record_audit(
            audit_sink,
            &CredentialAuditRecord::denial(
                binding_id,
                forward.forward_id(),
                request.purpose,
                CredentialAuditRecord::REASON_HEADER_COLLISION,
            ),
        );
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
            // Stage the headers in a local buffer and commit them to
            // `forward_headers` only after the whole loop succeeds. A multi-header
            // mechanism with an invalid later value must leave no earlier header
            // injected when the request is denied, or the fail-closed contract
            // breaks.
            let mut staged = Vec::with_capacity(brokered.headers.len());
            for header in &brokered.headers {
                // Never log the value. Build a sensitive HeaderValue so header
                // debuggers redact the secret at the transport boundary.
                let Ok(mut value) = HeaderValue::from_bytes(header.expose_value().expose_bytes())
                else {
                    // The brokerage succeeded but the resolved value is not a valid
                    // header value. Record the failure so a denied request is never
                    // audited as allowed, then fail closed.
                    let mut audit = brokered.audit.clone();
                    audit.outcome = ResolutionOutcome::Failed;
                    audit.failure_reason =
                        Some(CredentialAuditRecord::REASON_INVALID_HEADER_VALUE.to_owned());
                    record_audit(audit_sink, &audit);
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
                staged.push((header.header_name().clone(), value));
            }
            // Every header is valid, so commit the batch.
            for (name, value) in staged {
                forward_headers.insert(name, value);
            }
            // The headers are built, so record the brokerage outcome now. Recording
            // after the header loop keeps a header-rejection denial from being
            // audited as an allowed brokerage.
            record_audit(audit_sink, &brokered.audit);
            trace!(
                forward_id = %forward.forward_id(),
                binding_id = %brokered.audit.binding_id,
                revision = brokered.audit.binding_revision,
                outcome = %brokered.audit.outcome.as_str(),
                mechanism = %brokered.audit.mechanism,
                "injected brokered credential headers"
            );
            // Carry a redactor for both the raw secret material and the full
            // injected header value so an upstream that echoes either form is
            // stripped before it reaches the agent or the logs.
            Ok(CredentialRedactor::from_brokered(&brokered))
        }
        Err(e) => {
            // Fail closed. The audit record is redacted (no secret material).
            record_audit(audit_sink, &e.audit);
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
    crate::response::json_rpc_error(
        json_rpc_id,
        crate::response::JSONRPC_INTERNAL_ERROR,
        message,
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    use wanaku_types::credentials::binding::{
        BindingRestrictions, CacheRules, CredentialBinding, NormalizedOrigin,
    };
    use wanaku_types::credentials::fake_resolver::FakeResolver;
    use wanaku_types::credentials::injection::InjectionMechanism;
    use wanaku_types::credentials::resolver::{ResolverRegistry, SecretRef};

    const ADDRESS: &str = "https://api.example.com/mcp";

    fn broker() -> Arc<CredentialBroker> {
        let resolvers = ResolverRegistry::new()
            .with_resolver(Arc::new(FakeResolver::new().with_value("token", "s3cr3t")));
        Arc::new(CredentialBroker::new(resolvers))
    }

    fn binding(id: &str) -> CredentialBinding {
        CredentialBinding {
            id: id.to_owned(),
            forward_id: "fwd-a".to_owned(),
            origin: NormalizedOrigin::from_address("https://api.example.com").unwrap(),
            mechanism: InjectionMechanism::Bearer,
            secret_refs: vec![SecretRef::parse("fake:token").unwrap()],
            allowed_purposes: vec![CredentialPurpose::Invocation],
            restrictions: BindingRestrictions::default(),
            cache: CacheRules::default(),
            revision: 1,
        }
    }

    fn forward(binding_id: Option<&str>) -> ForwardEntry {
        let mut credential_bindings = HashMap::new();
        if let Some(id) = binding_id {
            credential_bindings.insert(CredentialPurpose::Invocation, id.to_owned());
        }
        ForwardEntry {
            name: "fwd-a".to_owned(),
            address: ADDRESS.to_owned(),
            namespace: None,
            server_info: None,
            labels: HashMap::new(),
            available: true,
            status_message: None,
            credential_bindings,
        }
    }

    fn request<'a>(
        forward: &'a ForwardEntry,
        address: &'a str,
        json_rpc_id: &'a serde_json::Value,
    ) -> ForwardCredentialRequest<'a> {
        ForwardCredentialRequest {
            forward,
            address,
            purpose: CredentialPurpose::Invocation,
            scope: UseScope::default(),
            json_rpc_id,
        }
    }

    #[tokio::test]
    async fn noop_when_forward_has_no_binding() {
        let forward = forward(None);
        let id = serde_json::json!(1);
        let req = request(&forward, ADDRESS, &id);
        let mut headers = HashMap::new();

        // No broker or registry is required when the forward has no binding.
        let result = inject_forward_credentials_inner(None, None, None, &req, &mut headers).await;

        assert!(result.is_ok());
        assert!(headers.is_empty());
    }

    #[tokio::test]
    async fn injects_bearer_header_on_success() {
        let registry = InMemoryRegistry::new();
        registry.register_binding(binding("b1"));
        let broker = broker();
        let forward = forward(Some("b1"));
        let id = serde_json::json!(1);
        let req = request(&forward, ADDRESS, &id);
        let mut headers = HashMap::new();

        let result = inject_forward_credentials_inner(
            Some(&broker),
            Some(&registry),
            None,
            &req,
            &mut headers,
        )
        .await;

        let redactor = result.expect("brokerage should succeed");
        let value = headers
            .get(&HeaderName::from_static("authorization"))
            .unwrap();
        assert_eq!(value.to_str().unwrap(), "Bearer s3cr3t");
        // The injected value must be marked sensitive so the transport redacts it.
        assert!(value.is_sensitive());
        // The returned redactor must strip both the raw secret and the full
        // injected header value from any upstream echo.
        assert_eq!(
            redactor.redact("leaked s3cr3t here"),
            "leaked <redacted> here"
        );
        assert_eq!(
            redactor.redact("Authorization: Bearer s3cr3t"),
            "Authorization: <redacted>"
        );
    }

    #[tokio::test]
    async fn noop_binding_yields_empty_redactor() {
        let forward = forward(None);
        let id = serde_json::json!(1);
        let req = request(&forward, ADDRESS, &id);
        let mut headers = HashMap::new();

        let redactor = inject_forward_credentials_inner(None, None, None, &req, &mut headers)
            .await
            .expect("no binding should succeed");

        // With nothing injected the redactor must leave input untouched.
        assert_eq!(redactor.redact("nothing to redact"), "nothing to redact");
    }

    #[tokio::test]
    async fn denies_when_broker_unavailable() {
        let registry = InMemoryRegistry::new();
        registry.register_binding(binding("b1"));
        let forward = forward(Some("b1"));
        let id = serde_json::json!(1);
        let req = request(&forward, ADDRESS, &id);
        let mut headers = HashMap::new();

        let result =
            inject_forward_credentials_inner(None, Some(&registry), None, &req, &mut headers).await;

        assert!(result.is_err());
        assert!(headers.is_empty());
    }

    #[tokio::test]
    async fn denies_when_registry_unavailable() {
        let broker = broker();
        let forward = forward(Some("b1"));
        let id = serde_json::json!(1);
        let req = request(&forward, ADDRESS, &id);
        let mut headers = HashMap::new();

        let result =
            inject_forward_credentials_inner(Some(&broker), None, None, &req, &mut headers).await;

        assert!(result.is_err());
        assert!(headers.is_empty());
    }

    #[tokio::test]
    async fn denies_when_binding_missing() {
        let registry = InMemoryRegistry::new();
        let broker = broker();
        let forward = forward(Some("missing"));
        let id = serde_json::json!(1);
        let req = request(&forward, ADDRESS, &id);
        let mut headers = HashMap::new();

        let result = inject_forward_credentials_inner(
            Some(&broker),
            Some(&registry),
            None,
            &req,
            &mut headers,
        )
        .await;

        assert!(result.is_err());
        assert!(headers.is_empty());
    }

    #[tokio::test]
    async fn denies_on_client_header_collision() {
        let registry = InMemoryRegistry::new();
        registry.register_binding(binding("b1"));
        let broker = broker();
        let forward = forward(Some("b1"));
        let id = serde_json::json!(1);
        let req = request(&forward, ADDRESS, &id);
        // The client forwards the same header the Bearer mechanism manages.
        let mut headers = HashMap::new();
        headers.insert(
            HeaderName::from_static("authorization"),
            HeaderValue::from_static("client-supplied"),
        );

        let result = inject_forward_credentials_inner(
            Some(&broker),
            Some(&registry),
            None,
            &req,
            &mut headers,
        )
        .await;

        assert!(result.is_err());
        // The client header must be left untouched; no managed credential injected.
        assert_eq!(
            headers
                .get(&HeaderName::from_static("authorization"))
                .unwrap()
                .to_str()
                .unwrap(),
            "client-supplied"
        );
    }

    #[tokio::test]
    async fn fails_closed_on_origin_mismatch() {
        let registry = InMemoryRegistry::new();
        registry.register_binding(binding("b1"));
        let broker = broker();
        let forward = forward(Some("b1"));
        let id = serde_json::json!(1);
        // The resolved address origin does not match the binding origin.
        let req = request(&forward, "https://evil.example.com/mcp", &id);
        let mut headers = HashMap::new();

        let result = inject_forward_credentials_inner(
            Some(&broker),
            Some(&registry),
            None,
            &req,
            &mut headers,
        )
        .await;

        assert!(result.is_err());
        assert!(headers.is_empty());
    }

    #[derive(Default)]
    struct RecordingSink {
        records: std::sync::Mutex<Vec<wanaku_infra::credentials::CredentialAuditRecord>>,
    }

    impl CredentialAuditSink for RecordingSink {
        fn record_credential_audit(
            &self,
            record: &wanaku_infra::credentials::CredentialAuditRecord,
        ) {
            if let Ok(mut records) = self.records.lock() {
                records.push(record.clone());
            }
        }
    }

    #[tokio::test]
    async fn successful_injection_emits_one_audit_record() {
        use wanaku_infra::credentials::ResolutionOutcome;

        let registry = InMemoryRegistry::new();
        registry.register_binding(binding("b1"));
        let broker = broker();
        let forward = forward(Some("b1"));
        let id = serde_json::json!(1);
        let req = request(&forward, ADDRESS, &id);
        let mut headers = HashMap::new();
        let sink = RecordingSink::default();

        inject_forward_credentials_inner(
            Some(&broker),
            Some(&registry),
            Some(&sink),
            &req,
            &mut headers,
        )
        .await
        .expect("brokerage should succeed");

        let records = sink.records.lock().unwrap();
        assert_eq!(records.len(), 1);
        assert_eq!(records[0].outcome, ResolutionOutcome::Resolved);
        assert_eq!(records[0].purpose, CredentialPurpose::Invocation);
        // The record must never carry secret material.
        assert!(!format!("{:?}", records[0]).contains("s3cr3t"));
    }

    #[tokio::test]
    async fn fail_closed_denial_emits_one_audit_record() {
        use wanaku_infra::credentials::ResolutionOutcome;

        let registry = InMemoryRegistry::new();
        registry.register_binding(binding("b1"));
        let broker = broker();
        let forward = forward(Some("b1"));
        let id = serde_json::json!(1);
        // The resolved address origin does not match the binding origin.
        let req = request(&forward, "https://evil.example.com/mcp", &id);
        let mut headers = HashMap::new();
        let sink = RecordingSink::default();

        let result = inject_forward_credentials_inner(
            Some(&broker),
            Some(&registry),
            Some(&sink),
            &req,
            &mut headers,
        )
        .await;

        assert!(result.is_err());
        let records = sink.records.lock().unwrap();
        assert_eq!(records.len(), 1);
        assert_eq!(records[0].outcome, ResolutionOutcome::Denied);
        assert_eq!(
            records[0].failure_reason.as_deref(),
            Some("origin_mismatch")
        );
    }

    #[tokio::test]
    async fn broker_unavailable_emits_denial_record() {
        let registry = InMemoryRegistry::new();
        registry.register_binding(binding("b1"));
        let forward = forward(Some("b1"));
        let id = serde_json::json!(1);
        let req = request(&forward, ADDRESS, &id);
        let mut headers = HashMap::new();
        let sink = RecordingSink::default();

        let result = inject_forward_credentials_inner(
            None,
            Some(&registry),
            Some(&sink),
            &req,
            &mut headers,
        )
        .await;

        assert!(result.is_err());
        let records = sink.records.lock().unwrap();
        assert_eq!(records.len(), 1);
        assert_eq!(records[0].outcome, ResolutionOutcome::Denied);
        assert_eq!(
            records[0].failure_reason.as_deref(),
            Some(CredentialAuditRecord::REASON_BROKER_UNAVAILABLE)
        );
    }

    #[tokio::test]
    async fn binding_missing_emits_denial_record() {
        let registry = InMemoryRegistry::new();
        let broker = broker();
        let forward = forward(Some("missing"));
        let id = serde_json::json!(1);
        let req = request(&forward, ADDRESS, &id);
        let mut headers = HashMap::new();
        let sink = RecordingSink::default();

        let result = inject_forward_credentials_inner(
            Some(&broker),
            Some(&registry),
            Some(&sink),
            &req,
            &mut headers,
        )
        .await;

        assert!(result.is_err());
        let records = sink.records.lock().unwrap();
        assert_eq!(records.len(), 1);
        assert_eq!(records[0].outcome, ResolutionOutcome::Denied);
        assert_eq!(
            records[0].failure_reason.as_deref(),
            Some(CredentialAuditRecord::REASON_BINDING_NOT_FOUND)
        );
    }

    #[tokio::test]
    async fn header_collision_emits_denial_record() {
        let registry = InMemoryRegistry::new();
        registry.register_binding(binding("b1"));
        let broker = broker();
        let forward = forward(Some("b1"));
        let id = serde_json::json!(1);
        let req = request(&forward, ADDRESS, &id);
        // The client forwards the same header the Bearer mechanism manages.
        let mut headers = HashMap::new();
        headers.insert(
            HeaderName::from_static("authorization"),
            HeaderValue::from_static("client-supplied"),
        );
        let sink = RecordingSink::default();

        let result = inject_forward_credentials_inner(
            Some(&broker),
            Some(&registry),
            Some(&sink),
            &req,
            &mut headers,
        )
        .await;

        assert!(result.is_err());
        let records = sink.records.lock().unwrap();
        assert_eq!(records.len(), 1);
        assert_eq!(records[0].outcome, ResolutionOutcome::Denied);
        assert_eq!(
            records[0].failure_reason.as_deref(),
            Some(CredentialAuditRecord::REASON_HEADER_COLLISION)
        );
    }

    #[tokio::test]
    async fn unusable_resolved_value_records_failure_not_allow() {
        let registry = InMemoryRegistry::new();
        registry.register_binding(binding("b1"));
        // A resolved value with a control character cannot form a valid header.
        // The broker rejects it during header construction, so the recorded
        // outcome must be a failure, never an allow.
        let resolvers = ResolverRegistry::new().with_resolver(Arc::new(
            FakeResolver::new().with_value("token", "bad\nvalue"),
        ));
        let broker = Arc::new(CredentialBroker::new(resolvers));
        let forward = forward(Some("b1"));
        let id = serde_json::json!(1);
        let req = request(&forward, ADDRESS, &id);
        let mut headers = HashMap::new();
        let sink = RecordingSink::default();

        let result = inject_forward_credentials_inner(
            Some(&broker),
            Some(&registry),
            Some(&sink),
            &req,
            &mut headers,
        )
        .await;

        assert!(result.is_err());
        // No credential header may be injected on the failure path.
        assert!(headers.is_empty());
        let records = sink.records.lock().unwrap();
        assert_eq!(records.len(), 1);
        // The denied request must never be audited as allowed.
        assert_eq!(records[0].outcome, ResolutionOutcome::Failed);
        assert_eq!(
            records[0].failure_reason.as_deref(),
            Some("injection_failure")
        );
    }
}
