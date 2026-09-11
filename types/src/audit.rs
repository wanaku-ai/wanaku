#![cfg_attr(
    feature = "openapi",
    allow(
        clippy::large_stack_frames,
        reason = "utoipa::ToSchema derive generates large stack frames"
    )
)]

use std::collections::{BTreeMap, VecDeque};
use std::sync::{Arc, Mutex, MutexGuard};

use serde::{Deserialize, Serialize};
use serde_json::Value;

use crate::audit_redaction::AuditRedactor;

pub const AUDIT_SCHEMA_VERSION: &str = "1.0";
pub const DEFAULT_AUDIT_CAPACITY: usize = 10_000;
const AUDIT_STORAGE_ERROR: &str = "audit storage unavailable";

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
#[serde(rename_all = "snake_case")]
pub enum AuditCategory {
    Decision,
    Administrative,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
#[serde(rename_all = "snake_case")]
pub enum AuditDecision {
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

#[derive(Clone)]
pub struct InMemoryAuditStore {
    inner: Arc<Mutex<StoreState>>,
    persistence_lock: Arc<Mutex<()>>,
    capacity: usize,
    persistence: Option<Arc<dyn AuditPersistence>>,
    redactor: AuditRedactor,
    capture_payloads: bool,
    failure_observer: Option<Arc<dyn AuditFailureObserver>>,
}

#[derive(Default)]
struct StoreState {
    events: VecDeque<AuditEvent>,
    next_sequence: u64,
    dropped: u64,
    last_error: Option<String>,
}

impl InMemoryAuditStore {
    #[must_use]
    pub fn new(capacity: usize) -> Self {
        Self {
            inner: Arc::new(Mutex::new(StoreState::default())),
            persistence_lock: Arc::new(Mutex::new(())),
            capacity,
            persistence: None,
            redactor: AuditRedactor::default(),
            capture_payloads: false,
            failure_observer: None,
        }
    }

    #[must_use]
    pub fn with_persistence(capacity: usize, persistence: Arc<dyn AuditPersistence>) -> Self {
        let store = Self {
            inner: Arc::new(Mutex::new(StoreState::default())),
            persistence_lock: Arc::new(Mutex::new(())),
            capacity,
            persistence: Some(persistence),
            redactor: AuditRedactor::default(),
            capture_payloads: false,
            failure_observer: None,
        };
        store.load_persisted();
        store
    }

    #[must_use]
    pub fn configured(
        capacity: usize,
        persistence: Option<Arc<dyn AuditPersistence>>,
        redactor: AuditRedactor,
        capture_payloads: bool,
        failure_observer: Option<Arc<dyn AuditFailureObserver>>,
    ) -> Self {
        let store = Self {
            inner: Arc::new(Mutex::new(StoreState::default())),
            persistence_lock: Arc::new(Mutex::new(())),
            capacity,
            persistence,
            redactor,
            capture_payloads,
            failure_observer,
        };
        store.load_persisted();
        store
    }

    fn load_persisted(&self) {
        let Some(backend) = &self.persistence else {
            return;
        };
        match backend.load() {
            Ok(events) => {
                if let Ok(mut state) = self.inner.lock() {
                    for event in events
                        .into_iter()
                        .rev()
                        .take(self.capacity)
                        .collect::<Vec<_>>()
                        .into_iter()
                        .rev()
                    {
                        state.next_sequence =
                            state.next_sequence.max(event.sequence.saturating_add(1));
                        state.events.push_back(event);
                    }
                }
            }
            Err(error) => self.mark_failure(&error),
        }
    }

    fn mark_failure(&self, error: &str) {
        tracing::error!(error = %error, "audit event storage failed");
        if let Some(observer) = &self.failure_observer {
            observer.record_storage_failure();
        }
        if let Ok(mut state) = self.inner.lock() {
            state.dropped = state.dropped.saturating_add(1);
            state.last_error = Some(AUDIT_STORAGE_ERROR.to_owned());
        }
    }

