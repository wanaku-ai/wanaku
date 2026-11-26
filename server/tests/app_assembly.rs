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
use wanaku_types::registry::{
    ForwardRegistry, PromptRegistry, ResourceRegistry, ToolEntry, ToolRegistry,
};

const POLICY_DENIED: i64 = -32003;
const EVALUATOR_DENIED: i64 = -32001;

/// Build an application from the embedded pipeline configuration, with
/// in-memory persistence and a broker that has no secret resolvers.
fn build_app() -> App {
    assemble_app(None).expect("application assembles")
}

fn assemble_app(wanaku_config: Option<&serde_yaml::Value>) -> Result<App, String> {
    let pipeline = wanaku_server::load_config(None).expect("embedded pipeline configuration");
    build_with_pipeline(&pipeline, wanaku_config)
}

fn build_with_pipeline(
    pipeline: &praxis_core::config::Config,
    wanaku_config: Option<&serde_yaml::Value>,
) -> Result<App, String> {
    App::build(
        &AppSettings {
            pipeline,
            wanaku_config,
            governance: GovernanceConfig::default(),
            plugins_dir: None,
        },
        AppResources {
            persistence: AppPersistence::default(),
            broker: Arc::new(CredentialBroker::new(ResolverRegistry::new())),
        },
    )
}

const CONNECTOR_CONFIG: &str = r#"
connector_profiles:
  - identity: {name: partner, version: 1}
    provider_key: partner
    transport: mcp_streamable_http
    address: {require_tls: true}
    credentials:
      - purpose: discovery
        allowed_mechanisms: [{type: bearer}]
connector_instances:
  - profile: {name: partner, version: 1}
    name: connector-a
    namespace: default
    address: https://unused.example/mcp
    credential_bindings: {discovery: binding-a}
bindings:
  - id: binding-a
    forward_id: connector-a
    origin: https://unused.example
    mechanism: {type: bearer}
    secret_refs: ["env:PARTNER_TOKEN"]
    allowed_purposes: [discovery]
"#;

#[test]
fn startup_validates_connectors_with_brokerage_metadata_without_publishing() {
    let config = serde_yaml::from_str(CONNECTOR_CONFIG).unwrap();
    let app = assemble_app(Some(&config)).unwrap();
    let registry = &app.registry;
    assert!(registry.list_forwards().is_empty());
    assert_eq!(
        (
            registry.tool_count(),
            registry.resource_count(),
            registry.prompt_count()
        ),
        (0, 0, 0)
    );
}

#[test]
fn startup_rejects_connector_profile_violations_before_discovery() {
    let collision = format!(
        "{CONNECTOR_CONFIG}\nforwards: [{{name: connector-a, address: 'https://unused.example/mcp'}}]"
    );
    for (yaml, expected) in [
        (
            CONNECTOR_CONFIG.replace("https://unused.example/mcp", "http://unused.example/mcp"),
            "field \"address\"",
        ),
        (
            CONNECTOR_CONFIG.replace(
                "origin: https://unused.example",
                "origin: https://other.example",
            ),
            "binding is missing or incompatible",
        ),
        (
            CONNECTOR_CONFIG.replace(
                "profile: {name: partner, version: 1}",
                "profile: {name: partner, version: 2}",
            ),
            "unknown profile version",
        ),
        (collision, "already in use by a forward"),
    ] {
        let config = serde_yaml::from_str(&yaml).unwrap();
        let error = assemble_app(Some(&config)).err().unwrap();
        assert!(error.contains(expected), "{error}");
    }
}

