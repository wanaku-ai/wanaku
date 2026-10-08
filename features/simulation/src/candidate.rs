use std::collections::BTreeSet;
use std::sync::Arc;

use serde_json::Value;
use wanaku_feature_action_policy::{ActionPolicy, CompiledPolicy, Effect, PolicySnapshot};
use wanaku_feature_evaluator::{config::EvaluatorDef, simulation::PreparedEvaluators};
use wanaku_types::governance::{EnforcementMode, FailureBehavior, NoMatchBehavior};
use wanaku_types::registry::{
    BindingRegistry, ForwardRegistry, NamespaceRegistry, PromptRegistry, ResourceRegistry,
    ToolRegistry,
};

use crate::{
    SimulationDeps,
    api::{CandidateSelection, Diagnostic, PolicyIdentity, ValidationReport},
};

pub(crate) struct PreparedCandidate {
    pub report: ValidationReport,
    pub policy: PolicySnapshot,
    pub evaluators: PreparedEvaluators,
}

struct CapturedRevisions {
    policy: Option<u64>,
    evaluators: Option<u64>,
}

#[cfg(test)]
pub(crate) fn prepare(
    deps: &SimulationDeps,
    selection: &CandidateSelection,
) -> Result<PreparedCandidate, Box<ValidationReport>> {
    prepare_limited(deps, selection, &crate::SimulationLimits::default())
}

pub(crate) fn prepare_limited(
    deps: &SimulationDeps,
    selection: &CandidateSelection,
    limits: &crate::SimulationLimits,
) -> Result<PreparedCandidate, Box<ValidationReport>> {
    let captured = CapturedRevisions {
        policy: deps.policy.revision_store().active_revision_id(),
        evaluators: deps.evaluators.active_config().revision_id,
    };
    let mut diagnostics = Vec::new();
    let policy = select_policy(deps, selection, &captured, &mut diagnostics);
    let defs = select_evaluators(deps, selection, &captured, &mut diagnostics);
    let (within_rules, within_evaluators) =
        check_limits(policy.as_ref(), &defs, limits, &mut diagnostics);
    let compiled = policy
        .as_ref()
        .filter(|_| within_rules)
        .and_then(|policy| validate_policy(deps, policy, &mut diagnostics));
    let evaluators = prepare_evaluators(deps, &defs, within_evaluators);
    record_evaluator_issues(&evaluators, &mut diagnostics);
    validate_references(deps, &mut diagnostics);
    validate_evaluator_namespaces(deps, &defs, &mut diagnostics);
    let identity = identity(selection, &captured, policy.as_ref(), &defs);
    let report = build_report(deps, identity, diagnostics);
    match evaluators {
        Ok(evaluators) if report.valid => Ok(PreparedCandidate {
            report,
            policy: compiled.map_or(PolicySnapshot::Unconfigured, |p| {
                PolicySnapshot::Valid(Arc::new(p))
            }),
            evaluators,
        }),
        _ => Err(Box::new(report)),
    }
}

fn identity(
    selection: &CandidateSelection,
    captured: &CapturedRevisions,
    policy: Option<&ActionPolicy>,
    defs: &[EvaluatorDef],
) -> PolicyIdentity {
    PolicyIdentity {
        policy_revision: selection.policy_revision.or_else(|| {
            (!policy_document_selected(selection))
                .then_some(captured.policy)
                .flatten()
        }),
        evaluator_revision: selection.evaluator_revision.or_else(|| {
            (!evaluator_document_selected(selection))
                .then_some(captured.evaluators)
                .flatten()
        }),
        policy_checksum: policy.and_then(wanaku_feature_action_policy::revision::policy_checksum),
        evaluator_checksum: wanaku_feature_evaluator::revision::config_checksum(defs),
        base_policy_revision: Some(
            selection
                .base_policy_revision
                .unwrap_or(captured.policy.unwrap_or(0)),
        ),
        base_evaluator_revision: Some(
            selection
                .base_evaluator_revision
                .unwrap_or(captured.evaluators.unwrap_or(0)),
        ),
    }
}

pub(crate) fn stale(deps: &SimulationDeps, identity: &PolicyIdentity) -> bool {
    identity.base_policy_revision.unwrap_or(0)
        != deps
            .policy
            .revision_store()
            .active_revision_id()
            .unwrap_or(0)
        || identity.base_evaluator_revision.unwrap_or(0)
            != deps
                .evaluators
                .revision_store()
                .active_revision_id()
                .unwrap_or(0)
}

