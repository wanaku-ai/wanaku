#![allow(clippy::unwrap_used, clippy::expect_used, clippy::panic)]
//! Integration tests for the evaluator engine.
//!
//! Tests that need pre-built WASM actions skip automatically if the
//! `.wasm` files are not found. Build them with:
//!
//! ```sh
//! cd actions/safety-review && cargo component build --release && cp target/wasm32-wasip1/release/*.wasm ../dist/
//! ```

use std::path::PathBuf;

use wanaku_feature_evaluator::action::ActionResult;
use wanaku_feature_evaluator::config::{
    ErrorPolicy, EvaluatorDef, EvaluatorsConfig, LlmConnection, LlmDef, LlmOperation, ProcessorRef,
    TriggerDef,
};
use wanaku_feature_evaluator::revision::RevisionOrigin;
use wanaku_feature_evaluator::schema::validate_against_schema;
use wanaku_feature_evaluator::state::EvaluatorState;

// ---- helpers --------------------------------------------------------

fn safety_evaluator(name: &str, method: &str, namespace: Option<&str>) -> EvaluatorDef {
    EvaluatorDef {
        name: name.to_owned(),
        trigger: TriggerDef {
            method: method.to_owned(),
            namespace: namespace.map(str::to_owned),
        },
        llm: LlmDef {
            operation: LlmOperation::Classify,
            prompt: "test prompt".to_owned(),
            connection: "test-connection".to_owned(),
            result_schema: None,
        },
        processor: ProcessorRef {
            path: PathBuf::from("/nonexistent/test.wasm"),
        },
        on_error: ErrorPolicy::Continue,
    }
}

fn safety_evaluator_with_schema(name: &str) -> EvaluatorDef {
    let mut eval = safety_evaluator(name, "tools/call", None);
    eval.llm.result_schema = Some(serde_json::json!({
        "type": "object",
        "properties": {
            "level": { "type": "string", "enum": ["green", "yellow", "red"] },
            "reason": { "type": "string" }
        },
        "required": ["level", "reason"]
    }));
    eval
}

fn wasm_path(name: &str) -> PathBuf {
    PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("../../actions/dist")
        .join(name)
}

// =====================================================================
// Config parsing
// =====================================================================

mod config {
    use super::*;

    #[test]
    fn deserialize_evaluator_without_schema() {
        let yaml = r#"
evaluators:
  - name: "test"
    trigger:
      method: "tools/call"
    llm:
      operation: classify
      prompt: "classify this"
      connection: "local-llama"
    processor:
      path: "/wasm/test.wasm"
"#;
        let config: EvaluatorsConfig = serde_yaml::from_str(yaml).unwrap();
        assert_eq!(config.evaluators.len(), 1);
        assert!(config.evaluators[0].llm.result_schema.is_none());
    }

    #[test]
    fn deserialize_evaluator_with_result_schema() {
        let yaml = r#"
evaluators:
  - name: "safety"
    trigger:
      method: "tools/call"
      namespace: "production"
    llm:
      operation: classify
      prompt: "classify this"
      connection: "local-llama"
      result_schema:
        type: object
        properties:
          level:
            type: string
            enum: ["green", "yellow", "red"]
          reason:
            type: string
        required: ["level", "reason"]
    processor:
      path: "/wasm/safety.wasm"
    on_error: block
"#;
        let config: EvaluatorsConfig = serde_yaml::from_str(yaml).unwrap();
        let eval = &config.evaluators[0];

        assert_eq!(eval.name, "safety");
        assert_eq!(eval.trigger.namespace.as_deref(), Some("production"));

        let schema = eval.llm.result_schema.as_ref().unwrap();
        assert_eq!(schema["type"], "object");
        assert!(schema["required"].as_array().unwrap().contains(&serde_json::json!("level")));
        assert!(schema["required"].as_array().unwrap().contains(&serde_json::json!("reason")));
    }

    #[test]
    fn config_round_trip_json() {
        let eval = safety_evaluator_with_schema("round-trip");
        let json = serde_json::to_string(&eval).unwrap();
        let deserialized: EvaluatorDef = serde_json::from_str(&json).unwrap();

        assert_eq!(deserialized.name, "round-trip");
        assert!(deserialized.llm.result_schema.is_some());
        let schema = deserialized.llm.result_schema.unwrap();
        assert_eq!(schema["properties"]["level"]["type"], "string");
    }

