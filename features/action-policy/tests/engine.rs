use std::collections::BTreeMap;

use serde_json::json;
use wanaku_feature_action_policy::{
    ActionContext, ActionPolicy, ActorContext, DEFAULT_DENY_MESSAGE, DEFAULT_DENY_REASON_CODE,
    Effect, MatchExpression, MatchKind, MatchedRule, PolicyDecision, PolicyEngine, PolicyState,
    Predicate, Rule, Selectors, TargetType,
};

fn rule(id: &str, effect: Effect) -> Rule {
    Rule {
        id: id.to_owned(),
        description: None,
        effect,
        selectors: Selectors {
            operation: Some("tools/call".to_owned()),
            ..Selectors::default()
        },
        predicates: Vec::new(),
        reason_code: None,
        message: None,
        metadata: BTreeMap::new(),
    }
}

fn context() -> ActionContext {
    ActionContext::new(
        "production",
        "tools/call",
        TargetType::Tool,
        json!({ "arguments": { "approved": true } }),
    )
    .with_target_name("reports/read")
    .with_labels(BTreeMap::from([
        ("owner".to_owned(), "finance".to_owned()),
        ("tier".to_owned(), "sensitive".to_owned()),
    ]))
    .with_uri("tool://reports/read")
}

#[test]
fn matches_all_transport_neutral_selectors_and_predicates() -> Result<(), Box<dyn std::error::Error>>
{
    let mut candidate = rule("complete-match", Effect::Allow);
    candidate.selectors.namespace = Some("production".to_owned());
    candidate.selectors.target_type = Some(TargetType::Tool);
    candidate.selectors.target_name = Some(MatchExpression {
        matcher: MatchKind::Glob,
        value: "reports/*".to_owned(),
    });
    candidate
        .selectors
        .labels
        .insert("owner".to_owned(), "finance".to_owned());
    candidate.selectors.uri = Some(MatchExpression {
        matcher: MatchKind::Prefix,
        value: "tool://reports/".to_owned(),
    });
    candidate.predicates.push(Predicate::Equals {
        pointer: "/arguments/approved".to_owned(),
        value: json!(true),
    });
    let policy = ActionPolicy {
        rules: vec![candidate],
    }
    .compile()?;

    assert!(matches!(
        PolicyEngine::evaluate(PolicyState::Available(&policy), &context()),
        PolicyDecision::ExplicitAllow { .. }
    ));
    Ok(())
}

#[test]
fn required_labels_are_a_subset_of_context_labels() -> Result<(), Box<dyn std::error::Error>> {
    let mut candidate = rule("label-subset", Effect::Allow);
    candidate
        .selectors
        .labels
        .insert("owner".to_owned(), "finance".to_owned());
    let policy = ActionPolicy {
        rules: vec![candidate],
    }
    .compile()?;

    assert!(matches!(
        PolicyEngine::evaluate(PolicyState::Available(&policy), &context()),
        PolicyDecision::ExplicitAllow { .. }
    ));
    Ok(())
}

#[test]
#[expect(clippy::too_many_lines, reason = "selector mismatch matrix")]
fn selector_and_predicate_mismatches_produce_no_match() -> Result<(), Box<dyn std::error::Error>> {
    let mut rules = Vec::new();
    for (id, selectors) in [
        (
            "namespace",
            Selectors {
                namespace: Some("development".to_owned()),
                ..Selectors::default()
            },
        ),
        (
            "operation",
            Selectors {
                operation: Some("resources/read".to_owned()),
                ..Selectors::default()
            },
        ),
        (
            "type",
            Selectors {
                target_type: Some(TargetType::Resource),
                ..Selectors::default()
            },
        ),
        (
            "name",
            Selectors {
                target_name: Some(MatchExpression {
                    matcher: MatchKind::Exact,
                    value: "reports/write".to_owned(),
                }),
                ..Selectors::default()
            },
        ),
        (
            "uri",
            Selectors {
                uri: Some(MatchExpression {
                    matcher: MatchKind::Exact,
                    value: "tool://other".to_owned(),
                }),
                ..Selectors::default()
            },
        ),
    ] {
        let mut candidate = rule(id, Effect::Deny);
        candidate.selectors = selectors;
        rules.push(candidate);
    }
    let mut labels = rule("labels", Effect::Deny);
    labels
        .selectors
        .labels
        .insert("owner".to_owned(), "engineering".to_owned());
    rules.push(labels);
    let mut predicate = rule("predicate", Effect::Deny);
    predicate.predicates.push(Predicate::Equals {
        pointer: "/arguments/approved".to_owned(),
        value: json!(false),
    });
    rules.push(predicate);
    let policy = ActionPolicy { rules }.compile()?;

    assert_eq!(
        PolicyEngine::evaluate(PolicyState::Available(&policy), &context()),
        PolicyDecision::NoMatch
    );
    Ok(())
}

