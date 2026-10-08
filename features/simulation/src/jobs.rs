use crate::{
    SimulationDeps, SimulationLimits,
    api::{CandidateSelection, ReplayDetail, ReplayJob, ReplayRequest, SimulationError},
    candidate::{self, PreparedCandidate},
    decision, failure, response,
};
use http::Response;
use std::{
    collections::BTreeMap,
    sync::{
        Arc, Mutex,
        atomic::{AtomicBool, AtomicUsize, Ordering},
    },
    time::{Duration, Instant},
};
use wanaku_types::audit::AuditStore;
type RegistrationResult = Result<(ReplayJob, Arc<AtomicBool>), Box<Response<Vec<u8>>>>;
type PreparedPair = Result<(PreparedCandidate, PreparedCandidate), Box<Response<Vec<u8>>>>;
struct StoredJob {
    report: ReplayJob,
    cancel: Arc<AtomicBool>,
    expires: Instant,
}
#[derive(Clone, Default)]
pub(crate) struct JobStore {
    jobs: Arc<Mutex<BTreeMap<String, StoredJob>>>,
    running: Arc<AtomicUsize>,
}
impl JobStore {
    pub(crate) fn admit(&self, max: usize) -> Result<RunningPermit, &'static str> {
        self.running
            .fetch_update(Ordering::Relaxed, Ordering::Relaxed, |n| {
                if n < max { Some(n + 1) } else { None }
            })
            .map_err(|_| "replay_concurrency_limit")?;
        Ok(RunningPermit(self.running.clone()))
    }
    #[cfg(test)]
    pub fn start(
        &self,
        deps: &SimulationDeps,
        limits: &SimulationLimits,
        request: ReplayRequest,
    ) -> Response<Vec<u8>> {
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
        let started = Instant::now();
        let permit = match self.admit(limits.max_concurrent_jobs) {
            Ok(p) => p,
            Err(e) => return failure(429, e),
        };
        self.start_admitted(ReplayStart {
            deps: deps.clone(),
            limits: limits.clone(),
            request,
            started,
            permit,
        })
    }
    pub(crate) fn start_admitted(&self, c: ReplayStart) -> Response<Vec<u8>> {
        let captured = match crate::snapshot::capture(&c.deps, &c.limits) {
            Ok(d) => d,
            Err(e) => return failure(413, e),
        };
        self.start_prepared(StartContext {
            deps: captured,
            limits: c.limits,
            request: c.request,
            started: c.started,
            permit: c.permit,
        })
    }
    fn start_prepared(&self, c: StartContext) -> Response<Vec<u8>> {
        let StartContext {
            deps,
            limits,
            mut request,
            started,
            permit,
        } = c;
        let deps = &deps;
        let (candidate, active) = match prepare_pair(deps, &limits, &request.candidate) {
            Ok(p) => p,
            Err(r) => return *r,
        };
        let skipped = add_audit_actions(deps, &mut request);
        let (report, cancel) =
            match self.register_before_deadline(&limits, &candidate, skipped, started) {
                Ok(r) => r,
                Err(r) => return *r,
            };
        self.spawn(RunContext {
            deps: deps.clone(),
            limits: limits.clone(),
            request,
            candidate,
            active,
            id: report.id.clone(),
            cancel,
            started,
            _permit: permit,
        });
        response(202, &report)
    }
    fn spawn(&self, context: RunContext) {
        let store = self.clone();
        tokio::task::spawn_blocking(move || store.run(&context));
    }
    fn register_before_deadline(
        &self,
        limits: &SimulationLimits,
        candidate: &PreparedCandidate,
        skipped: Vec<String>,
        started: Instant,
    ) -> RegistrationResult {
        if started.elapsed().as_millis() >= u128::from(limits.max_duration_ms) {
            return Err(Box::new(failure(408, "simulation_duration_limit")));
        }
        self.register(limits, candidate, skipped).map_err(|e| {
            Box::new(failure(
                match e {
                    "simulation_store_unavailable" => 503,
                    "replay_result_size_limit" => 413,
                    _ => 429,
                },
                e,
            ))
        })
    }
    fn register(
        &self,
        limits: &SimulationLimits,
        candidate: &PreparedCandidate,
        skipped: Vec<String>,
    ) -> Result<(ReplayJob, Arc<AtomicBool>), &'static str> {
        let Ok(mut jobs) = self.jobs.lock() else {
            return Err("simulation_store_unavailable");
        };
        jobs.retain(|_, job| {
            let retain = job.expires > Instant::now();
            if !retain {
                job.cancel.store(true, Ordering::Relaxed);
            }
            retain
        });
        if jobs.len() >= limits.max_stored_jobs {
            return Err("simulation_result_capacity");
        }

        let id = wanaku_types::correlation::generate_short_id();
        let report = initial_report(id.clone(), candidate, skipped);
        if encoded_size(&report).saturating_add(256) > limits.max_result_bytes {
            return Err("replay_result_size_limit");
        }
        let cancel = Arc::new(AtomicBool::new(false));
        jobs.insert(
            id.clone(),
            StoredJob {
                report: report.clone(),
                cancel: cancel.clone(),
                expires: expiry(limits.retention_seconds),
            },
        );
        drop(jobs);
        Ok((report, cancel))
    }
    pub fn get(&self, deps: &SimulationDeps, id: &str) -> Response<Vec<u8>> {
        let Ok(mut jobs) = self.jobs.lock() else {
            return failure(503, "simulation_store_unavailable");
        };
        jobs.retain(|_, j| {
            let retain = j.expires > Instant::now();
            if !retain {
                j.cancel.store(true, Ordering::Relaxed);
            }
            retain
        });
        match jobs.get_mut(id) {
            Some(j) => {
                j.report.stale = candidate::stale(deps, &j.report.identity);
                response(200, &j.report)
            }
            None => failure(404, "simulation_job_not_found"),
        }
    }
    pub fn cancel(&self, deps: &SimulationDeps, id: &str) -> Response<Vec<u8>> {
        let Ok(mut jobs) = self.jobs.lock() else {
            return failure(503, "simulation_store_unavailable");
        };
        let Some(j) = jobs.get_mut(id) else {
            return failure(404, "simulation_job_not_found");
        };
        if j.report.status == "running" {
            j.cancel.store(true, Ordering::Relaxed);
            j.report.status = "cancelled".to_owned();
        }
        j.report.stale = candidate::stale(deps, &j.report.identity);
        response(200, &j.report)
    }
    pub fn delete(&self, id: &str) -> Response<Vec<u8>> {
        let Ok(mut jobs) = self.jobs.lock() else {
            return failure(503, "simulation_store_unavailable");
        };
        match jobs.remove(id) {
            Some(j) => {
                j.cancel.store(true, Ordering::Relaxed);
                response(200, &serde_json::json!({"deleted":true}))
            }
            None => failure(404, "simulation_job_not_found"),
        }
    }
    fn run(&self, c: &RunContext) {
        let started = c.started;
        let result = replay(c, started);
        self.finish(c, result, started);
    }
    fn finish(&self, c: &RunContext, result: ReplayResult, started: Instant) {
        if let Ok(mut jobs) = self.jobs.lock()
            && let Some(job) = jobs.get_mut(&c.id)
        {
            if job.cancel.load(Ordering::Relaxed) {
                return;
            }
            job.report.status = if result.failure_code.is_some() {
                "failed"
            } else {
                "completed"
            }
            .to_owned();
            job.report.reason_code = result.failure_code;
            job.report.details = result.details;
            job.report.counts = result.counts;
            job.report.duration_ms =
                u64::try_from(started.elapsed().as_millis()).unwrap_or(u64::MAX);
            job.report.stale = candidate::stale(&c.deps, &job.report.identity);
            enforce_result_size(&mut job.report, c.limits.max_result_bytes);
            job.expires = expiry(c.limits.retention_seconds);
        }
    }
}
struct RunContext {
    deps: SimulationDeps,
    limits: SimulationLimits,
    request: ReplayRequest,
    candidate: PreparedCandidate,
    active: PreparedCandidate,
    id: String,
    cancel: Arc<AtomicBool>,
    started: Instant,
    _permit: RunningPermit,
}
fn compare(a: &crate::api::SimulationReport, c: &crate::api::SimulationReport) -> &'static str {
    if a.conditional
        || c.conditional
        || a.decision == "indeterminate"
        || c.decision == "indeterminate"
    {
        return "error_or_indeterminate";
    }
    match (a.decision.as_str(), c.decision.as_str()) {
        ("allow", "allow") | ("deny", "deny") if a.baseline_used != c.baseline_used => {
            "no_match_changed"
        }
        ("allow", "allow") => "allow_to_allow",
        ("allow", "deny") => "allow_to_deny",
        ("deny", "allow") => "deny_to_allow",

        ("deny", "deny") => "deny_to_deny",
        _ => "error_or_indeterminate",
    }
}

