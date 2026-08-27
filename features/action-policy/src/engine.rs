use std::collections::BTreeMap;

use serde_json::Value;

use crate::{CompiledPolicy, CompiledRule, Effect, TargetType};

/// The safe message used when a deny rule does not configure one.
pub const DEFAULT_DENY_MESSAGE: &str = "The requested action is not allowed.";
/// The stable reason code used when a deny rule does not configure one.
pub const DEFAULT_DENY_REASON_CODE: &str = "action_policy_denied";

/// Trusted identity data for future actor selectors.
///
/// Only a trusted proxy identity component must construct this value. Action
/// request headers and parameters must not populate it directly.
#[derive(Debug, Clone, PartialEq)]
pub struct ActorContext {
    principal_id: String,
    issuer: String,
    attributes: BTreeMap<String, Value>,
}

impl ActorContext {
    #[must_use]
    pub fn new(
        principal_id: impl Into<String>,
        issuer: impl Into<String>,
        attributes: BTreeMap<String, Value>,
    ) -> Self {
        Self {
            principal_id: principal_id.into(),
            issuer: issuer.into(),
            attributes,
        }
    }

    #[must_use]
    pub fn principal_id(&self) -> &str {
        &self.principal_id
    }

    #[must_use]
    pub fn issuer(&self) -> &str {
        &self.issuer
    }

    #[must_use]
    pub const fn attributes(&self) -> &BTreeMap<String, Value> {
        &self.attributes
    }
}

/// A transport-neutral action presented to the policy engine.
#[derive(Debug, Clone, PartialEq)]
pub struct ActionContext {
    namespace: String,
    operation: String,
    target_type: TargetType,
    target_name: Option<String>,
    labels: BTreeMap<String, String>,
    uri: Option<String>,
    input: Value,
    actor: Option<ActorContext>,
}

impl ActionContext {
    #[must_use]
    pub fn new(
        namespace: impl Into<String>,
        operation: impl Into<String>,
        target_type: TargetType,
        input: Value,
    ) -> Self {
        Self {
            namespace: namespace.into(),
            operation: operation.into(),
            target_type,
            target_name: None,
            labels: BTreeMap::new(),
            uri: None,
            input,
            actor: None,
        }
    }

    #[must_use]
    pub fn with_target_name(mut self, target_name: impl Into<String>) -> Self {
        self.target_name = Some(target_name.into());
        self
    }

    #[must_use]
    pub fn with_labels(mut self, labels: BTreeMap<String, String>) -> Self {
        self.labels = labels;
        self
    }

    #[must_use]
    pub fn with_uri(mut self, uri: impl Into<String>) -> Self {
        self.uri = Some(uri.into());
        self
    }

    #[must_use]
    pub fn with_actor(mut self, actor: ActorContext) -> Self {
        self.actor = Some(actor);
        self
    }

    #[must_use]
    pub const fn actor(&self) -> Option<&ActorContext> {
        self.actor.as_ref()
    }
}

/// Availability of a compiled policy snapshot.
#[derive(Clone, Copy)]
pub enum PolicyState<'a> {
    Available(&'a CompiledPolicy),
    Unavailable,
    Invalid,
}

/// A matching rule retained for audit and authorized inspection.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct MatchedRule {
    rule_id: String,
    effect: Effect,
    reason_code: Option<String>,
}

impl MatchedRule {
    #[must_use]
    pub fn rule_id(&self) -> &str {
        &self.rule_id
    }

    #[must_use]
    pub const fn effect(&self) -> Effect {
        self.effect
    }

    #[must_use]
    pub fn reason_code(&self) -> Option<&str> {
        self.reason_code.as_deref()
    }
}

/// Internal rule evidence for an explicit decision.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DecisionDetails {
    matched_rules: Vec<MatchedRule>,
    contributing_rules: Vec<MatchedRule>,
}

/// The single caller-safe reason selected for an explicit denial.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct PrimaryDenyReason {
    reason_code: String,
    message: String,
}

impl PrimaryDenyReason {
    #[must_use]
    pub fn reason_code(&self) -> &str {
        &self.reason_code
    }

    #[must_use]
    pub fn message(&self) -> &str {
        &self.message
    }
}

impl DecisionDetails {
    #[must_use]
    pub fn matched_rules(&self) -> &[MatchedRule] {
        &self.matched_rules
    }

    #[must_use]
    pub fn contributing_rules(&self) -> &[MatchedRule] {
        &self.contributing_rules
    }
}

/// A policy result. Baseline behavior is deliberately not applied here.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum PolicyDecision {
    /// One or more allow rules matched and no deny rule matched. The caller
    /// must continue through downstream governance filters.
    ExplicitAllow {
        details: DecisionDetails,
    },
    /// At least one deny rule matched.
    ExplicitDeny {
        reason: PrimaryDenyReason,
        details: DecisionDetails,
    },
    NoMatch,
    PolicyUnavailable,
    PolicyInvalid,
}

