//! Composition tests for the assembled application.
//!
//! The tests build the production runtime graph with [`App::build`], drive
//! the management API through [`WanakuManagementService::dispatch`], and run
//! MCP requests through the assembled `mcp` listener pipeline in process.
//! They bind no ports and contact no external services.
//!
//! [`WanakuManagementService::dispatch`]: wanaku_server::management::WanakuManagementService::dispatch
#![cfg_attr(test, allow(clippy::unwrap_used, clippy::expect_used))]

use std::sync::Arc;

use bytes::Bytes;
use praxis_filter::FilterAction;
use praxis_protocol::http::pingora::context::PingoraRequestCtx;
use serde_json::{Value, json};

use wanaku_infra::credentials::CredentialBroker;
use wanaku_server::app::{App, AppPersistence, AppResources, AppSettings};
use wanaku_types::credentials::ResolverRegistry;
use wanaku_types::feature::HttpContext;
use wanaku_types::governance::GovernanceConfig;
use wanaku_types::registry::{ToolEntry, ToolRegistry};

const POLICY_DENIED: i64 = -32003;
const EVALUATOR_DENIED: i64 = -32001;

/// Build an application from the embedded pipeline configuration, with
/// in-memory persistence and a broker that has no secret resolvers.
fn build_app() -> App {
    let pipeline = wanaku_server::load_config(None).expect("embedded pipeline configuration");
    App::build(
        &AppSettings {
            pipeline: &pipeline,
            wanaku_config: None,
            governance: GovernanceConfig::default(),
            plugins_dir: None,
        },
        AppResources {
            persistence: AppPersistence::default(),
            broker: Arc::new(CredentialBroker::new(ResolverRegistry::new())),
        },
    )
    .expect("application assembles")
}

fn register_tool(app: &App, name: &str) {
    let tool: ToolEntry = serde_json::from_value(json!({
        "name": name,
        "description": "test tool",
        "uri": name,
        "type": "mcp",
        "inputSchema": {"type": "object"},
        "forwardId": "absent-forward",
    }))
    .expect("valid tool entry");
    app.registry.register_tool(tool);
}

async fn manage(app: &App, method: &str, path: &str, body: Option<&str>) -> (u16, Value) {
    let headers = http::HeaderMap::new();
    let ctx = HttpContext::new(method, path, None, body, &headers);
    let response = app.management.dispatch(&ctx).await;
    let body = serde_json::from_slice(response.body()).expect("management JSON body");
    (response.status().as_u16(), body)
}

/// Run one MCP JSON-RPC request through the assembled `mcp` pipeline.
///
/// This mirrors the protocol layer for a `StreamBuffer` request: the
/// pipeline prepares the request extensions, then runs the body filters
/// with the complete body. Returns the synthetic JSON-RPC response that a
/// filter produced, or `None` when the request continues to an upstream.
async fn mcp(app: &App, request: &Value) -> Option<Value> {
    let swap = app.pipelines.get("mcp").expect("mcp listener pipeline");
    let mut protocol = PingoraRequestCtx::default();
    let pipeline = protocol.pin_pipeline(swap);
    let mut headers = http::HeaderMap::new();
    headers.insert(
        http::header::CONTENT_TYPE,
        "application/json".parse().unwrap(),
    );
    let http_request = praxis_filter::Request {
        method: http::Method::POST,
        uri: http::Uri::from_static("/default/mcp"),
        headers,
    };
    let mut ctx = protocol.build_filter_context(&pipeline, &http_request, None);
    let mut body = Some(Bytes::from(request.to_string()));
    let action = pipeline
        .execute_http_request_body(&mut ctx, &mut body, true)
        .await
        .expect("request body phase");
    match action {
        FilterAction::Reject(rejection) => {
            let body = rejection.body.expect("synthetic response body");
            Some(serde_json::from_slice(&body).expect("JSON-RPC response"))
        }
        _ => None,
    }
}

async fn listed_tools(app: &App) -> Vec<String> {
    let response = mcp(
        app,
        &json!({"jsonrpc": "2.0", "id": 1, "method": "tools/list"}),
    )
    .await
    .expect("tools/list response");
    response["result"]["tools"]
        .as_array()
        .expect("tool array")
        .iter()
        .filter_map(|tool| tool["name"].as_str().map(str::to_owned))
        .collect()
}

