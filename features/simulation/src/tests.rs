use crate::{api::CandidateSelection, *};
pub(crate) fn deps() -> SimulationDeps {
    SimulationDeps {
        policy: ActionPolicyState::new(),
        evaluators: EvaluatorState::new(),
        registry: InMemoryRegistry::new(),
        governance: GovernanceConfig::default(),
        audit: InMemoryAuditStore::new(100),
    }
}
#[test]
fn transient_validation_preserves_revisions() {
    let d = deps();
    let selection = CandidateSelection {
        inline_policy: Some(
            serde_json::json!({"rules":[{"id":"duplicate","effect":"deny","selectors":{}},{"id":"duplicate","effect":"allow","selectors":{}}]}),
        ),
        base_policy_revision: Some(0),
        ..CandidateSelection::default()
    };
    let result = candidate::prepare(&d, &selection);
    assert!(result.is_err());
    assert!(d.policy.revision_store().list_revisions().is_empty());
    assert!(d.evaluators.revision_store().list_revisions().is_empty());
    if let Err(r) = result {
        assert!(r.diagnostics.len() >= 2);
    }
}
#[test]
fn all_mcp_methods_use_parser_without_forwarding() {
    let d = deps();
    let p = candidate::prepare(&d, &CandidateSelection::default()).expect("candidate");
    for (method, params) in [
        (
            "tools/call",
            serde_json::json!({"name":"t","arguments":{"password":"secret"}}),
        ),
        ("resources/read", serde_json::json!({"uri":"file:///test"})),
        ("prompts/get", serde_json::json!({"name":"p"})),
    ] {
        let action = api::SyntheticAction {
            namespace: "default".to_owned(),
            request: serde_json::json!({"jsonrpc":"2.0","id":1,"method":method,"params":params}),
            evidence_id: None,
        };
        let r = decision::simulate(&d, &p, &action).expect("report");
        assert_eq!(r.decision, "deny");
        assert!(
            !serde_json::to_string(&r)
                .expect("serialize")
                .contains("secret")
        );
    }
    assert!(d.policy.revision_store().list_revisions().is_empty());
    assert_eq!(
        d.audit
            .query(&wanaku_types::audit::AuditQuery::default())
            .total,
        0
    );
}
#[tokio::test]
async fn external_execution_fails_closed() {
    let f = SimulationFeature::new(deps());
    let r =
        f.simulate(r#"{"candidate":{},"action":{"request":{}},"allow_external_evaluators":true}"#);
    assert_eq!(r.await.status(), 400);
}
#[test]
fn request_and_candidate_limits_are_enforced() {
    let mut limits = SimulationLimits {
        max_request_bytes: 2,
        ..SimulationLimits::default()
    };
    assert!(parse::<api::ValidationRequest>("{} ", &limits).is_err());
    limits.max_request_bytes = 1000;
    limits.max_candidate_bytes = 1;
    assert!(parse::<api::ValidationRequest>(r#"{"candidate":{}}"#, &limits).is_err());
}

#[tokio::test]
async fn replay_reports_expansion_and_supports_deletion() {
    let mut d = deps();
    d.governance.default.no_match = wanaku_types::governance::NoMatchBehavior::Allow;
    let f = SimulationFeature::new(d.clone());
    let request = serde_json::json!({"candidate":{"base_policy_revision":0,"inline_policy":{"rules":[{"id":"block","effect":"deny","selectors":{"operation":"tools/call"}}]}},"actions":[{"request":{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"example"}}}]});
    let headers = http::HeaderMap::new();
    let body = request.to_string();
    let response = f
        .handle_route(&HttpContext::new(
            "POST",
            "/api/v1/policy-simulations/replays",
            None,
            Some(&body),
            &headers,
        ))
        .await;
    let response = response.expect("replay route");
    assert_eq!(response.status(), 202);
    let parsed: serde_json::Value = serde_json::from_slice(response.body()).unwrap_or_default();
    let id = parsed["data"]["id"].as_str().unwrap_or("");
    let path = format!("/api/v1/policy-simulations/replays/{id}");
    let parsed = wait_for_replay(&f, &d, id).await;
    assert_eq!(parsed["data"]["counts"]["allow_to_deny"], 1);
    assert_eq!(parsed["data"]["details"][0]["compatibility_impact"], true);
    let deleted = f
        .handle_route(&HttpContext::new("DELETE", &path, None, None, &headers))
        .await;
    assert!(deleted.is_some_and(|r| r.status() == 200));
    assert_eq!(f.jobs.get(&d, id).status(), 404);
    assert!(d.policy.revision_store().list_revisions().is_empty());
}
#[tokio::test]
async fn replay_skips_unavailable_audit_payload() {
    let d = deps();
    let f = SimulationFeature::new(d.clone());
    let request = api::ReplayRequest {
        candidate: api::CandidateSelection::default(),
        actions: Vec::new(),
        audit_event_ids: vec!["missing".to_owned()],
        allow_external_evaluators: false,
    };
    let response = f.jobs.start(&d, &SimulationLimits::default(), request);
    assert_eq!(response.status(), 202);
    let report: serde_json::Value = serde_json::from_slice(response.body()).unwrap_or_default();
    assert_eq!(
        report["data"]["skipped_audit_ids"],
        serde_json::json!(["missing"])
    );
}
#[test]
fn reports_redact_posture_and_rule_identifiers() {
    let mut d = deps();
    d.governance.default.mode = wanaku_types::governance::EnforcementMode::Disabled;
    d.governance.default.disabled_reason = Some("Bearer posture-secret".to_owned());
    let selection = CandidateSelection {
        inline_policy: Some(
            serde_json::json!({"rules":[{"id":"ghp_rule-secret","effect":"allow","selectors":{"operation":"tools/call"}}]}),
        ),
        base_policy_revision: Some(0),
        ..CandidateSelection::default()
    };
    let p = candidate::prepare(&d, &selection).expect("prepared candidate");
    let action = api::SyntheticAction {
        namespace: "default".to_owned(),
        request: serde_json::json!({"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"test"}}),
        evidence_id: None,
    };
    let r = decision::simulate(&d, &p, &action).expect("report");
    let encoded = serde_json::to_string(&r).expect("serialize");
    assert!(!encoded.contains("posture-secret"));
    assert!(!encoded.contains("ghp_rule-secret"));
    assert!(!r.redaction.redacted_fields.is_empty());
}
#[test]
fn simulated_forward_never_connects_to_upstream() {
    use wanaku_types::registry::{ForwardRegistry, ToolRegistry};
    let d = deps();
    let listener = std::net::TcpListener::bind("127.0.0.1:0").expect("local listener");
    listener.set_nonblocking(true).expect("nonblocking");
    let address = listener.local_addr().expect("address");
    let forward = serde_json::from_value(
        serde_json::json!({"name":"upstream","address":format!("http://{address}/mcp")}),
    )
    .expect("forward");
    d.registry.register_forward(forward);
    let tool=serde_json::from_value(serde_json::json!({"name":"example","description":"","uri":"example","type":"mcp-forward","forwardId":"upstream","inputSchema":{"type":"object"}})).expect("tool");
    d.registry.register_tool(tool);
    let p = candidate::prepare(&d, &CandidateSelection::default()).expect("prepared");
    let action = api::SyntheticAction {
        namespace: "default".to_owned(),
        request: serde_json::json!({"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"example"}}),
        evidence_id: None,
    };
    assert!(decision::simulate(&d, &p, &action).is_ok());
    assert!(
        listener
            .accept()
            .is_err_and(|e| e.kind() == std::io::ErrorKind::WouldBlock)
    );
    assert_eq!(d.registry.tool_count(), 1);
}
#[test]
fn simulation_registry_snapshot_is_detached() {
    use wanaku_types::registry::ToolRegistry;
    let d = deps();
    let tool=serde_json::from_value(serde_json::json!({"name":"example","description":"","uri":"example","type":"mcp-forward","inputSchema":{"type":"object"},"labels":{"access":"private"}})).expect("tool");
    d.registry.register_tool(tool);
    let snapshot = snapshot::capture(&d, &SimulationLimits::default()).expect("snapshot");
    let mut tool = d.registry.get_tool("example").expect("original tool");
    tool.labels.insert("access".to_owned(), "public".to_owned());
    d.registry.register_tool(tool);
    assert_eq!(
        snapshot
            .registry
            .get_tool("example")
            .expect("snapshot tool")
            .labels
            .get("access")
            .map(String::as_str),
        Some("private")
    );
    assert_eq!(
        d.registry
            .get_tool("example")
            .expect("original tool")
            .labels
            .get("access")
            .map(String::as_str),
        Some("public")
    );
}

async fn wait_for_replay(f: &SimulationFeature, d: &SimulationDeps, id: &str) -> serde_json::Value {
    for _ in 0..100 {
        tokio::time::sleep(std::time::Duration::from_millis(2)).await;
        let response = f.jobs.get(d, id);
        let parsed: serde_json::Value = serde_json::from_slice(response.body()).expect("report");
        if parsed["data"]["status"] != "running" {
            return parsed;
        }
    }
    serde_json::Value::Null
}

#[test]
fn unmatched_evaluator_reports_effective_baseline_after_explicit_allow() {
    for behavior in [
        wanaku_types::governance::NoMatchBehavior::Allow,
        wanaku_types::governance::NoMatchBehavior::Deny,
    ] {
        let mut d = deps();
        d.governance.default.no_match = behavior;
        let selection = CandidateSelection {
            base_policy_revision: Some(0),
            inline_policy: Some(
                serde_json::json!({"rules":[{"id":"permit","effect":"allow","selectors":{"operation":"tools/call"}}]}),
            ),
            ..CandidateSelection::default()
        };
        let p = candidate::prepare(&d, &selection).expect("candidate");
        let action = api::SyntheticAction {
            namespace: "default".to_owned(),
            request: serde_json::json!({"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"test"}}),
            evidence_id: None,
        };
        let report = decision::simulate(&d, &p, &action).expect("report");
        assert!(report.baseline_used);
        assert!(!report.failure_used);
        assert_eq!(report.stages[0].reason_code, "action_policy_allowed");
        assert_eq!(report.stages[1].reason_code, "evaluator_not_matched");
        assert_eq!(report.decision, baseline_label(behavior));
    }
}

const fn baseline_label(behavior: wanaku_types::governance::NoMatchBehavior) -> &'static str {
    match behavior {
        wanaku_types::governance::NoMatchBehavior::Allow => "allow",
        wanaku_types::governance::NoMatchBehavior::Deny => "deny",
    }
}