impl PolicyDecision {
    #[must_use]
    pub const fn details(&self) -> Option<&DecisionDetails> {
        match self {
            Self::ExplicitAllow { details } | Self::ExplicitDeny { details, .. } => Some(details),
            Self::NoMatch | Self::PolicyUnavailable | Self::PolicyInvalid => None,
        }
    }

    #[must_use]
    pub fn deny_message(&self) -> Option<&str> {
        match self {
            Self::ExplicitDeny { reason, .. } => Some(reason.message()),
            _ => None,
        }
    }

    #[must_use]
    pub fn deny_reason_code(&self) -> Option<&str> {
        match self {
            Self::ExplicitDeny { reason, .. } => Some(reason.reason_code()),
            _ => None,
        }
    }
}

pub struct PolicyEngine;

impl PolicyEngine {
    /// Evaluate every rule in one immutable policy snapshot.
    #[must_use]
    pub fn evaluate(state: PolicyState<'_>, context: &ActionContext) -> PolicyDecision {
        let policy = match state {
            PolicyState::Available(policy) => policy,
            PolicyState::Unavailable => return PolicyDecision::PolicyUnavailable,
            PolicyState::Invalid => return PolicyDecision::PolicyInvalid,
        };

        evaluate_policy(policy, context)
    }
}

fn evaluate_policy(policy: &CompiledPolicy, context: &ActionContext) -> PolicyDecision {
    let mut matches: Vec<&CompiledRule> = policy
        .rules()
        .iter()
        .filter(|rule| rule_matches(rule, context))
        .collect();
    matches.sort_by(|left, right| left.rule().id.cmp(&right.rule().id));

    if matches.is_empty() {
        return PolicyDecision::NoMatch;
    }

    let deny_rules: Vec<&CompiledRule> = matches
        .iter()
        .copied()
        .filter(|rule| rule.rule().effect == Effect::Deny)
        .collect();
    if let Some(primary) = deny_rules.first() {
        return PolicyDecision::ExplicitDeny {
            reason: primary_deny_reason(primary),
            details: decision_details(&matches, &deny_rules),
        };
    }

    PolicyDecision::ExplicitAllow {
        details: decision_details(&matches, &matches),
    }
}

fn primary_deny_reason(rule: &CompiledRule) -> PrimaryDenyReason {
    PrimaryDenyReason {
        reason_code: rule
            .rule()
            .reason_code
            .clone()
            .unwrap_or_else(|| DEFAULT_DENY_REASON_CODE.to_owned()),
        message: rule
            .rule()
            .message
            .clone()
            .unwrap_or_else(|| DEFAULT_DENY_MESSAGE.to_owned()),
    }
}

fn decision_details(matches: &[&CompiledRule], contributors: &[&CompiledRule]) -> DecisionDetails {
    DecisionDetails {
        matched_rules: matches.iter().map(|rule| rule_evidence(rule)).collect(),
        contributing_rules: contributors
            .iter()
            .map(|rule| rule_evidence(rule))
            .collect(),
    }
}

fn rule_evidence(rule: &CompiledRule) -> MatchedRule {
    MatchedRule {
        rule_id: rule.rule().id.clone(),
        effect: rule.rule().effect,
        reason_code: rule.rule().reason_code.clone(),
    }
}

fn rule_matches(rule: &CompiledRule, context: &ActionContext) -> bool {
    basic_selectors_match(rule, context)
        && target_selectors_match(rule, context)
        && labels_match(rule, context)
        && rule
            .predicates()
            .iter()
            .all(|predicate| predicate.matches(&context.input))
}

fn basic_selectors_match(rule: &CompiledRule, context: &ActionContext) -> bool {
    let selectors = &rule.rule().selectors;
    selectors
        .namespace
        .as_ref()
        .is_none_or(|namespace| namespace == &context.namespace)
        && selectors
            .operation
            .as_ref()
            .is_none_or(|operation| operation == &context.operation)
        && selectors
            .target_type
            .is_none_or(|target_type| target_type == context.target_type)
}

fn target_selectors_match(rule: &CompiledRule, context: &ActionContext) -> bool {
    rule.target_name().is_none_or(|matcher| {
        context
            .target_name
            .as_deref()
            .is_some_and(|name| matcher.matches(name))
    }) && rule.uri().is_none_or(|matcher| {
        context
            .uri
            .as_deref()
            .is_some_and(|uri| matcher.matches(uri))
    })
}

fn labels_match(rule: &CompiledRule, context: &ActionContext) -> bool {
    rule.rule()
        .selectors
        .labels
        .iter()
        .all(|(key, value)| context.labels.get(key) == Some(value))
}
