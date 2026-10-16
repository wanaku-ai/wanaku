//! Transport-independent evaluator outcomes and their resolution under a governance posture.
//!
//! The live filter and simulation produce the same [`Outcome`] values. Each consumer serializes
//! reason codes at its own API or audit edge.
use crate::action::ActionResult;
use crate::engine::EvaluatorExecutionError;
use crate::evaluation::EvaluationError;
use wanaku_types::governance::{
    EnforcementMode, FailureBehavior, GovernancePosture, NoMatchBehavior,
};

/// An operational failure that prevented a matched evaluator from proposing an action.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum EvaluatorFailure {
    StateUnavailable,
    RegistryUnavailable,
    InteractionsUnavailable,
    ProcessorUnavailable,
    Engine(EvaluationError),
    Processor(EvaluatorExecutionError),
}

impl EvaluatorFailure {
    /// Stable audit reason code.
    pub const fn reason_code(self) -> &'static str {
        match self {
            Self::StateUnavailable => "evaluator_state_unavailable",
            Self::RegistryUnavailable => "evaluator_registry_unavailable",
            Self::InteractionsUnavailable => "evaluator_interactions_unavailable",
            Self::ProcessorUnavailable => "evaluator_processor_unavailable",
            Self::Engine(error) => error.reason_code(),
            Self::Processor(EvaluatorExecutionError::Instantiation) => {
                "evaluator_processor_instantiation_failed"
            }
            Self::Processor(EvaluatorExecutionError::Execution) => {
                "evaluator_processor_execution_failed"
            }
        }
    }

    /// Reason code without the `evaluator_` prefix.
    pub fn reason(self) -> &'static str {
        let code = self.reason_code();
        code.strip_prefix("evaluator_").unwrap_or(code)
    }
}

/// Why an evaluator did not run.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Skip {
    /// Governance is disabled for the namespace.
    Disabled,
    /// Basic audit does not call external engines.
    ExternalEvaluationDisabled,
    /// Simulation does not call external engines, so the result is unknown.
    ExternalEngineNotSimulated,
}

/// The result of evaluator selection and execution, before the posture applies.
#[derive(Debug, Clone)]
pub enum Outcome {
    /// No evaluator matched. The `no_match` baseline applies.
    NoMatch,
    /// A matched evaluator failed. The `on_failure` behavior applies.
    Failure(EvaluatorFailure),
    /// The active configuration is invalid. This blocks under enforcement
    /// regardless of `on_failure`.
    Unavailable(&'static str),
    /// The evaluator did not run. No action is proposed.
    Skipped(Skip),
    /// The processor proposed an action.
    Evaluated {
        action: ActionResult,
        side_effects_suppressed: bool,
    },
}

/// Whether an outcome lets the request continue.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Allowance {
    Allow,
    Deny,
    /// The outcome is unknown because the evaluator did not run.
    Indeterminate,
}

const fn deny_if(deny: bool) -> Allowance {
    if deny {
        Allowance::Deny
    } else {
        Allowance::Allow
    }
}

impl Outcome {
    /// Allowance that applies when the posture enforces this outcome.
    pub fn proposed_allowance(&self, posture: &GovernancePosture) -> Allowance {
        match self {
            Self::Unavailable(_) => Allowance::Deny,
            Self::NoMatch => deny_if(posture.no_match == NoMatchBehavior::Deny),
            Self::Failure(_) => deny_if(posture.on_failure == FailureBehavior::Deny),
            Self::Skipped(_) => Allowance::Indeterminate,
            Self::Evaluated { action, .. } => deny_if(matches!(
                action,
                ActionResult::Block(_) | ActionResult::RejectMalformed(_)
            )),
        }
    }

    /// Allowance after the enforcement mode applies. Only `enforce` can deny.
    pub fn effective_allowance(&self, posture: &GovernancePosture) -> Allowance {
        if posture.mode == EnforcementMode::Enforce {
            self.proposed_allowance(posture)
        } else {
            Allowance::Allow
        }
    }

