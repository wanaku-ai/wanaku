#![allow(clippy::unwrap_used, clippy::expect_used, clippy::panic)]
//! Tests of the compiled example actions in `actions/dist/`.
//!
//! These tests verify the behavior of the real actions (for example, the
//! safety-review algorithm). They need the actions built with
//! `cargo-component`, so they use the `#[ignore]` attribute and do not run in
//! the default suite. The filter unit tests that run these actions use the
//! same gate, so `--lib` is part of the command. When you request the suite, a
//! missing artifact fails the test:
//!
//! ```sh
//! # Build the actions into actions/dist/ first (see docs/contributing/testing.md).
//! cargo test -p wanaku-feature-evaluator --lib --test example_actions -- --ignored
//! ```

use std::path::PathBuf;
use std::sync::Arc;

use wanaku_feature_evaluator::action::ActionResult;
use wanaku_feature_evaluator::engine::CompiledEvaluator;
use wanaku_feature_evaluator::schema::CompiledSchema;
use wanaku_infra::registry::InMemoryRegistry;
use wanaku_types::interactions::InMemoryInteractionStore;
use wanaku_types::registry::ToolRegistry;

/// Resolve a compiled example action. A missing artifact fails the test.
fn action_path(name: &str) -> PathBuf {
    let path = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("../../actions/dist")
        .join(name);
    assert!(
        path.is_file(),
        "missing example action {}: build the actions with cargo-component first",
        path.display()
    );
    path
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
#[ignore = "requires actions/dist built with cargo-component"]
fn audit_capabilities_prevent_real_processor_registry_writes() {
    let path = action_path("assembly_filter_action.wasm");
    let processor = CompiledEvaluator::from_file("assembly", &path).unwrap();
    let registry = InMemoryRegistry::new();
    registry.register_tool(
        serde_json::from_value(serde_json::json!({
            "name": "alpha", "description": "test", "uri": "test", "type": "mcp-forward",
            "inputSchema": {}, "namespace": "default"
        }))
        .unwrap(),
    );
    let before = serde_json::to_value(registry.list_tools()).unwrap();
    let result = processor
        .evaluate(
            wanaku_feature_evaluator::engine::ExecutionContext {
                registry: registry.clone(),
                interactions: InMemoryInteractionStore::new(10),
                compiled_schema: None,
                mode: wanaku_feature_evaluator::engine::ExecutionMode::Production,
                limits: None,
                allow_side_effects: false,
            },
            eval_context("tools/list", r#"["alpha"]"#),
        )
        .unwrap();
    assert!(matches!(result.action, ActionResult::FilterTools(_)));
    assert!(result.side_effects_suppressed);
    assert_eq!(serde_json::to_value(registry.list_tools()).unwrap(), before);
}

#[test]
#[ignore = "requires actions/dist built with cargo-component"]
fn safety_action_blocks_on_red() {
    let path = action_path("safety_review_action.wasm");
    let compiled = CompiledEvaluator::from_file("safety-review", &path)
        .expect("failed to compile safety-review WASM");

    let ctx = eval_context("tools/call", r#"{"level": "red", "reason": "dangerous"}"#);
    let result = compiled
        .evaluate(
            wanaku_feature_evaluator::engine::ExecutionContext {
                registry: InMemoryRegistry::new(),
                interactions: InMemoryInteractionStore::new(100),
                compiled_schema: None,
                mode: wanaku_feature_evaluator::engine::ExecutionMode::Production,
                limits: None,
                allow_side_effects: true,
            },
            ctx,
        )
        .expect("WASM evaluator execution failed")
        .action;

    assert!(
        matches!(result, ActionResult::Block(_)),
        "safety-review should block on red, got: {result:?}"
    );
}

#[test]
#[ignore = "requires actions/dist built with cargo-component"]
fn safety_action_warns_on_yellow() {
    let path = action_path("safety_review_action.wasm");
    let compiled = CompiledEvaluator::from_file("safety-review", &path)
        .expect("failed to compile safety-review WASM");

    let ctx = eval_context("tools/call", r#"{"level": "yellow", "reason": "elevated"}"#);
    let result = compiled
        .evaluate(
            wanaku_feature_evaluator::engine::ExecutionContext {
                registry: InMemoryRegistry::new(),
                interactions: InMemoryInteractionStore::new(100),
                compiled_schema: None,
                mode: wanaku_feature_evaluator::engine::ExecutionMode::Production,
                limits: None,
                allow_side_effects: true,
            },
            ctx,
        )
        .expect("WASM evaluator execution failed")
        .action;

    assert!(
        matches!(result, ActionResult::Warn(_)),
        "safety-review should warn on yellow, got: {result:?}"
    );
}

#[test]
#[ignore = "requires actions/dist built with cargo-component"]
fn safety_action_passes_on_green() {
    let path = action_path("safety_review_action.wasm");
    let compiled = CompiledEvaluator::from_file("safety-review", &path)
        .expect("failed to compile safety-review WASM");

    let ctx = eval_context("tools/call", r#"{"level": "green", "reason": "safe"}"#);
    let result = compiled
        .evaluate(
            wanaku_feature_evaluator::engine::ExecutionContext {
                registry: InMemoryRegistry::new(),
                interactions: InMemoryInteractionStore::new(100),
                compiled_schema: None,
                mode: wanaku_feature_evaluator::engine::ExecutionMode::Production,
                limits: None,
                allow_side_effects: true,
            },
            ctx,
        )
        .expect("WASM evaluator execution failed")
        .action;

    assert!(
        matches!(result, ActionResult::Pass),
        "safety-review should pass on green, got: {result:?}"
    );
}

#[test]
#[ignore = "requires actions/dist built with cargo-component"]
fn block_action_includes_llm_result_in_reason() {
    let path = action_path("safety_review_action.wasm");
    let compiled = CompiledEvaluator::from_file("safety-review", &path)
        .expect("failed to compile safety-review WASM");

    let llm_output = r#"{"level": "red", "reason": "nuclear launch detected"}"#;
    let ctx = eval_context("tools/call", llm_output);
    let result = compiled
        .evaluate(
            wanaku_feature_evaluator::engine::ExecutionContext {
                registry: InMemoryRegistry::new(),
                interactions: InMemoryInteractionStore::new(100),
                compiled_schema: None,
                mode: wanaku_feature_evaluator::engine::ExecutionMode::Production,
                limits: None,
                allow_side_effects: true,
            },
            ctx,
        )
        .expect("WASM evaluator execution failed")
        .action;

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
#[ignore = "requires actions/dist built with cargo-component"]
fn evaluate_with_schema_passed_to_host() {
    let path = action_path("safety_review_action.wasm");
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
    let result = compiled
        .evaluate(
            wanaku_feature_evaluator::engine::ExecutionContext {
                registry: InMemoryRegistry::new(),
                interactions: InMemoryInteractionStore::new(100),
                compiled_schema,
                mode: wanaku_feature_evaluator::engine::ExecutionMode::Production,
                limits: None,
                allow_side_effects: true,
            },
            ctx,
        )
        .expect("WASM evaluator execution failed")
        .action;

    assert!(
        matches!(result, ActionResult::Block(_)),
        "should still block with schema present, got: {result:?}"
    );
}

#[test]
#[ignore = "requires actions/dist built with cargo-component"]
fn assembly_action_skips_tools_from_other_namespaces() {
    let path = action_path("assembly_filter_action.wasm");
    let processor = CompiledEvaluator::from_file("assembly", &path).unwrap();
    let registry = InMemoryRegistry::new();
    for (name, namespace) in [("shared", "default"), ("private", "tenant-b")] {
        registry.register_tool(
            serde_json::from_value(serde_json::json!({
                "name": name, "description": "test", "uri": "test", "type": "mcp-forward",
                "inputSchema": {}, "namespace": namespace
            }))
            .unwrap(),
        );
    }
    let mut ctx = eval_context("tools/list", r#"["shared", "private"]"#);
    ctx.namespace = "curated".to_owned();
    let result = processor
        .evaluate(
            wanaku_feature_evaluator::engine::ExecutionContext {
                registry: registry.clone(),
                interactions: InMemoryInteractionStore::new(10),
                compiled_schema: None,
                mode: wanaku_feature_evaluator::engine::ExecutionMode::Production,
                limits: None,
                allow_side_effects: true,
            },
            ctx,
        )
        .unwrap();
    assert!(
        matches!(&result.action, ActionResult::FilterTools(names) if names == &["shared".to_owned()]),
        "only the shared tool should be assembled, got: {:?}",
        result.action
    );
    assert!(registry.get_tool("tenant-b", "private").is_some());
    assert!(registry.get_tool("curated", "private").is_none());
    assert!(registry.get_tool("curated", "shared").is_some());
    assert!(registry.get_tool("default", "shared").is_some());
}

#[test]
#[ignore = "requires actions/dist built with cargo-component"]
fn safety_action_rejects_unrecognized_result() {
    let path = action_path("safety_review_action.wasm");
    let compiled = CompiledEvaluator::from_file("safety-review", &path)
        .expect("failed to compile safety-review WASM");

    for llm_output in [
        r#"{"level": "purple"}"#,
        r#"{"reason": "no level"}"#,
        "no idea",
    ] {
        let result = compiled
            .evaluate(
                wanaku_feature_evaluator::engine::ExecutionContext {
                    registry: InMemoryRegistry::new(),
                    interactions: InMemoryInteractionStore::new(100),
                    compiled_schema: None,
                    mode: wanaku_feature_evaluator::engine::ExecutionMode::Production,
                    limits: None,
                    allow_side_effects: true,
                },
                eval_context("tools/call", llm_output),
            )
            .expect("WASM evaluator execution failed")
            .action;
        assert!(
            matches!(result, ActionResult::RejectMalformed(_)),
            "safety-review should reject {llm_output}, got: {result:?}"
        );
    }
}
