#![allow(clippy::unwrap_used, clippy::expect_used, clippy::panic)]
//! End-to-end tests for the evaluator engine against a running server.
//!
//! These tests hit the management API (port 8080) and MCP endpoint (port 8081).
//! They are gated with `#[ignore]` — run them with:
//!
//! ```sh
//! # Start the server and build WASM actions first, then:
//! cargo test -p wanaku-feature-evaluator --test e2e -- --ignored
//! ```
//!
//! Tests that also need Ollama (LLM classification) are in the `classification` module.

use std::time::Duration;

use reqwest::blocking::Client;
use serde_json::{Value, json};

// ---- config ---------------------------------------------------------

const MGMT_URL: &str = "http://localhost:8080";
const MCP_URL: &str = "http://localhost:8081";

// ---- helpers --------------------------------------------------------

struct TestHarness {
    client: Client,
    registered_tools: Vec<String>,
}

impl TestHarness {
    fn new() -> Self {
        let client = Client::builder()
            .timeout(Duration::from_secs(10))
            .build()
            .expect("failed to build HTTP client");
        Self {
            client,
            registered_tools: Vec::new(),
        }
    }

    fn server_is_reachable(&self) -> bool {
        self.client
            .get(format!("{MGMT_URL}/api/v1/tools"))
            .send()
            .is_ok_and(|r| r.status().is_success())
    }

    fn ollama_is_reachable(&self) -> bool {
        self.client
            .get("http://localhost:11434/v1/models")
            .send()
            .is_ok_and(|r| r.status().is_success())
    }

    fn register_tool(&mut self, name: &str, description: &str, namespace: Option<&str>) {
        let mut body = json!({
            "name": name,
            "description": description,
            "uri": format!("{MCP_URL}/mcp"),
            "type": "mcp-forward",
            "inputSchema": {"type": "object", "properties": {}}
        });
        if let Some(ns) = namespace {
            body["namespace"] = json!(ns);
        }

        let resp = self.client
            .post(format!("{MGMT_URL}/api/v1/tools"))
            .json(&body)
            .send()
            .expect("failed to register tool");
        assert!(resp.status().is_success(), "register tool {name} failed: {}", resp.status());
        self.registered_tools.push(name.to_owned());
    }

    fn configure_evaluators(&self, config: &Value) {
        let resp = self.client
            .put(format!("{MGMT_URL}/api/v1/evaluators"))
            .json(config)
            .send()
            .expect("failed to configure evaluators");
        assert!(resp.status().is_success(), "configure evaluators failed: {}", resp.status());
    }

    fn clear_evaluators(&self) {
        let _ = self.client
            .put(format!("{MGMT_URL}/api/v1/evaluators"))
            .json(&json!({"evaluators": []}))
            .send();
    }

    fn list_evaluators(&self) -> Value {
        self.client
            .get(format!("{MGMT_URL}/api/v1/evaluators"))
            .send()
            .expect("failed to list evaluators")
            .json::<Value>()
            .expect("failed to parse evaluators response")
    }

    /// Returns `None` on timeout (upstream unreachable — proves evaluator didn't block).
    fn call_tool(&self, name: &str, arguments: &Value, namespace: &str) -> Option<Value> {
        let mcp_path = if namespace == "default" {
            format!("{MCP_URL}/mcp")
        } else {
            format!("{MCP_URL}/{namespace}/mcp")
        };

        let body = json!({
            "jsonrpc": "2.0",
            "id": 1,
            "method": "tools/call",
            "params": {
                "name": name,
                "arguments": arguments
            }
        });

        match self.client.post(mcp_path).json(&body).send() {
            Ok(resp) => Some(resp.json::<Value>().expect("failed to parse MCP response")),
            Err(e) if e.is_timeout() => None,
            Err(e) => panic!("MCP request failed: {e}"),
        }
    }

    fn error_code(resp: &Value) -> Option<i64> {
        resp.get("error")
            .and_then(|e| e.get("code"))
            .and_then(Value::as_i64)
    }

    fn was_blocked(resp: &Option<Value>) -> bool {
        resp.as_ref()
            .and_then(|v| Self::error_code(v))
            .is_some_and(|c| c == -32001 || c == -32002)
    }
}

impl Drop for TestHarness {
    fn drop(&mut self) {
        for tool in &self.registered_tools {
            let _ = self.client
                .delete(format!("{MGMT_URL}/api/v1/tools/{tool}"))
                .send();
        }
        self.clear_evaluators();
    }
}

// =====================================================================
// Evaluator engine e2e (server + WASM, no LLM required)
// =====================================================================

mod engine {
    use super::*;
    use serial_test::serial;
    use std::path::PathBuf;

    fn wasm_path(name: &str) -> String {
        PathBuf::from(env!("CARGO_MANIFEST_DIR"))
            .join("../../actions/dist")
            .join(name)
            .to_string_lossy()
            .into_owned()
    }