    #[test]
    fn rejects_legacy_inline_connection_fields() {
        // Connection details (model/url/api_key) moved to named, config-only
        // LlmConnection entries. An evaluator payload embedding them inline
        // (the pre-security-fix shape) must be rejected, not silently
        // stripped, so callers get a clear signal instead of a dropped key.
        let json = r#"{
            "name": "legacy",
            "trigger": {"method": "tools/call"},
            "llm": {
                "operation": "classify",
                "prompt": "classify this",
                "model": "llama3.2",
                "url": "http://localhost:11434/v1",
                "api_key": "secret-token"
            },
            "processor": {"path": "/wasm/test.wasm"}
        }"#;
        let err = serde_json::from_str::<EvaluatorDef>(json).unwrap_err();
        assert!(err.to_string().contains("unknown field"));
    }

    #[test]
    fn default_on_error_is_continue() {
        let yaml = r#"
evaluators:
  - name: "minimal"
    trigger:
      method: "tools/call"
    llm:
      operation: classify
      prompt: "test"
      connection: "local-llama"
    processor:
      path: "/wasm/t.wasm"
"#;
        let config: EvaluatorsConfig = serde_yaml::from_str(yaml).unwrap();
        assert!(matches!(config.evaluators[0].on_error, ErrorPolicy::Continue));
    }
}

// =====================================================================
// Trigger matching
// =====================================================================

mod trigger {
    use super::*;

    #[test]
    fn matches_method_only() {
        let trigger = TriggerDef {
            method: "tools/call".to_owned(),
            namespace: None,
        };
        assert!(trigger.matches("tools/call", "default"));
        assert!(trigger.matches("tools/call", "finance"));
        assert!(!trigger.matches("tools/list", "default"));
    }

    #[test]
    fn matches_method_and_namespace() {
        let trigger = TriggerDef {
            method: "tools/call".to_owned(),
            namespace: Some("production".to_owned()),
        };
        assert!(trigger.matches("tools/call", "production"));
        assert!(!trigger.matches("tools/call", "staging"));
        assert!(!trigger.matches("tools/list", "production"));
    }
}

// =====================================================================
// EvaluatorState management
// =====================================================================

mod state {
    use super::*;

    #[test]
    fn load_and_list_evaluators() {
        let state = EvaluatorState::new();
        assert!(state.list_evaluators().is_empty());

        let defs = vec![
            safety_evaluator("eval-1", "tools/call", None),
            safety_evaluator("eval-2", "tools/list", Some("finance")),
        ];
        state.load_evaluators(defs);

        let listed = state.list_evaluators();
        assert_eq!(listed.len(), 2);
    }

    #[test]
    fn find_matching_by_method() {
        let state = EvaluatorState::new();
        state.load_evaluators(vec![
            safety_evaluator("call-gate", "tools/call", None),
            safety_evaluator("list-gate", "tools/list", None),
        ]);

        let found = state.find_matching("tools/call", "default");
        assert!(found.is_some());
        assert_eq!(found.unwrap().name, "call-gate");

        let found = state.find_matching("tools/list", "default");
        assert!(found.is_some());
        assert_eq!(found.unwrap().name, "list-gate");
    }

    #[test]
    fn find_matching_respects_namespace() {
        let state = EvaluatorState::new();
        state.load_evaluators(vec![
            safety_evaluator("prod-gate", "tools/call", Some("production")),
        ]);

        assert!(state.find_matching("tools/call", "production").is_some());
        assert!(state.find_matching("tools/call", "staging").is_none());
    }

    #[test]
    fn find_matching_no_match_returns_none() {
        let state = EvaluatorState::new();
        state.load_evaluators(vec![
            safety_evaluator("gate", "tools/call", None),
        ]);
        assert!(state.find_matching("resources/read", "default").is_none());
    }

    #[test]
    fn reload_replaces_evaluators() {
        let state = EvaluatorState::new();
        state.load_evaluators(vec![
            safety_evaluator("first", "tools/call", None),
        ]);
        assert_eq!(state.list_evaluators().len(), 1);

        state.load_evaluators(vec![
            safety_evaluator("second", "tools/list", None),
            safety_evaluator("third", "resources/read", None),
        ]);
        let listed = state.list_evaluators();
        assert_eq!(listed.len(), 2);
        assert!(state.find_matching("tools/call", "default").is_none());
        assert!(state.find_matching("tools/list", "default").is_some());
    }

    #[test]
    fn clear_evaluators() {
        let state = EvaluatorState::new();
        state.load_evaluators(vec![
            safety_evaluator("gate", "tools/call", None),
        ]);
        assert_eq!(state.list_evaluators().len(), 1);

        state.load_evaluators(vec![]);
        assert!(state.list_evaluators().is_empty());
        assert!(state.find_matching("tools/call", "default").is_none());
    }

