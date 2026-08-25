# Evaluator Engine — Developer Guide

The evaluator engine lets you build **trigger→evaluate→act** pipelines that inject LLM reasoning into the MCP request path. When a request matches a trigger, the engine calls an LLM to classify, filter, or augment the request, then executes a WebAssembly action script that has full access to the registry, conversation history, and response control.

You write the logic in JavaScript or Rust. The engine compiles it to WASM and runs it in a sandboxed environment with a clean, versioned API defined by the [WIT interface](../features/evaluator/wit/evaluator.wit).

**Why this exists:** MCP tools can be dangerous. You want to block "restart production database" calls, or dynamically assemble a per-conversation tool catalog based on what the LLM thinks the user needs. Pure LLM-based gates are too brittle (hallucination, prompt injection). Pure rule-based gates are too rigid. This engine gives you both: LLM cognitive work + deterministic WASM execution.

This guide is for developers who configure evaluators or write evaluator action scripts. It covers configuration, the JavaScript and Rust guest APIs, management routes, tests, internals, and operational limits.

## Contents

- [YAML Configuration](#yaml-configuration)
- [Complete Examples](#complete-examples)
- [Writing Action Scripts in JavaScript](#writing-action-scripts-in-javascript)
- [Writing Action Scripts in Rust](#writing-action-scripts-in-rust)
- [Management API](#management-api)
- [Testing Action Scripts](#testing-action-scripts)
- [How It Works](#how-it-works-internals)
- [Troubleshooting](#troubleshooting)
- [Performance Notes](#performance-notes)
- [Security Considerations](#security-considerations)

## YAML Configuration

Evaluators live in `wanaku.yaml` or get pushed via the management API. LLM connections
are config-only. They hold the model, endpoint, and credential. Set them in
`wanaku.yaml`. The management API cannot set or read them. An evaluator refers to a
connection by name. Each evaluator has four parts:

```yaml
llm_connections:                      # Config-only. Never exposed via the management API.
  - name: "local-llama"
    model: "llama3.2"
    url: "http://localhost:11434/v1"
    api_key: ""                       # Optional bearer token

evaluators:
  - name: "safety-gate"               # Unique identifier
    trigger:                          # When to run
      method: "tools/call"            # MCP method (tools/call, tools/list, etc.)
      namespace: "production"         # Optional: only this namespace
    llm:                              # LLM operation
      operation: classify             # classify | filter | augment
      prompt: "You are a safety classifier..."
      connection: "local-llama"       # References an entry in llm_connections
    processor:                        # WASM action script
      path: "/wasm/safety-gate.wasm"
    on_error: continue                # continue | block (default: continue)
```

### LLM Connections

| Field | Type | Purpose |
|-------|------|---------|
| `name` | string | Unique identifier referenced by an evaluator's `llm.connection` |
| `model` | string | Model name (passed to `/v1/chat/completions`) |
| `url` | string | OpenAI-compatible endpoint URL |
| `api_key` | string | Optional bearer token for the LLM endpoint |

The server loads connections once at startup from `wanaku.yaml`. No API can create,
update, or read a connection's `api_key`. This design keeps credentials out of the
management API, including out of evaluator revision history. See
[Security Considerations](#security-considerations).

### Trigger Fields

| Field | Type | Purpose |
|-------|------|---------|
| `method` | string | MCP method to match (e.g., `tools/call`, `tools/list`, `resources/read`) |
| `namespace` | string | Optional. Only trigger for this namespace (extracted from URL path `/finance/mcp` → `finance`) |

### LLM Fields

| Field | Type | Purpose |
|-------|------|---------|
| `operation` | string | `classify` (pick a label), `filter` (return structured data), or `augment` (enrich prompt) |
| `prompt` | string | System prompt for the LLM. The engine builds a user prompt with request context. |
| `connection` | string | Name of an entry in `llm_connections` (see above) |
| `result_schema` | object | Optional JSON Schema for validating LLM output. When set, the host validates the LLM result before passing it to the WASM guest. On mismatch, retries once with a correction prompt. |

**How the LLM sees context:** The engine builds a user prompt containing:
- Recent conversation history (last 10 interactions from the intercept filter)
- MCP method and namespace
- Tool name and arguments (for `tools/call`)
- Available tools (for `tools/list`)

Your system prompt tells the LLM what to do with that context.

### Processor

A single WASM action script that processes the LLM output. The script receives the raw LLM result in `ctx.llmResult` and decides what to do:

```yaml
processor:
  path: "/wasm/safety-gate.wasm"
```

The WASM script can call `block()`, `pass()`, `warn()`, `filterTools()`, or `setMetadata()` based on its analysis of the LLM output.

### Error Policy

- `on_error: continue` (default) — WASM failures are logged, request proceeds
- `on_error: block` — WASM failures block the request with a JSON-RPC error

## Complete Examples

### Example 1: Safety Classification

Block dangerous tool calls based on LLM classification.

```yaml
llm_connections:
  - name: "local-llama"
    model: "llama3.2"
    url: "http://localhost:11434/v1"

evaluators:
  - name: "safety-gate"
    trigger:
      method: "tools/call"
    llm:
      operation: classify
      prompt: |
        You are a strict safety classifier. Classify this tool call as:
        - green: safe operations (read-only, low impact)
        - yellow: elevated operations (writes, config changes)
        - red: dangerous operations (database restarts, production deploys)
        
        Respond with ONLY a JSON object: {"level": "green|yellow|red", "reason": "brief explanation"}
      connection: "local-llama"
      result_schema:                    # Optional: validate LLM output shape
        type: object
        properties:
          level:
            type: string
            enum: ["green", "yellow", "red"]
          reason:
            type: string
        required: ["level", "reason"]
    processor:
      path: "/wasm/safety_review_action.wasm"
    on_error: continue
```

**What happens:**
1. User calls `restart-database` tool
2. Engine sends tool name + args to LLM with your prompt
3. LLM returns `{"level": "red", "reason": "database restart is dangerous"}`
4. Engine runs `safety_review_action.wasm` with the raw LLM output in `ctx.llmResult`
5. WASM script parses the JSON, sees `"red"`, calls `block("Tool call blocked: red")`
6. User gets JSON-RPC error instead of executing the tool

### Example 2: Tool Assembly

Dynamically populate a namespace with only the tools the LLM approves.

```yaml
evaluators:
  - name: "assembly-gate"
    trigger:
      method: "tools/list"
      namespace: "curated"
    llm:
      operation: filter
      prompt: |
        You are a tool curator. Given the conversation history and available tools,
        return a JSON array of tool names that are relevant and safe for this user.
        
        Respond with ONLY a JSON array: ["tool-name-1", "tool-name-2"]
      connection: "local-llama"
    processor:
      path: "/wasm/assembly-filter.wasm"
```

This example reuses the `local-llama` connection from Example 1.

**What happens:**
1. User sends `tools/list` to the `curated` namespace (`/curated/mcp`)
2. Engine sends conversation history + available tools to LLM
3. LLM returns `["read-balance", "transfer-funds", "generate-report"]`
4. Engine runs `assembly-filter.wasm` with that JSON in `ctx.llmResult`
5. WASM script parses JSON, calls `copyToolToNamespace(name, ctx.namespace)` for each
6. WASM calls `filterTools(approved)` to return only the assembled tools
7. User sees a curated tool list

## Writing Action Scripts in JavaScript

JavaScript is the fastest path from idea to working WASM. You write normal JS, compile it with `jco`, and the engine runs it.

### Prerequisites

```bash
npm install -g @bytecodealliance/jco
```

Install the TypeScript definitions to enable autocomplete:

```bash
# Copy from the repo
cp sdk/js/wanaku-actions.d.ts /path/to/your/action/
```

### Step 1: Import Host Functions

The host provides four namespaces. Import what you need:

```javascript
import { block, rejectMalformed, warn, pass, filterTools, setMetadata } from 'wanaku:evaluator/response';
import { verifyLlmResult } from 'wanaku:evaluator/validation';
import { listTools, getTool, copyToolToNamespace } from 'wanaku:evaluator/registry';
import { getHistory } from 'wanaku:evaluator/conversation';
import { info, warn, error } from 'wanaku:evaluator/log';
```

### Step 2: Implement `evaluate(ctx)`

This is the only export the host calls:

```javascript
export function evaluate(ctx) {
  // ctx fields:
  // - method: "tools/call" | "tools/list" | etc.
  // - namespace: "default" | "finance" | etc.
  // - toolName: "restart-database" (present for tools/call)
  // - arguments: [["target", "prod"], ["timeout", "30"]]
  // - llmResult: raw string from the LLM
  // - conversationId: "conv-12345" (if present)
  
  // Your logic here
}
```

### Step 3: Available Host APIs

#### Registry (`wanaku:evaluator/registry`)

```javascript
import { listTools, listToolsInNamespace, getTool, copyToolToNamespace } from 'wanaku:evaluator/registry';

// List all tools across all namespaces
const tools = listTools();  // [{name, description, uri, toolType, namespace}, ...]

// List tools in a specific namespace
const financeTools = listToolsInNamespace("finance");

// Get a single tool by name
const tool = getTool("restart-database");  // {name, description, ...} or undefined

// Copy a tool into a target namespace (for assembly)
const copied = copyToolToNamespace("read-balance", "finance-team");  // true if found
```

#### Response (`wanaku:evaluator/response`)

**Call exactly ONE of these per evaluation.** If you call none, the default is `pass()`.

```javascript
import { pass, block, rejectMalformed, warn, filterTools, setMetadata } from 'wanaku:evaluator/response';

// Allow the request to proceed
pass();

// Block the request with a JSON-RPC error (code -32001)
block("Tool call blocked: database restart requires manual approval");

// Signal that the input data is malformed and the evaluator cannot decide (code -32002)
// Use this for invalid LLM output, not for a policy rejection
rejectMalformed("LLM result missing required 'level' field");

// Log a warning but allow the request to proceed
warn("Elevated privilege operation detected");

// Return a filtered tools/list response (only for tools/list triggers)
filterTools(["tool-1", "tool-2", "tool-3"]);

// Set metadata for downstream filters
setMetadata("wanaku.risk_level", "high");
```

#### Validation (`wanaku:evaluator/validation`)

```javascript
import { verifyLlmResult } from 'wanaku:evaluator/validation';

// Validate ctx.llmResult against the evaluator's declared result_schema.
// Returns { tag: 'ok', val: validatedString } on success,
// or { tag: 'err', val: errorMessage } on failure.
// If no result_schema is configured, always succeeds.
const result = verifyLlmResult(ctx.llmResult);
if (result.tag === 'err') {
  rejectMalformed(`Invalid LLM output: ${result.val}`);
  return;
}
```

#### Conversation (`wanaku:evaluator/conversation`)

```javascript
import { getHistory } from 'wanaku:evaluator/conversation';

const messages = getHistory(ctx.conversationId);
// [{role: "user", content: "..."}, {role: "assistant", content: "..."}, ...]

// Empty array if no history or conversation ID is absent
```

#### Logging (`wanaku:evaluator/log`)

```javascript
import { info, warn, error } from 'wanaku:evaluator/log';

info("Evaluator executed successfully");
warn("LLM returned unexpected format, falling back to default");
error("Failed to parse JSON from LLM result");
```

### Step 4: EvaluationContext Object

The `ctx` parameter passed to `evaluate(ctx)` has these fields:

```typescript
interface EvaluationContext {
  method: string;              // "tools/call", "tools/list", etc.
  namespace: string;           // "default", "finance", etc.
  toolName?: string;           // Present for tools/call, absent for tools/list
  arguments: [string, string][]; // Key-value pairs from the tool call
  llmResult: string;           // Raw LLM output (JSON string, label, or prose)
  conversationId?: string;     // If present in the request
}
```

### Step 5: Compile to WASM

```bash
npx @bytecodealliance/jco componentize action.js \
  --wit features/evaluator/wit/evaluator.wit \
  --world-name evaluator-action \
  --disable all \
  -o action.wasm
```

**Flags explained:**
- `--wit`: path to the WIT interface file
- `--world-name`: the world to target (always `evaluator-action`)
- `--disable all`: Disable all optional WASI features. The action uses only the features in the WIT interface.
- `-o`: output file

The first compilation can report errors if the imports do not match the WIT interface. Verify the namespace and function names.

### Step 6: Deploy

Place the `.wasm` file somewhere the server can read it (e.g., `/wasm/` directory), then reference it in your evaluator config:

```yaml
processor:
  path: "/wasm/safety-block.wasm"
```

Or update via the management API:

```bash
curl -X PUT http://localhost:8080/api/v1/evaluators \
  -H "Content-Type: application/json" \
  -d @evaluator-config.json
```

### Complete JavaScript Examples

#### Safety Gate (classify → block or pass)

```javascript
import { block, rejectMalformed, pass } from 'wanaku:evaluator/response';
import { verifyLlmResult } from 'wanaku:evaluator/validation';
import { warn } from 'wanaku:evaluator/log';

export function evaluate(ctx) {
  // If a result_schema is configured, validate first
  const validated = verifyLlmResult(ctx.llmResult);
  if (validated.tag === 'err') {
    warn(`LLM result failed schema validation: ${validated.val}`);
    rejectMalformed(`Cannot assess safety: ${validated.val}`);
    return;
  }

  let level = "red";
  try {
    const result = JSON.parse(ctx.llmResult);
    level = result.level || "red";
  } catch (e) {
    warn("Failed to parse LLM result, defaulting to red");
  }

  if (level === "red") {
    const reason = `Tool call blocked by safety classification: ${ctx.llmResult}`;
    warn(reason);
    block(reason);
  } else {
    pass();
  }
}
```

**Compile:**

```bash
npx @bytecodealliance/jco componentize safety-block.js \
  --wit features/evaluator/wit/evaluator.wit \
  --world-name evaluator-action \
  --disable all \
  -o safety-block.wasm
```

#### Assembly Filter (filter → populate namespace)

```javascript
import { copyToolToNamespace } from 'wanaku:evaluator/registry';
import { filterTools } from 'wanaku:evaluator/response';
import { info, warn } from 'wanaku:evaluator/log';

export function evaluate(ctx) {
  let approved;
  try {
    approved = JSON.parse(ctx.llmResult);
  } catch (e) {
    warn('Failed to parse LLM result as tool name array, returning all tools');
    return;  // Default behavior: pass, show all tools
  }

  if (!Array.isArray(approved) || approved.length === 0) {
    info('LLM returned empty tool list, returning all tools (fail-open)');
    return;
  }

  // Copy each approved tool into the target namespace
  for (const name of approved) {
    copyToolToNamespace(name, ctx.namespace);
  }

  info(`Registered ${approved.length} tools into namespace '${ctx.namespace}'`);
  
  // Return only the approved tools in the tools/list response
  filterTools(approved);
}
```

**Error handling pattern:** The LLM can return invalid output. Use `try/catch` when you parse JSON. Return early to fail open; the default action is `pass()`.

## Writing Action Scripts in Rust

Rust gives you type safety and better tooling for complex logic. The trade-off is more boilerplate.

### Prerequisites

```bash
# Install cargo-component
cargo install cargo-component

# Add wasm32-wasip1 target
rustup target add wasm32-wasip1
```

### Step 1: Create a cdylib Crate

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

### Step 2: Use wit-bindgen

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

### Step 3: Implement the Guest Trait

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

### Step 4: Build

```bash
cargo component build --release
```

Output: `target/wasm32-wasip1/release/safety_review_action.wasm`

The filename comes from your `[package] name` with `_` replacing `-`.

### Step 5: Deploy

Same as JavaScript — reference the WASM file in your evaluator config:

```yaml
processor:
  path: "/wasm/safety_review_action.wasm"
```

### Complete Rust Example

Full `src/lib.rs` for a safety gate. The action reads the LLM classification
from `ctx.llm_result` and maps the level to a response: `red` blocks, `yellow`
warns, and any other level passes. The LLM is expected to return a JSON object
of the form `{"level": "green|yellow|red", "reason": "..."}`. When the result
is not valid JSON, the level is inferred from the raw text.

```rust
#[allow(warnings)]
mod bindings;

use bindings::wanaku::evaluator::types::EvaluationContext;
use bindings::Guest;

struct SafetyReview;

impl Guest for SafetyReview {
    fn evaluate(ctx: EvaluationContext) {
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

fn classify(llm_result: &str) -> (String, String) {
    if let Ok(value) = serde_json::from_str::<serde_json::Value>(llm_result) {
        let level = value
            .get("level")
            .and_then(serde_json::Value::as_str)
            .unwrap_or("green")
            .to_string();
        let reason = value
            .get("reason")
            .and_then(serde_json::Value::as_str)
            .unwrap_or(llm_result)
            .to_string();
        return (level, reason);
    }

    let lower = llm_result.to_lowercase();
    let level = if lower.contains("red") {
        "red"
    } else if lower.contains("yellow") {
        "yellow"
    } else {
        "green"
    };
    (level.to_string(), llm_result.to_string())
}

bindings::export!(SafetyReview with_types_in bindings);
```

**Why this works:** The `bindings` module is generated from `evaluator.wit` by `cargo-component`. It provides Rust types for `EvaluationContext` and functions for all host imports. You implement the `Guest` trait's `evaluate` method, and `bindings::export!` makes it callable from the host.

## Management API

Hot-reload evaluators and manage namespace bindings without restarting the server.

### List Evaluators

```bash
curl http://localhost:8080/api/v1/evaluators
```

**Response:**

```json
{
  "data": [
    {
      "name": "safety-gate",
      "trigger": {"method": "tools/call"},
      "llm": {
        "operation": "classify",
        "prompt": "...",
        "connection": "local-llama"
      },
      "processor": {"path": "/wasm/safety-gate.wasm"},
      "on_error": "continue"
    }
  ],
  "error": null
}
```

This response has no `model`, `url`, or `api_key` field. Those fields live only in
`llm_connections` in `wanaku.yaml`. They never transit the management API. The
`llm.connection` field names the connection the evaluator uses.

### Update Evaluators (Hot-Reload)

```bash
curl -X PUT http://localhost:8080/api/v1/evaluators \
  -H "Content-Type: application/json" \
  -d '{
    "evaluators": [
      {
        "name": "safety-gate",
        "trigger": {"method": "tools/call"},
        "llm": {
          "operation": "classify",
          "prompt": "You are a safety classifier...",
          "connection": "local-llama"
        },
        "processor": {"path": "/wasm/safety-gate.wasm"}
      }
    ]
  }'
```

**What happens:** The engine:
1. Parses the config
2. Validates that `llm.connection` names a connection already loaded from `wanaku.yaml` — an unknown name rejects the whole update with `422` and leaves the previous evaluators active
3. Compiles all WASM files (expensive — do this at startup or infrequently)
4. Replaces the active evaluators
5. Returns the new config in `{"data": [...], "error": null}`

**If a WASM file fails to compile:** That evaluator is skipped, the rest are loaded, and you get a warning in the response.

**Legacy payloads:** a request with `model`/`url`/`api_key` inline under `llm` (the
pre-connection shape) is rejected with `400` rather than silently dropping those fields.

### List LLM Connections

```bash
curl http://localhost:8080/api/v1/evaluators/llm-connections
```

**Response:**

```json
{
  "data": ["local-llama"],
  "error": null
}
```

Read-only. Lists connection names loaded from `wanaku.yaml` at startup, for
display/selection purposes. Returns names only — never `model`, `url`, or `api_key` —
so this endpoint has nothing about your LLM backend worth leaking. There is no
endpoint to create, update, or delete a connection; edit `wanaku.yaml` and restart
the server.

### List Namespace Bindings

```bash
curl http://localhost:8080/api/v1/evaluators/namespaces
```

**Response:**

```json
{
  "data": {
    "finance-team": "conv-finance-2024",
    "engineering": "conv-eng-0815"
  },
  "error": null
}
```

### Bind Namespace to Conversation

```bash
curl -X PUT http://localhost:8080/api/v1/evaluators/namespaces/finance-team \
  -H "Content-Type: application/json" \
  -d '{"conversation_id": "conv-finance-2024"}'
```

**Use case:** You want the evaluator to retrieve conversation history for a specific namespace. When a request arrives for the `finance-team` namespace, the evaluator looks up the binding, resolves the conversation ID, and fetches the matching interaction history to include in the LLM prompt.

### Unbind Namespace

```bash
curl -X DELETE http://localhost:8080/api/v1/evaluators/namespaces/finance-team
```

## Testing Action Scripts

### Quick Local Test

This script configures an evaluator, makes a tool call against a tool discovered from a forwarded MCP server, and verifies the response.

**Prerequisites:** You need an upstream MCP server that exposes at least one tool. Register it as a forward before running this script:

```bash
curl -X POST http://localhost:8080/api/v1/forwards \
  -H "Content-Type: application/json" \
  -d '{"name":"my-mcp-server","address":"http://<your-mcp-server>/mcp"}'
```

Replace `restart-database` in the script below with a tool name discovered from your forward (`curl http://localhost:8080/api/v1/tools` to see available tools).

```bash
#!/usr/bin/env bash
set -euo pipefail

MGMT=http://localhost:8080
MCP=http://localhost:8081
WASM="$(pwd)/actions/dist/safety_review_action.wasm"

echo "Configuring evaluator with WASM action..."
curl -sf -X PUT $MGMT/api/v1/evaluators -H "Content-Type: application/json" \
  -d '{
    "evaluators": [{
      "name": "js-safety",
      "trigger": {"method": "tools/call"},
      "llm": {
        "operation": "classify",
        "prompt": "You are a safety classifier. You MUST classify every tool call as exactly one of: green, yellow, or red. Restarting any database is ALWAYS red. Respond with ONLY a JSON object, no other text: {\"level\": \"green|yellow|red\", \"reason\": \"brief\"}",
        "model": "llama3.2",
        "url": "http://localhost:11434/v1"
      },
      "processor": {"path": "'"$WASM"'"}
    }]
  }' > /dev/null

echo "Making tool call..."
curl -sf -X POST $MCP/default/mcp -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"restart-database","arguments":{"target":"prod"}}}' | jq .

echo "Cleaning up evaluator..."
curl -sf -X PUT $MGMT/api/v1/evaluators -H "Content-Type: application/json" -d '{"evaluators":[]}' > /dev/null
echo "Done."
```

**Expected output:** JSON-RPC error with code `-32001` and message containing "blocked by safety classification".

### Rust Integration Tests (no server needed)

The evaluator crate has integration tests that run in the process. The test runner skips WASM engine tests if the action `.wasm` files do not exist.

```bash
# Run all integration tests
cargo test -p wanaku-feature-evaluator

# Build WASM actions so engine tests run (instead of skipping)
cd actions/safety-review && cargo component build --release && cp target/wasm32-wasip1/release/*.wasm ../dist/
```

Tests cover: config parsing (including `result_schema`), trigger matching, evaluator state management, schema validation, action result variants, and WASM action execution with hardcoded `llm_result` (no LLM needed).

### Rust E2E Tests (server required, no LLM needed)

End-to-end tests send requests to the management API and MCP endpoint on a running server. These tests replace the shell scripts and use the `#[ignore]` attribute.

```bash
# Start wanaku-server first, then:
cargo test -p wanaku-feature-evaluator --test e2e -- --ignored
```

Tests cover: evaluator API CRUD, WASM block/warn actions, clearing evaluators, `result_schema` config, namespace-scoped evaluators, and LLM classification (classification tests also need Ollama on `localhost:11434`).

**What to look for in server logs:**

```
INFO  evaluator triggered method="tools/call" namespace="default" evaluator="safety-gate"
INFO  LLM operation result llm_result="{\"level\":\"red\",\"reason\":\"database restart\"}"
INFO  processor result action=Block("Tool call blocked by safety classification: red")
```

These traces confirm:
1. The filter matched the trigger
2. The LLM returned a classification
3. The WASM action executed and returned a decision

## How It Works (Internals)

You do not need these internal details to use the engine. Use them to diagnose execution problems.

### Execution Flow

1. **MCP request arrives** → praxis-ai MCP filter parses JSON-RPC, sets `mcp.method` metadata
2. **Namespace filter** sets `wanaku.namespace` metadata from URL path
3. **Evaluator filter** (`wanaku_evaluator` in the pipeline):
   - Reads metadata to get method, namespace, tool name, arguments
   - Finds matching evaluators (trigger.method matches, optional namespace/binding checks)
   - For each match:
     - Builds context prompt (conversation history + request details)
     - Calls LLM with your system prompt + context
     - Extracts the raw LLM output
     - If `result_schema` is configured, validates the output; on mismatch, retries once with a correction prompt that includes the specific validation error
     - Loads pre-compiled WASM processor module
     - Instantiates WASM with fresh host state (registry, interactions, result accumulator)
     - Calls `evaluate(ctx)` export
     - WASM calls host imports (e.g., `block()`, `copyToolToNamespace()`)
     - Host state accumulates the action result
     - Returns action (Pass, Block, RejectMalformed, Warn, FilterTools, SetMetadata)
   - Applies the action: Block → JSON-RPC error (-32001), RejectMalformed → JSON-RPC error (-32002), FilterTools → synthetic response, etc.

### WASM Compilation

Happens at startup and on `PUT /api/v1/evaluators`. The engine uses **wasmtime** with the component model:
- Loads the `.wasm` file
- Compiles it to native code (JIT)
- Links WASI + custom host imports
- Stores the compiled module

**Each invocation gets a fresh instance** — no state leaks between calls.

### LLM Context Building

The engine builds the user prompt from:
- **Conversation history** (last 10 interactions from the intercept filter's in-memory store)
- **Request details** (method, namespace, tool name, arguments)
- **Available tools** (for `tools/list` triggers)

Your system prompt tells the LLM what to extract from that context. The engine sanitizes all user input (truncates long strings, strips control characters) to prevent prompt injection.

### Error Handling

- **LLM schema validation failure**: If you set `result_schema` and the LLM output does not match, the engine retries once with a correction prompt. If the retry fails, the engine passes the raw result to the WASM guest. The guest can use `verifyLlmResult()` and `rejectMalformed()`.
- **LLM failure** (network error, timeout, invalid response): logged, evaluator skipped, request proceeds
- **WASM compile failure**: logged at hot-reload time, evaluator disabled
- **WASM runtime failure**: depends on `on_error`:
  - `continue` (default): logged, action is treated as `Pass`, request proceeds
  - `block`: logged, request blocked with JSON-RPC error

**Philosophy:** Fail open for operational resilience. A safety gate must not cause an availability failure. To fail closed, use `on_error: block`.

## Common Patterns

### Fail-Open Safety Gate

```yaml
on_error: continue  # LLM/WASM failures do not block production
processor:
  path: "/wasm/safety-gate.wasm"
```

If the LLM is down, requests proceed. The WASM script decides whether to block based on the LLM output — if it never runs, the request continues.

### Fail-Closed Safety Gate

```yaml
on_error: block  # Any failure blocks the request
processor:
  path: "/wasm/safety-gate.wasm"
```

If the LLM is down or WASM crashes, the request is blocked. Use this for high-security environments where availability is secondary to safety.

### Namespace-Scoped Evaluation

Restrict evaluators to specific namespaces:

```yaml
trigger:
  method: "tools/list"
  namespace: "production"
```

This evaluator only fires for requests to `/production/mcp`. The WASM action can use `filterTools()` to curate the tool catalog for that namespace.

### Namespace-Conversation Binding

Bind a namespace to a conversation ID so the evaluator can retrieve relevant interaction history:

```bash
curl -X PUT http://localhost:8080/api/v1/evaluators/namespaces/finance-team \
  -H "Content-Type: application/json" \
  -d '{"conversation_id": "conv-finance-2024"}'
```

When a request arrives for the `finance-team` namespace, the evaluator resolves the conversation ID from the binding and includes the matching interaction history in the LLM prompt. This gives the LLM context about what the user has been doing in this session.

### Metadata Propagation

Set metadata in your WASM action:

```javascript
import { setMetadata } from 'wanaku:evaluator/response';

export function evaluate(ctx) {
  const risk = JSON.parse(ctx.llmResult).risk_level;
  setMetadata("wanaku.risk_level", risk);
  setMetadata("wanaku.evaluator", "safety-gate");
}
```

Downstream filters can read this metadata and make decisions (e.g., extra logging for high-risk calls, rate limiting).

## Troubleshooting

### "WASM component failed to compile"

**Cause:** The `.wasm` file is invalid or does not match the WIT interface.

**Fix:**
1. Verify you compiled with the correct `--wit` path and `--world-name evaluator-action`
2. Check that your imports match the WIT exactly (namespace, function names, signatures)
3. For JavaScript, use `jco componentize`. Do not use `jco transpile`.
4. For Rust, verify `cargo.toml` has the correct `[package.metadata.component.target]` path and world

### "Evaluator did not trigger"

**Cause:** The trigger does not match the request.

**Fix:**
1. Check server logs for `evaluator filter` trace messages — they show what the filter sees
2. Verify `trigger.method` matches `mcp.method` metadata (e.g., `tools/call` not `tool/call`)
3. If you use `trigger.namespace`, verify that the request URL uses `/{namespace}/mcp`. A bare `/mcp` path is invalid.

Enable trace logs: `RUST_LOG=wanaku_feature_evaluator=trace`

### "LLM returned invalid output"

**Cause:** The LLM did not follow the prompt format.

**Fix:**
1. Add `result_schema` to the LLM configuration. The host validates the LLM output. If the output does not match, the host retries once with a correction prompt:
   ```yaml
   llm:
     result_schema:
       type: object
       properties:
         level: { type: string, enum: ["green", "yellow", "red"] }
         reason: { type: string }
       required: ["level", "reason"]
   ```
2. **Use `verifyLlmResult()` in your WASM script** — even with host-side validation, the guest can double-check and call `rejectMalformed()` instead of silently proceeding with bad data:
   ```javascript
   const validated = verifyLlmResult(ctx.llmResult);
   if (validated.tag === 'err') {
     rejectMalformed(`Cannot assess: ${validated.val}`);
     return;
   }
   ```
3. Make your prompt more explicit: "Respond with ONLY a JSON object, no other text"
4. Use a smaller, faster model for classification tasks — they need less context

### "WASM action did nothing"

**Cause:** The action did not call a response function. The default `pass()` action applied.

**Fix:** Call `block()`, `warn()`, `filterTools()`, or `setMetadata()` in the action. To pass explicitly, call `pass()`. This call is optional.

### "Hot-reload did not apply my WASM changes"

**Cause:** You changed the WASM file but did not call `PUT /api/v1/evaluators`.

**Fix:** The engine only compiles WASM at hot-reload time. After building a new `.wasm` file, trigger a reload:

```bash
curl -X PUT http://localhost:8080/api/v1/evaluators \
  -H "Content-Type: application/json" \
  -d @current-config.json
```

This re-compiles all WASM files referenced in the config.

## Performance Notes

- **LLM calls are slow** (100-500ms per classification). Evaluators add latency to the request path. Use them sparingly, or only for high-stakes operations (e.g., `tools/call`, not `tools/list`).
- **WASM compilation is expensive** (10-50ms per module). The engine caches compiled modules, so only the first load (or hot-reload) is slow.
- **WASM execution is fast** (sub-millisecond for simple logic). A compiled action has minimal overhead.
- **Conversation history is capped at 10 interactions** to keep LLM context bounded. If you need more, fetch it explicitly in your WASM action via `getHistory()`.

## Security Considerations

- **WASM is sandboxed** — actions cannot access the filesystem, network, or system calls beyond what the WIT interface exposes.
- **LLM prompts can be attacked** — sanitize user input, use system prompts that are robust to injection, and fail open if unsure.
- **WASM actions are deterministic** — the same input always produces the same output. Use this property to test your logic thoroughly.
- **Namespace bindings are ephemeral** — they live in memory, not persisted. If the server restarts, you lose bindings (but evaluator definitions from `wanaku.yaml` are preserved).
- **LLM credentials never transit the management API.** `model`, `url`, and `api_key` live only in `llm_connections` in `wanaku.yaml`, loaded once at startup. Evaluators refer to a connection by name. No route can set, update, or read an `api_key`, not on evaluator create or update, and not in evaluator revision history. To rotate a credential, edit `wanaku.yaml` and restart the server.

## Next Steps

- **Start simple:** Safety classification with a single rule is the easiest on-ramp.
- **Iterate on prompts:** The LLM is the cognitive core — spend time tuning your system prompt.
- **Test offline:** Write unit tests for your WASM logic before deploying.
- **Monitor logs:** The evaluator filter emits detailed traces — watch for LLM responses, WASM decisions, and action results.
- **Read the WIT:** [`features/evaluator/wit/evaluator.wit`](../features/evaluator/wit/evaluator.wit) is the authoritative API reference.

## Reference Links

- **WIT Interface:** [`features/evaluator/wit/evaluator.wit`](../features/evaluator/wit/evaluator.wit)
- **TypeScript Definitions:** [`sdk/js/wanaku-actions.d.ts`](../sdk/js/wanaku-actions.d.ts)
- **JavaScript Examples:** [`actions/js-examples/`](../actions/js-examples/)
- **Rust Example:** [`actions/safety-review/`](../actions/safety-review/)
- **jco Componentize Docs:** [Bytecode Alliance jco](https://github.com/bytecodealliance/jco)
- **cargo-component Docs:** [cargo-component](https://github.com/bytecodealliance/cargo-component)
