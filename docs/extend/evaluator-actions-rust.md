---
title: Write evaluator actions in Rust
description: Write a Wanaku evaluator action script in Rust with wit-bindgen and cargo-component, build it to WebAssembly, and deploy it to Wanaku.
---

# Write evaluator actions in Rust

Rust gives you type safety and better tooling for complex logic. The trade-off is more boilerplate.

## Prerequisites

```bash
# Install cargo-component
cargo install cargo-component

# Add wasm32-wasip1 target
rustup target add wasm32-wasip1
```

## Step 1: Create a cdylib Crate

```bash
cargo new --lib safety-review-action
cd safety-review-action
```

Edit `Cargo.toml`:

```toml
[package]
name = "safety-review-action"
version = "0.1.0"
edition = "2024"

[lib]
crate-type = ["cdylib"]

[dependencies]
wit-bindgen = "0.41"
wit-bindgen-rt = "0.41"
serde_json = "1.0"

[package.metadata.component]
package = "wanaku:safety-review"

[package.metadata.component.target]
path = "wit/evaluator.wit"
world = "evaluator-action"
```

Copy the WIT file into your crate:

```bash
cp /path/to/wanaku/features/evaluator/wit/evaluator.wit wit/evaluator.wit
```

You can also get `wit/evaluator.wit` from the `wanaku-evaluators-<version>.tar.gz` release asset. Use the WIT file that matches your server version.

## Step 2: Use wit-bindgen

In `src/lib.rs`:

```rust
#[allow(warnings)]
mod bindings;

use bindings::wanaku::evaluator::types::EvaluationContext;
use bindings::Guest;

struct SafetyReview;

impl Guest for SafetyReview {
    fn evaluate(ctx: EvaluationContext) {
        // Your logic here
    }
}

bindings::export!(SafetyReview with_types_in bindings);
```

The `cargo component build` command generates the `bindings` module. Do not write this module.

## Step 3: Implement the Guest Trait

Access host imports via `bindings::wanaku::evaluator::{registry, response, validation, log, conversation}`:

```rust
impl Guest for SafetyReview {
    fn evaluate(ctx: EvaluationContext) {
        let reason = format!(
            "Tool call blocked by safety classification: {}",
            ctx.llm_result
        );
        bindings::wanaku::evaluator::log::warn(&reason);
        bindings::wanaku::evaluator::response::block(&reason);
    }
}
```

**Available host functions:**

```rust
use bindings::wanaku::evaluator::{registry, response, validation, conversation, log};

// Registry
let tools = registry::list_tools();
let tool = registry::get_tool("restart-database");
let copied = registry::copy_tool_to_namespace("read-balance", "finance");

// Response (call exactly one)
response::pass();
response::block("reason");
response::reject_malformed("reason");  // "cannot decide" versus "decided to reject"
response::warn("message");
response::filter_tools(&["tool-1", "tool-2"]);
response::set_metadata("key", "value");

// Validation — check LLM result against declared result_schema
match validation::verify_llm_result(&ctx.llm_result) {
    Ok(validated) => { /* use validated */ }
    Err(error) => {
        response::reject_malformed(&format!("Invalid LLM output: {error}"));
        return;
    }
}

// Conversation
let messages = conversation::get_history(&ctx.conversation_id.unwrap_or_default());

// Logging
log::info("message");
log::warn("message");
log::error("message");
```

## Step 4: Build

```bash
cargo component build --release
```

Output: `target/wasm32-wasip1/release/safety_review_action.wasm`

The filename comes from your `[package] name` with `_` replacing `-`.

## Step 5: Deploy

Same as JavaScript — reference the WASM file in your evaluator config:

```yaml
processor:
  path: "/wasm/safety_review_action.wasm"
```

## Complete Rust Example

Full `src/lib.rs` for a safety gate. This file is the shipped
[`actions/safety-review/src/lib.rs`](../../actions/safety-review/src/lib.rs) without
its tests. The action reads the engine result from `ctx.llm_result` and maps the
level to a response: `red` blocks, `yellow` warns, and any other level passes.
The action accepts two result formats:

