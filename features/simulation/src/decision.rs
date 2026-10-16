use crate::{
    SimulationDeps,
    api::{SimulationReport, StageDecision, SyntheticAction},
    candidate::PreparedCandidate,
};
use bytes::Bytes;
use std::{collections::HashMap, time::Instant};
use wanaku_feature_action_policy::{PolicyDecision, PolicyEngine, PolicySnapshot, PolicyState};
use wanaku_feature_evaluator::{
    action::ActionResult,
    outcome::{Allowance, Outcome},
    simulation::{SimulationInput, SimulationOutcome},
};
use wanaku_filters::json_rpc::McpRequestView;
use wanaku_types::{
    audit_redaction::AuditRedactor,
    governance::{EnforcementMode, GovernancePosture},
};

pub(crate) fn simulate(
    deps: &SimulationDeps,
    candidate: &PreparedCandidate,
    action: &SyntheticAction,
) -> Result<SimulationReport, &'static str> {
    simulate_with_limits(deps, candidate, action, None)
}
pub(crate) fn simulate_with_limits(
    deps: &SimulationDeps,
    candidate: &PreparedCandidate,
    action: &SyntheticAction,
    limits: Option<wanaku_feature_evaluator::simulation::SimulationLimits>,
) -> Result<SimulationReport, &'static str> {
    let started = Instant::now();
    let bytes =
        Bytes::from(serde_json::to_vec(&action.request).map_err(|_| "invalid_action_request")?);
    let view = McpRequestView::parse(Some(&bytes)).map_err(|_| "invalid_action_request")?;
    simulate_view(&ViewInput {
        deps,
        candidate,
        action,
        view: &view,
        started,
        limits,
    })
}
struct ViewInput<'a> {
    deps: &'a SimulationDeps,
    candidate: &'a PreparedCandidate,
    action: &'a SyntheticAction,
    view: &'a McpRequestView,
    started: Instant,
    limits: Option<wanaku_feature_evaluator::simulation::SimulationLimits>,
}
fn simulate_view(input: &ViewInput<'_>) -> Result<SimulationReport, &'static str> {
    let ViewInput {
        deps,
        candidate,
        action,
        view,
        started,
        limits,
    } = *input;
    let method = view.method().map_err(|_| "invalid_action_request")?;
    let decision = policy_decision(deps, candidate, action, view)?;
    let posture = deps.governance.resolve(&action.namespace);
    let effective = effective_decision(&posture, &decision);
    let target = target(view, method)?;
    let mut input = ReportInput {
        deps,
        candidate,
        action,
        method,
        target,
        posture,
        stages: vec![policy_stage(&decision, effective)],
        effective: effective.to_owned(),
        conditional: false,
        evaluator: None,
        decision,
        started,
        limits,
    };
    downstream_report(&mut input, view)?;
    redact_report(report(input))
}
fn downstream_report(
    input: &mut ReportInput<'_>,
    view: &McpRequestView,
) -> Result<(), &'static str> {
    let (effective, conditional, evaluator) = evaluate_downstream(
        &Downstream {
            deps: input.deps,
            candidate: input.candidate,
            view,
            namespace: &input.action.namespace,
            target: input.target.as_deref(),
            posture: &input.posture,
            effective: &input.effective,
            limits: input.limits,
        },
        &mut input.stages,
    )?;
    input.effective = effective;
    input.conditional = conditional;
    input.evaluator = evaluator;
    Ok(())
}