    #[test]
    fn namespace_bindings() {
        let state = EvaluatorState::new();

        assert!(state.get_binding("finance").is_none());

        state.bind_namespace("finance", "conv-123");
        assert_eq!(state.get_binding("finance").as_deref(), Some("conv-123"));

        state.unbind_namespace("finance");
        assert!(state.get_binding("finance").is_none());
    }

    #[test]
    fn list_bindings() {
        let state = EvaluatorState::new();
        state.bind_namespace("finance", "conv-1");
        state.bind_namespace("engineering", "conv-2");

        let bindings = state.list_bindings();
        assert_eq!(bindings.len(), 2);
        assert_eq!(bindings.get("finance").unwrap(), "conv-1");
        assert_eq!(bindings.get("engineering").unwrap(), "conv-2");
    }

    fn test_connection() -> LlmConnection {
        LlmConnection {
            name: "test-connection".to_owned(),
            model: "llama3.2".to_owned(),
            url: "http://localhost:11434/v1".to_owned(),
            api_key: String::new(),
        }
    }

    #[test]
    fn activation_installs_snapshot_matching_revision() {
        let path = wasm_path("safety_review_action.wasm");
        if !path.exists() {
            eprintln!("SKIP: {} not found — build WASM actions first", path.display());
            return;
        }

        let state = EvaluatorState::new();
        state.load_llm_connections(vec![test_connection()]).unwrap();

        let mut eval = safety_evaluator("gate", "tools/call", None);
        eval.processor.path = path.clone();

        let revision = state
            .try_activate(vec![eval], RevisionOrigin::Api, None, None)
            .expect("activation should succeed");

        // The active snapshot (evaluators + compiled modules) and the recorded
        // revision must describe the same configuration.
        let listed = state.list_evaluators();
        assert_eq!(listed.len(), 1);
        assert_eq!(listed[0].name, "gate");
        assert_eq!(revision.evaluators[0].name, listed[0].name);
        assert!(state.get_compiled(&path).is_some());
    }

    #[test]
    #[expect(
        clippy::disallowed_methods,
        reason = "synchronous test needs real OS threads to exercise concurrent activation locking; the tokio guidance does not apply"
    )]
    fn concurrent_activations_keep_snapshot_and_revision_consistent() {
        let path = wasm_path("safety_review_action.wasm");
        if !path.exists() {
            eprintln!("SKIP: {} not found — build WASM actions first", path.display());
            return;
        }

        let state = EvaluatorState::new();
        state.load_llm_connections(vec![test_connection()]).unwrap();

        // Fire many activations at once, each carrying a distinct evaluator
        // name. With `expected_revision` unset none of them conflict, so every
        // thread commits a revision and installs its snapshot.
        let handles: Vec<_> = (0..16)
            .map(|i| {
                let state = state.clone();
                let path = path.clone();
                std::thread::spawn(move || {
                    let mut eval = safety_evaluator(&format!("gate-{i}"), "tools/call", None);
                    eval.processor.path = path;
                    state
                        .try_activate(vec![eval], RevisionOrigin::Api, None, None)
                        .expect("activation should succeed");
                })
            })
            .collect();
        for handle in handles {
            handle.join().expect("activation thread panicked");
        }

        // Whichever revision ended up active, the installed snapshot must match
        // it exactly — never a different revision's configuration.
        let active = state
            .revision_store()
            .active_revision()
            .expect("an active revision must exist");
        let listed = state.list_evaluators();
        assert_eq!(listed.len(), 1);
        assert_eq!(
            listed[0].name, active.evaluators[0].name,
            "active snapshot must match the active revision's configuration"
        );
    }

    #[test]
    fn captured_snapshot_is_immutable_across_activation() {
        let path = wasm_path("safety_review_action.wasm");
        if !path.exists() {
            eprintln!("SKIP: {} not found — build WASM actions first", path.display());
            return;
        }

        let state = EvaluatorState::new();
        state.load_llm_connections(vec![test_connection()]).unwrap();

        let mut first = safety_evaluator_with_schema("gate-old");
        first.processor.path = path.clone();
        state
            .try_activate(vec![first], RevisionOrigin::Api, None, None)
            .expect("first activation should succeed");

        // A request captures one snapshot at its start.
        let request_config = state.active_config();

        // A concurrent activation swaps in an entirely new configuration. The
        // new config carries no result schema, so if the captured snapshot ever
        // read through to the live state its schema lookup would return None.
        let mut second = safety_evaluator("gate-new", "tools/call", None);
        second.processor.path = path.clone();
        state
            .try_activate(vec![second], RevisionOrigin::Api, None, None)
            .expect("second activation should succeed");

        // The captured snapshot still resolves the old definition, its compiled
        // processor, and its compiled result schema — it never mixes in the
        // newly activated artifacts.
        let matched = request_config
            .find_matching("tools/call", "default")
            .expect("old evaluator must still match in the captured snapshot");
        assert_eq!(matched.name, "gate-old");
        assert!(request_config.get_compiled(&path).is_some());
        assert!(
            request_config.get_compiled_schema("gate-old").is_some(),
            "captured snapshot must retain the old evaluator's compiled schema"
        );

        // The live state reflects the new activation.
        assert_eq!(
            state
                .find_matching("tools/call", "default")
                .expect("new evaluator must match live state")
                .name,
            "gate-new"
        );
    }
}