async fn call_tool_error_code(app: &App, name: &str) -> Option<i64> {
    let response = mcp(
        app,
        &json!({
            "jsonrpc": "2.0",
            "id": 7,
            "method": "tools/call",
            "params": {"name": name, "arguments": {}},
        }),
    )
    .await
    .expect("tools/call stops before forwarding");
    response["error"]["code"].as_i64()
}

/// Activate a one-rule action policy and return its revision ID.
async fn put_policy(app: &App, rule_id: &str, effect: &str) -> String {
    let body = json!({
        "policy": {"rules": [{
            "id": rule_id,
            "effect": effect,
            "selectors": {"operation": "tools/call"},
        }]}
    })
    .to_string();
    let (status, response) = manage(app, "PUT", "/api/v1/action-policies", Some(&body)).await;
    assert_eq!(status, 200, "policy update: {response}");
    response["data"]["revision"]["id"]
        .as_u64()
        .expect("active revision id")
        .to_string()
}

async fn audit_events(app: &App) -> Vec<Value> {
    let (status, response) = manage(app, "GET", "/api/v1/audit/events", None).await;
    assert_eq!(status, 200, "audit query: {response}");
    response["data"]["events"]
        .as_array()
        .expect("audit event array")
        .clone()
}

#[tokio::test]
async fn management_tool_toggle_is_reflected_in_mcp_tools_list() {
    let app = build_app();
    register_tool(&app, "weather");
    assert_eq!(listed_tools(&app).await, ["weather"]);

    let (status, _) = manage(&app, "PUT", "/api/v1/tools/weather/disable", None).await;
    assert_eq!(status, 200);
    assert!(listed_tools(&app).await.is_empty());

    let (status, _) = manage(&app, "PUT", "/api/v1/tools/weather/enable", None).await;
    assert_eq!(status, 200);
    assert_eq!(listed_tools(&app).await, ["weather"]);
}

#[tokio::test]
async fn management_policy_update_denies_next_call_and_is_audited() {
    let app = build_app();
    register_tool(&app, "weather");

    // An allow rule lets the call pass the action policy. The default
    // fail-closed posture then stops it at the evaluator, before forwarding.
    put_policy(&app, "allow-weather", "allow").await;
    assert_eq!(
        call_tool_error_code(&app, "weather").await,
        Some(EVALUATOR_DENIED)
    );

    let revision = put_policy(&app, "deny-weather", "deny").await;
    assert_eq!(
        call_tool_error_code(&app, "weather").await,
        Some(POLICY_DENIED)
    );

    let events = audit_events(&app).await;
    let denial = events
        .iter()
        .find(|event| {
            event["decision"] == "block"
                && event["policy_revision"] == revision.as_str()
                && event["operation"] == "tools/call"
        })
        .unwrap_or_else(|| unreachable!("no policy denial event in {events:#?}"));
    assert_eq!(denial["target"], "weather");
}

#[tokio::test]
async fn applications_in_one_process_have_independent_state() {
    let (first, second) = (build_app(), build_app());
    register_tool(&first, "weather");

    let (revision, first_tools, second_tools) = tokio::join!(
        put_policy(&first, "deny-weather", "deny"),
        listed_tools(&first),
        listed_tools(&second)
    );
    assert_eq!(first_tools, ["weather"]);
    assert!(second_tools.is_empty());

    let (status, _) = manage(&second, "GET", "/api/v1/action-policies", None).await;
    assert_eq!(status, 404, "the second application has no policy revision");

    let (first_code, second_code) = tokio::join!(
        call_tool_error_code(&first, "weather"),
        call_tool_error_code(&second, "weather")
    );
    // Both deny: the first by its explicit rule, the second by the
    // fail-closed no-match posture. The audit trails show the difference.
    assert_eq!(first_code, Some(POLICY_DENIED));
    assert_eq!(second_code, Some(POLICY_DENIED));

    let (first_events, second_events) = (audit_events(&first).await, audit_events(&second).await);
    assert!(!first_events.is_empty() && !second_events.is_empty());
    assert!(
        first_events
            .iter()
            .all(|event| event["policy_revision"] == revision.as_str())
    );
    assert!(
        second_events
            .iter()
            .all(|event| event["reason_code"] == "governance_no_match"
                && event["policy_revision"].is_null())
    );
}