const fn policy_document_selected(s: &CandidateSelection) -> bool {
    s.inline_policy.is_some() || s.policy_patch.is_some()
}
const fn evaluator_document_selected(s: &CandidateSelection) -> bool {
    s.inline_evaluators.is_some() || s.evaluator_patch.is_some()
}

fn select_policy(
    deps: &SimulationDeps,
    s: &CandidateSelection,
    captured: &CapturedRevisions,
    issues: &mut Vec<Diagnostic>,
) -> Option<ActionPolicy> {
    let active = selected_policy(deps, s, captured, issues);
    let base = s.base_policy_revision.and_then(|id| match id {
        0 => Some(serde_json::json!({"rules": []})),
        _ => deps
            .policy
            .revision_store()
            .get_revision(id)
            .and_then(|r| serde_json::to_value(r.policy).ok()),
    });
    match select_document(
        DocumentSelection {
            inline: s.inline_policy.as_ref(),
            patch: s.policy_patch.as_ref(),
            base,
            revision: s.base_policy_revision,
            kind: "policy",
        },
        issues,
    ) {
        Some(value) => deserialize_document(value, "policy", issues),
        None => active,
    }
}

fn select_evaluators(
    deps: &SimulationDeps,
    s: &CandidateSelection,
    captured: &CapturedRevisions,
    issues: &mut Vec<Diagnostic>,
) -> Vec<EvaluatorDef> {
    let defs = selected_evaluators(deps, s, captured, issues);
    let base = s.base_evaluator_revision.and_then(|id| match id {
        0 => Some(serde_json::json!([])),
        _ => deps
            .evaluators
            .revision_store()
            .get_revision(id)
            .and_then(|r| serde_json::to_value(r.evaluators).ok()),
    });
    match select_document(
        DocumentSelection {
            inline: s.inline_evaluators.as_ref(),
            patch: s.evaluator_patch.as_ref(),
            base,
            revision: s.base_evaluator_revision,
            kind: "evaluators",
        },
        issues,
    ) {
        Some(value) => deserialize_document(value, "evaluator", issues).unwrap_or_default(),
        None => defs,
    }
}

struct DocumentSelection<'a> {
    inline: Option<&'a Value>,
    patch: Option<&'a Value>,
    base: Option<Value>,
    revision: Option<u64>,
    kind: &'a str,
}
fn select_document(s: DocumentSelection<'_>, issues: &mut Vec<Diagnostic>) -> Option<Value> {
    let path = format!("candidate/{}", s.kind);
    if s.inline.is_some() && s.patch.is_some() {
        error(issues, "candidate_sources_conflict", path.clone());
    }
    if (s.inline.is_some() || s.patch.is_some()) && s.revision.is_none() {
        error(issues, "candidate_base_revision_required", path.clone());
    }
    if s.revision.is_some() && s.base.is_none() {
        error(issues, "candidate_base_revision_not_found", path);
    }
    match (s.inline, s.patch) {
        (Some(inline), _) => Some(inline.clone()),
        (_, Some(patch)) => {
            let mut base = s.base.unwrap_or(Value::Null);
            merge_patch(&mut base, patch);
            Some(base)
        }
        _ => None,
    }
}
fn merge_patch(target: &mut Value, patch: &Value) {
    match patch {
        Value::Object(fields) => {
            if !target.is_object() {
                *target = serde_json::json!({});
            }
            if let Some(object) = target.as_object_mut() {
                for (key, value) in fields {
                    if value.is_null() {
                        object.remove(key);
                    } else {
                        merge_patch(object.entry(key).or_insert(Value::Null), value);
                    }
                }
            }
        }
        _ => *target = patch.clone(),
    }
}

fn validate_policy(
    deps: &SimulationDeps,
    policy: &ActionPolicy,
    issues: &mut Vec<Diagnostic>,
) -> Option<CompiledPolicy> {
    let mut ids = BTreeSet::new();
    for (index, rule) in policy.rules.iter().enumerate() {
        let path = format!("rules/{index}");
        if !ids.insert(&rule.id) {
            error(issues, "rule_id_duplicate", path.clone());
        }
        if (ActionPolicy {
            rules: vec![rule.clone()],
        })
        .compile()
        .is_err()
        {
            error(issues, "rule_invalid", path.clone());
        }
        validate_predicates(rule, &path, issues);
        validate_selectors(deps, rule, &path, issues);
        if rule.effect == Effect::Allow
            && policy.rules.iter().any(|deny| {
                deny.effect == Effect::Deny
                    && deny.selectors == rule.selectors
                    && deny.predicates == rule.predicates
            })
        {
            warning(issues, "rule_shadowed_by_deny", path);
        }
    }
    policy.clone().compile().ok()
}

