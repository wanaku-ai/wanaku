#![deny(unsafe_code)]
#![cfg_attr(test, allow(clippy::expect_used, clippy::unwrap_used))]
//! Isolated transient policy validation and replay.
pub mod api;
mod candidate;
mod decision;
mod jobs;
mod routes;
mod snapshot;
#[cfg(test)]
mod tests;
use api::{ReplayRequest, SimulationError, SimulationRequest, ValidationRequest};
use http::Response;
use praxis_filter::{FilterRegistry, PipelineExtension};
use serde::{Deserialize, Serialize};
use std::sync::{Arc, RwLock};
use std::time::{Duration, Instant};
use wanaku_feature_action_policy::ActionPolicyState;
use wanaku_feature_evaluator::state::EvaluatorState;
use wanaku_infra::registry::InMemoryRegistry;
use wanaku_types::{
    audit::{AuditCategory, AuditDecision, AuditEvent, AuditStore, InMemoryAuditStore},
    feature::{Feature, HttpContext},
    governance::GovernanceConfig,
};

#[derive(Clone)]
pub struct SimulationDeps {
    pub policy: ActionPolicyState,
    pub evaluators: EvaluatorState,
    pub registry: InMemoryRegistry,
    pub governance: GovernanceConfig,
    pub audit: InMemoryAuditStore,
}
#[derive(Debug, Clone, Deserialize)]
#[serde(default, deny_unknown_fields)]
pub struct SimulationLimits {
    pub max_candidate_bytes: usize,
    pub max_request_bytes: usize,
    pub max_actions: usize,
    pub max_concurrent_jobs: usize,
    pub max_stored_jobs: usize,
    pub max_result_bytes: usize,
    pub max_duration_ms: u64,
    pub retention_seconds: u64,
    pub max_registry_entries: usize,
    pub max_evaluators: usize,
    pub max_rules: usize,
    pub wasm_fuel: u64,
    pub wasm_memory_bytes: usize,
    pub wasm_timeout_ms: u64,
}
impl Default for SimulationLimits {
    fn default() -> Self {
        Self {
            max_candidate_bytes: 262_144,
            max_request_bytes: 1_048_576,
            max_actions: 1000,
            max_concurrent_jobs: 2,
            max_stored_jobs: 50,
            max_result_bytes: 4_194_304,
            max_duration_ms: 30_000,
            retention_seconds: 3600,
            max_registry_entries: 10_000,
            max_evaluators: 32,
            max_rules: 1000,
            wasm_fuel: 10_000_000,
            wasm_memory_bytes: 67_108_864,
            wasm_timeout_ms: 1000,
        }
    }
}
pub struct SimulationFeature {
    deps: SimulationDeps,
    limits: Arc<RwLock<SimulationLimits>>,
    jobs: jobs::JobStore,
}
impl SimulationFeature {
    #[must_use]
    pub fn new(deps: SimulationDeps) -> Self {
        Self {
            deps,
            limits: Arc::new(RwLock::new(SimulationLimits::default())),
            jobs: jobs::JobStore::default(),
        }
    }
    fn limits(&self) -> SimulationLimits {
        self.limits
            .read()
            .map_or_else(|_| SimulationLimits::default(), |l| l.clone())
    }
    fn audit(&self, operation: &str, result: &Response<Vec<u8>>) {
        let mut event = AuditEvent::new(
            AuditCategory::Administrative,
            if result.status().is_success() {
                AuditDecision::Allow
            } else {
                AuditDecision::Error
            },
            operation,
            "policy_simulation_requested",
            "Transient policy simulation requested.",
        );
        event.response_status = Some(result.status().as_u16());
        event.policy_revision = self
            .deps
            .policy
            .revision_store()
            .active_revision_id()
            .map(|id| id.to_string());
        event.attributes.insert(
            "external_evaluators_enabled".to_owned(),
            serde_json::json!(false),
        );
        add_candidate_identity(&mut event, result.body());
        self.deps.audit.record(event);
    }

