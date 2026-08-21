#![allow(clippy::unwrap_used, clippy::expect_used, clippy::panic)]
//! Integration tests for the evaluator engine.
//!
//! Tests that need pre-built WASM actions skip automatically if the
//! `.wasm` files are not found. Build them with:
//!
//! ```sh
//! cd actions/safety-block && cargo component build --release && cp target/wasm32-wasip1/release/*.wasm ../dist/
//! cd actions/safety-warn  && cargo component build --release && cp target/wasm32-wasip1/release/*.wasm ../dist/
//! ```

use std::path::PathBuf;

use wanaku_feature_evaluator::action::ActionResult;
use wanaku_feature_evaluator::config::{
    ErrorPolicy, EvaluatorDef, EvaluatorsConfig, LlmDef, LlmOperation, ProcessorRef, TriggerDef,
};
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
            model: "test-model".to_owned(),
            url: "http://localhost:11434/v1".to_owned(),
            api_key: String::new(),
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
      model: "llama3.2"
      url: "http://localhost:11434/v1"
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
      model: "llama3.2"
      url: "http://localhost:11434/v1"
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
        assert!(
            schema["required"]
                .as_array()
                .unwrap()
                .contains(&serde_json::json!("level"))
        );
        assert!(
            schema["required"]
                .as_array()
                .unwrap()
                .contains(&serde_json::json!("reason"))
        );
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
    fn default_on_error_is_continue() {
        let yaml = r#"
evaluators:
  - name: "minimal"
    trigger:
      method: "tools/call"
    llm:
      operation: classify
      prompt: "test"
      model: "m"
      url: "http://localhost"
    processor:
      path: "/wasm/t.wasm"
"#;
        let config: EvaluatorsConfig = serde_yaml::from_str(yaml).unwrap();
        assert!(matches!(
            config.evaluators[0].on_error,
            ErrorPolicy::Continue
        ));
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
        state.load_evaluators(vec![safety_evaluator(
            "prod-gate",
            "tools/call",
            Some("production"),
        )]);

        assert!(state.find_matching("tools/call", "production").is_some());
        assert!(state.find_matching("tools/call", "staging").is_none());
    }

    #[test]
    fn find_matching_no_match_returns_none() {
        let state = EvaluatorState::new();
        state.load_evaluators(vec![safety_evaluator("gate", "tools/call", None)]);
        assert!(state.find_matching("resources/read", "default").is_none());
    }

    #[test]
    fn reload_replaces_evaluators() {
        let state = EvaluatorState::new();
        state.load_evaluators(vec![safety_evaluator("first", "tools/call", None)]);
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
        state.load_evaluators(vec![safety_evaluator("gate", "tools/call", None)]);
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
        assert!(
            err.contains("reason"),
            "should mention missing field: {err}"
        );
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
//   cd actions/safety-block && cargo component build --release && cp target/wasm32-wasip1/release/*.wasm ../dist/
//   cd actions/safety-warn  && cargo component build --release && cp target/wasm32-wasip1/release/*.wasm ../dist/
// =====================================================================

mod engine {
    use super::*;
    use std::sync::Arc;
    use wanaku_apis::interactions::InMemoryInteractionStore;
    use wanaku_apis::registry::InMemoryRegistry;
    use wanaku_feature_evaluator::engine::CompiledEvaluator;
    use wanaku_feature_evaluator::schema::CompiledSchema;

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

    fn eval_context(
        method: &str,
        llm_result: &str,
    ) -> wanaku_feature_evaluator::wit_types::EvaluationContext {
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
    fn safety_block_action_always_blocks() {
        let path = require_wasm!("safety_block_action.wasm");
        let compiled = CompiledEvaluator::from_file("safety-block", &path)
            .expect("failed to compile safety-block WASM");

        let ctx = eval_context("tools/call", r#"{"level": "red", "reason": "dangerous"}"#);
        let result = compiled.evaluate(
            InMemoryRegistry::new(),
            InMemoryInteractionStore::new(100),
            ctx,
            None,
        );

        assert!(
            matches!(result, ActionResult::Block(_)),
            "safety-block should always block, got: {result:?}"
        );
    }

    #[test]
    fn safety_warn_action_always_warns() {
        let path = require_wasm!("safety_warn_action.wasm");
        let compiled = CompiledEvaluator::from_file("safety-warn", &path)
            .expect("failed to compile safety-warn WASM");

        let ctx = eval_context("tools/call", r#"{"level": "yellow", "reason": "elevated"}"#);
        let result = compiled.evaluate(
            InMemoryRegistry::new(),
            InMemoryInteractionStore::new(100),
            ctx,
            None,
        );

        assert!(
            matches!(result, ActionResult::Warn(_)),
            "safety-warn should always warn, got: {result:?}"
        );
    }

    #[test]
    fn block_action_includes_llm_result_in_reason() {
        let path = require_wasm!("safety_block_action.wasm");
        let compiled = CompiledEvaluator::from_file("safety-block", &path)
            .expect("failed to compile safety-block WASM");

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
        let path = require_wasm!("safety_block_action.wasm");
        let compiled = CompiledEvaluator::from_file("safety-block", &path)
            .expect("failed to compile safety-block WASM");

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
        let path = require_wasm!("safety_block_action.wasm");

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
