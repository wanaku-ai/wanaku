#![cfg_attr(
    feature = "openapi",
    allow(
        clippy::large_stack_frames,
        reason = "utoipa::ToSchema derive generates large stack frames"
    )
)]

use std::collections::{BTreeMap, VecDeque};
use std::sync::{Arc, Condvar, Mutex, Weak};

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
    // Drop and join the persistence worker before the state it reports into.
    persistence_worker: Option<PersistenceWorker>,
    inner: Arc<Mutex<StoreState>>,
    capacity: usize,
    persistence: Option<Arc<dyn AuditPersistence>>,
    redactor: AuditRedactor,
    capture_payloads: bool,
    failure_observer: Option<Arc<dyn AuditFailureObserver>>,
}

#[derive(Default)]
struct StoreState {
    events: VecDeque<Arc<AuditEvent>>,
    next_sequence: u64,
    dropped: u64,
    last_error: Option<String>,
}

struct PersistenceQueue {
    state: Mutex<PersistenceQueueState>,
    ready: Condvar,
}

#[derive(Default)]
struct PersistenceQueueState {
    pending_generation: Option<u64>,
    producers: usize,
}

struct PersistenceWorker {
    queue: Arc<PersistenceQueue>,
    join: Arc<Mutex<Option<std::thread::JoinHandle<()>>>>,
    inner: Weak<Mutex<StoreState>>,
    observer: Option<Arc<dyn AuditFailureObserver>>,
}

impl PersistenceWorker {
    #[expect(
        clippy::too_many_lines,
        reason = "worker setup keeps queue, failure reporting, and shutdown state together"
    )]
    fn start(
        backend: Arc<dyn AuditPersistence>,
        inner: &Arc<Mutex<StoreState>>,
        observer: Option<&Arc<dyn AuditFailureObserver>>,
    ) -> Option<Self> {
        let queue = Arc::new(PersistenceQueue {
            state: Mutex::new(PersistenceQueueState {
                producers: 1,
                ..PersistenceQueueState::default()
            }),
            ready: Condvar::new(),
        });
        let worker_queue = queue.clone();
        let weak_inner = Arc::downgrade(inner);
        let worker_observer = observer.cloned();
        let join = match std::thread::Builder::new()
            .name("wanaku-audit-persistence".to_owned())
            .spawn(move || {
                persistence_loop(
                    &worker_queue,
                    &backend,
                    &weak_inner,
                    worker_observer.as_ref(),
                );
            }) {
            Ok(join) => join,
            Err(error) => {
                mark_failure_shared(inner, observer, &error.to_string());
                return None;
            }
        };
        Some(Self {
            queue,
            join: Arc::new(Mutex::new(Some(join))),
            inner: Arc::downgrade(inner),
            observer: observer.cloned(),
        })
    }

    fn enqueue(&self, generation: u64) -> Result<(), String> {
        let mut state = self.queue.state.lock().map_err(|error| error.to_string())?;
        state.pending_generation = Some(
            state
                .pending_generation
                .map_or(generation, |pending| pending.max(generation)),
        );
        self.queue.ready.notify_one();
        Ok(())
    }

    fn increment_producer_count(&self) {
        let update = |state: &mut PersistenceQueueState| {
            state.producers = state.producers.saturating_add(1);
        };
        match self.queue.state.lock() {
            Ok(mut state) => update(&mut state),
            Err(error) => update(&mut error.into_inner()),
        }
    }
}

impl Clone for PersistenceWorker {
    fn clone(&self) -> Self {
        self.increment_producer_count();
        Self {
            queue: self.queue.clone(),
            join: self.join.clone(),
            inner: self.inner.clone(),
            observer: self.observer.clone(),
        }
    }
}

impl Drop for PersistenceWorker {
    fn drop(&mut self) {
        let last_producer = match self.queue.state.lock() {
            Ok(mut state) => {
                state.producers = state.producers.saturating_sub(1);
                state.producers == 0
            }
            Err(error) => {
                let mut state = error.into_inner();
                state.producers = state.producers.saturating_sub(1);
                state.producers == 0
            }
        };
        self.queue.ready.notify_one();
        if !last_producer {
            return;
        }
        let join = match self.join.lock() {
            Ok(mut join) => join.take(),
            Err(error) => error.into_inner().take(),
        };
        if let Some(join) = join
            && join.join().is_err()
        {
            report_worker_failure(
                &self.inner,
                self.observer.as_ref(),
                "audit persistence worker failed",
            );
        }
    }
}