    #[test]
    #[serial]
    #[ignore = "requires running wanaku server"]
    fn evaluators_api_crud() {
        let harness = TestHarness::new();
        if !harness.server_is_reachable() {
            eprintln!("SKIP: wanaku server not running");
            return;
        }

        // List — starts empty or with existing config
        let resp = harness.list_evaluators();
        assert!(resp.get("data").is_some(), "GET /evaluators should have data field");

        // Configure
        harness.configure_evaluators(&json!({
            "evaluators": [{
                "name": "e2e-test",
                "trigger": {"method": "tools/call"},
                "llm": {
                    "operation": "classify",
                    "prompt": "test",
                    "model": "test",
                    "url": "http://localhost:11434/v1"
                },
                "processor": {"path": "/nonexistent.wasm"},
                "on_error": "continue"
            }]
        }));

        let resp = harness.list_evaluators();
        let evaluators = resp["data"].as_array().expect("data should be array");
        assert!(
            evaluators.iter().any(|e| e["name"] == "e2e-test"),
            "configured evaluator should appear in list"
        );

        // Clear
        harness.clear_evaluators();
        let resp = harness.list_evaluators();
        let evaluators = resp["data"].as_array().expect("data should be array");
        assert!(evaluators.is_empty(), "evaluators should be empty after clear");
    }

    #[test]
    #[serial]
    #[ignore = "requires running wanaku server and pre-built WASM actions"]
    fn safety_block_wasm_blocks_tool_call() {
        let mut harness = TestHarness::new();
        if !harness.server_is_reachable() {
            eprintln!("SKIP: wanaku server not running");
            return;
        }

        let block_wasm = wasm_path("safety_block_action.wasm");

        harness.register_tool("e2e-restart-db", "Restart a production database", None);

        // Configure evaluator with safety-block WASM.
        // LLM URL points to localhost:11434 — if Ollama isn't running,
        // llm_result will be empty, but safety-block always blocks anyway.
        harness.configure_evaluators(&json!({
            "evaluators": [{
                "name": "e2e-safety",
                "trigger": {"method": "tools/call"},
                "llm": {
                    "operation": "classify",
                    "prompt": "Classify as green/yellow/red",
                    "model": "llama3.2",
                    "url": "http://localhost:11434/v1"
                },
                "processor": {"path": block_wasm},
                "on_error": "continue"
            }]
        }));

        let resp = harness.call_tool(
            "e2e-restart-db",
            &json!({"target": "prod-primary"}),
            "default",
        );

        assert!(
            TestHarness::was_blocked(&resp),
            "safety-block should block the request, got: {resp:?}"
        );
    }

    #[test]
    #[serial]
    #[ignore = "requires running wanaku server and pre-built WASM actions"]
    fn safety_warn_wasm_does_not_block() {
        let mut harness = TestHarness::new();
        if !harness.server_is_reachable() {
            eprintln!("SKIP: wanaku server not running");
            return;
        }

        let warn_wasm = wasm_path("safety_warn_action.wasm");

        harness.register_tool("e2e-scale-app", "Scale an application", None);

        harness.configure_evaluators(&json!({
            "evaluators": [{
                "name": "e2e-warn",
                "trigger": {"method": "tools/call"},
                "llm": {
                    "operation": "classify",
                    "prompt": "Classify",
                    "model": "llama3.2",
                    "url": "http://localhost:11434/v1"
                },
                "processor": {"path": warn_wasm},
                "on_error": "continue"
            }]
        }));

        // warn() lets the request continue — timeout or non-error response
        // both prove it wasn't blocked
        let resp = harness.call_tool(
            "e2e-scale-app",
            &json!({"replicas": "3"}),
            "default",
        );

        assert!(
            !TestHarness::was_blocked(&resp),
            "safety-warn should NOT block, got: {resp:?}"
        );
    }

    #[test]
    #[serial]
    #[ignore = "requires running wanaku server and pre-built WASM actions"]
    fn cleared_evaluator_does_not_block() {
        let mut harness = TestHarness::new();
        if !harness.server_is_reachable() {
            eprintln!("SKIP: wanaku server not running");
            return;
        }

        let block_wasm = wasm_path("safety_block_action.wasm");

        harness.register_tool("e2e-clear-test", "Test clearing evaluators", None);

        harness.configure_evaluators(&json!({
            "evaluators": [{
                "name": "e2e-block-then-clear",
                "trigger": {"method": "tools/call"},
                "llm": {
                    "operation": "classify",
                    "prompt": "Classify",
                    "model": "llama3.2",
                    "url": "http://localhost:11434/v1"
                },
                "processor": {"path": block_wasm},
                "on_error": "continue"
            }]
        }));

        harness.clear_evaluators();

        let resp = harness.call_tool(
            "e2e-clear-test",
            &json!({"target": "prod"}),
            "default",
        );

        assert!(
            !TestHarness::was_blocked(&resp),
            "tool call should pass after clearing evaluators, got: {resp:?}"
        );
    }