fn policy_decision(
    deps: &SimulationDeps,
    candidate: &PreparedCandidate,
    action: &SyntheticAction,
    view: &McpRequestView,
) -> Result<PolicyDecision, &'static str> {
    let method = view.method().map_err(|_| "invalid_action_request")?;
    if !matches!(
        method,
        wanaku_types::TOOLS_CALL | wanaku_types::RESOURCES_READ | wanaku_types::PROMPTS_GET
    ) {
        return Err("unsupported_action_method");
    }
    let context = wanaku_feature_action_policy::filter::action_context(
        method,
        &action.namespace,
        view,
        &deps.registry,
    )
    .map_err(|_| "invalid_action_request")?;
    Ok(match &candidate.policy {
        PolicySnapshot::Valid(p) => PolicyEngine::evaluate(PolicyState::Available(p), &context),
        PolicySnapshot::Unconfigured => PolicyDecision::NoMatch,
        PolicySnapshot::Invalid => PolicyDecision::PolicyInvalid,
    })
}
fn target(view: &McpRequestView, method: &str) -> Result<Option<String>, &'static str> {
    let target = match method {
        wanaku_types::TOOLS_CALL => view
            .tool_call()
            .map_err(|_| "invalid_action_request")?
            .name()
            .to_owned(),
        wanaku_types::RESOURCES_READ => view
            .resource_read()
            .map_err(|_| "invalid_action_request")?
            .uri()
            .to_owned(),
        _ => view
            .prompt_get()
            .map_err(|_| "invalid_action_request")?
            .name()
            .to_owned(),
    };
    Ok(Some(target))
}
fn policy_stage(decision: &PolicyDecision, effective: &str) -> StageDecision {
    let reason = match decision {
        PolicyDecision::ExplicitAllow { .. } => "action_policy_allowed",
        PolicyDecision::ExplicitDeny { .. } => "action_policy_denied",
        PolicyDecision::NoMatch | PolicyDecision::PolicyUnavailable => "governance_no_match",
        PolicyDecision::PolicyInvalid => "action_policy_invalid",
    };
    StageDecision {
        stage: "action_policy".to_owned(),
        decision: effective.to_owned(),
        reason_code: reason.to_owned(),
        matched_ids: decision.details().map_or_else(Vec::new, |d| {
            d.matched_rules()
                .iter()
                .map(|r| r.rule_id().to_owned())
                .collect()
        }),
    }
}
struct Downstream<'a> {
    deps: &'a SimulationDeps,
    candidate: &'a PreparedCandidate,
    view: &'a McpRequestView,
    namespace: &'a str,
    target: Option<&'a str>,
    posture: &'a GovernancePosture,
    effective: &'a str,
    limits: Option<wanaku_feature_evaluator::simulation::SimulationLimits>,
}
fn evaluate_downstream(
    input: &Downstream<'_>,
    stages: &mut Vec<StageDecision>,
) -> Result<(String, bool, Option<Outcome>), &'static str> {
    if input.effective != "allow" || input.posture.mode == EnforcementMode::Disabled {
        return Ok((input.effective.to_owned(), false, None));
    }
    let arguments = arguments(input.view)?;
    let simulation = SimulationInput {
        method: input.view.method().map_err(|_| "invalid_action_request")?,
        namespace: input.namespace,
        target: input.target,
        arguments: &arguments,
    };
    let simulated = execute_evaluator(input, &simulation);
    let outcome = &simulated.outcome;
    let enforce = input.posture.mode == EnforcementMode::Enforce;
    let proposed = outcome.proposed_allowance(input.posture);
    // Simulation cannot know what a skipped external engine or a suppressed
    // host write would change, so enforcement depends on live data.
    let conditional =
        enforce && (proposed == Allowance::Indeterminate || outcome.side_effects_suppressed());
    stages.push(StageDecision {
        stage: "evaluator".to_owned(),
        decision: allowance_label(proposed).to_owned(),
        reason_code: evaluator_reason(&simulated).to_owned(),
        matched_ids: simulated.evaluator.iter().cloned().collect(),
    });
    Ok((
        allowance_label(outcome.effective_allowance(input.posture)).to_owned(),
        conditional,
        Some(simulated.outcome),
    ))
}
fn arguments(view: &McpRequestView) -> Result<HashMap<String, String>, &'static str> {
    Ok(view
        .params()
        .map_err(|_| "invalid_action_request")?
        .get("arguments")
        .and_then(serde_json::Value::as_object)
        .map_or_else(HashMap::new, |args| {
            args.iter()
                .map(|(k, v)| {
                    (
                        k.clone(),
                        v.as_str().map_or_else(|| v.to_string(), str::to_owned),
                    )
                })
                .collect()
        }))
}
const fn allowance_label(allowance: Allowance) -> &'static str {
    match allowance {
        Allowance::Allow => "allow",
        Allowance::Deny => "deny",
        Allowance::Indeterminate => "indeterminate",
    }
}
struct ReportInput<'a> {
    deps: &'a SimulationDeps,
    candidate: &'a PreparedCandidate,
    action: &'a SyntheticAction,
    method: &'a str,
    target: Option<String>,
    posture: GovernancePosture,
    stages: Vec<StageDecision>,
    effective: String,
    conditional: bool,
    evaluator: Option<Outcome>,
    decision: PolicyDecision,
    started: Instant,
    limits: Option<wanaku_feature_evaluator::simulation::SimulationLimits>,
}
fn report(input: ReportInput<'_>) -> SimulationReport {
    let failure_used = failure_used(&input.decision, input.evaluator.as_ref());
    let baseline_used = baseline_used(&input);
    SimulationReport {
        schema_version: "1.0".to_owned(),
        identity: input.candidate.report.identity.clone(),
        stale: crate::candidate::stale(input.deps, &input.candidate.report.identity),
        namespace: input.action.namespace.clone(),
        operation: input.method.to_owned(),
        target: input.target,
        posture: input.posture,
        stages: input.stages,
        decision: input.effective,
        baseline_used,
        failure_used,
        conditional: input.conditional,
        redaction: wanaku_types::audit::RedactionMetadata::default(),
        duration_ms: u64::try_from(input.started.elapsed().as_millis()).unwrap_or(u64::MAX),
    }
}