    async fn replay(&self, body: &str) -> Response<Vec<u8>> {
        let limits = self.limits();
        let request: ReplayRequest = match parse(body, &limits) {
            Ok(request) => request,
            Err(code) => return parse_failure(code),
        };
        if request.allow_external_evaluators {
            return failure(400, "external_execution_unsupported");
        }
        if request
            .actions
            .len()
            .saturating_add(request.audit_event_ids.len())
            > limits.max_actions
        {
            return failure(413, "replay_action_limit");
        }
        self.start_replay(request, limits).await
    }

    async fn start_replay(
        &self,
        request: ReplayRequest,
        limits: SimulationLimits,
    ) -> Response<Vec<u8>> {
        let permit = match self.jobs.admit(limits.max_concurrent_jobs) {
            Ok(permit) => permit,
            Err(code) => return failure(429, code),
        };
        let deadline = Duration::from_millis(limits.max_duration_ms);
        let context = jobs::ReplayStart {
            deps: self.deps.clone(),
            limits,
            request,
            started: Instant::now(),
            permit,
        };
        let jobs = self.jobs.clone();
        let task = tokio::task::spawn_blocking(move || jobs.start_admitted(context));
        match tokio::time::timeout(deadline, task).await {
            Ok(Ok(result)) => result,
            Ok(Err(_)) => failure(503, "simulation_worker_failed"),
            Err(_) => failure(408, "simulation_duration_limit"),
        }
    }

    async fn validate(&self, body: &str) -> Response<Vec<u8>> {
        let limits = self.limits();
        let request: ValidationRequest = match parse(body, &limits) {
            Ok(request) => request,
            Err(code) => return parse_failure(code),
        };
        if request.allow_external_evaluators {
            return failure(400, "external_execution_unsupported");
        }
        self.run_operation(Operation::Validate(request), limits)
            .await
    }

    async fn simulate(&self, body: &str) -> Response<Vec<u8>> {
        let limits = self.limits();
        let request: SimulationRequest = match parse(body, &limits) {
            Ok(request) => request,
            Err(code) => return parse_failure(code),
        };
        if request.allow_external_evaluators {
            return failure(400, "external_execution_unsupported");
        }
        self.run_operation(Operation::Simulate(request), limits)
            .await
    }