fn validate_selectors(
    deps: &SimulationDeps,
    rule: &wanaku_feature_action_policy::Rule,
    path: &str,
    issues: &mut Vec<Diagnostic>,
) {
    if let Some(ns) = &rule.selectors.namespace
        && deps.registry.get_namespace(ns).is_none()
    {
        error(issues, "namespace_unavailable", path.to_owned());
    }
    if rule
        .selectors
        .target_name
        .as_ref()
        .is_some_and(|m| m.value.contains('*'))
    {
        warning(issues, "broad_wildcard", path.to_owned());
    }
    let mut selector_only = rule.clone();
    selector_only.predicates.clear();
    if let Ok(policy) = (ActionPolicy {
        rules: vec![selector_only],
    })
    .compile()
        && !matches_inventory(deps, &policy)
    {
        warning(issues, "rule_matches_no_capability", path.to_owned());
    }
    if !rule.selectors.labels.is_empty() && !inventory_has_labels(deps, &rule.selectors.labels) {
        warning(issues, "registry_labels_unavailable", path.to_owned());
    }
}

fn matches_inventory(deps: &SimulationDeps, policy: &CompiledPolicy) -> bool {
    use wanaku_feature_action_policy::{
        ActionContext, PolicyDecision, PolicyEngine, PolicyState, TargetType,
    };
    let mut contexts = Vec::new();
    tool_contexts(deps, &mut contexts);
    resource_contexts(deps, &mut contexts);
    for prompt in deps.registry.list_prompts() {
        contexts.push(
            ActionContext::new(
                prompt.namespace.as_deref().unwrap_or("default"),
                wanaku_types::PROMPTS_GET,
                TargetType::Prompt,
                Value::Null,
            )
            .with_target_name(prompt.name),
        );
    }
    contexts.iter().any(|context| {
        !matches!(
            PolicyEngine::evaluate(PolicyState::Available(policy), context),
            PolicyDecision::NoMatch
        )
    })
}

fn inventory_has_labels(
    deps: &SimulationDeps,
    labels: &std::collections::BTreeMap<String, String>,
) -> bool {
    deps.registry
        .list_tools()
        .iter()
        .any(|entry| labels.iter().all(|(k, v)| entry.labels.get(k) == Some(v)))
        || deps
            .registry
            .list_resources()
            .iter()
            .any(|entry| labels.iter().all(|(k, v)| entry.labels.get(k) == Some(v)))
}

fn validate_evaluator_namespaces(
    deps: &SimulationDeps,
    defs: &[EvaluatorDef],
    issues: &mut Vec<Diagnostic>,
) {
    for (index, definition) in defs.iter().enumerate() {
        if definition
            .trigger
            .namespace
            .as_ref()
            .is_some_and(|ns| deps.registry.get_namespace(ns).is_none())
        {
            error(
                issues,
                "namespace_unavailable",
                format!("evaluators/{index}/trigger/namespace"),
            );
        }
    }
}

fn validate_references(deps: &SimulationDeps, issues: &mut Vec<Diagnostic>) {
    if deps.governance.validate().is_err() {
        error(
            issues,
            "governance_posture_invalid",
            "governance".to_owned(),
        );
    }
    validate_posture(&deps.governance.default, "governance", issues);
    for namespace in deps.governance.namespaces.keys() {
        if deps.registry.get_namespace(namespace).is_none() {
            warning(
                issues,
                "governance_namespace_unavailable",
                "governance/namespaces".to_owned(),
            );
        }
        validate_posture(
            &deps.governance.resolve(namespace),
            "governance/namespaces",
            issues,
        );
    }
    validate_forward_references(deps, issues);
}