    fn persistence_guard(&self) -> Option<MutexGuard<'_, ()>> {
        self.persistence.as_ref()?;
        match self.persistence_lock.lock() {
            Ok(guard) => Some(guard),
            Err(error) => {
                let message = error.to_string();
                let guard = error.into_inner();
                self.mark_failure(&message);
                Some(guard)
            }
        }
    }
}

impl AuditStore for InMemoryAuditStore {
    #[expect(
        clippy::too_many_lines,
        reason = "recording applies redaction, retention, and persistence"
    )]
    fn record(&self, mut event: AuditEvent) {
        if self.capture_payloads {
            if let Some(payload) = event.payload.as_mut() {
                event.redaction = self.redactor.redact(payload);
            }
        } else {
            event.payload = None;
            event.redaction.payload_captured = false;
        }
        let mut attributes = Value::Object(
            event
                .attributes
                .clone()
                .into_iter()
                .collect::<serde_json::Map<_, _>>(),
        );
        let attribute_redaction = self.redactor.redact(&mut attributes);
        event.redaction.redacted_fields.extend(
            attribute_redaction
                .redacted_fields
                .into_iter()
                .map(|field| format!("/attributes{field}")),
        );
        if let Value::Object(attributes) = attributes {
            event.attributes = attributes.into_iter().collect();
        } else {
            event.attributes.clear();
            event.attributes.insert(
                "truncated".to_owned(),
                Value::String("[REDACTED]".to_owned()),
            );
        }
        let _persistence_guard = self.persistence_guard();
        let snapshot = match self.inner.lock() {
            Ok(mut state) => {
                event.sequence = state.next_sequence;
                state.next_sequence = state.next_sequence.saturating_add(1);
                event.dropped_events = state.dropped;
                event.coverage_complete = state.dropped == 0;
                if state.events.len() >= self.capacity {
                    state.events.pop_front();
                }
                state.events.push_back(event);
                state.events.iter().cloned().collect::<Vec<_>>()
            }
            Err(error) => {
                tracing::error!(error = %error, "audit store lock poisoned");
                return;
            }
        };
        if let Some(backend) = &self.persistence {
            match backend.save(&snapshot) {
                Ok(()) => {}
                Err(error) => self.mark_failure(&error),
            }
        }
    }

    fn query(&self, query: &AuditQuery) -> AuditPage {
        let limit = if query.limit == 0 {
            100
        } else {
            query.limit.min(1000)
        };
        let matched = self
            .inner
            .lock()
            .map(|state| {
                state
                    .events
                    .iter()
                    .rev()
                    .filter(|event| matches_query(event, query))
                    .cloned()
                    .collect::<Vec<_>>()
            })
            .unwrap_or_default();
        let total = matched.len();
        AuditPage {
            events: matched.into_iter().skip(query.offset).take(limit).collect(),
            offset: query.offset,
            limit,
            total,
        }
    }

    fn get(&self, event_id: &str) -> Option<AuditEvent> {
        self.inner
            .lock()
            .ok()?
            .events
            .iter()
            .find(|event| event.event_id == event_id)
            .cloned()
    }

    fn health(&self) -> AuditHealth {
        match self.inner.lock() {
            Ok(state) => AuditHealth {
                healthy: state.last_error.is_none(),
                dropped_events: state.dropped,
                retained_events: state.events.len(),
                capacity: self.capacity,
                last_error: state.last_error.clone(),
            },
            Err(error) => {
                tracing::error!(error = %error, "audit store lock poisoned");
                AuditHealth {
                    healthy: false,
                    dropped_events: 1,
                    retained_events: 0,
                    capacity: self.capacity,
                    last_error: Some(AUDIT_STORAGE_ERROR.to_owned()),
                }
            }
        }
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
    let request_id = payload
        .pointer("/params/arguments/x-request-id")
        .and_then(Value::as_str)
        .map(str::to_owned);
    if let Some(request_id) = request_id {
        event.request_id = Some(request_id.clone());
        event.conversation_id = Some(request_id.clone());
        event.correlation_id = request_id.clone();
        event.stream_id = request_id;
    }
    event.payload = Some(payload);
}