    async fn run_operation(
        &self,
        operation: Operation,
        limits: SimulationLimits,
    ) -> Response<Vec<u8>> {
        let permit = match self.jobs.admit(limits.max_concurrent_jobs) {
            Ok(permit) => permit,
            Err(code) => return failure(429, code),
        };
        let deps = self.deps.clone();
        let deadline = Duration::from_millis(limits.max_duration_ms);
        let task = tokio::task::spawn_blocking(move || {
            let _permit = permit;
            execute_operation(&deps, &limits, operation)
        });
        match tokio::time::timeout(deadline, task).await {
            Ok(Ok(response)) => response,
            Ok(Err(_)) => failure(503, "simulation_worker_failed"),
            Err(_) => failure(408, "simulation_duration_limit"),
        }
    }
}
fn parse<T: serde::de::DeserializeOwned>(
    body: &str,
    limits: &SimulationLimits,
) -> Result<T, &'static str> {
    if body.len() > limits.max_request_bytes {
        return Err("request_size_limit");
    }
    let value: serde_json::Value =
        serde_json::from_str(body).map_err(|_| "request_schema_invalid")?;
    if value
        .get("candidate")
        .is_some_and(|v| v.to_string().len() > limits.max_candidate_bytes)
    {
        return Err("candidate_size_limit");
    }
    serde_json::from_value(value).map_err(|_| "request_schema_invalid")
}
pub(crate) fn response<T: Serialize>(status: u16, value: &T) -> Response<Vec<u8>> {
    let body =
        serde_json::to_vec(&serde_json::json!({"data":value,"error":null})).unwrap_or_default();
    match Response::builder()
        .status(status)
        .header("content-type", "application/json")
        .body(body)
    {
        Ok(r) => r,
        Err(_) => Response::new(Vec::new()),
    }
}
pub(crate) fn failure(status: u16, code: &str) -> Response<Vec<u8>> {
    match Response::builder()
        .status(status)
        .header("content-type", "application/json")
        .body(
            serde_json::to_vec(&serde_json::json!({"data":null,"error":code})).unwrap_or_default(),
        ) {
        Ok(r) => r,
        Err(_) => Response::new(Vec::new()),
    }
}
#[async_trait::async_trait]
impl Feature for SimulationFeature {
    fn name(&self) -> &'static str {
        "simulation"
    }
    fn register_filters(&self, _registry: &mut FilterRegistry) {}
    fn pipeline_extensions(&self) -> Vec<Box<dyn PipelineExtension>> {
        Vec::new()
    }
    async fn handle_route(&self, ctx: &HttpContext<'_>) -> Option<Response<Vec<u8>>> {
        use routes::SimulationRoute;
        let (operation, result) = match routes::resolve(ctx.method, ctx.path) {
            SimulationRoute::Validate => (
                "policy_simulation_validate",
                self.validate(ctx.body.unwrap_or("")).await,
            ),
            SimulationRoute::Simulate => (
                "policy_simulation_single",
                self.simulate(ctx.body.unwrap_or("")).await,
            ),
            SimulationRoute::Replay => (
                "policy_simulation_replay",
                self.replay(ctx.body.unwrap_or("")).await,
            ),
            SimulationRoute::Get(id) => return Some(self.jobs.get(&self.deps, id)),
            SimulationRoute::Cancel(id) => {
                ("policy_simulation_cancel", self.jobs.cancel(&self.deps, id))
            }
            SimulationRoute::Delete(id) => ("policy_simulation_delete", self.jobs.delete(id)),
            SimulationRoute::NotFound => return None,
        };
        self.audit(operation, &result);
        Some(result)
    }

    fn load_yaml_config(&self, root: &serde_yaml::Value) {
        if let Some(value) = root.get("simulation") {
            match serde_yaml::from_value::<SimulationLimits>(value.clone()) {
                Ok(l) => {
                    if let Ok(mut limits) = self.limits.write() {
                        *limits = l;
                    }
                }
                Err(_) => {
                    if let Ok(mut limits) = self.limits.write() {
                        limits.max_concurrent_jobs = 0;
                    }
                }
            }
        }
    }
    fn load_env_config(&self) {}
    fn validate_startup(&self) -> Result<(), String> {
        let l = self.limits();
        if l.max_candidate_bytes == 0
            || l.max_request_bytes == 0
            || l.max_result_bytes == 0
            || l.max_registry_entries == 0
            || l.max_evaluators == 0
            || l.max_rules == 0
            || l.wasm_fuel == 0
            || l.wasm_memory_bytes == 0
            || l.wasm_timeout_ms == 0
            || l.max_actions == 0
            || l.max_concurrent_jobs == 0
            || l.max_stored_jobs == 0
            || l.max_duration_ms == 0
            || l.retention_seconds == 0
            || Instant::now()
                .checked_add(Duration::from_millis(l.max_duration_ms))
                .is_none()
            || Instant::now()
                .checked_add(Duration::from_secs(l.retention_seconds))
                .is_none()
        {
            Err("simulation_limits_invalid".to_owned())
        } else {
            Ok(())
        }
    }
}

fn invalid_candidate(
    limits: &SimulationLimits,
    report: crate::api::ValidationReport,
) -> Response<Vec<u8>> {
    bounded_response(
        limits,
        422,
        &SimulationError {
            reason_code: "candidate_invalid".to_owned(),
            validation: Some(report),
        },
    )
}

enum Operation {
    Validate(ValidationRequest),
    Simulate(SimulationRequest),
}

fn execute_operation(
    deps: &SimulationDeps,
    limits: &SimulationLimits,
    operation: Operation,
) -> Response<Vec<u8>> {
    let started = Instant::now();
    let deps = match snapshot::capture(deps, limits) {
        Ok(snapshot) => snapshot,
        Err(code) => return failure(413, code),
    };
    let selection = match &operation {
        Operation::Validate(r) => &r.candidate,
        Operation::Simulate(r) => &r.candidate,
    };
    let prepared = candidate::prepare_limited(&deps, selection, limits);
    if started.elapsed().as_millis() >= u128::from(limits.max_duration_ms) {
        return failure(408, "simulation_duration_limit");
    }
    match operation {
        Operation::Validate(_) => match prepared {
            Ok(candidate) => limited_response(limits, &candidate.report),
            Err(report) => limited_response(limits, &report),
        },
        Operation::Simulate(request) => {
            execute_single(&deps, limits, prepared, &request.action, started)
        }
    }
}