#[tokio::test]
async fn action_policy_denial_precedes_external_integrity_inspection() {
    let mut config = wanaku_server::load_config(None).expect("embedded pipeline configuration");
    let router = config
        .filter_chains
        .iter_mut()
        .find(|chain| chain.name == "mcp_router")
        .unwrap();
    for filter in &mut router.filters {
        match filter.filter_type.as_str() {
            "wanaku_mcp_id" => {
                filter.config = serde_yaml::from_str("max_body_bytes: 2097152").unwrap();
            }
            "wanaku_mcp_integrity" => {
                filter.config = serde_yaml::from_str(
                    "external: {id: test, url: 'https://scanner.invalid', kinds: [tool_arguments], action: block}",
                ).unwrap();
            }
            _ => {}
        }
    }
    let app = build_with_pipeline(&config, None).expect("application assembles");
    assert_eq!(
        app.pipelines
            .get("mcp")
            .expect("MCP listener pipeline")
            .load()
            .body_capabilities()
            .request_body_mode,
        praxis_filter::BodyMode::StreamBuffer {
            max_bytes: Some(2_097_152)
        }
    );
    register_tool(&app, "weather");
    let call = json!({
        "jsonrpc":"2.0","id":7,"method":"tools/call",
        "params":{"name":"weather","arguments":{"payload":"x".repeat(1_048_576)}}
    });

    put_policy(&app, "deny-weather", "deny").await;
    assert_eq!(
        mcp(&app, &call).await.unwrap()["error"]["code"],
        POLICY_DENIED
    );
    assert!(
        audit_events(&app)
            .await
            .iter()
            .all(|event| event["reason_code"] != "mcp_content_integrity")
    );

    put_policy(&app, "allow-weather", "allow").await;
    assert_eq!(
        mcp(&app, &call).await.unwrap()["error"]["code"],
        POLICY_DENIED
    );
    assert!(audit_events(&app).await.iter().any(|event| {
        event["attributes"]["scanner_failure"]["reason"] == "scanner_request_size_limit"
    }));
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
    mcp_at(app, "/default/mcp", request).await
}

async fn mcp_at(app: &App, path: &str, request: &Value) -> Option<Value> {
    protocol_request(app, "mcp", http::Method::POST, path, request).await
}