    /// Action label that applies when the posture enforces this outcome.
    pub fn proposed_action(&self, posture: &GovernancePosture) -> Option<&'static str> {
        match self {
            Self::Skipped(_) => None,
            Self::Evaluated { action, .. } => Some(action_label(action)),
            Self::NoMatch | Self::Failure(_) | Self::Unavailable(_) => {
                Some(match self.proposed_allowance(posture) {
                    Allowance::Deny => "block",
                    Allowance::Allow | Allowance::Indeterminate => "pass",
                })
            }
        }
    }

    /// Action label after the enforcement mode applies.
    pub fn effective_action(&self, posture: &GovernancePosture) -> &'static str {
        if posture.mode == EnforcementMode::Enforce {
            self.proposed_action(posture).unwrap_or("pass")
        } else {
            "pass"
        }
    }

    /// Reason without the `evaluator_` prefix.
    pub fn reason(&self) -> &'static str {
        match self {
            Self::NoMatch => "no_match",
            Self::Failure(failure) => failure.reason(),
            Self::Unavailable(reason) => reason,
            Self::Skipped(Skip::Disabled) => "disabled",
            Self::Skipped(Skip::ExternalEvaluationDisabled) => "external_evaluation_disabled",
            Self::Skipped(Skip::ExternalEngineNotSimulated) => "external_engine_skipped",
            Self::Evaluated { .. } => "evaluated",
        }
    }

    /// The `no_match` baseline decided the outcome.
    pub const fn baseline_used(&self) -> bool {
        matches!(self, Self::NoMatch)
    }

    /// Failure handling decided the outcome.
    pub const fn failure_used(&self) -> bool {
        matches!(self, Self::Failure(_) | Self::Unavailable(_))
    }

    /// The processor proposed an action, possibly with suppressed host writes.
    pub const fn action(&self) -> Option<&ActionResult> {
        match self {
            Self::Evaluated { action, .. } => Some(action),
            _ => None,
        }
    }

    /// The host suppressed at least one processor write.
    pub const fn side_effects_suppressed(&self) -> bool {
        matches!(
            self,
            Self::Evaluated {
                side_effects_suppressed: true,
                ..
            }
        )
    }
}

pub const fn action_label(result: &ActionResult) -> &'static str {
    match result {
        ActionResult::Pass => "pass",
        ActionResult::Block(_) => "block",
        ActionResult::RejectMalformed(_) => "reject_malformed",
        ActionResult::Warn(_) => "warn",
        ActionResult::FilterTools(_) => "filter_tools",
        ActionResult::SetMetadata(_, _) => "set_metadata",
    }
}

#[cfg(test)]
pub(crate) mod fixtures {
    use super::*;
    use wanaku_types::governance::AuditLevel;

    /// Every posture combination that changes outcome resolution.
    pub(crate) fn postures() -> Vec<GovernancePosture> {
        let mut postures = Vec::new();
        for (mode, audit_level) in [
            (EnforcementMode::Enforce, AuditLevel::Full),
            (EnforcementMode::Audit, AuditLevel::Full),
            (EnforcementMode::Audit, AuditLevel::Basic),
            (EnforcementMode::Disabled, AuditLevel::Full),
        ] {
            for no_match in [NoMatchBehavior::Allow, NoMatchBehavior::Deny] {
                for on_failure in [FailureBehavior::Allow, FailureBehavior::Deny] {
                    postures.push(GovernancePosture {
                        mode,
                        audit_level,
                        no_match,
                        on_failure,
                        ..GovernancePosture::default()
                    });
                }
            }
        }
        postures
    }

    /// Expected allowance when the posture enforces the outcome.
    pub(crate) struct Case {
        pub name: &'static str,
        pub outcome: Outcome,
        pub proposed: fn(&GovernancePosture) -> Allowance,
    }

    const fn allow(_: &GovernancePosture) -> Allowance {
        Allowance::Allow
    }
    const fn deny(_: &GovernancePosture) -> Allowance {
        Allowance::Deny
    }
    const fn indeterminate(_: &GovernancePosture) -> Allowance {
        Allowance::Indeterminate
    }
    fn baseline(posture: &GovernancePosture) -> Allowance {
        deny_if(posture.no_match == NoMatchBehavior::Deny)
    }
    fn failure(posture: &GovernancePosture) -> Allowance {
        deny_if(posture.on_failure == FailureBehavior::Deny)
    }

    const fn evaluated(action: ActionResult, side_effects_suppressed: bool) -> Outcome {
        Outcome::Evaluated {
            action,
            side_effects_suppressed,
        }
    }