fn matches_query(event: &AuditEvent, query: &AuditQuery) -> bool {
    query.from.as_ref().is_none_or(|v| event.timestamp >= *v)
        && query.to.as_ref().is_none_or(|v| event.timestamp <= *v)
        && query
            .namespace
            .as_ref()
            .is_none_or(|v| event.namespace.as_ref() == Some(v))
        && query
            .actor
            .as_ref()
            .is_none_or(|v| event.actor.as_ref() == Some(v))
        && query
            .operation
            .as_ref()
            .is_none_or(|v| &event.operation == v)
        && query
            .target
            .as_ref()
            .is_none_or(|v| event.target.as_ref() == Some(v))
        && query.decision.is_none_or(|v| event.decision == v)
        && query
            .reason_code
            .as_ref()
            .is_none_or(|v| &event.reason_code == v)
        && query
            .correlation_id
            .as_ref()
            .is_none_or(|v| &event.correlation_id == v)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::atomic::{AtomicU64, Ordering};
    use std::sync::mpsc::{Receiver, Sender, channel};
    use std::time::Duration;

    #[test]
    fn retention_and_query_are_bounded() {
        let store = InMemoryAuditStore::new(2);
        for operation in ["one", "two", "three"] {
            store.record(AuditEvent::new(
                AuditCategory::Decision,
                AuditDecision::Allow,
                operation,
                "allowed",
                "Allowed",
            ));
        }
        let page = store.query(&AuditQuery {
            limit: 10,
            ..AuditQuery::default()
        });
        assert_eq!(page.total, 2);
        assert_eq!(page.events[0].operation, "three");
        assert_eq!(page.events[1].sequence, 1);
    }

    #[test]
    fn request_context_uses_supplied_correlation_id() {
        let mut event = AuditEvent::new(
            AuditCategory::Decision,
            AuditDecision::Allow,
            "tools/call",
            "allowed",
            "Allowed",
        );
        add_mcp_request_context(
            &mut event,
            Some(br#"{"params":{"arguments":{"x-request-id":"wk-client"}}}"#),
        );
        assert_eq!(event.correlation_id, "wk-client");
        assert_eq!(event.conversation_id.as_deref(), Some("wk-client"));
        assert!(event.payload.is_some());
    }

    struct FailingPersistence;

    impl AuditPersistence for FailingPersistence {
        fn load(&self) -> Result<Vec<AuditEvent>, String> {
            Ok(Vec::new())
        }

        fn save(&self, _events: &[AuditEvent]) -> Result<(), String> {
            Err("write failed: /secret/audit-events.json".to_owned())
        }
    }

    #[derive(Default)]
    struct FailureCount(AtomicU64);

    impl AuditFailureObserver for FailureCount {
        fn record_storage_failure(&self) {
            self.0.fetch_add(1, Ordering::Relaxed);
        }
    }

    #[test]
    fn persistence_failure_degrades_health_without_blocking_record() {
        let failures = Arc::new(FailureCount::default());
        let store = InMemoryAuditStore::configured(
            10,
            Some(Arc::new(FailingPersistence)),
            AuditRedactor::default(),
            false,
            Some(failures.clone()),
        );
        store.record(AuditEvent::new(
            AuditCategory::Decision,
            AuditDecision::Block,
            "tools/call",
            "denied",
            "Denied",
        ));
        assert_eq!(store.query(&AuditQuery::default()).total, 1);
        let health = store.health();
        assert!(!health.healthy);
        assert_eq!(health.dropped_events, 1);
        assert_eq!(health.last_error.as_deref(), Some(AUDIT_STORAGE_ERROR));
        assert_eq!(failures.0.load(Ordering::Relaxed), 1);
    }

    #[test]
    fn poisoned_persistence_lock_does_not_block_in_memory_recording() {
        let store = InMemoryAuditStore::with_persistence(10, Arc::new(FailingPersistence));
        let persistence_lock = store.persistence_lock.clone();
        let poisoned = std::thread::Builder::new()
            .spawn(move || {
                let _guard = persistence_lock.lock().expect("persistence lock");
                std::panic::resume_unwind(Box::new("poison audit persistence lock"));
            })
            .expect("poison thread")
            .join();
        assert!(poisoned.is_err());

        store.record(AuditEvent::new(
            AuditCategory::Decision,
            AuditDecision::Block,
            "tools/call",
            "denied",
            "Denied",
        ));

        assert_eq!(store.query(&AuditQuery::default()).total, 1);
    }

    struct OrderedPersistence {
        call_count: AtomicU64,
        snapshots: Mutex<Vec<Vec<AuditEvent>>>,
        first_entered: Sender<()>,
        release_first: Mutex<Receiver<()>>,
        second_entered: Sender<()>,
    }

    impl AuditPersistence for OrderedPersistence {
        fn load(&self) -> Result<Vec<AuditEvent>, String> {
            Ok(Vec::new())
        }

        fn save(&self, events: &[AuditEvent]) -> Result<(), String> {
            match self.call_count.fetch_add(1, Ordering::SeqCst) {
                0 => {
                    self.first_entered.send(()).map_err(|e| e.to_string())?;
                    self.release_first
                        .lock()
                        .map_err(|e| e.to_string())?
                        .recv()
                        .map_err(|e| e.to_string())?;
                }
                1 => self.second_entered.send(()).map_err(|e| e.to_string())?,
                _ => {}
            }
            self.snapshots
                .lock()
                .map_err(|e| e.to_string())?
                .push(events.to_vec());
            Ok(())
        }
    }

    #[test]
    #[expect(
        clippy::too_many_lines,
        reason = "test coordinates two concurrent record operations"
    )]
    fn persistent_records_save_snapshots_in_sequence() {
        let (first_entered_tx, first_entered_rx) = channel();
        let (release_first_tx, release_first_rx) = channel();
        let (second_started_tx, second_started_rx) = channel();
        let (second_entered_tx, second_entered_rx) = channel();
        let persistence = Arc::new(OrderedPersistence {
            call_count: AtomicU64::new(0),
            snapshots: Mutex::new(Vec::new()),
            first_entered: first_entered_tx,
            release_first: Mutex::new(release_first_rx),
            second_entered: second_entered_tx,
        });
        let store = InMemoryAuditStore::with_persistence(10, persistence.clone());
        let first_store = store.clone();
        let first = std::thread::Builder::new()
            .spawn(move || {
                first_store.record(AuditEvent::new(
                    AuditCategory::Decision,
                    AuditDecision::Allow,
                    "first",
                    "allowed",
                    "Allowed",
                ));
            })
            .expect("first record thread");
        let first_started = first_entered_rx
            .recv_timeout(Duration::from_secs(2))
            .is_ok();

        let second_store = store.clone();
        let second = std::thread::Builder::new()
            .spawn(move || {
                let _ = second_started_tx.send(());
                second_store.record(AuditEvent::new(
                    AuditCategory::Decision,
                    AuditDecision::Allow,
                    "second",
                    "allowed",
                    "Allowed",
                ));
            })
            .expect("second record thread");
        assert!(
            second_started_rx
                .recv_timeout(Duration::from_secs(1))
                .is_ok()
        );
        let second_overlapped = second_entered_rx
            .recv_timeout(Duration::from_millis(250))
            .is_ok();
        assert!(release_first_tx.send(()).is_ok());
        assert!(first.join().is_ok());
        assert!(second.join().is_ok());

        assert!(first_started);
        assert!(!second_overlapped);
        let snapshots = persistence.snapshots.lock().expect("snapshot lock");
        assert_eq!(snapshots.len(), 2);
        assert_eq!(snapshots[0].len(), 1);
        assert_eq!(snapshots[1].len(), 2);
    }

    #[test]
    fn poisoned_health_does_not_expose_lock_details() {
        let store = InMemoryAuditStore::new(10);
        let inner = store.inner.clone();
        let poisoned = std::thread::Builder::new()
            .spawn(move || {
                let _guard = inner.lock().expect("store lock");
                std::panic::resume_unwind(Box::new("poison audit store"));
            })
            .expect("poison thread")
            .join();
        assert!(poisoned.is_err());

        let health = store.health();
        assert!(!health.healthy);
        assert_eq!(health.last_error.as_deref(), Some(AUDIT_STORAGE_ERROR));
    }

    #[test]
    fn store_redacts_captured_payload_before_retention() {
        let store = InMemoryAuditStore::configured(10, None, AuditRedactor::default(), true, None);
        let mut event = AuditEvent::new(
            AuditCategory::Decision,
            AuditDecision::Allow,
            "tools/call",
            "allowed",
            "Allowed",
        );
        event.payload = Some(serde_json::json!({"arguments":{"api_key":"secret"}}));
        store.record(event);
        let page = store.query(&AuditQuery::default());
        assert_eq!(
            page.events[0]
                .payload
                .as_ref()
                .and_then(|value| value.pointer("/arguments/api_key")),
            Some(&Value::String("[REDACTED]".to_owned()))
        );
    }
}