pub(crate) struct RunningPermit(Arc<AtomicUsize>);
impl Drop for RunningPermit {
    fn drop(&mut self) {
        self.0.fetch_sub(1, Ordering::Relaxed);
    }
}

fn prepare_pair(
    deps: &SimulationDeps,
    limits: &SimulationLimits,
    selection: &CandidateSelection,
) -> PreparedPair {
    let mut candidate = match candidate::prepare_limited(deps, selection, limits) {
        Ok(p) => p,
        Err(r) => return Err(Box::new(invalid_replay_candidate(*r, limits))),
    };
    let Ok(mut active) = candidate::prepare_limited(deps, &CandidateSelection::default(), limits)
    else {
        return Err(Box::new(failure(422, "active_configuration_invalid")));
    };
    let wasm_limits = wanaku_feature_evaluator::simulation::SimulationLimits {
        fuel: limits.wasm_fuel,
        memory_bytes: limits.wasm_memory_bytes,
        timeout_ms: limits.wasm_timeout_ms,
    };
    candidate.evaluators = candidate.evaluators.with_limits(wasm_limits);
    active.evaluators = active.evaluators.with_limits(wasm_limits);
    Ok((candidate, active))
}

fn add_audit_actions(deps: &SimulationDeps, request: &mut ReplayRequest) -> Vec<String> {
    let mut skipped = Vec::new();
    for id in &request.audit_event_ids {
        match deps.audit.get(id) {
            Some(event)
                if event.category == wanaku_types::audit::AuditCategory::Decision
                    && !event.redaction.payload_truncated
                    && event.redaction.redacted_fields.is_empty() =>
            {
                if let Some(payload) = event.payload {
                    request.actions.push(crate::api::SyntheticAction {
                        namespace: event.namespace.unwrap_or_else(|| "default".to_owned()),
                        request: payload,
                        evidence_id: Some(id.clone()),
                    });
                } else {
                    skipped.push(id.clone());
                }
            }
            _ => skipped.push(id.clone()),
        }
    }
    let redactor = wanaku_types::audit_redaction::AuditRedactor::default();
    for id in &mut skipped {
        redactor.redact_string(id);
    }
    skipped
}