fn redact_report(report: SimulationReport) -> Result<SimulationReport, &'static str> {
    let mut value = serde_json::to_value(report).map_err(|_| "simulation_report_invalid")?;
    let metadata = AuditRedactor::default().redact(&mut value);
    let mut report: SimulationReport =
        serde_json::from_value(value).map_err(|_| "simulation_report_redaction_limit")?;
    report
        .redaction
        .redacted_fields
        .extend(metadata.redacted_fields);
    report.redaction.payload_truncated |= metadata.payload_truncated;
    report.redaction.payload_captured = false;
    Ok(report)
}

fn effective_decision(posture: &GovernancePosture, decision: &PolicyDecision) -> &'static str {
    if wanaku_feature_action_policy::filter::enforcement_action(posture, decision) == "reject" {
        "deny"
    } else {
        "allow"
    }
}

fn execute_evaluator(
    input: &Downstream<'_>,
    simulation: &SimulationInput<'_>,
) -> SimulationOutcome {
    match input.limits {
        Some(limits) => input.candidate.evaluators.evaluate_with_limits(
            simulation,
            &input.deps.registry,
            limits,
        ),
        None => input
            .candidate
            .evaluators
            .evaluate(simulation, &input.deps.registry),
    }
}

const fn evaluator_reason(simulated: &SimulationOutcome) -> &'static str {
    match &simulated.outcome {
        Outcome::Evaluated {
            side_effects_suppressed: true,
            ..
        } => "evaluator_side_effects_suppressed",
        Outcome::Evaluated { action, .. } => match action {
            ActionResult::SetMetadata(..) => "evaluator_metadata_proposed",
            ActionResult::FilterTools(_) => "evaluator_filter_proposed",
            ActionResult::Warn(_) => "evaluator_warning_proposed",
            _ => simulated.reason_code(),
        },
        _ => simulated.reason_code(),
    }
}

fn baseline_used(input: &ReportInput<'_>) -> bool {
    input.posture.mode != EnforcementMode::Disabled
        && (matches!(
            input.decision,
            PolicyDecision::NoMatch | PolicyDecision::PolicyUnavailable
        ) || input.evaluator.as_ref().is_some_and(Outcome::baseline_used))
}
fn failure_used(decision: &PolicyDecision, evaluator: Option<&Outcome>) -> bool {
    matches!(decision, PolicyDecision::PolicyInvalid)
        || evaluator.is_some_and(Outcome::failure_used)
}
#[cfg(test)]
mod tests {
    use super::*;
    use wanaku_feature_evaluator::{
        engine::EvaluatorExecutionError,
        outcome::{EvaluatorFailure, Skip},
    };

    fn simulated(outcome: Outcome) -> SimulationOutcome {
        SimulationOutcome {
            evaluator: Some("test".to_owned()),
            engine: Some("passthrough"),
            outcome,
        }
    }

    #[test]
    fn execution_failure_keeps_failure_reason_and_fact() {
        let outcome = simulated(Outcome::Failure(EvaluatorFailure::Processor(
            EvaluatorExecutionError::Execution,
        )));
        assert_eq!(evaluator_reason(&outcome), "evaluator_processor_failed");
        assert!(failure_used(
            &PolicyDecision::NoMatch,
            Some(&outcome.outcome)
        ));
        assert!(!outcome.outcome.baseline_used());
    }

    #[test]
    fn typed_facts_do_not_depend_on_reason_codes() {
        for (outcome, failure, baseline) in [
            (Outcome::NoMatch, false, true),
            (
                Outcome::Skipped(Skip::ExternalEngineNotSimulated),
                false,
                false,
            ),
            (Outcome::Unavailable("configuration_invalid"), true, false),
            (
                Outcome::Evaluated {
                    action: ActionResult::Pass,
                    side_effects_suppressed: true,
                },
                false,
                false,
            ),
        ] {
            assert_eq!(
                failure_used(&PolicyDecision::NoMatch, Some(&outcome)),
                failure
            );
            assert_eq!(outcome.baseline_used(), baseline);
        }
        assert!(failure_used(&PolicyDecision::PolicyInvalid, None));
    }

    #[test]
    fn suppressed_effects_report_suppression_before_action() {
        let outcome = simulated(Outcome::Evaluated {
            action: ActionResult::Warn("message".to_owned()),
            side_effects_suppressed: true,
        });
        assert_eq!(
            evaluator_reason(&outcome),
            "evaluator_side_effects_suppressed"
        );
    }
}