fn persistence_loop(
    queue: &PersistenceQueue,
    backend: &Arc<dyn AuditPersistence>,
    inner: &Weak<Mutex<StoreState>>,
    observer: Option<&Arc<dyn AuditFailureObserver>>,
) {
    let mut last_saved_generation = 0;
    while let Some(requested_generation) = wait_for_generation(queue) {
        last_saved_generation = persist_generation(
            backend,
            inner,
            observer,
            requested_generation,
            last_saved_generation,
        );
    }
}

fn wait_for_generation(queue: &PersistenceQueue) -> Option<u64> {
    let mut state = match queue.state.lock() {
        Ok(state) => state,
        Err(error) => error.into_inner(),
    };
    while state.pending_generation.is_none() && state.producers > 0 {
        state = match queue.ready.wait(state) {
            Ok(state) => state,
            Err(error) => error.into_inner(),
        };
    }
    state.pending_generation.take()
}

fn persist_generation(
    backend: &Arc<dyn AuditPersistence>,
    inner: &Weak<Mutex<StoreState>>,
    observer: Option<&Arc<dyn AuditFailureObserver>>,
    requested: u64,
    last_saved: u64,
) -> u64 {
    if requested <= last_saved {
        return last_saved;
    }
    let Some(inner_state) = inner.upgrade() else {
        report_worker_failure(
            inner,
            observer,
            "audit store unavailable during persistence",
        );
        return last_saved;
    };
    let (generation, retained) = snapshot_events(&inner_state, observer);
    if generation <= last_saved {
        return last_saved;
    }
    let snapshot = retained
        .iter()
        .map(|event| event.as_ref().clone())
        .collect::<Vec<_>>();
    if let Err(error) = backend.save(&snapshot) {
        report_worker_failure(inner, observer, &error);
    }
    generation
}

fn snapshot_events(
    inner: &Arc<Mutex<StoreState>>,
    observer: Option<&Arc<dyn AuditFailureObserver>>,
) -> (u64, Vec<Arc<AuditEvent>>) {
    match inner.lock() {
        Ok(state) => (state.next_sequence, state.events.iter().cloned().collect()),
        Err(error) => {
            tracing::error!(error = %error, "audit store lock poisoned");
            if let Some(observer) = observer {
                observer.record_storage_failure();
            }
            let mut state = error.into_inner();
            state.dropped = state.dropped.saturating_add(1);
            state.last_error = Some(AUDIT_STORAGE_ERROR.to_owned());
            (state.next_sequence, state.events.iter().cloned().collect())
        }
    }
}

fn report_worker_failure(
    inner: &Weak<Mutex<StoreState>>,
    observer: Option<&Arc<dyn AuditFailureObserver>>,
    error: &str,
) {
    tracing::error!(error = %error, "audit event storage failed");
    if let Some(observer) = observer {
        observer.record_storage_failure();
    }
    if let Some(inner) = inner.upgrade() {
        mark_failure_state(&inner);
    }
}

fn mark_failure_shared(
    inner: &Arc<Mutex<StoreState>>,
    observer: Option<&Arc<dyn AuditFailureObserver>>,
    error: &str,
) {
    tracing::error!(error = %error, "audit event storage failed");
    if let Some(observer) = observer {
        observer.record_storage_failure();
    }
    mark_failure_state(inner);
}

fn mark_failure_state(inner: &Arc<Mutex<StoreState>>) {
    match inner.lock() {
        Ok(mut state) => {
            state.dropped = state.dropped.saturating_add(1);
            state.last_error = Some(AUDIT_STORAGE_ERROR.to_owned());
        }
        Err(lock_error) => {
            tracing::error!(error = %lock_error, "audit store lock poisoned");
            let mut state = lock_error.into_inner();
            state.dropped = state.dropped.saturating_add(1);
            state.last_error = Some(AUDIT_STORAGE_ERROR.to_owned());
        }
    }
}

impl InMemoryAuditStore {
    #[must_use]
    pub fn new(capacity: usize) -> Self {
        Self::configured(capacity, None, AuditRedactor::default(), false, None)
    }

    #[must_use]
    pub fn with_persistence(capacity: usize, persistence: Arc<dyn AuditPersistence>) -> Self {
        Self::configured(
            capacity,
            Some(persistence),
            AuditRedactor::default(),
            false,
            None,
        )
    }

