---
title: Test Wanaku
description: "Run the Wanaku evaluator test suites: the default suite, the example-action suite, and the end-to-end tests against a running server."
---

# Testing

This page describes the test suites of the evaluator crate.

## Rust Integration Tests (no server needed)

The evaluator crate has two in-process test suites: the default suite and the example-action suite.

**Default suite.** This suite needs no build prerequisites. It does not need `cargo-component` or `actions/dist/`. Host and runtime tests use the minimal WAT components in `features/evaluator/tests/fixtures/`. These tests cover activation, CAS conflicts and metrics, concurrent activation, snapshot immutability, restart, and host side effects. If a fixture is missing, the test fails.

```bash
cargo test -p wanaku-feature-evaluator
```

The default suite also covers config parsing (including `result_schema`), trigger matching, evaluator state management, schema validation, and action result variants.

**Example-action suite.** This suite tests the compiled example actions in `actions/dist/`, for example the safety-review and OPA allow algorithms. The suite contains `features/evaluator/tests/example_actions.rs` and the filter unit tests that run these actions. These tests use the `#[ignore]` attribute, so `cargo test` does not run them. If you run the suite and an action `.wasm` file is missing, the test fails.

```bash
# Build the Rust example actions into actions/dist/
mkdir -p actions/dist
(cd actions/safety-review && cargo component build --release)
cp actions/safety-review/target/wasm32-wasip1/release/safety_review_action.wasm actions/dist/
(cd actions/assembly-filter && cargo component build --release)
cp actions/assembly-filter/target/wasm32-wasip1/release/assembly_filter_action.wasm actions/dist/
(cd actions/opa-allow && cargo component build --release)
cp actions/opa-allow/target/wasm32-wasip1/release/opa_allow_action.wasm actions/dist/

# Run the example-action suite
cargo test -p wanaku-feature-evaluator --lib --test example_actions -- --ignored
```

The example-action tests use a hardcoded `llm_result`, so they do not need an LLM. CI runs both suites.

## Rust E2E Tests (server required, no LLM needed)

End-to-end tests send requests to the management API and MCP endpoint on a running server. These tests replace the shell scripts and use the `#[ignore]` attribute.

```bash
# Start wanaku-server first, then:
cargo test -p wanaku-feature-evaluator --test e2e -- --ignored
```

Tests cover: evaluator API CRUD, WASM block/warn actions, clearing evaluators, `result_schema` config, namespace-scoped evaluators, and LLM classification (classification tests also need Ollama on `localhost:11434`).

**What to look for in server logs:**

```text
INFO  evaluator triggered method="tools/call" namespace="default" evaluator="safety-gate"
INFO  LLM operation result llm_result="{\"level\":\"red\",\"reason\":\"database restart\"}"
INFO  processor result action=Block("Tool call blocked by safety classification: red")
```

These traces confirm:

1. The filter matched the trigger
2. The LLM returned a classification
3. The WASM action executed and returned a decision

## Next steps

- [Contributing to Wanaku](../govern/index.md): the development workflow and the checks to run.
- [Write evaluator actions in Rust](../extend/evaluator-actions-rust.md): build the actions that the example-action suite tests.