async fn protocol_request(
    app: &App,
    listener: &str,
    method: http::Method,
    path: &str,
    request: &Value,
) -> Option<Value> {
    let swap = app.pipelines.get(listener).expect("listener pipeline");
    let mut protocol = PingoraRequestCtx::default();
    let pipeline = protocol.pin_pipeline(swap);
    let mut headers = http::HeaderMap::new();
    headers.insert(
        http::header::CONTENT_TYPE,
        "application/json".parse().unwrap(),
    );
    let http_request = praxis_filter::Request {
        method,
        uri: path.parse().expect("MCP path"),
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

#[tokio::test]
async fn virtual_a2a_catalog_tracks_management_registration_and_removal() {
    let app = build_app();
    for namespace in ["kafka", "flink", "private"] {
        let body =
            json!({"name":"worker","namespace":namespace,"address":"http://unused.example/rpc"})
                .to_string();
        let (status, response) = manage(&app, "POST", "/api/v1/agents", Some(&body)).await;
        assert_eq!(status, 200, "{response}");
    }
    let (status, response) = manage(
        &app,
        "POST",
        "/api/v1/namespaces",
        Some(r#"{"name":"integration","members":["kafka","flink"]}"#),
    )
    .await;
    assert_eq!(status, 200, "{response}");
    let path = "/virtual-ns/integration/a2a";
    let catalog = protocol_request(&app, "a2a", http::Method::GET, path, &Value::Null)
        .await
        .unwrap();
    let agents = catalog["agents"].as_array().unwrap();
    assert_eq!(agents.len(), 2);
    assert_eq!(agents[0]["name"], "flink/worker");
    assert_eq!(agents[1]["name"], "kafka/worker");
    assert!(
        agents[1]["url"]
            .as_str()
            .unwrap()
            .ends_with("/virtual-ns/integration/a2a/kafka/worker")
    );
    assert_eq!(
        manage(&app, "DELETE", "/api/v1/agents/kafka/worker", None)
            .await
            .0,
        200
    );
    let catalog = protocol_request(&app, "a2a", http::Method::GET, path, &Value::Null)
        .await
        .unwrap();
    assert_eq!(catalog["agents"].as_array().unwrap().len(), 1);
    assert_eq!(catalog["agents"][0]["name"], "flink/worker");
}

#[tokio::test]
async fn virtual_catalogs_preserve_collisions_and_track_forward_removal() {
    let app = build_app();
    for name in ["kafka", "flink"] {
        let body = json!({"name":name}).to_string();
        assert_eq!(
            manage(&app, "POST", "/api/v1/namespaces", Some(&body))
                .await
                .0,
            200
        );
        let forward = format!("{name}-forward");
        app.registry.register_forward(
            serde_json::from_value(json!({
                "name":forward,"namespace":name,"address":"http://unused.example/mcp"
            }))
            .unwrap(),
        );
        app.registry.register_tool(serde_json::from_value(json!({
                "name":"health-check", "description":"health", "inputSchema":{"type":"object"}, "namespace":name,"uri":"health-check","type":"mcp-forward", "forwardId":forward
        })).unwrap());
        app.registry.register_resource(serde_json::from_value(json!({
            "name":"report", "namespace":name,"location":"file:///report.csv","type":"mcp-forward", "forwardId":forward
        })).unwrap());
        app.registry.register_prompt(
            serde_json::from_value(json!({
                "name":"summary", "namespace":name,"type":"mcp-forward", "forwardId":forward
            }))
            .unwrap(),
        );
    }
    let (status, response) = manage(
        &app,
        "POST",
        "/api/v1/namespaces",
        Some(r#"{"name":"integration","members":["kafka","flink"]}"#),
    )
    .await;
    assert_eq!(status, 200, "{response}");
    let path = "/virtual-ns/integration/mcp";
    for (method, array, names) in [
        (
            "tools/list",
            "tools",
            ["flink/health-check", "kafka/health-check"],
        ),
        (
            "resources/list",
            "resources",
            ["flink/report", "kafka/report"],
        ),
        (
            "prompts/list",
            "prompts",
            ["flink/summary", "kafka/summary"],
        ),
    ] {
        let response = mcp_at(&app, path, &json!({"jsonrpc":"2.0","id":1,"method":method}))
            .await
            .unwrap();
        let entries = response["result"][array].as_array().expect("catalog array");
        let mut listed: Vec<_> = entries
            .iter()
            .map(|entry| entry["name"].as_str().unwrap())
            .collect();
        listed.sort_unstable();
        assert_eq!(listed, names);
        if method == "resources/list" {
            for entry in entries {
                assert!(entry["uri"].as_str().unwrap().starts_with("wanaku://"));
            }
        }
    }
    assert_eq!(
        manage(&app, "DELETE", "/api/v1/forwards/kafka-forward", None)
            .await
            .0,
        200
    );
    for (method, array) in [
        ("tools/list", "tools"),
        ("resources/list", "resources"),
        ("prompts/list", "prompts"),
    ] {
        let response = mcp_at(&app, path, &json!({"jsonrpc":"2.0","id":2,"method":method}))
            .await
            .unwrap();
        let entries = response["result"][array].as_array().unwrap();
        assert_eq!(entries.len(), 1);
        assert!(entries[0]["name"].as_str().unwrap().starts_with("flink/"));
    }
    // The concrete endpoint keeps the original names, and the virtual name
    // cannot be used as a concrete endpoint.
    let concrete = mcp_at(
        &app,
        "/flink/mcp",
        &json!({"jsonrpc":"2.0","id":3,"method":"tools/list"}),
    )
    .await
    .unwrap();
    assert_eq!(concrete["result"]["tools"][0]["name"], "health-check");
    let wrong = mcp_at(
        &app,
        "/integration/mcp",
        &json!({"jsonrpc":"2.0","id":4,"method":"tools/list"}),
    )
    .await
    .unwrap();
    assert!(wrong.get("error").is_some());
}

#[tokio::test]
async fn virtual_calls_enforce_both_namespace_policies_after_resolution() {
    let app = build_app();
    register_tool(&app, "weather");
    assert_eq!(
        manage(
            &app,
            "POST",
            "/api/v1/namespaces",
            Some(r#"{"name":"integration","members":["default"]}"#)
        )
        .await
        .0,
        200
    );
    for denied_scope in ["default", "integration"] {
        let policy = json!({"policy":{"rules":[
            {"id":"allow","effect":"allow","selectors":{"operation":"tools/call"}},
            {"id":"deny","effect":"deny","selectors":{"operation":"tools/call","namespace":denied_scope}}
        ]}}).to_string();
        let (status, activation) =
            manage(&app, "PUT", "/api/v1/action-policies", Some(&policy)).await;
        assert_eq!(status, 200, "{activation}");
        let revision = activation["data"]["revision"]["id"]
            .as_u64()
            .unwrap()
            .to_string();
        let response = mcp_at(&app, "/virtual-ns/integration/mcp", &json!({
            "jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"default/weather","arguments":{}}
        })).await.unwrap();
        assert_eq!(response["error"]["code"], POLICY_DENIED);
        let decisions: Vec<_> = audit_events(&app)
            .await
            .into_iter()
            .filter(|event| {
                event["operation"] == "tools/call" && event["policy_revision"] == revision
            })
            .collect();
        assert_eq!(decisions.len(), 2, "{decisions:?}");
        for event in decisions {
            assert_eq!(event["attributes"]["concrete_namespace"], "default");
            assert_eq!(event["attributes"]["virtual_namespace"], "integration");
        }
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