// =====================================================================
// Named LLM connections — config-only, never exposed via the management API
// =====================================================================

mod connections {
    use super::*;

    fn secret_connection() -> LlmConnection {
        LlmConnection {
            name: "test-connection".to_owned(),
            model: "llama3.2".to_owned(),
            url: "http://localhost:11434/v1".to_owned(),
            api_key: "super-secret-token".to_owned(),
        }
    }

    #[test]
    fn try_activate_rejects_unknown_connection() {
        let state = EvaluatorState::new();
        let def = safety_evaluator("gate", "tools/call", None);

        let result = state.try_activate(vec![def], RevisionOrigin::Api, None, None);

        match result {
            Err(err) => assert!(err.to_string().contains("unknown llm connection")),
            Ok(_) => panic!("expected activation to fail for an unregistered connection"),
        }
    }

    #[test]
    fn load_llm_connections_rejects_duplicate_names() {
        let state = EvaluatorState::new();
        let first = secret_connection();
        let mut second = secret_connection();
        second.model = "other-model".to_owned();

        let result = state.load_llm_connections(vec![first, second]);

        assert!(result.is_err());
        assert!(state.list_llm_connections().is_empty());
    }

    #[test]
    fn load_llm_connections_rejects_empty_name() {
        let state = EvaluatorState::new();
        let mut unnamed = secret_connection();
        unnamed.name = String::new();

        assert!(state.load_llm_connections(vec![unnamed]).is_err());
    }

    #[test]
    fn list_llm_connections_is_sorted_by_name() {
        let state = EvaluatorState::new();
        let mut zeta = secret_connection();
        zeta.name = "zeta".to_owned();
        let mut alpha = secret_connection();
        alpha.name = "alpha".to_owned();
        state.load_llm_connections(vec![zeta, alpha]).unwrap();

        let names = state.list_llm_connections();
        assert_eq!(names, vec!["alpha".to_owned(), "zeta".to_owned()]);
    }

    #[test]
    fn list_llm_connections_returns_names_only() {
        let state = EvaluatorState::new();
        state.load_llm_connections(vec![secret_connection()]).unwrap();

        let names = state.list_llm_connections();
        assert_eq!(names, vec!["test-connection".to_owned()]);

        let json = serde_json::to_string(&names).unwrap();
        assert!(!json.contains("super-secret-token"));
        assert!(!json.contains("llama3.2"));
        assert!(!json.contains("localhost"));
        assert!(!json.contains("api_key"));
    }

    #[test]
    fn evaluator_list_never_serializes_api_key() {
        let state = EvaluatorState::new();
        state.load_llm_connections(vec![secret_connection()]).unwrap();
        state.load_evaluators(vec![safety_evaluator("gate", "tools/call", None)]);

        let json = serde_json::to_string(&state.list_evaluators()).unwrap();
        assert!(!json.contains("super-secret-token"));
        assert!(!json.contains("api_key"));
    }
}

// =====================================================================
// Schema validation
// =====================================================================

mod schema {
    use super::*;

    #[test]
    fn valid_classification_result() {
        let schema = serde_json::json!({
            "type": "object",
            "properties": {
                "level": { "type": "string", "enum": ["green", "yellow", "red"] },
                "reason": { "type": "string" }
            },
            "required": ["level", "reason"]
        });
        let raw = r#"{"level": "red", "reason": "database restart is dangerous"}"#;
        assert!(validate_against_schema(&schema, raw).is_ok());
    }

    #[test]
    fn invalid_enum_value() {
        let schema = serde_json::json!({
            "type": "object",
            "properties": {
                "level": { "type": "string", "enum": ["green", "yellow", "red"] }
            },
            "required": ["level"]
        });
        let raw = r#"{"level": "blue"}"#;
        assert!(validate_against_schema(&schema, raw).is_err());
    }

    #[test]
    fn missing_required_fields() {
        let schema = serde_json::json!({
            "type": "object",
            "properties": {
                "level": { "type": "string" },
                "reason": { "type": "string" }
            },
            "required": ["level", "reason"]
        });
        let raw = r#"{"level": "green"}"#;
        let err = validate_against_schema(&schema, raw).unwrap_err();
        assert!(err.contains("reason"), "should mention missing field: {err}");
    }