fn validate_posture(
    posture: &wanaku_types::governance::GovernancePosture,
    path: &str,
    issues: &mut Vec<Diagnostic>,
) {
    if posture.no_match == NoMatchBehavior::Allow {
        warning(issues, "permissive_no_match", path.to_owned());
    }
    if posture.on_failure == FailureBehavior::Allow {
        warning(issues, "fail_open", path.to_owned());
    }
    if posture.mode == EnforcementMode::Disabled {
        warning(issues, "protected_scope_disabled", path.to_owned());
    }
}

pub(crate) fn error(issues: &mut Vec<Diagnostic>, code: &str, path: String) {
    issues.push(Diagnostic {
        severity: "error".to_owned(),
        reason_code: code.to_owned(),
        path,
    });
}
fn warning(issues: &mut Vec<Diagnostic>, code: &str, path: String) {
    issues.push(Diagnostic {
        severity: "warning".to_owned(),
        reason_code: code.to_owned(),
        path,
    });
}

fn check_limits(
    policy: Option<&ActionPolicy>,
    defs: &[EvaluatorDef],
    limits: &crate::SimulationLimits,
    diagnostics: &mut Vec<Diagnostic>,
) -> (bool, bool) {
    let within_rules = policy.is_none_or(|p| p.rules.len() <= limits.max_rules);
    let within_evaluators = defs.len() <= limits.max_evaluators;
    if !within_rules {
        error(
            diagnostics,
            "policy_rule_limit",
            "candidate/policy".to_owned(),
        );
    }
    if !within_evaluators {
        error(
            diagnostics,
            "evaluator_count_limit",
            "candidate/evaluators".to_owned(),
        );
    }
    (within_rules, within_evaluators)
}

fn build_report(
    deps: &SimulationDeps,
    identity: PolicyIdentity,
    mut diagnostics: Vec<Diagnostic>,
) -> ValidationReport {
    diagnostics.sort_by(|a, b| {
        (&a.path, &a.reason_code, &a.severity).cmp(&(&b.path, &b.reason_code, &b.severity))
    });
    diagnostics.dedup_by(|a, b| {
        a.path == b.path && a.reason_code == b.reason_code && a.severity == b.severity
    });
    ValidationReport {
        schema_version: "1.0".to_owned(),
        valid: !diagnostics.iter().any(|issue| issue.severity == "error"),
        stale: stale(deps, &identity),
        identity,
        diagnostics,
    }
}

fn validate_predicates(
    rule: &wanaku_feature_action_policy::Rule,
    path: &str,
    issues: &mut Vec<Diagnostic>,
) {
    for (predicate_index, predicate) in rule.predicates.iter().enumerate() {
        let mut isolated = rule.clone();
        isolated.predicates = vec![predicate.clone()];
        if (ActionPolicy {
            rules: vec![isolated],
        })
        .compile()
        .is_err()
        {
            error(
                issues,
                "predicate_invalid",
                format!("{path}/predicates/{predicate_index}"),
            );
        }
    }
}

fn validate_forward_references(deps: &SimulationDeps, issues: &mut Vec<Diagnostic>) {
    for forward in deps.registry.list_forwards() {
        for (purpose, id) in &forward.credential_bindings {
            match deps.registry.get_binding(id) {
                None => error(
                    issues,
                    "credential_binding_unavailable",
                    "registry/forwards".to_owned(),
                ),
                Some(binding) => {
                    if binding_incompatible(&binding, &forward, *purpose) {
                        error(
                            issues,
                            "credential_binding_incompatible",
                            "registry/forwards".to_owned(),
                        );
                    }
                }
            }
        }
        if !forward.available {
            warning(
                issues,
                "forward_unavailable",
                "registry/forwards".to_owned(),
            );
        }
    }
}

fn record_evaluator_issues(
    evaluators: &Result<
        PreparedEvaluators,
        Vec<wanaku_feature_evaluator::simulation::ValidationIssue>,
    >,
    diagnostics: &mut Vec<Diagnostic>,
) {
    if let Err(issues) = &evaluators {
        for issue in issues {
            error(
                diagnostics,
                issue.reason_code,
                format!("evaluators/{}", issue.evaluator_index),
            );
        }
    }
}

