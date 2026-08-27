use serde_json::json;
use wanaku_feature_action_policy::{
    ActionPolicy, Effect, MatchExpression, MatchKind, Predicate, Rule, Selectors, TargetType,
    ValidationError,
};

fn rule(id: &str) -> Rule {
    Rule {
        id: id.to_owned(),
        description: None,
        effect: Effect::Deny,
        selectors: Selectors {
            operation: Some("tools/call".to_owned()),
            ..Selectors::default()
        },
        predicates: Vec::new(),
        reason_code: None,
        message: None,
        metadata: std::collections::BTreeMap::new(),
    }
}

#[test]
fn schema_round_trips_all_selector_and_predicate_types() -> Result<(), Box<dyn std::error::Error>> {
    let source = json!({
        "rules": [{
            "id": "protect.files",
            "description": "Protect confidential file resources.",
            "effect": "deny",
            "selectors": {
                "namespace": "production",
                "operation": "resources/read",
                "target_type": "resource",
                "target_name": { "matcher": "glob", "value": "documents/*" },
                "labels": { "classification": "confidential" },
                "uri": { "matcher": "prefix", "value": "file:///approved/" }
            },
            "predicates": [
                { "operator": "exists", "pointer": "/arguments/path", "value": true },
                { "operator": "equals", "pointer": "/arguments/mode", "value": "write" },
                { "operator": "not_equals", "pointer": "/arguments/count", "value": 0 },
                { "operator": "one_of", "pointer": "/arguments/tier", "values": ["a", "b"] },
                { "operator": "not_one_of", "pointer": "/arguments/enabled", "values": [false] }
            ],
            "reason_code": "restricted_resource",
            "message": "This resource is not available.",
            "metadata": { "owner": "security", "ticket": 1870 }
        }]
    });

    let policy: ActionPolicy = serde_json::from_value(source.clone())?;
    assert_eq!(serde_json::to_value(&policy)?, source);
    assert_eq!(
        policy.rules[0].selectors.target_type,
        Some(TargetType::Resource)
    );
    assert!(policy.compile().is_ok());
    Ok(())
}

#[test]
fn rejects_duplicate_and_invalid_rule_ids() {
    let duplicate = ActionPolicy {
        rules: vec![rule("same-id"), rule("same-id")],
    };
    assert_eq!(
        duplicate.compile().err(),
        Some(ValidationError::DuplicateRuleId("same-id".to_owned()))
    );

    let invalid = ActionPolicy {
        rules: vec![rule("invalid id")],
    };
    assert_eq!(
        invalid.compile().err(),
        Some(ValidationError::InvalidRuleId("invalid id".to_owned()))
    );
}

#[test]
fn requires_an_action_selector() {
    let mut candidate = rule("no-selector");
    candidate.selectors = Selectors::default();
    assert_eq!(
        ActionPolicy {
            rules: vec![candidate]
        }
        .compile()
        .err(),
        Some(ValidationError::MissingSelector("no-selector".to_owned()))
    );
}

#[test]
fn validates_reason_code_and_metadata_keys() {
    let mut bad_reason = rule("bad-reason");
    bad_reason.reason_code = Some("not stable".to_owned());
    assert!(matches!(
        ActionPolicy {
            rules: vec![bad_reason]
        }
        .compile(),
        Err(ValidationError::InvalidIdentifier {
            field: "reason_code",
            ..
        })
    ));

    let mut bad_metadata = rule("bad-metadata");
    bad_metadata
        .metadata
        .insert("unsafe key".to_owned(), json!(true));
    assert!(matches!(
        ActionPolicy {
            rules: vec![bad_metadata]
        }
        .compile(),
        Err(ValidationError::InvalidIdentifier {
            field: "metadata",
            ..
        })
    ));
}

#[test]
#[expect(clippy::too_many_lines, reason = "matcher behavior matrix")]
fn compiles_exact_glob_and_literal_uri_prefix_matchers() -> Result<(), Box<dyn std::error::Error>> {
    let mut first = rule("matchers");
    first.selectors.target_name = Some(MatchExpression {
        matcher: MatchKind::Glob,
        value: "report-??-\\*".to_owned(),
    });
    first.selectors.uri = Some(MatchExpression {
        matcher: MatchKind::Prefix,
        value: "file:///safe/%2e%2e/".to_owned(),
    });
    let compiled = ActionPolicy { rules: vec![first] }.compile()?;
    let compiled_rule = &compiled.rules()[0];

    assert!(
        compiled_rule
            .target_name()
            .is_some_and(|matcher| matcher.matches("report-ab-*"))
    );
    assert!(
        !compiled_rule
            .target_name()
            .is_some_and(|matcher| matcher.matches("report-a-*"))
    );
    assert!(
        compiled_rule
            .uri()
            .is_some_and(|matcher| matcher.matches("file:///safe/%2e%2e/item"))
    );
    assert!(
        !compiled_rule
            .uri()
            .is_some_and(|matcher| matcher.matches("file:///safe/../item"))
    );
    Ok(())
}

#[test]
fn compiles_exact_target_name_matcher() -> Result<(), Box<dyn std::error::Error>> {
    let mut candidate = rule("exact-name");
    candidate.selectors.target_name = Some(MatchExpression {
        matcher: MatchKind::Exact,
        value: "reports/read".to_owned(),
    });
    let compiled = ActionPolicy {
        rules: vec![candidate],
    }
    .compile()?;
    let matcher = compiled.rules()[0].target_name();
    assert!(matcher.is_some_and(|matcher| matcher.matches("reports/read")));
    assert!(!matcher.is_some_and(|matcher| matcher.matches("reports/read/all")));
    Ok(())
}