    #[test]
    fn wrong_property_type() {
        let schema = serde_json::json!({
            "type": "object",
            "properties": {
                "level": { "type": "string" },
                "count": { "type": "integer" }
            },
            "required": ["level", "count"]
        });
        let raw = r#"{"level": "green", "count": "not-a-number"}"#;
        assert!(validate_against_schema(&schema, raw).is_err());
    }

    #[test]
    fn extra_properties_allowed_by_default() {
        let schema = serde_json::json!({
            "type": "object",
            "properties": {
                "level": { "type": "string" }
            },
            "required": ["level"]
        });
        let raw = r#"{"level": "green", "extra_field": "ignored"}"#;
        assert!(validate_against_schema(&schema, raw).is_ok());
    }

    #[test]
    fn additional_properties_forbidden() {
        let schema = serde_json::json!({
            "type": "object",
            "properties": {
                "level": { "type": "string" }
            },
            "required": ["level"],
            "additionalProperties": false
        });
        let raw = r#"{"level": "green", "extra": "fail"}"#;
        assert!(validate_against_schema(&schema, raw).is_err());
    }

    #[test]
    fn not_json() {
        let schema = serde_json::json!({"type": "object"});
        let err = validate_against_schema(&schema, "this is not JSON").unwrap_err();
        assert!(err.contains("not valid JSON"));
    }

    #[test]
    fn empty_string() {
        let schema = serde_json::json!({"type": "object"});
        let err = validate_against_schema(&schema, "").unwrap_err();
        assert!(err.contains("not valid JSON"));
    }

    #[test]
    fn wrong_top_level_type() {
        let schema = serde_json::json!({"type": "object"});
        let raw = r#"["an", "array"]"#;
        assert!(validate_against_schema(&schema, raw).is_err());
    }

    #[test]
    fn nested_object_validation() {
        let schema = serde_json::json!({
            "type": "object",
            "properties": {
                "assessment": {
                    "type": "object",
                    "properties": {
                        "level": { "type": "string" },
                        "score": { "type": "number", "minimum": 0, "maximum": 100 }
                    },
                    "required": ["level", "score"]
                }
            },
            "required": ["assessment"]
        });

        let valid = r#"{"assessment": {"level": "high", "score": 85}}"#;
        assert!(validate_against_schema(&schema, valid).is_ok());

        let out_of_range = r#"{"assessment": {"level": "high", "score": 150}}"#;
        assert!(validate_against_schema(&schema, out_of_range).is_err());

        let missing_nested = r#"{"assessment": {"level": "high"}}"#;
        assert!(validate_against_schema(&schema, missing_nested).is_err());
    }

    #[test]
    fn array_schema() {
        let schema = serde_json::json!({
            "type": "array",
            "items": { "type": "string" }
        });

        let valid = r#"["tool-a", "tool-b"]"#;
        assert!(validate_against_schema(&schema, valid).is_ok());

        let wrong_item = r#"["tool-a", 42]"#;
        assert!(validate_against_schema(&schema, wrong_item).is_err());
    }
}

// =====================================================================
// ActionResult variants
// =====================================================================

mod action_result {
    use super::*;

    #[test]
    fn reject_malformed_is_distinct_from_block() {
        let block = ActionResult::Block("policy violation".to_owned());
        let reject = ActionResult::RejectMalformed("bad LLM output".to_owned());

        assert!(matches!(block, ActionResult::Block(_)));
        assert!(matches!(reject, ActionResult::RejectMalformed(_)));
        assert!(!matches!(block, ActionResult::RejectMalformed(_)));
        assert!(!matches!(reject, ActionResult::Block(_)));
    }

    #[test]
    fn all_variants_are_debug() {
        let variants: Vec<ActionResult> = vec![
            ActionResult::Pass,
            ActionResult::Block("reason".to_owned()),
            ActionResult::RejectMalformed("reason".to_owned()),
            ActionResult::Warn("msg".to_owned()),
            ActionResult::FilterTools(vec!["t".to_owned()]),
            ActionResult::SetMetadata("k".to_owned(), "v".to_owned()),
        ];
        for v in &variants {
            let debug = format!("{v:?}");
            assert!(!debug.is_empty());
        }
    }
}

// =====================================================================
// WASM engine (requires pre-built actions in actions/dist/)
//
// These tests skip automatically if the WASM files are not built.
// Build them with:
//   cd actions/safety-review && cargo component build --release && cp target/wasm32-wasip1/release/*.wasm ../dist/
//   cd actions/safety-warn  && cargo component build --release && cp target/wasm32-wasip1/release/*.wasm ../dist/
// =====================================================================

