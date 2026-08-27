#![allow(clippy::unwrap_used, clippy::expect_used, clippy::panic)]

use std::sync::Arc;

use http::{HeaderMap, StatusCode};
use serde_json::json;
use wanaku_feature_action_policy::revision_persistence::FileRevisionPersistence;
use wanaku_feature_action_policy::{
    ActionContext, ActionPolicy, ActionPolicyFeature, PolicyDecision, PolicyEngine, PolicyState,
    TargetType,
};
use wanaku_types::feature::{Feature, HttpContext};

fn yaml_policy() -> ActionPolicy {
    serde_yaml::from_str(
        r#"
rules:
  - id: deny-tool
    effect: deny
    selectors:
      operation: tools/call
      target_name: { matcher: exact, value: delete }
  - id: deny-resource
    effect: deny
    selectors:
      operation: resources/read
      uri: { matcher: prefix, value: "file:///secret/" }
  - id: deny-exact-resource
    effect: deny
    selectors:
      operation: resources/read
      uri: { matcher: exact, value: "file:///locked" }
  - id: deny-prompt
    effect: deny
    selectors:
      operation: prompts/get
      target_name: { matcher: exact, value: admin }
"#,
    )
    .expect("valid YAML policy")
}

#[test]
fn yaml_compilation_governs_all_three_mcp_action_types() {
    let policy = yaml_policy().compile().expect("compiled policy");
    let cases = [
        ActionContext::new("default", "tools/call", TargetType::Tool, json!({}))
            .with_target_name("delete"),
        ActionContext::new("default", "resources/read", TargetType::Resource, json!({}))
            .with_uri("file:///secret/report"),
        ActionContext::new("default", "resources/read", TargetType::Resource, json!({}))
            .with_uri("file:///locked"),
        ActionContext::new("default", "prompts/get", TargetType::Prompt, json!({}))
            .with_target_name("admin"),
    ];
    for context in cases {
        assert!(matches!(
            PolicyEngine::evaluate(PolicyState::Available(&policy), &context),
            PolicyDecision::ExplicitDeny { .. }
        ));
    }
}

#[test]
fn static_allow_remains_an_explicit_continue_state_for_the_adapter() {
    let policy: ActionPolicy = serde_json::from_value(json!({"rules": [
        {"id": "allow-tool", "effect": "allow", "selectors": {"operation": "tools/call"}},
        {"id": "allow-resource", "effect": "allow", "selectors": {"operation": "resources/read"}},
        {"id": "allow-prompt", "effect": "allow", "selectors": {"operation": "prompts/get"}}
    ]}))
    .expect("policy");
    let compiled = policy.compile().expect("compiled policy");
    let actions = [
        ActionContext::new("default", "tools/call", TargetType::Tool, json!({})),
        ActionContext::new("default", "resources/read", TargetType::Resource, json!({})),
        ActionContext::new("default", "prompts/get", TargetType::Prompt, json!({})),
    ];
    for action in actions {
        assert!(matches!(
            PolicyEngine::evaluate(PolicyState::Available(&compiled), &action),
            PolicyDecision::ExplicitAllow { .. }
        ));
    }
}

fn context<'a>(
    method: &'a str,
    path: &'a str,
    body: Option<&'a str>,
    headers: &'a HeaderMap,
) -> HttpContext<'a> {
    HttpContext::new(method, path, None, body, headers)
}

#[tokio::test]
#[expect(
    clippy::too_many_lines,
    reason = "one management lifecycle must retain state across all requests"
)]
async fn management_activation_conflict_rollback_and_restart_cross_public_boundary() {
    let directory =
        std::env::temp_dir().join(format!("wanaku-policy-integration-{}", std::process::id()));
    let path = directory.join("action-policy-revisions.json");
    let persistence = Arc::new(FileRevisionPersistence::new(&path));
    let feature = ActionPolicyFeature::new().with_revision_persistence(persistence.clone());
    let headers = HeaderMap::new();
    let body = json!({"policy": yaml_policy()}).to_string();
    let response = feature
        .handle_route(&context(
            "PUT",
            "/api/v1/action-policies",
            Some(&body),
            &headers,
        ))
        .await
        .expect("owned route");
    assert_eq!(response.status(), StatusCode::OK);

    let stale = json!({"policy": yaml_policy(), "expected_revision": 99}).to_string();
    let response = feature
        .handle_route(&context(
            "PUT",
            "/api/v1/action-policies",
            Some(&stale),
            &headers,
        ))
        .await
        .expect("owned route");
    assert_eq!(response.status(), StatusCode::CONFLICT);

    let response = feature
        .handle_route(&context(
            "POST",
            "/api/v1/action-policies/revisions/1/activate",
            Some(r#"{"expected_revision":1}"#),
            &headers,
        ))
        .await
        .expect("owned route");
    assert_eq!(response.status(), StatusCode::OK);

    let restarted = ActionPolicyFeature::new().with_revision_persistence(persistence);
    let response = restarted
        .handle_route(&context(
            "GET",
            "/api/v1/action-policies/revisions/active",
            None,
            &headers,
        ))
        .await
        .expect("owned route");
    assert_eq!(response.status(), StatusCode::OK);
    let _ = std::fs::remove_dir_all(directory);
}