#[test]
#[expect(clippy::too_many_lines, reason = "rule order and evidence matrix")]
fn deny_overrides_allow_and_is_independent_of_declaration_order()
-> Result<(), Box<dyn std::error::Error>> {
    let allow = rule("a-allow", Effect::Allow);
    let mut later_deny = rule("z-deny", Effect::Deny);
    later_deny.reason_code = Some("later_deny".to_owned());
    later_deny.message = Some("Later deny".to_owned());
    let mut primary_deny = rule("b-deny", Effect::Deny);
    primary_deny.reason_code = Some("primary_deny".to_owned());
    primary_deny.message = Some("Primary deny".to_owned());

    let forward = ActionPolicy {
        rules: vec![allow.clone(), later_deny.clone(), primary_deny.clone()],
    }
    .compile()?;
    let reverse = ActionPolicy {
        rules: vec![primary_deny, later_deny, allow],
    }
    .compile()?;
    let forward_decision = PolicyEngine::evaluate(PolicyState::Available(&forward), &context());
    let reverse_decision = PolicyEngine::evaluate(PolicyState::Available(&reverse), &context());

    assert_eq!(forward_decision, reverse_decision);
    assert_eq!(forward_decision.deny_reason_code(), Some("primary_deny"));
    assert_eq!(forward_decision.deny_message(), Some("Primary deny"));
    let details = forward_decision
        .details()
        .ok_or("missing decision details")?;
    let matched: Vec<&str> = details
        .matched_rules()
        .iter()
        .map(MatchedRule::rule_id)
        .collect();
    let contributing: Vec<&str> = details
        .contributing_rules()
        .iter()
        .map(MatchedRule::rule_id)
        .collect();
    assert_eq!(matched, vec!["a-allow", "b-deny", "z-deny"]);
    assert_eq!(contributing, vec!["b-deny", "z-deny"]);
    Ok(())
}

#[test]
fn explicit_allow_remains_a_continue_state() -> Result<(), Box<dyn std::error::Error>> {
    let policy = ActionPolicy {
        rules: vec![rule("allow", Effect::Allow)],
    }
    .compile()?;
    let decision = PolicyEngine::evaluate(PolicyState::Available(&policy), &context());

    assert!(matches!(decision, PolicyDecision::ExplicitAllow { .. }));
    assert!(decision.deny_message().is_none());
    Ok(())
}

#[test]
fn default_deny_message_is_safe() -> Result<(), Box<dyn std::error::Error>> {
    let policy = ActionPolicy {
        rules: vec![rule("deny", Effect::Deny)],
    }
    .compile()?;
    let decision = PolicyEngine::evaluate(PolicyState::Available(&policy), &context());
    assert_eq!(decision.deny_message(), Some(DEFAULT_DENY_MESSAGE));
    assert_eq!(decision.deny_reason_code(), Some(DEFAULT_DENY_REASON_CODE));
    Ok(())
}

#[test]
fn distinguishes_no_match_unavailable_and_invalid() -> Result<(), Box<dyn std::error::Error>> {
    let mut candidate = rule("other-operation", Effect::Allow);
    candidate.selectors.operation = Some("prompts/get".to_owned());
    let policy = ActionPolicy {
        rules: vec![candidate],
    }
    .compile()?;

    assert_eq!(
        PolicyEngine::evaluate(PolicyState::Available(&policy), &context()),
        PolicyDecision::NoMatch
    );
    assert_eq!(
        PolicyEngine::evaluate(PolicyState::Unavailable, &context()),
        PolicyDecision::PolicyUnavailable
    );
    assert_eq!(
        PolicyEngine::evaluate(PolicyState::Invalid, &context()),
        PolicyDecision::PolicyInvalid
    );
    Ok(())
}

#[test]
fn actor_context_is_typed_and_not_used_as_a_selector() -> Result<(), Box<dyn std::error::Error>> {
    let actor = ActorContext::new(
        "user-123",
        "https://identity.example",
        BTreeMap::from([("admin".to_owned(), json!(false))]),
    );
    let action = context().with_actor(actor);
    let policy = ActionPolicy {
        rules: vec![rule("allow", Effect::Allow)],
    }
    .compile()?;

    let retained = action.actor().ok_or("missing actor")?;
    assert_eq!(retained.principal_id(), "user-123");
    assert_eq!(retained.issuer(), "https://identity.example");
    assert_eq!(retained.attributes().get("admin"), Some(&json!(false)));
    assert!(matches!(
        PolicyEngine::evaluate(PolicyState::Available(&policy), &action),
        PolicyDecision::ExplicitAllow { .. }
    ));
    Ok(())
}