mod engine {
    use super::*;
    use std::sync::Arc;
    use wanaku_feature_evaluator::engine::CompiledEvaluator;
    use wanaku_feature_evaluator::schema::CompiledSchema;
    use wanaku_apis::interactions::InMemoryInteractionStore;
    use wanaku_apis::registry::InMemoryRegistry;

    macro_rules! require_wasm {
        ($name:expr) => {{
            let p = wasm_path($name);
            if !p.exists() {
                eprintln!("SKIP: {} not found — build WASM actions first", p.display());
                return;
            }
            p
        }};
    }

    fn eval_context(method: &str, llm_result: &str) -> wanaku_feature_evaluator::wit_types::EvaluationContext {
        wanaku_feature_evaluator::wit_types::EvaluationContext {
            method: method.to_owned(),
            namespace: "default".to_owned(),
            tool_name: Some("restart-database".to_owned()),
            arguments: vec![("target".to_owned(), "prod".to_owned())],
            llm_result: llm_result.to_owned(),
            conversation_id: None,
        }
    }

    #[test]
    fn safety_action_blocks_on_red() {
        let path = require_wasm!("safety_review_action.wasm");
        let compiled = CompiledEvaluator::from_file("safety-review", &path)
            .expect("failed to compile safety-review WASM");

        let ctx = eval_context("tools/call", r#"{"level": "red", "reason": "dangerous"}"#);
        let result = compiled.evaluate(
            InMemoryRegistry::new(),
            InMemoryInteractionStore::new(100),
            ctx,
            None,
        );

        assert!(
            matches!(result, ActionResult::Block(_)),
            "safety-review should block on red, got: {result:?}"
        );
    }

    #[test]
    fn safety_action_warns_on_yellow() {
        let path = require_wasm!("safety_review_action.wasm");
        let compiled = CompiledEvaluator::from_file("safety-review", &path)
            .expect("failed to compile safety-review WASM");

        let ctx = eval_context("tools/call", r#"{"level": "yellow", "reason": "elevated"}"#);
        let result = compiled.evaluate(
            InMemoryRegistry::new(),
            InMemoryInteractionStore::new(100),
            ctx,
            None,
        );

        assert!(
            matches!(result, ActionResult::Warn(_)),
            "safety-review should warn on yellow, got: {result:?}"
        );
    }

    #[test]
    fn safety_action_passes_on_green() {
        let path = require_wasm!("safety_review_action.wasm");
        let compiled = CompiledEvaluator::from_file("safety-review", &path)
            .expect("failed to compile safety-review WASM");

        let ctx = eval_context("tools/call", r#"{"level": "green", "reason": "safe"}"#);
        let result = compiled.evaluate(
            InMemoryRegistry::new(),
            InMemoryInteractionStore::new(100),
            ctx,
            None,
        );

        assert!(
            matches!(result, ActionResult::Pass),
            "safety-review should pass on green, got: {result:?}"
        );
    }

    #[test]
    fn block_action_includes_llm_result_in_reason() {
        let path = require_wasm!("safety_review_action.wasm");
        let compiled = CompiledEvaluator::from_file("safety-review", &path)
            .expect("failed to compile safety-review WASM");

        let llm_output = r#"{"level": "red", "reason": "nuclear launch detected"}"#;
        let ctx = eval_context("tools/call", llm_output);
        let result = compiled.evaluate(
            InMemoryRegistry::new(),
            InMemoryInteractionStore::new(100),
            ctx,
            None,
        );

        if let ActionResult::Block(reason) = result {
            assert!(
                reason.contains("nuclear launch detected"),
                "block reason should contain LLM output, got: {reason}"
            );
        } else {
            panic!("expected Block, got: {result:?}");
        }
    }

    #[test]
    fn evaluate_with_schema_passed_to_host() {
        let path = require_wasm!("safety_review_action.wasm");
        let compiled = CompiledEvaluator::from_file("safety-review", &path)
            .expect("failed to compile safety-review WASM");

        let schema_val = serde_json::json!({
            "type": "object",
            "properties": {
                "level": { "type": "string" },
                "reason": { "type": "string" }
            },
            "required": ["level", "reason"]
        });
        let compiled_schema = CompiledSchema::compile(&schema_val).map(Arc::new);

        let ctx = eval_context("tools/call", r#"{"level": "red", "reason": "test"}"#);
        let result = compiled.evaluate(
            InMemoryRegistry::new(),
            InMemoryInteractionStore::new(100),
            ctx,
            compiled_schema,
        );

        assert!(
            matches!(result, ActionResult::Block(_)),
            "should still block with schema present, got: {result:?}"
        );
    }

    #[test]
    fn state_compiles_and_caches_wasm() {
        let path = require_wasm!("safety_review_action.wasm");

        let state = EvaluatorState::new();
        let mut eval = safety_evaluator("compile-test", "tools/call", None);
        eval.processor.path = path.clone();

        state.load_evaluators(vec![eval]);
        assert!(
            state.get_compiled(&path).is_some(),
            "WASM should be compiled and cached after load_evaluators"
        );
    }
}

// =====================================================================
// Revision persistence (state-level restart survival)
// =====================================================================

mod persistence {
    use super::*;
    use std::sync::Arc;

    use wanaku_feature_evaluator::revision::{
        ActivationStatus, Revision, RevisionMetadata, config_checksum,
    };
    use wanaku_feature_evaluator::revision_persistence::{
        FileRevisionPersistence, RevisionPersistence, RevisionsSnapshot,
    };

    fn temp_dir(name: &str) -> PathBuf {
        let dir = std::env::temp_dir().join(name);
        let _ = std::fs::remove_dir_all(&dir);
        dir
    }

    fn connection() -> LlmConnection {
        LlmConnection {
            name: "test-connection".to_owned(),
            model: "llama3.2".to_owned(),
            url: "http://localhost:11434/v1".to_owned(),
            api_key: String::new(),
        }
    }

    /// Write a persisted history with a single active revision for `defs`,
    /// bypassing the normal activation path. This lets a test set up a
    /// persisted active revision that no longer validates or compiles on the
    /// simulated restart host — a state the live API can never produce but a
    /// real deployment can (a removed WASM file, a dropped connection).
    fn seed_active(file: &PathBuf, defs: Vec<EvaluatorDef>) {
        let checksum = config_checksum(&defs).expect("checksum");
        let revision = Revision {
            metadata: RevisionMetadata {
                id: 1,
                created_at: "2026-01-01T00:00:00Z".to_owned(),
                activated_at: Some("2026-01-01T00:00:00Z".to_owned()),
                status: ActivationStatus::Active,
                checksum,
                origin: RevisionOrigin::Api,
                actor: None,
                failure_reason: None,
            },
            evaluators: defs,
        };
        let snapshot = RevisionsSnapshot {
            revisions: vec![revision],
            active_id: Some(1),
            next_id: 2,
        };
        FileRevisionPersistence::new(file).save(&snapshot).expect("seed persist");
    }

    fn activate_and_persist(file: &PathBuf, eval: EvaluatorDef) -> u64 {
        let backend = Arc::new(FileRevisionPersistence::new(file));
        let state = EvaluatorState::new().with_revision_persistence(backend);
        state.load_llm_connections(vec![connection()]).unwrap();
        state
            .try_activate(vec![eval], RevisionOrigin::Api, None, None)
            .expect("activation should succeed")
            .metadata
            .id
    }

    #[test]
    fn active_revision_and_runtime_survive_restart() {
        let path = wasm_path("safety_review_action.wasm");
        if !path.exists() {
            eprintln!("SKIP: {} not found — build WASM actions first", path.display());
            return;
        }
        let dir = temp_dir("wanaku-eval-persist-restart");
        let file = dir.join("evaluator-revisions.json");

        let mut eval = safety_evaluator("gate", "tools/call", None);
        eval.processor.path = path.clone();

        let revision_id = activate_and_persist(&file, eval.clone());

        // Restart: a fresh state reading the same persisted file. Connections
        // load first, then reconciliation restores the active revision.
        let backend = Arc::new(FileRevisionPersistence::new(&file));
        let restored = EvaluatorState::new().with_revision_persistence(backend);
        restored.load_llm_connections(vec![connection()]).unwrap();
        restored.reconcile_startup(Some(vec![eval]));

        let active = restored
            .revision_store()
            .active_revision()
            .expect("active revision must survive restart");
        assert_eq!(active.metadata.id, revision_id);

        // Dedup: the unchanged config must NOT create a new revision.
        assert_eq!(
            restored.revision_store().list_revisions().len(),
            1,
            "unchanged startup config must not record a new revision"
        );

        let listed = restored.list_evaluators();
        assert_eq!(listed.len(), 1);
        assert_eq!(listed[0].name, "gate");
        assert!(
            restored.get_compiled(&path).is_some(),
            "restored runtime snapshot must have the compiled WASM"
        );

        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn changed_startup_config_records_new_revision() {
        let path = wasm_path("safety_review_action.wasm");
        if !path.exists() {
            eprintln!("SKIP: {} not found — build WASM actions first", path.display());
            return;
        }
        let dir = temp_dir("wanaku-eval-persist-changed");
        let file = dir.join("evaluator-revisions.json");

        let mut eval = safety_evaluator("gate", "tools/call", None);
        eval.processor.path = path.clone();
        activate_and_persist(&file, eval);

        // Restart with a different config: a new revision must supersede.
        let mut changed = safety_evaluator("gate-v2", "tools/call", None);
        changed.processor.path = path.clone();

        let backend = Arc::new(FileRevisionPersistence::new(&file));
        let restored = EvaluatorState::new().with_revision_persistence(backend);
        restored.load_llm_connections(vec![connection()]).unwrap();
        restored.reconcile_startup(Some(vec![changed]));

        assert_eq!(restored.revision_store().list_revisions().len(), 2);
        assert_eq!(restored.list_evaluators()[0].name, "gate-v2");

        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn restart_with_uncompilable_persisted_active_fails_closed_without_churn() {
        // A persisted active revision points at a WASM file that no longer
        // resolves on this host (removed between runs). On restart the runtime
        // must fail closed (empty), and reconciliation must NOT append a new
        // revision — re-applying an already-recorded revision must never churn
        // history (Findings 1 & 2).
        let dir = temp_dir("wanaku-eval-persist-badwasm");
        let file = dir.join("evaluator-revisions.json");

        let mut eval = safety_evaluator("gate", "tools/call", None);
        eval.processor.path = PathBuf::from("/nonexistent/gone.wasm");
        seed_active(&file, vec![eval.clone()]);

        let backend = Arc::new(FileRevisionPersistence::new(&file));
        let restored = EvaluatorState::new().with_revision_persistence(backend);
        restored.load_llm_connections(vec![connection()]).unwrap();
        // Startup config is byte-identical to the persisted active revision, so
        // dedup matches — the reconcile path still re-compiles and fails closed.
        restored.reconcile_startup(Some(vec![eval]));

        assert!(
            restored.list_evaluators().is_empty(),
            "uncompilable persisted revision must not populate the runtime"
        );
        assert!(
            restored
                .get_compiled(&PathBuf::from("/nonexistent/gone.wasm"))
                .is_none()
        );
        assert_eq!(
            restored.revision_store().list_revisions().len(),
            1,
            "reinstalling an existing revision must not append to history"
        );
    }

    #[test]
    fn repeated_restarts_of_uncompilable_active_do_not_grow_history() {
        // Finding 1 regression guard: a broken-on-this-host active revision must
        // not accumulate one rejected revision per restart, which would evict
        // real rollback history once the bounded limit is reached. Runs without
        // a built WASM artifact, so it always executes in CI.
        let dir = temp_dir("wanaku-eval-persist-nochurn");
        let file = dir.join("evaluator-revisions.json");

        let mut eval = safety_evaluator("gate", "tools/call", None);
        eval.processor.path = PathBuf::from("/nonexistent/gone.wasm");
        seed_active(&file, vec![eval.clone()]);

        for _ in 0..5 {
            let backend = Arc::new(FileRevisionPersistence::new(&file));
            let restored = EvaluatorState::new().with_revision_persistence(backend);
            restored.load_llm_connections(vec![connection()]).unwrap();
            restored.reconcile_startup(Some(vec![eval.clone()]));
            assert_eq!(
                restored.revision_store().list_revisions().len(),
                1,
                "history must not grow across repeated restarts of a broken revision"
            );
            assert!(restored.list_evaluators().is_empty());
        }

        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn restart_with_missing_connection_leaves_runtime_empty() {
        // A persisted active revision references an LLM connection that is not
        // present in this run's config. This is Finding 2: connection
        // validation must run on restart exactly as it does on first boot, so
        // the dangling revision is not silently reinstalled.
        let dir = temp_dir("wanaku-eval-persist-badconn");
        let file = dir.join("evaluator-revisions.json");

        let mut eval = safety_evaluator("gate", "tools/call", None);
        eval.llm.connection = "gone-connection".to_owned();
        seed_active(&file, vec![eval.clone()]);

        let backend = Arc::new(FileRevisionPersistence::new(&file));
        let restored = EvaluatorState::new().with_revision_persistence(backend);
        // Only "test-connection" is loaded; the revision needs "gone-connection".
        restored.load_llm_connections(vec![connection()]).unwrap();
        restored.reconcile_startup(Some(vec![eval]));

        assert!(
            restored.list_evaluators().is_empty(),
            "revision with an unknown connection must not be reinstalled on restart"
        );

        let _ = std::fs::remove_dir_all(&dir);
    }
}