struct ReplayResult {
    details: Vec<ReplayDetail>,
    counts: BTreeMap<String, usize>,
    failure_code: Option<String>,
}
fn replay(c: &RunContext, started: Instant) -> ReplayResult {
    let mut details = Vec::new();
    let mut counts = BTreeMap::new();
    let mut failure_code = None;
    for (index, action) in c.request.actions.iter().enumerate() {
        if let Some(reason) = stopped(c, started) {
            failure_code = Some(reason.to_owned());
            break;
        }

        if let Some(detail) = replay_action(c, index, action) {
            *counts.entry(detail.category.clone()).or_insert(0) += 1;
            details.push(detail);
            if serde_json::to_vec(&details).map_or(true, |v| v.len() > c.limits.max_result_bytes) {
                details.pop();
                failure_code = Some("replay_result_size_limit".to_owned());
                break;
            }
        } else {
            *counts
                .entry("error_or_indeterminate".to_owned())
                .or_insert(0) += 1;
        }
    }
    if started.elapsed().as_millis() > u128::from(c.limits.max_duration_ms) {
        failure_code = Some("replay_duration_limit".to_owned());
    }
    ReplayResult {
        details,
        counts,
        failure_code,
    }
}

fn replay_action(
    c: &RunContext,
    index: usize,
    action: &crate::api::SyntheticAction,
) -> Option<ReplayDetail> {
    let active =
        decision::simulate_with_limits(&c.deps, &c.active, action, Some(remaining_limits(c)?))
            .ok()?;
    let candidate =
        decision::simulate_with_limits(&c.deps, &c.candidate, action, Some(remaining_limits(c)?))
            .ok()?;
    let category = compare(&active, &candidate);
    Some(ReplayDetail {
        index,
        category: category.to_owned(),
        access_expansion: category == "deny_to_allow",
        compatibility_impact: category == "allow_to_deny",
        outside_evidence: !c.request.candidate.evidence_ids.is_empty()
            && action
                .evidence_id
                .as_ref()
                .is_none_or(|id| !c.request.candidate.evidence_ids.contains(id)),
        active,
        candidate,
    })
}

fn initial_report(id: String, candidate: &PreparedCandidate, skipped: Vec<String>) -> ReplayJob {
    ReplayJob {
        schema_version: "1.0".to_owned(),
        id,
        status: "running".to_owned(),
        stale: candidate.report.stale,
        identity: candidate.report.identity.clone(),
        counts: BTreeMap::new(),
        details: Vec::new(),
        skipped_audit_ids: skipped,
        reason_code: None,
        duration_ms: 0,
    }
}

fn enforce_result_size(report: &mut ReplayJob, limit: usize) {
    if encoded_size(report) > limit {
        report.details.clear();
        report.counts.clear();
        report.skipped_audit_ids.clear();
        report.status = "failed".to_owned();
        report.reason_code = Some("replay_result_size_limit".to_owned());
    }
}