    #[expect(
        clippy::too_many_lines,
        reason = "one shared table lists every outcome case"
    )]
    pub(crate) fn cases() -> Vec<Case> {
        let case = |name, outcome, proposed| Case {
            name,
            outcome,
            proposed,
        };
        vec![
            case("pass", evaluated(ActionResult::Pass, false), allow),
            case(
                "block",
                evaluated(ActionResult::Block("x".into()), false),
                deny,
            ),
            case(
                "warn",
                evaluated(ActionResult::Warn("x".into()), false),
                allow,
            ),
            case(
                "malformed",
                evaluated(ActionResult::RejectMalformed("x".into()), false),
                deny,
            ),
            case(
                "filter_tools",
                evaluated(ActionResult::FilterTools(vec![]), false),
                allow,
            ),
            case(
                "set_metadata",
                evaluated(ActionResult::SetMetadata("k".into(), "v".into()), false),
                allow,
            ),
            case(
                "suppressed_pass",
                evaluated(ActionResult::Pass, true),
                allow,
            ),
            case(
                "suppressed_block",
                evaluated(ActionResult::Block("x".into()), true),
                deny,
            ),
            case("no_match", Outcome::NoMatch, baseline),
            case(
                "state_unavailable",
                Outcome::Failure(EvaluatorFailure::StateUnavailable),
                failure,
            ),
            case(
                "engine_failure",
                Outcome::Failure(EvaluatorFailure::Engine(EvaluationError::Remote)),
                failure,
            ),
            case(
                "processor_failure",
                Outcome::Failure(EvaluatorFailure::Processor(
                    EvaluatorExecutionError::Execution,
                )),
                failure,
            ),
            case(
                "invalid_configuration",
                Outcome::Unavailable("configuration_invalid"),
                deny,
            ),
            case(
                "external_evaluation_disabled",
                Outcome::Skipped(Skip::ExternalEvaluationDisabled),
                indeterminate,
            ),
            case(
                "external_engine_not_simulated",
                Outcome::Skipped(Skip::ExternalEngineNotSimulated),
                indeterminate,
            ),
        ]
    }
}

#[cfg(test)]
mod tests {
    use super::fixtures::{cases, postures};
    use super::*;

    #[test]
    #[expect(
        clippy::too_many_lines,
        reason = "posture matrix checks allowance and action labels together"
    )]
    fn resolver_matches_fixture_table_for_every_posture() {
        for posture in postures() {
            for case in cases() {
                let proposed = (case.proposed)(&posture);
                let context = format!("{} {posture:?}", case.name);
                assert_eq!(
                    case.outcome.proposed_allowance(&posture),
                    proposed,
                    "{context}"
                );
                let effective = if posture.mode == EnforcementMode::Enforce {
                    proposed
                } else {
                    Allowance::Allow
                };
                assert_eq!(
                    case.outcome.effective_allowance(&posture),
                    effective,
                    "{context}"
                );
                assert_eq!(
                    case.outcome.effective_action(&posture) == "block"
                        || case.outcome.effective_action(&posture) == "reject_malformed",
                    effective == Allowance::Deny,
                    "{context}"
                );
                assert_eq!(
                    case.outcome.proposed_action(&posture).is_none(),
                    proposed == Allowance::Indeterminate,
                    "{context}"
                );
            }
        }
    }

    #[test]
    fn unavailable_configuration_blocks_even_when_failures_allow() {
        let posture = GovernancePosture {
            on_failure: FailureBehavior::Allow,
            no_match: NoMatchBehavior::Allow,
            ..GovernancePosture::default()
        };
        let unavailable = Outcome::Unavailable("configuration_invalid");
        let failure = Outcome::Failure(EvaluatorFailure::StateUnavailable);
        assert_eq!(unavailable.effective_allowance(&posture), Allowance::Deny);
        assert_eq!(failure.effective_allowance(&posture), Allowance::Allow);
        assert!(unavailable.failure_used() && failure.failure_used());
    }

    #[test]
    #[expect(
        clippy::too_many_lines,
        reason = "fact and reason tables stay together"
    )]
    fn typed_facts_and_reasons_are_stable() {
        for case in cases() {
            assert_eq!(
                case.outcome.baseline_used(),
                case.name == "no_match",
                "{}",
                case.name
            );
            assert_eq!(
                case.outcome.failure_used(),
                matches!(
                    case.name,
                    "state_unavailable"
                        | "engine_failure"
                        | "processor_failure"
                        | "invalid_configuration"
                ),
                "{}",
                case.name
            );
            assert_eq!(
                case.outcome.side_effects_suppressed(),
                case.name.starts_with("suppressed_"),
                "{}",
                case.name
            );
        }
        for (failure, reason) in [
            (EvaluatorFailure::StateUnavailable, "state_unavailable"),
            (
                EvaluatorFailure::RegistryUnavailable,
                "registry_unavailable",
            ),
            (
                EvaluatorFailure::InteractionsUnavailable,
                "interactions_unavailable",
            ),
            (
                EvaluatorFailure::ProcessorUnavailable,
                "processor_unavailable",
            ),
            (
                EvaluatorFailure::Engine(EvaluationError::MissingConnection),
                "connection_unavailable",
            ),
            (
                EvaluatorFailure::Processor(EvaluatorExecutionError::Instantiation),
                "processor_instantiation_failed",
            ),
            (
                EvaluatorFailure::Processor(EvaluatorExecutionError::Execution),
                "processor_execution_failed",
            ),
        ] {
            assert_eq!(Outcome::Failure(failure).reason(), reason);
        }
        assert_eq!(Outcome::Skipped(Skip::Disabled).reason(), "disabled");
        assert_eq!(
            Outcome::Skipped(Skip::ExternalEvaluationDisabled).reason(),
            "external_evaluation_disabled"
        );
    }
}
