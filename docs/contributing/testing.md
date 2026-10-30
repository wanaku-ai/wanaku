---
title: Test Wanaku
description: Run the Wanaku test suites, from the Rust workspace tests and the evaluator suites to the semantic router acceptance suite and the UI tests.
---

# Testing

This page describes the Wanaku test suites and the commands that run them.

## Workspace tests

Run all unit and composition tests from the repository root:

```bash
cargo test
```

To test one crate, use `-p`. To show tracing output, add `-- --nocapture`:

```bash
cargo test -p wanaku-infra
cargo test -- --nocapture
```

Unit tests live in their modules under `#[cfg(test)]`. The composition tests in `server/tests/app_assembly.rs` build the production graph with `wanaku_server::app::App::build`.

## Evaluator tests

The evaluator crate has extra suites. Some suites need compiled actions or a running server.

### Rust Integration Tests (no server needed)

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

### Rust E2E Tests (server required, no LLM needed)

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

## Semantic router acceptance suite

This suite tests Barn, the Wanaku Semantic Router (WSR), and Wanaku together.

Use a JDK 21 or later. Use Node.js 22 or later. Build Barn with `mvn verify`. Build WSR with `mvn verify`. Build Wanaku with `cargo build`. Run `cargo test --workspace` in Wanaku.

Run the cross-repository suite from the Wanaku checkout:

```bash
node tests/semantic-router/acceptance.mjs /path/to/wanaku-barn /path/to/wanaku-semantic-router
```

The suite starts the three built applications and a local HTTP provider fixture. It uses isolated state and dynamically selected ports. It uploads ordinary and semantic Kamelets through Barn. It runs an ordinary Kamelet through Camel's native HTTP lookup. It verifies pinned semantic content after replacement and removal. It inspects generated files without persistence or inference. It creates a definition, runs previews, publishes a real catalog, and starts WSR from the published revision. It checks MCP initialization, discovery, schemas, namespace isolation, both actions, no match, failed and malformed evaluations, policy denial before inference, unavailable forwards, revision replacement, and graceful shutdown. The suite writes logs to a temporary directory and stops its processes after the checks.

Run Barn's browser tests separately. Use the documented model evaluation procedure in WSR for representative model inputs. CI checks integration behavior with deterministic fixtures. CI does not require paid inference or backend side effects.

## Admin UI tests

The Admin UI has Playwright end-to-end tests in `tests/e2e/ui/`. Read [E2E Tests](admin-ui.md#e2e-tests) in the Admin UI development guide.

## Next steps

- [Contributing to Wanaku](index.md): the development workflow and the checks to run.
- [Write evaluator actions in Rust](../extend/evaluator-actions-rust.md): build the actions that the example-action suite tests.
