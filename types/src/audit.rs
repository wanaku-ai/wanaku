#![cfg_attr(
    feature = "openapi",
    allow(
        clippy::large_stack_frames,
        reason = "utoipa::ToSchema derive generates large stack frames"
    )
)]

use std::collections::BTreeMap;
use std::sync::Arc;

use serde::{Deserialize, Serialize};
use serde_json::Value;

pub const AUDIT_SCHEMA_VERSION: &str = "1.0";
pub const DEFAULT_AUDIT_CAPACITY: usize = 10_000;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
#[serde(rename_all = "snake_case")]
pub enum AuditCategory {
    Decision,
    Administrative,
    Semantic,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
#[serde(rename_all = "snake_case")]
pub enum AuditDecision {
    Observed,
    Allow,
    Block,
    Warn,
    RejectMalformed,
    Error,
}

#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct RedactionMetadata {
    pub redacted_fields: Vec<String>,
    pub payload_captured: bool,
    pub payload_truncated: bool,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct AuditEvent {
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub source: Option<crate::semantic_audit::AuditSource>,
    pub schema_version: String,
    pub event_id: String,
    pub stream_id: String,
    pub sequence: u64,
    pub timestamp: String,
    pub category: AuditCategory,
    pub decision: AuditDecision,
    pub correlation_id: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub request_id: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub conversation_id: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub actor: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub workload: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub namespace: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub audience: Option<String>,
    pub protocol: String,
    pub operation: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub target_type: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub target: Option<String>,
    pub reason_code: String,
    pub explanation: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub evaluator: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub filter: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub policy_revision: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub upstream_id: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub response_status: Option<u16>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub duration_ms: Option<u64>,
    pub redaction: RedactionMetadata,
    pub attributes: BTreeMap<String, Value>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub payload: Option<Value>,
    pub coverage_complete: bool,
    pub dropped_events: u64,
}

impl AuditEvent {
    #[must_use]
    #[expect(
        clippy::too_many_lines,
        reason = "constructor initializes the stable event schema"
    )]
    pub fn new(
        category: AuditCategory,
        decision: AuditDecision,
        operation: impl Into<String>,
        reason_code: impl Into<String>,
        explanation: impl Into<String>,
    ) -> Self {
        let event_id = crate::correlation::generate_short_id();
        Self {
            source: None,
            schema_version: AUDIT_SCHEMA_VERSION.to_owned(),
            event_id: event_id.clone(),
            stream_id: event_id.clone(),
            sequence: 0,
            timestamp: crate::time::iso_now(),
            category,
            decision,
            correlation_id: event_id,
            request_id: None,
            conversation_id: None,
            actor: None,
            workload: None,
            namespace: None,
            audience: None,
            protocol: "mcp".to_owned(),
            operation: operation.into(),
            target_type: None,
            target: None,
            reason_code: reason_code.into(),
            explanation: explanation.into(),
            evaluator: None,
            filter: None,
            policy_revision: None,
            upstream_id: None,
            response_status: None,
            duration_ms: None,
            redaction: RedactionMetadata::default(),
            attributes: BTreeMap::new(),
            payload: None,
            coverage_complete: true,
            dropped_events: 0,
        }
    }
}

#[derive(Debug, Clone, Default, Deserialize)]
pub struct AuditQuery {
    pub category: Option<AuditCategory>,
    pub source_instance_id: Option<String>,
    pub source_event_id: Option<String>,
    pub from: Option<String>,
    pub to: Option<String>,
    pub namespace: Option<String>,
    pub actor: Option<String>,
    pub operation: Option<String>,
    pub target: Option<String>,
    pub decision: Option<AuditDecision>,
    pub reason_code: Option<String>,
    pub correlation_id: Option<String>,
    pub offset: usize,
    pub limit: usize,
}

#[derive(Debug, Clone, Serialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct AuditPage {
    pub events: Vec<AuditEvent>,
    pub offset: usize,
    pub limit: usize,
    pub total: usize,
}

#[derive(Debug, Clone, Serialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct AuditHealth {
    pub healthy: bool,
    pub dropped_events: u64,
    pub retained_events: usize,
    pub capacity: usize,
    pub last_error: Option<String>,
}

pub trait AuditPersistence: Send + Sync {
    fn load(&self) -> Result<Vec<AuditEvent>, String>;
    fn save(&self, events: &[AuditEvent]) -> Result<(), String>;
}

pub trait AuditFailureObserver: Send + Sync {
    fn record_storage_failure(&self);
}

pub trait AuditStore: Send + Sync {
    fn record(&self, event: AuditEvent);
    fn query(&self, query: &AuditQuery) -> AuditPage;
    fn get(&self, event_id: &str) -> Option<AuditEvent>;
    fn health(&self) -> AuditHealth;
}