fn selected_policy(
    deps: &SimulationDeps,
    s: &CandidateSelection,
    captured: &CapturedRevisions,
    issues: &mut Vec<Diagnostic>,
) -> Option<ActionPolicy> {
    validate_source_conflict(
        s.policy_revision,
        policy_document_selected(s),
        "policy",
        issues,
    );
    let selected = s.policy_revision.or(captured.policy);
    let active = selected.and_then(|id| {
        deps.policy
            .revision_store()
            .get_revision(id)
            .map(|r| r.policy)
    });
    validate_revision(selected, active.is_some(), "policy", issues);
    if !policy_document_selected(s)
        && s.policy_revision.is_none()
        && matches!(deps.policy.snapshot(), PolicySnapshot::Invalid)
    {
        error(
            issues,
            "active_policy_invalid",
            "candidate/policy".to_owned(),
        );
    }
    active
}

fn selected_evaluators(
    deps: &SimulationDeps,
    s: &CandidateSelection,
    captured: &CapturedRevisions,
    issues: &mut Vec<Diagnostic>,
) -> Vec<EvaluatorDef> {
    validate_source_conflict(
        s.evaluator_revision,
        evaluator_document_selected(s),
        "evaluators",
        issues,
    );
    let snapshot = deps.evaluators.active_config();
    if !evaluator_document_selected(s)
        && s.evaluator_revision.is_none()
        && snapshot.invalid_reason.is_some()
    {
        error(
            issues,
            "active_evaluators_invalid",
            "candidate/evaluators".to_owned(),
        );
    }
    let selected = s.evaluator_revision.or(captured.evaluators);
    let defs = selected.and_then(|id| {
        deps.evaluators
            .revision_store()
            .get_revision(id)
            .map(|r| r.evaluators)
    });
    validate_revision(selected, defs.is_some(), "evaluator", issues);
    defs.unwrap_or_else(|| snapshot.list_evaluators())
}

fn deserialize_document<T: serde::de::DeserializeOwned>(
    value: Value,
    kind: &str,
    issues: &mut Vec<Diagnostic>,
) -> Option<T> {
    match serde_json::from_value(value) {
        Ok(document) => Some(document),
        Err(_) => {
            let field = if kind == "policy" {
                "policy"
            } else {
                "evaluators"
            };
            error(
                issues,
                &format!("{kind}_schema_invalid"),
                format!("candidate/inline_{field}"),
            );
            None
        }
    }
}

fn tool_contexts(
    deps: &SimulationDeps,
    contexts: &mut Vec<wanaku_feature_action_policy::ActionContext>,
) {
    use wanaku_feature_action_policy::{ActionContext, TargetType};
    for tool in deps.registry.list_tools() {
        contexts.push(
            ActionContext::new(
                tool.namespace.as_deref().unwrap_or("default"),
                wanaku_types::TOOLS_CALL,
                TargetType::Tool,
                Value::Null,
            )
            .with_target_name(tool.name)
            .with_labels(tool.labels.into_iter().collect()),
        );
    }
}

fn resource_contexts(
    deps: &SimulationDeps,
    contexts: &mut Vec<wanaku_feature_action_policy::ActionContext>,
) {
    use wanaku_feature_action_policy::{ActionContext, TargetType};
    for resource in deps.registry.list_resources() {
        contexts.push(
            ActionContext::new(
                resource.namespace.as_deref().unwrap_or("default"),
                wanaku_types::RESOURCES_READ,
                TargetType::Resource,
                Value::Null,
            )
            .with_target_name(resource.name.clone())
            .with_uri(resource.location)
            .with_labels(resource.labels.into_iter().collect()),
        );
    }
}

fn binding_incompatible(
    binding: &wanaku_types::credentials::CredentialBinding,
    forward: &wanaku_types::registry::ForwardEntry,
    purpose: wanaku_types::credentials::CredentialPurpose,
) -> bool {
    let namespace = forward.namespace.as_deref().unwrap_or("default");
    binding.validate().is_err()
        || binding.forward_id != forward.forward_id()
        || !binding.origin.matches_address(&forward.address)
        || !binding.allowed_purposes.contains(&purpose)
        || (!binding.restrictions.namespaces.is_empty()
            && !binding
                .restrictions
                .namespaces
                .iter()
                .any(|ns| ns == namespace))
}

fn prepare_evaluators(
    deps: &SimulationDeps,
    defs: &[EvaluatorDef],
    within_limit: bool,
) -> Result<PreparedEvaluators, Vec<wanaku_feature_evaluator::simulation::ValidationIssue>> {
    let selected = if within_limit {
        defs.to_vec()
    } else {
        Vec::new()
    };
    wanaku_feature_evaluator::simulation::prepare(&deps.evaluators, selected)
}