- A TypeSafe System One Noul result. A probability below 0.5 is `red`. Other
  probabilities are `green`.
- An LLM result of the form `{"level": "green|yellow|red", "reason": "..."}`.
  When the result is not valid JSON, the action infers the level from the raw text.

```rust
#[allow(warnings)]
mod bindings;

use bindings::Guest;
use bindings::wanaku::evaluator::types::EvaluationContext;

struct SafetyReview;

impl Guest for SafetyReview {
    fn evaluate(ctx: EvaluationContext) {
        let details = format!(
            "Safety review triggered by method {} for tool name {} in namespace {} for conversation id {}",
            ctx.method,
            ctx.tool_name.clone().unwrap_or_default(),
            ctx.namespace,
            ctx.conversation_id.clone().unwrap_or_default()
        );
        bindings::wanaku::evaluator::log::info(&details);

        let (level, reason) = classify(&ctx.llm_result);

        match level.as_str() {
            "red" => {
                bindings::wanaku::evaluator::log::warn(&format!("Blocked: {reason}"));
                bindings::wanaku::evaluator::response::block(&format!(
                    "Tool call blocked by safety classification: {reason}"
                ));
            }
            "yellow" => {
                bindings::wanaku::evaluator::log::warn(&format!("Warning: {reason}"));
                bindings::wanaku::evaluator::response::warn(&format!("Safety warning: {reason}"));
            }
            _ => {
                bindings::wanaku::evaluator::response::pass();
            }
        }
    }
}

/// Extract the safety `level` and `reason` from the engine result.
///
/// A TypeSafe Noul result contains the probability that its safety condition
/// is true. A probability below 0.5 blocks the request. An LLM result uses
/// `{"level": "green|yellow|red", "reason": "..."}`.
fn classify(engine_result: &str) -> (String, String) {
    if let Ok(value) = serde_json::from_str::<serde_json::Value>(engine_result) {
        if let Some(result) = classify_system_one(&value) {
            return result;
        }
        let level = value
            .get("level")
            .and_then(serde_json::Value::as_str)
            .unwrap_or("green")
            .to_string();
        let reason = value
            .get("reason")
            .and_then(serde_json::Value::as_str)
            .unwrap_or(engine_result)
            .to_string();
        return (level, reason);
    }

    let lower = engine_result.to_lowercase();
    let level = if lower.contains("red") {
        "red"
    } else if lower.contains("yellow") {
        "yellow"
    } else {
        "green"
    };
    (level.to_string(), engine_result.to_string())
}

fn classify_system_one(value: &serde_json::Value) -> Option<(String, String)> {
    let is_noul = value.get("engine").and_then(serde_json::Value::as_str)
        == Some("typesafe-system-one")
        && value
            .pointer("/primitive/type")
            .and_then(serde_json::Value::as_str)
            == Some("noul");
    if !is_noul {
        return None;
    }
    let probability = value
        .pointer("/answer/noul")
        .and_then(serde_json::Value::as_f64)?;
    if !(0.0..=1.0).contains(&probability) {
        return None;
    }
    let level = if probability < 0.5 { "red" } else { "green" };
    Some((
        level.to_string(),
        format!("TypeSafe Noul safety probability: {probability:.2}"),
    ))
}

bindings::export!(SafetyReview with_types_in bindings);
```

**Why this works:** The `bindings` module is generated from `evaluator.wit` by `cargo-component`. It provides Rust types for `EvaluationContext` and functions for all host imports. You implement the `Guest` trait's `evaluate` method, and `bindings::export!` makes it callable from the host.

## Next steps

- [Rust example](https://github.com/wanaku-ai/wanaku/blob/main/actions/safety-review): the source of the bundled safety-review action.
- [cargo-component](https://github.com/bytecodealliance/cargo-component): the build tool documentation.
- [Evaluators](../govern/evaluators.md): configure the evaluator that runs your action.
- [Testing](../contributing/testing.md): run the evaluator test suites.