/// Cloneable handle that audit producers and readers use.
///
/// The handle hides the concrete store behind the [`AuditStore`] contract.
/// The audit feature puts the handle into the request extensions. The handle
/// does not give access to semantic ingestion.
#[derive(Clone)]
pub struct AuditHandle(Arc<dyn AuditStore>);

impl AuditHandle {
    #[must_use]
    pub fn new(store: Arc<dyn AuditStore>) -> Self {
        Self(store)
    }
}

impl std::fmt::Debug for AuditHandle {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("AuditHandle").finish_non_exhaustive()
    }
}

impl AuditStore for AuditHandle {
    fn record(&self, event: AuditEvent) {
        self.0.record(event);
    }

    fn query(&self, query: &AuditQuery) -> AuditPage {
        self.0.query(query)
    }

    fn get(&self, event_id: &str) -> Option<AuditEvent> {
        self.0.get(event_id)
    }

    fn health(&self) -> AuditHealth {
        self.0.health()
    }
}

/// Add correlation and optional payload data from an MCP JSON-RPC request.
///
/// The store removes the payload when capture is disabled. The store redacts
/// the payload before it keeps or persists the event when capture is enabled.
pub fn add_mcp_request_context(event: &mut AuditEvent, body: Option<&[u8]>) {
    let Some(body) = body else {
        return;
    };
    let Ok(payload) = serde_json::from_slice::<Value>(body) else {
        return;
    };
    let supplied_request_id = payload
        .pointer("/params/arguments/x-request-id")
        .and_then(Value::as_str)
        .map(str::to_owned);
    if let Some(request_id) = supplied_request_id {
        event.request_id = Some(request_id.clone());
        event.conversation_id = Some(request_id.clone());
        event.correlation_id = request_id.clone();
        event.stream_id = request_id;
    } else if let Some(request_id) = json_rpc_request_id(&payload) {
        event.request_id = Some(request_id.clone());
        event.correlation_id = request_id.clone();
        event.stream_id = request_id;
    }
    event.payload = Some(payload);
}

/// Add MCP body context and prefer a trusted HTTP request ID for correlation.
///
/// The body-derived conversation ID remains unchanged when the HTTP request ID
/// overrides the request, correlation, and stream IDs.
pub fn add_mcp_request_context_with_id(
    event: &mut AuditEvent,
    body: Option<&[u8]>,
    http_request_id: Option<&str>,
) {
    add_mcp_request_context(event, body);
    if let Some(request_id) = http_request_id.filter(|value| !value.is_empty()) {
        event.request_id = Some(request_id.to_owned());
        event.correlation_id = request_id.to_owned();
        event.stream_id = request_id.to_owned();
    }
}

fn json_rpc_request_id(payload: &Value) -> Option<String> {
    match payload.get("id") {
        Some(Value::String(request_id)) => Some(request_id.clone()),
        Some(Value::Number(request_id)) => Some(request_id.to_string()),
        _ => None,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn decision_event(operation: &str) -> AuditEvent {
        AuditEvent::new(
            AuditCategory::Decision,
            AuditDecision::Allow,
            operation,
            "allowed",
            "Allowed",
        )
    }

    #[test]
    fn request_context_uses_supplied_correlation_id() {
        let mut event = decision_event("tools/call");
        add_mcp_request_context(
            &mut event,
            Some(br#"{"params":{"arguments":{"x-request-id":"wk-client"}}}"#),
        );
        assert_eq!(event.correlation_id, "wk-client");
        assert_eq!(event.conversation_id.as_deref(), Some("wk-client"));
        assert!(event.payload.is_some());
    }

    #[test]
    fn request_context_uses_string_json_rpc_id_as_fallback() {
        let mut event = decision_event("resources/read");
        add_mcp_request_context(&mut event, Some(br#"{"jsonrpc":"2.0","id":"request-7"}"#));
        assert_eq!(event.request_id.as_deref(), Some("request-7"));
        assert_eq!(event.correlation_id, "request-7");
        assert_eq!(event.stream_id, "request-7");
        assert!(event.conversation_id.is_none());
    }

    #[test]
    fn request_context_uses_numeric_json_rpc_id_as_fallback() {
        let mut event = decision_event("prompts/get");
        add_mcp_request_context(&mut event, Some(br#"{"jsonrpc":"2.0","id":42}"#));
        assert_eq!(event.request_id.as_deref(), Some("42"));
        assert_eq!(event.correlation_id, "42");
    }

    #[test]
    fn http_request_id_overrides_body_id_and_preserves_conversation() {
        let mut event = decision_event("tools/call");
        add_mcp_request_context_with_id(
            &mut event,
            Some(br#"{"id":7,"params":{"arguments":{"x-request-id":"conversation-3"}}}"#),
            Some("http-request-9"),
        );
        assert_eq!(event.request_id.as_deref(), Some("http-request-9"));
        assert_eq!(event.correlation_id, "http-request-9");
        assert_eq!(event.stream_id, "http-request-9");
        assert_eq!(event.conversation_id.as_deref(), Some("conversation-3"));
    }
}