fn remaining_limits(
    c: &RunContext,
) -> Option<wanaku_feature_evaluator::simulation::SimulationLimits> {
    let elapsed = u64::try_from(c.started.elapsed().as_millis()).unwrap_or(u64::MAX);
    let remaining = c.limits.max_duration_ms.checked_sub(elapsed)?;
    if remaining == 0 {
        return None;
    }
    Some(wanaku_feature_evaluator::simulation::SimulationLimits {
        fuel: c.limits.wasm_fuel,
        memory_bytes: c.limits.wasm_memory_bytes,
        timeout_ms: c.limits.wasm_timeout_ms.min(remaining),
    })
}

struct StartContext {
    deps: SimulationDeps,
    limits: SimulationLimits,
    request: ReplayRequest,
    started: Instant,
    permit: RunningPermit,
}

fn stopped(c: &RunContext, started: Instant) -> Option<&'static str> {
    if c.cancel.load(Ordering::Relaxed) {
        Some("replay_cancelled")
    } else if started.elapsed().as_millis() > u128::from(c.limits.max_duration_ms) {
        Some("replay_duration_limit")
    } else {
        None
    }
}

fn encoded_size(report: &ReplayJob) -> usize {
    serde_json::to_vec(&serde_json::json!({"data":report,"error":null}))
        .map_or(usize::MAX, |v| v.len())
}

pub(crate) struct ReplayStart {
    pub deps: SimulationDeps,
    pub limits: SimulationLimits,
    pub request: ReplayRequest,
    pub started: Instant,
    pub permit: RunningPermit,
}

fn invalid_replay_candidate(
    report: crate::api::ValidationReport,
    limits: &SimulationLimits,
) -> Response<Vec<u8>> {
    let r = response(
        422,
        &SimulationError {
            reason_code: "candidate_invalid".to_owned(),
            validation: Some(report),
        },
    );
    if r.body().len() > limits.max_result_bytes {
        failure(413, "replay_result_size_limit")
    } else {
        r
    }
}

fn expiry(seconds: u64) -> Instant {
    let now = Instant::now();
    now.checked_add(Duration::from_secs(seconds)).unwrap_or(now)
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn admission_stays_bounded_until_worker_permit_drops() {
        let store = JobStore::default();
        let permit = store.admit(1).expect("first slot");
        assert!(store.admit(1).is_err());
        drop(permit);
        assert!(store.admit(1).is_ok());
    }
    fn empty_request() -> ReplayRequest {
        ReplayRequest {
            candidate: CandidateSelection::default(),
            actions: Vec::new(),
            audit_event_ids: Vec::new(),
            allow_external_evaluators: false,
        }
    }
    #[tokio::test]
    async fn replay_resource_limits_reject_before_publication() {
        let d = crate::tests::deps();
        let store = JobStore::default();
        let limits = SimulationLimits {
            max_duration_ms: 0,
            ..SimulationLimits::default()
        };
        assert_eq!(store.start(&d, &limits, empty_request()).status(), 408);
        let limits = SimulationLimits {
            max_result_bytes: 1,
            ..SimulationLimits::default()
        };
        assert_eq!(store.start(&d, &limits, empty_request()).status(), 413);
        let mut request = empty_request();
        request.audit_event_ids.push("event".to_owned());
        let limits = SimulationLimits {
            max_actions: 0,
            ..SimulationLimits::default()
        };
        assert_eq!(store.start(&d, &limits, request).status(), 413);
        let permit = store.admit(1).expect("admission");
        let limits = SimulationLimits {
            max_concurrent_jobs: 1,
            ..SimulationLimits::default()
        };
        assert_eq!(store.start(&d, &limits, empty_request()).status(), 429);
        drop(permit);
        assert!(store.jobs.lock().expect("jobs").is_empty());
    }
    #[test]
    fn expiration_cancels_worker_and_deletes_result() {
        let d = crate::tests::deps();
        let store = JobStore::default();
        let candidate = candidate::prepare(&d, &CandidateSelection::default()).expect("candidate");
        let limits = SimulationLimits {
            retention_seconds: 1,
            ..SimulationLimits::default()
        };
        let (report, cancel) = store
            .register(&limits, &candidate, Vec::new())
            .expect("registered");
        if let Ok(mut jobs) = store.jobs.lock()
            && let Some(job) = jobs.get_mut(&report.id)
        {
            job.expires = Instant::now();
        }
        assert_eq!(store.get(&d, &report.id).status(), 404);
        assert!(cancel.load(Ordering::Relaxed));
    }
}