    #[must_use]
    pub fn configured(
        capacity: usize,
        persistence: Option<Arc<dyn AuditPersistence>>,
        redactor: AuditRedactor,
        capture_payloads: bool,
        failure_observer: Option<Arc<dyn AuditFailureObserver>>,
    ) -> Self {
        let mut store = Self {
            inner: Arc::new(Mutex::new(StoreState::default())),
            capacity,
            persistence,
            persistence_worker: None,
            redactor,
            capture_payloads,
            failure_observer,
        };
        store.load_persisted();
        store.persistence_worker = store.persistence.as_ref().and_then(|backend| {
            PersistenceWorker::start(
                backend.clone(),
                &store.inner,
                store.failure_observer.as_ref(),
            )
        });
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
                        state.events.push_back(Arc::new(event));
                    }
                }
            }
            Err(error) => self.mark_failure(&error),
        }
    }

    fn prepare_event(&self, event: &mut AuditEvent) {
        redact_event_strings(&self.redactor, event);
        if self.capture_payloads {
            if let Some(payload) = event.payload.as_mut() {
                let metadata = self.redactor.redact(payload);
                event
                    .redaction
                    .redacted_fields
                    .extend(metadata.redacted_fields);
                event.redaction.payload_captured = metadata.payload_captured;
                event.redaction.payload_truncated = metadata.payload_truncated;
            }
        } else {
            event.payload = None;
            event.redaction.payload_captured = false;
        }
        redact_attributes(&self.redactor, event);
    }

    fn retain_event(&self, mut event: AuditEvent) -> u64 {
        let mut state = match self.inner.lock() {
            Ok(state) => state,
            Err(error) => {
                tracing::error!(error = %error, "audit store lock poisoned");
                if let Some(observer) = &self.failure_observer {
                    observer.record_storage_failure();
                }
                let mut state = error.into_inner();
                state.dropped = state.dropped.saturating_add(1);
                state.last_error = Some(AUDIT_STORAGE_ERROR.to_owned());
                state
            }
        };
        event.sequence = state.next_sequence;
        state.next_sequence = state.next_sequence.saturating_add(1);
        event.dropped_events = state.dropped;
        event.coverage_complete = state.dropped == 0;
        if state.events.len() >= self.capacity {
            state.events.pop_front();
        }
        state.events.push_back(Arc::new(event));
        state.next_sequence
    }

    fn mark_failure(&self, error: &str) {
        mark_failure_shared(&self.inner, self.failure_observer.as_ref(), error);
    }
}