    #[test]
    #[serial]
    #[ignore = "requires running wanaku server and pre-built WASM actions"]
    fn evaluator_with_result_schema_config() {
        let mut harness = TestHarness::new();
        if !harness.server_is_reachable() {
            eprintln!("SKIP: wanaku server not running");
            return;
        }

        let block_wasm = wasm_path("safety_block_action.wasm");

        harness.register_tool("e2e-schema-test", "Test schema validation config", None);

        harness.configure_evaluators(&json!({
            "evaluators": [{
                "name": "e2e-schema",
                "trigger": {"method": "tools/call"},
                "llm": {
                    "operation": "classify",
                    "prompt": "Classify as green/yellow/red. Respond with JSON: {\"level\": \"green|yellow|red\", \"reason\": \"brief\"}",
                    "model": "llama3.2",
                    "url": "http://localhost:11434/v1",
                    "result_schema": {
                        "type": "object",
                        "properties": {
                            "level": {"type": "string", "enum": ["green", "yellow", "red"]},
                            "reason": {"type": "string"}
                        },
                        "required": ["level", "reason"]
                    }
                },
                "processor": {"path": block_wasm},
                "on_error": "continue"
            }]
        }));

        let resp = harness.list_evaluators();
        let evaluators = resp["data"].as_array().expect("data should be array");
        let eval = evaluators.iter().find(|e| e["name"] == "e2e-schema");
        assert!(eval.is_some(), "evaluator should be configured");

        let schema = &eval.unwrap()["llm"]["result_schema"];
        assert!(schema.is_object(), "result_schema should be present in config");
        assert_eq!(schema["required"][0], "level");

        let resp = harness.call_tool(
            "e2e-schema-test",
            &json!({"action": "test"}),
            "default",
        );

        assert!(
            TestHarness::was_blocked(&resp),
            "safety-block should block even with schema configured, got: {resp:?}"
        );
    }

    #[test]
    #[serial]
    #[ignore = "requires running wanaku server and pre-built WASM actions"]
    fn namespace_scoped_evaluator() {
        let mut harness = TestHarness::new();
        if !harness.server_is_reachable() {
            eprintln!("SKIP: wanaku server not running");
            return;
        }

        let block_wasm = wasm_path("safety_block_action.wasm");

        harness.register_tool("e2e-ns-tool", "Namespace test tool", Some("e2e-ns"));

        harness.configure_evaluators(&json!({
            "evaluators": [{
                "name": "e2e-ns-gate",
                "trigger": {
                    "method": "tools/call",
                    "namespace": "e2e-ns"
                },
                "llm": {
                    "operation": "classify",
                    "prompt": "Classify",
                    "model": "llama3.2",
                    "url": "http://localhost:11434/v1"
                },
                "processor": {"path": block_wasm},
                "on_error": "continue"
            }]
        }));

        let resp = harness.call_tool(
            "e2e-ns-tool",
            &json!({"target": "prod"}),
            "e2e-ns",
        );

        assert!(
            TestHarness::was_blocked(&resp),
            "scoped evaluator should block in matching namespace, got: {resp:?}"
        );
    }
}

// =====================================================================
// Classification e2e (server + WASM + Ollama)
// =====================================================================

mod classification {
    use super::*;
    use serial_test::serial;
    use std::path::PathBuf;

    fn wasm_path(name: &str) -> String {
        PathBuf::from(env!("CARGO_MANIFEST_DIR"))
            .join("../../actions/dist")
            .join(name)
            .to_string_lossy()
            .into_owned()
    }

    fn configure_safety_evaluator(harness: &TestHarness, namespace: &str) {
        let block_wasm = wasm_path("safety_block_action.wasm");

        harness.configure_evaluators(&json!({
            "evaluators": [{
                "name": "e2e-classify",
                "trigger": {
                    "method": "tools/call",
                    "namespace": namespace
                },
                "llm": {
                    "operation": "classify",
                    "prompt": "You are a strict safety classifier. Classify this tool call as green (safe, read-only), yellow (elevated, writes), or red (dangerous, database restarts, scaling to zero). Restarting production databases is ALWAYS red. Respond with ONLY: {\"level\": \"green|yellow|red\", \"reason\": \"brief\"}",
                    "model": "llama3.2",
                    "url": "http://localhost:11434/v1",
                    "result_schema": {
                        "type": "object",
                        "properties": {
                            "level": {"type": "string", "enum": ["green", "yellow", "red"]},
                            "reason": {"type": "string"}
                        },
                        "required": ["level", "reason"]
                    }
                },
                "processor": {"path": block_wasm},
                "on_error": "continue"
            }]
        }));
    }

    #[test]
    #[serial]
    #[ignore = "requires running wanaku server, pre-built WASM actions, and Ollama"]
    fn dangerous_restart_is_blocked() {
        let mut harness = TestHarness::new();
        if !harness.server_is_reachable() || !harness.ollama_is_reachable() {
            eprintln!("SKIP: server or Ollama not running");
            return;
        }

        let ns = "e2e-classify";
        harness.register_tool("restart-database", "Restart a production database instance", Some(ns));
        configure_safety_evaluator(&harness, ns);

        let resp = harness.call_tool(
            "restart-database",
            &json!({"target": "production-primary", "server": "db-master-01"}),
            ns,
        );

        assert!(
            TestHarness::was_blocked(&resp),
            "dangerous restart should be blocked, got: {resp:?}"
        );
    }
}