fn validate_source_conflict(
    revision: Option<u64>,
    document: bool,
    kind: &str,
    issues: &mut Vec<Diagnostic>,
) {
    if revision.is_some() && document {
        error(
            issues,
            "candidate_sources_conflict",
            format!("candidate/{kind}"),
        );
    }
}

fn validate_revision(revision: Option<u64>, found: bool, kind: &str, issues: &mut Vec<Diagnostic>) {
    if revision.is_some() && !found {
        error(
            issues,
            &format!("{kind}_revision_not_found"),
            format!("candidate/{kind}_revision"),
        );
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use wanaku_feature_action_policy::ActionPolicyFeature;
    use wanaku_feature_evaluator::state::EvaluatorState;
    use wanaku_infra::registry::InMemoryRegistry;
    use wanaku_types::audit::InMemoryAuditStore;
    use wanaku_types::feature::Feature;
    use wanaku_types::governance::GovernanceConfig;

    fn deps() -> SimulationDeps {
        SimulationDeps {
            policy: ActionPolicyFeature::new().state(),
            evaluators: EvaluatorState::new(),
            registry: InMemoryRegistry::new(),
            governance: GovernanceConfig::default(),
            audit: InMemoryAuditStore::new(10),
        }
    }

    #[test]
    fn first_inline_policy_uses_explicit_empty_base_without_activation() {
        let deps = deps();
        let selection = CandidateSelection {
            base_policy_revision: Some(0),
            inline_policy: Some(serde_json::json!({"rules": []})),
            ..CandidateSelection::default()
        };
        let prepared = prepare(&deps, &selection).expect("candidate should be valid");
        assert!(!prepared.report.stale);
        assert_eq!(prepared.report.identity.policy_revision, None);
        assert_eq!(deps.policy.revision_store().active_revision_id(), None);
        assert!(matches!(
            deps.policy.snapshot(),
            PolicySnapshot::Unconfigured
        ));
    }

    #[test]
    fn conflicting_sources_and_missing_revisions_are_aggregated() {
        let deps = deps();
        let selection = CandidateSelection {
            policy_revision: Some(99),
            base_policy_revision: Some(0),
            inline_policy: Some(serde_json::json!({"rules": []})),
            evaluator_revision: Some(98),
            ..CandidateSelection::default()
        };
        let report = prepare(&deps, &selection).err().expect("invalid candidate");
        let codes: BTreeSet<_> = report
            .diagnostics
            .iter()
            .map(|d| d.reason_code.as_str())
            .collect();
        assert!(codes.contains("candidate_sources_conflict"));
        assert!(codes.contains("policy_revision_not_found"));
        assert!(codes.contains("evaluator_revision_not_found"));
    }

    #[test]
    fn invalid_runtime_cannot_be_reported_as_unconfigured() {
        let deps = deps();
        deps.evaluators.mark_invalid("configuration_invalid");
        let report = prepare(&deps, &CandidateSelection::default())
            .err()
            .expect("invalid runtime");
        assert!(
            report
                .diagnostics
                .iter()
                .any(|d| d.reason_code == "active_evaluators_invalid")
        );
    }

    #[test]
    fn merge_patch_is_transient_and_becomes_stale_after_activation() {
        let feature = ActionPolicyFeature::new();
        let root = serde_yaml::from_str("action_policy:\n  rules: []\n").expect("YAML");
        feature.load_yaml_config(&root);
        let mut deps = deps();
        deps.policy = feature.state();
        let id = deps
            .policy
            .revision_store()
            .active_revision_id()
            .expect("active revision");
        let selection = CandidateSelection {
            base_policy_revision: Some(id),
            policy_patch: Some(
                serde_json::json!({"rules":[{"id":"deny","effect":"deny","selectors":{"operation":"tools/call"}}]}),
            ),
            ..CandidateSelection::default()
        };
        let prepared = prepare(&deps, &selection).expect("valid patch");
        assert_eq!(deps.policy.revision_store().active_revision_id(), Some(id));
        feature.load_yaml_config(&serde_yaml::from_str("action_policy:\n  rules:\n    - id: allow\n      effect: allow\n      selectors:\n        operation: tools/call\n").expect("YAML"));
        assert!(stale(&deps, &prepared.report.identity));
    }
}