#[test]
fn rejects_glob_uri_and_invalid_matcher_values() {
    let mut glob_uri = rule("glob-uri");
    glob_uri.selectors.uri = Some(MatchExpression {
        matcher: MatchKind::Glob,
        value: "file:///*".to_owned(),
    });
    assert_eq!(
        ActionPolicy {
            rules: vec![glob_uri]
        }
        .compile()
        .err(),
        Some(ValidationError::InvalidUriMatcher("glob-uri".to_owned()))
    );

    let mut bad_glob = rule("bad-glob");
    bad_glob.selectors.target_name = Some(MatchExpression {
        matcher: MatchKind::Glob,
        value: "unfinished\\".to_owned(),
    });
    assert!(matches!(
        ActionPolicy {
            rules: vec![bad_glob]
        }
        .compile(),
        Err(ValidationError::InvalidMatcher { .. })
    ));
}

#[test]
fn rejects_prefix_target_name() {
    let mut candidate = rule("prefix-name");
    candidate.selectors.target_name = Some(MatchExpression {
        matcher: MatchKind::Prefix,
        value: "reports/".to_owned(),
    });
    assert_eq!(
        ActionPolicy {
            rules: vec![candidate]
        }
        .compile()
        .err(),
        Some(ValidationError::InvalidTargetNameMatcher(
            "prefix-name".to_owned()
        ))
    );
}

#[test]
#[expect(clippy::too_many_lines, reason = "missing-value operator matrix")]
fn missing_values_only_match_exists_false() -> Result<(), Box<dyn std::error::Error>> {
    let document = json!({});
    let predicates = vec![
        Predicate::Exists {
            pointer: "/missing".to_owned(),
            value: false,
        },
        Predicate::Equals {
            pointer: "/missing".to_owned(),
            value: json!(null),
        },
        Predicate::NotEquals {
            pointer: "/missing".to_owned(),
            value: json!(null),
        },
        Predicate::OneOf {
            pointer: "/missing".to_owned(),
            values: vec![json!(null)],
        },
        Predicate::NotOneOf {
            pointer: "/missing".to_owned(),
            values: vec![json!(null)],
        },
    ];
    let mut candidate = rule("missing");
    candidate.predicates = predicates;
    let compiled = ActionPolicy {
        rules: vec![candidate],
    }
    .compile()?;
    let results: Vec<bool> = compiled.rules()[0]
        .predicates()
        .iter()
        .map(|predicate| predicate.matches(&document))
        .collect();
    assert_eq!(results, vec![true, false, false, false, false]);
    Ok(())
}

#[test]
#[expect(clippy::too_many_lines, reason = "typed JSON value matrix")]
fn comparisons_preserve_json_types_and_empty_values() -> Result<(), Box<dyn std::error::Error>> {
    let document = json!({
        "null": null,
        "false": false,
        "zero": 0,
        "empty_string": "",
        "empty_array": [],
        "empty_object": {}
    });
    let values = [
        ("/null", json!(null)),
        ("/false", json!(false)),
        ("/zero", json!(0)),
        ("/empty_string", json!("")),
        ("/empty_array", json!([])),
        ("/empty_object", json!({})),
    ];
    let mut candidate = rule("typed-values");
    candidate.predicates = values
        .into_iter()
        .map(|(pointer, value)| Predicate::Equals {
            pointer: pointer.to_owned(),
            value,
        })
        .collect();
    let compiled = ActionPolicy {
        rules: vec![candidate],
    }
    .compile()?;
    assert!(
        compiled.rules()[0]
            .predicates()
            .iter()
            .all(|predicate| predicate.matches(&document))
    );

    let wrong_type = Predicate::Equals {
        pointer: "/zero".to_owned(),
        value: json!("0"),
    };
    let mut candidate = rule("wrong-type");
    candidate.predicates.push(wrong_type);
    let compiled = ActionPolicy {
        rules: vec![candidate],
    }
    .compile()?;
    assert!(!compiled.rules()[0].predicates()[0].matches(&document));
    Ok(())
}

#[test]
fn validates_json_pointers_and_non_empty_set_operands() {
    for pointer in ["missing-slash", "/bad~2escape", "/trailing~"] {
        let mut candidate = rule("bad-pointer");
        candidate.predicates.push(Predicate::Exists {
            pointer: pointer.to_owned(),
            value: true,
        });
        assert!(matches!(
            ActionPolicy {
                rules: vec![candidate]
            }
            .compile(),
            Err(ValidationError::InvalidJsonPointer { .. })
        ));
    }

    let mut candidate = rule("empty-one-of");
    candidate.predicates.push(Predicate::OneOf {
        pointer: "/value".to_owned(),
        values: Vec::new(),
    });
    assert!(matches!(
        ActionPolicy {
            rules: vec![candidate]
        }
        .compile(),
        Err(ValidationError::EmptyOperand { .. })
    ));
}

#[test]
fn rejects_unknown_matchers_operators_and_malformed_operands() {
    let unknown_matcher = json!({
        "rules": [{
            "id": "bad-matcher",
            "effect": "deny",
            "selectors": {
                "target_name": { "matcher": "regex", "value": ".*" }
            }
        }]
    });
    assert!(serde_json::from_value::<ActionPolicy>(unknown_matcher).is_err());

    let unknown_operator = json!({
        "rules": [{
            "id": "bad-operator",
            "effect": "deny",
            "predicates": [{ "operator": "contains", "pointer": "/value", "value": "x" }]
        }]
    });
    assert!(serde_json::from_value::<ActionPolicy>(unknown_operator).is_err());

    let malformed_operand = json!({
        "rules": [{
            "id": "bad-operand",
            "effect": "deny",
            "predicates": [{ "operator": "one_of", "pointer": "/value", "values": "x" }]
        }]
    });
    assert!(serde_json::from_value::<ActionPolicy>(malformed_operand).is_err());
}