impl AuditStore for InMemoryAuditStore {
    fn record(&self, mut event: AuditEvent) {
        self.prepare_event(&mut event);
        let generation = self.retain_event(event);
        if let Some(worker) = &self.persistence_worker
            && let Err(error) = worker.enqueue(generation)
        {
            self.mark_failure(&error);
        } else if self.persistence.is_some() && self.persistence_worker.is_none() {
            self.mark_failure("audit persistence worker unavailable");
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
                    .map(|event| event.as_ref().clone())
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
            .map(|event| event.as_ref().clone())
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

fn redact_attributes(redactor: &AuditRedactor, event: &mut AuditEvent) {
    let mut attributes = Value::Object(
        event
            .attributes
            .clone()
            .into_iter()
            .collect::<serde_json::Map<_, _>>(),
    );
    let metadata = redactor.redact(&mut attributes);
    event.redaction.redacted_fields.extend(
        metadata
            .redacted_fields
            .into_iter()
            .map(|field| format!("/attributes{field}")),
    );
    if metadata.payload_truncated {
        event
            .redaction
            .redacted_fields
            .push("/attributes".to_owned());
    }
    event.attributes = match attributes {
        Value::Object(attributes) => attributes.into_iter().collect(),
        _ => BTreeMap::from([(
            "truncated".to_owned(),
            Value::String("[REDACTED]".to_owned()),
        )]),
    };
}

fn redact_event_strings(redactor: &AuditRedactor, event: &mut AuditEvent) {
    redact_tracking_strings(redactor, event);
    redact_principal_strings(redactor, event);
    redact_outcome_strings(redactor, event);
}

fn redact_tracking_strings(redactor: &AuditRedactor, event: &mut AuditEvent) {
    redact_required_string(
        redactor,
        "/stream_id",
        &mut event.stream_id,
        &mut event.redaction,
    );
    redact_required_string(
        redactor,
        "/correlation_id",
        &mut event.correlation_id,
        &mut event.redaction,
    );
    redact_optional_string(
        redactor,
        "/request_id",
        &mut event.request_id,
        &mut event.redaction,
    );
    redact_optional_string(
        redactor,
        "/conversation_id",
        &mut event.conversation_id,
        &mut event.redaction,
    );
}

fn redact_principal_strings(redactor: &AuditRedactor, event: &mut AuditEvent) {
    redact_optional_string(redactor, "/actor", &mut event.actor, &mut event.redaction);
    redact_optional_string(
        redactor,
        "/workload",
        &mut event.workload,
        &mut event.redaction,
    );
    redact_optional_string(
        redactor,
        "/namespace",
        &mut event.namespace,
        &mut event.redaction,
    );
    redact_optional_string(
        redactor,
        "/audience",
        &mut event.audience,
        &mut event.redaction,
    );
}

fn redact_outcome_strings(redactor: &AuditRedactor, event: &mut AuditEvent) {
    redact_optional_string(redactor, "/target", &mut event.target, &mut event.redaction);
    redact_required_string(
        redactor,
        "/explanation",
        &mut event.explanation,
        &mut event.redaction,
    );
    redact_optional_string(
        redactor,
        "/evaluator",
        &mut event.evaluator,
        &mut event.redaction,
    );
    redact_optional_string(
        redactor,
        "/policy_revision",
        &mut event.policy_revision,
        &mut event.redaction,
    );
    redact_optional_string(
        redactor,
        "/upstream_id",
        &mut event.upstream_id,
        &mut event.redaction,
    );
}

fn redact_optional_string(
    redactor: &AuditRedactor,
    path: &str,
    value: &mut Option<String>,
    metadata: &mut RedactionMetadata,
) {
    if let Some(value) = value {
        redact_required_string(redactor, path, value, metadata);
    }
}

fn redact_required_string(
    redactor: &AuditRedactor,
    path: &str,
    value: &mut String,
    metadata: &mut RedactionMetadata,
) {
    let field_metadata = redactor.redact_string(value);
    if !field_metadata.redacted_fields.is_empty() || field_metadata.payload_truncated {
        metadata.redacted_fields.push(path.to_owned());
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

fn matches_query(event: &AuditEvent, query: &AuditQuery) -> bool {
    timestamp_matches(&event.timestamp, query.from.as_deref(), query.to.as_deref())
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

fn timestamp_matches(timestamp: &str, from: Option<&str>, to: Option<&str>) -> bool {
    use time::format_description::well_known::Rfc3339;

    let Ok(timestamp) = time::OffsetDateTime::parse(timestamp, &Rfc3339) else {
        return false;
    };
    from.is_none_or(|value| {
        time::OffsetDateTime::parse(value, &Rfc3339).is_ok_and(|from| timestamp >= from)
    }) && to.is_none_or(|value| {
        time::OffsetDateTime::parse(value, &Rfc3339).is_ok_and(|to| timestamp <= to)
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::atomic::{AtomicU64, Ordering};
    use std::sync::mpsc::{Receiver, Sender, channel};
    use std::time::{Duration, Instant};

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
    fn timestamp_query_uses_inclusive_rfc3339_instants() {
        let mut event = decision_event("tools/call");
        event.timestamp = "2026-09-14T12:00:00Z".to_owned();
        let query = AuditQuery {
            from: Some("2026-09-14T09:00:00-03:00".to_owned()),
            to: Some("2026-09-14T12:00:00.000Z".to_owned()),
            ..AuditQuery::default()
        };

        assert!(matches_query(&event, &query));
    }

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
        let deadline = Instant::now() + Duration::from_secs(2);
        let health = loop {
            let health = store.health();
            if !health.healthy || Instant::now() >= deadline {
                break health;
            }
            std::thread::yield_now();
        };
        assert!(!health.healthy);
        assert_eq!(health.dropped_events, 1);
        assert_eq!(health.last_error.as_deref(), Some(AUDIT_STORAGE_ERROR));
        assert_eq!(failures.0.load(Ordering::Relaxed), 1);
    }

    #[test]
    fn final_persistence_failure_is_observed_during_shutdown() {
        let failures = Arc::new(FailureCount::default());
        {
            let store = InMemoryAuditStore::configured(
                10,
                Some(Arc::new(FailingPersistence)),
                AuditRedactor::default(),
                false,
                Some(failures.clone()),
            );
            store.record(decision_event("tools/call"));
        }
        assert_eq!(failures.0.load(Ordering::Relaxed), 1);
    }

    #[test]
    fn worker_failure_is_observed_without_store_state() {
        let failures = Arc::new(FailureCount::default());
        let observer: Arc<dyn AuditFailureObserver> = failures.clone();
        report_worker_failure(&Weak::new(), Some(&observer), "write failed");
        assert_eq!(failures.0.load(Ordering::Relaxed), 1);
    }

    #[test]
    #[expect(
        clippy::too_many_lines,
        reason = "test poisons the store and verifies retained state and observation"
    )]
    fn poisoned_store_lock_records_event_and_observes_failure() {
        let failures = Arc::new(FailureCount::default());
        let store = InMemoryAuditStore::configured(
            10,
            None,
            AuditRedactor::default(),
            false,
            Some(failures.clone()),
        );
        let inner = store.inner.clone();
        let poisoned = std::thread::Builder::new()
            .spawn(move || {
                let _guard = inner.lock().expect("store lock");
                std::panic::resume_unwind(Box::new("poison audit store lock"));
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

        let state = match store.inner.lock() {
            Ok(state) => state,
            Err(error) => error.into_inner(),
        };
        assert_eq!(state.events.len(), 1);
        assert_eq!(state.dropped, 1);
        assert_eq!(state.last_error.as_deref(), Some(AUDIT_STORAGE_ERROR));
        assert_eq!(failures.0.load(Ordering::Relaxed), 1);
    }

    struct BlockingPersistence {
        call_count: AtomicU64,
        snapshots: Mutex<Vec<Vec<AuditEvent>>>,
        first_entered: Sender<()>,
        release_first: Mutex<Receiver<()>>,
    }

    impl AuditPersistence for BlockingPersistence {
        fn load(&self) -> Result<Vec<AuditEvent>, String> {
            Ok(Vec::new())
        }

        fn save(&self, events: &[AuditEvent]) -> Result<(), String> {
            let call = self.call_count.fetch_add(1, Ordering::SeqCst);
            if call == 0 {
                self.first_entered.send(()).map_err(|e| e.to_string())?;
                self.release_first
                    .lock()
                    .map_err(|e| e.to_string())?
                    .recv()
                    .map_err(|e| e.to_string())?;
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
        reason = "test coordinates concurrent records with a blocked persistence worker"
    )]
    fn persistence_coalesces_concurrent_and_stale_generations() {
        let (first_entered_tx, first_entered_rx) = channel();
        let (release_first_tx, release_first_rx) = channel();
        let persistence = Arc::new(BlockingPersistence {
            call_count: AtomicU64::new(0),
            snapshots: Mutex::new(Vec::new()),
            first_entered: first_entered_tx,
            release_first: Mutex::new(release_first_rx),
        });
        let store = InMemoryAuditStore::with_persistence(10, persistence.clone());
        store.record(AuditEvent::new(
            AuditCategory::Decision,
            AuditDecision::Allow,
            "first",
            "allowed",
            "Allowed",
        ));
        assert!(
            first_entered_rx
                .recv_timeout(Duration::from_secs(2))
                .is_ok()
        );

        let barrier = Arc::new(std::sync::Barrier::new(3));
        let record = |operation: &'static str| {
            let store = store.clone();
            let barrier = barrier.clone();
            std::thread::Builder::new()
                .spawn(move || {
                    barrier.wait();
                    store.record(decision_event(operation));
                })
                .expect("record thread")
        };
        let second = record("second");
        let third = record("third");
        barrier.wait();
        assert!(second.join().is_ok());
        assert!(third.join().is_ok());
        if let Some(worker) = &store.persistence_worker {
            assert!(worker.enqueue(1).is_ok());
        }
        assert!(release_first_tx.send(()).is_ok());
        drop(store);

        let snapshots = persistence.snapshots.lock().expect("snapshot lock");
        assert_eq!(snapshots.len(), 2);
        assert_eq!(snapshots[0].len(), 1);
        assert_eq!(snapshots[1].len(), 3);
        assert_eq!(snapshots[1][0].sequence, 0);
        assert_eq!(snapshots[1][2].sequence, 2);
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

    #[test]
    fn store_redacts_external_event_strings_before_retention() {
        let store = InMemoryAuditStore::configured(10, None, AuditRedactor::default(), true, None);
        let mut event = AuditEvent::new(
            AuditCategory::Decision,
            AuditDecision::Block,
            "tools/call",
            "evaluator_blocked",
            "Bearer evaluator-secret",
        );
        event.target = Some("ghp_target-secret".to_owned());
        event.payload = Some(serde_json::json!({"password":"also-secret"}));
        store.record(event);

        let page = store.query(&AuditQuery::default());
        assert_eq!(page.events[0].explanation, "[REDACTED]");
        assert_eq!(page.events[0].target.as_deref(), Some("[REDACTED]"));
        assert!(
            page.events[0]
                .redaction
                .redacted_fields
                .contains(&"/explanation".to_owned())
        );
        assert!(
            page.events[0]
                .redaction
                .redacted_fields
                .contains(&"/target".to_owned())
        );
    }
}