fn execute_single(
    deps: &SimulationDeps,
    limits: &SimulationLimits,
    prepared: Result<candidate::PreparedCandidate, Box<api::ValidationReport>>,
    action: &api::SyntheticAction,
    started: Instant,
) -> Response<Vec<u8>> {
    let mut candidate = match prepared {
        Ok(candidate) => candidate,
        Err(report) => return invalid_candidate(limits, *report),
    };
    candidate.evaluators = candidate
        .evaluators
        .with_limits(wasm_limits(limits, started));
    let result = decision::simulate(deps, &candidate, action);
    if started.elapsed().as_millis() >= u128::from(limits.max_duration_ms) {
        return failure(408, "simulation_duration_limit");
    }
    match result {
        Ok(report) => limited_response(limits, &report),
        Err(code) => failure(400, code),
    }
}

pub(crate) fn wasm_limits(
    limits: &SimulationLimits,
    started: Instant,
) -> wanaku_feature_evaluator::simulation::SimulationLimits {
    let elapsed = u64::try_from(started.elapsed().as_millis()).unwrap_or(u64::MAX);
    wanaku_feature_evaluator::simulation::SimulationLimits {
        fuel: limits.wasm_fuel,
        memory_bytes: limits.wasm_memory_bytes,
        timeout_ms: limits
            .wasm_timeout_ms
            .min(limits.max_duration_ms.saturating_sub(elapsed))
            .max(1),
    }
}

fn limited_response(limits: &SimulationLimits, report: &impl Serialize) -> Response<Vec<u8>> {
    bounded_response(limits, 200, report)
}

fn bounded_response(
    limits: &SimulationLimits,
    status: u16,
    report: &impl Serialize,
) -> Response<Vec<u8>> {
    let result = response(status, report);
    if result.body().len() > limits.max_result_bytes {
        failure(413, "simulation_result_size_limit")
    } else {
        result
    }
}

fn parse_failure(code: &str) -> Response<Vec<u8>> {
    let status = if matches!(code, "request_size_limit" | "candidate_size_limit") {
        413
    } else {
        400
    };
    failure(status, code)
}

fn add_candidate_identity(event: &mut AuditEvent, bytes: &[u8]) {
    let identity = serde_json::from_slice::<serde_json::Value>(bytes)
        .ok()
        .and_then(|body| {
            body.pointer("/data/identity")
                .or_else(|| body.pointer("/data/validation/identity"))
                .cloned()
        });
    if let Some(identity) = identity {
        event
            .attributes
            .insert("candidate_identity".to_owned(), identity);
    }
}

#[cfg(test)]
mod limit_tests {
    use super::*;

    #[tokio::test]
    async fn shared_admission_rejects_requests_before_candidate_preparation() {
        let feature = SimulationFeature::new(crate::tests::deps());
        let first = feature.jobs.admit(2).expect("first permit");
        let _second = feature.jobs.admit(2).expect("second permit");
        assert_eq!(feature.validate("{}").await.status(), 429);
        assert_eq!(
            feature
                .simulate(r#"{"action":{"request":{}}}"#)
                .await
                .status(),
            429
        );
        assert_eq!(feature.replay("{}").await.status(), 429);
        drop(first);
        assert_eq!(feature.validate("{}").await.status(), 200);
    }

    #[tokio::test]
    async fn invalid_candidate_reports_obey_output_limit() {
        let feature = SimulationFeature::new(crate::tests::deps());
        feature.limits.write().expect("limits").max_result_bytes = 128;
        let request = r#"{"candidate":{"policy_revision":999},"action":{"request":{}}}"#;
        let response = feature.simulate(request).await;
        assert_eq!(response.status(), 413);
        assert!(response.body().len() <= 128);
    }
}
