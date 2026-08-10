# Evaluator Engine — Developer Guide

The evaluator engine lets you build **trigger→evaluate→act** pipelines that inject LLM reasoning into the MCP request path. When a request matches a trigger, the engine calls an LLM to classify, filter, or augment the request, then executes a WebAssembly action script that has full access to the registry, conversation history, and response control.

You write the logic in JavaScript or Rust. The engine compiles it to WASM and runs it in a sandboxed environment with a clean, versioned API defined by the [WIT interface](../features/evaluator/wit/evaluator.wit).

**Why this exists:** MCP tools can be dangerous. You want to block "restart production database" calls, or dynamically assemble a per-conversation tool catalog based on what the LLM thinks the user needs. Pure LLM-based gates are too brittle (hallucination, prompt injection). Pure rule-based gates are too rigid. This engine gives you both: LLM cognitive work + deterministic WASM execution.

## YAML Configuration

Evaluators live in `wanaku.yaml` or get pushed via the management API. Each evaluator has five parts:

```yaml
evaluators:
  - name: "safety-gate"               # Unique identifier
    trigger:                          # When to run
      method: "tools/call"            # MCP method (tools/call, tools/list, etc.)
      namespace: "production"         # Optional: only this namespace
      binding: "conv-12345"           # Optional: only this conversation ID
    llm:                              # LLM operation
      operation: classify             # classify | filter | augment
      labels: ["green", "yellow", "red"]  # For classify: possible outputs
      prompt: "You are a safety classifier..."
      model: "llama3.2"
      url: "http://localhost:11434/v1"
      api_key: ""                     # Optional bearer token
    rules:                            # For classify: label → action map
      green: "pass"
      yellow: "pass"
      red: {path: "/wasm/safety-block.wasm"}
    action:                           # For filter/augment: single action
      path: "/wasm/assembly-filter.wasm"
    on_error: continue                # continue | block (default: continue)
```

### Trigger Fields

| Field | Type | Purpose |
|-------|------|---------|
| `method` | string | MCP method to match (e.g., `tools/call`, `tools/list`, `resources/read`) |
| `namespace` | string | Optional. Only trigger for this namespace (extracted from URL path `/finance/mcp` → `finance`) |
| `binding` | string | Optional. Only trigger for requests with this conversation ID |

**Namespace binding pattern:** Use `PUT /api/v1/evaluators/namespaces/{namespace}` to bind a namespace to a conversation ID. Then set `binding` to that conversation ID. This lets you create per-conversation tool catalogs.

### LLM Fields

| Field | Type | Purpose |
|-------|------|---------|
| `operation` | string | `classify` (pick a label), `filter` (return structured data), or `augment` (enrich prompt) |
| `labels` | array | For classify only: the allowed labels (e.g., `["safe", "dangerous"]`) |
| `prompt` | string | System prompt for the LLM. The engine builds a user prompt with request context. |
| `model` | string | Model name (passed to `/v1/chat/completions`) |
| `url` | string | OpenAI-compatible endpoint URL |
| `api_key` | string | Optional bearer token for the LLM endpoint |

**How the LLM sees context:** The engine builds a user prompt containing:
- Recent conversation history (last 10 interactions from the intercept filter)
- MCP method and namespace
- Tool name and arguments (for `tools/call`)
- Available tools (for `tools/list`)

Your system prompt tells the LLM what to do with that context.

### Rules (classify only)

Map each label to an action. Actions are either `"pass"` (continue) or a WASM file path:

```yaml
rules:
  safe: "pass"
  dangerous: {path: "/wasm/block-dangerous.wasm"}
```

**The WASM script receives the raw LLM output** in `ctx.llmResult` — useful if the LLM returns JSON with extra metadata beyond just the label.

### Action (filter/augment only)

Single WASM action that processes the LLM output:

```yaml
action: {path: "/wasm/assembly-filter.wasm"}
```

Or string shorthand:

```yaml
action: "/wasm/assembly-filter.wasm"
```

### Error Policy

- `on_error: continue` (default) — WASM failures are logged, request proceeds
- `on_error: block` — WASM failures block the request with a JSON-RPC error

## Complete Examples

### Example 1: Safety Classification

Block dangerous tool calls based on LLM classification.

```yaml
evaluators:
  - name: "safety-gate"
    trigger:
      method: "tools/call"
    llm:
      operation: classify
      labels: ["green", "yellow", "red"]
      prompt: |
        You are a strict safety classifier. Classify this tool call as:
        - green: safe operations (read-only, low impact)
        - yellow: elevated operations (writes, config changes)
        - red: dangerous operations (database restarts, production deploys)
        
        Respond with ONLY a JSON object: {"level": "green|yellow|red", "reason": "brief explanation"}
      model: "llama3.2"
      url: "http://localhost:11434/v1"
    rules:
      green: "pass"
      yellow: "pass"
      red: {path: "/wasm/safety-block.wasm"}
    on_error: continue
```

**What happens:**
1. User calls `restart-database` tool
2. Engine sends tool name + args to LLM with your prompt
3. LLM returns `{"level": "red", "reason": "database restart is dangerous"}`
4. Engine extracts `"red"` label, runs `safety-block.wasm`
5. WASM script calls `block("Tool call blocked by safety classification: red")`
6. User gets JSON-RPC error instead of executing the tool

### Example 2: Tool Assembly

Dynamically populate a namespace with only the tools the LLM approves for this conversation.

```yaml
evaluators:
  - name: "assembly-gate"
    trigger:
      method: "tools/list"
      binding: "conv-finance-2024"  # Only for this conversation
    llm:
      operation: filter
      prompt: |
        You are a tool curator. Given the conversation history and available tools,
        return a JSON array of tool names that are relevant and safe for this user.
        
        Respond with ONLY a JSON array: ["tool-name-1", "tool-name-2"]
      model: "llama3.2"
      url: "http://localhost:11434/v1"
    action: {path: "/wasm/assembly-filter.wasm"}
```

**What happens:**
1. User sends `tools/list` with conversation ID `conv-finance-2024`
2. Engine sends conversation history + available tools to LLM
3. LLM returns `["read-balance", "transfer-funds", "generate-report"]`
4. Engine runs `assembly-filter.wasm` with that JSON in `ctx.llmResult`
5. WASM script parses JSON, calls `copyToolToNamespace(name, ctx.namespace)` for each
6. WASM calls `filterTools(approved)` to return only the assembled tools
7. User sees a curated tool list

**Namespace binding setup:**

```bash
# Bind the 'finance-team' namespace to conversation ID 'conv-finance-2024'
curl -X PUT http://localhost:8080/api/v1/evaluators/namespaces/finance-team \
  -H "Content-Type: application/json" \
  -d '{"conversationId": "conv-finance-2024"}'
```

## Writing Action Scripts in JavaScript

JavaScript is the fastest path from idea to working WASM. You write normal JS, compile it with `jco`, and the engine runs it.

### Prerequisites

```bash
npm install -g @bytecodealliance/jco
```

You'll also want the TypeScript definitions for autocomplete:

```bash
# Copy from the repo
cp sdk/js/wanaku-actions.d.ts /path/to/your/action/
```

### Step 1: Import Host Functions

The host provides four namespaces. Import what you need:

```javascript
import { block, warn, pass, filterTools, setMetadata } from 'wanaku:evaluator/response';
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
import { pass, block, warn, filterTools, setMetadata } from 'wanaku:evaluator/response';

// Allow the request to proceed
pass();

// Block the request with a JSON-RPC error
block("Tool call blocked: database restart requires manual approval");

// Log a warning but allow the request to proceed
warn("Elevated privilege operation detected");

// Return a filtered tools/list response (only for tools/list triggers)
filterTools(["tool-1", "tool-2", "tool-3"]);

// Set metadata for downstream filters
setMetadata("wanaku.risk_level", "high");
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
- `--disable all`: disable all optional WASI features (we only need what's in the WIT)
- `-o`: output file

The first time you compile, expect cryptic errors if your imports don't match the WIT interface exactly. Double-check the namespace and function names.

### Step 6: Deploy

Place the `.wasm` file somewhere the server can read it (e.g., `/wasm/` directory), then reference it in your YAML:

```yaml
rules:
  red: {path: "/wasm/safety-block.wasm"}
```

Or update via the management API:

```bash
curl -X PUT http://localhost:8080/api/v1/evaluators \
  -H "Content-Type: application/json" \
  -d @evaluator-config.json
```

### Complete JavaScript Examples

#### Safety Block (classify → block)

```javascript
import { block } from 'wanaku:evaluator/response';
import { warn } from 'wanaku:evaluator/log';

export function evaluate(ctx) {
  const reason = `Tool call blocked by safety classification: ${ctx.llmResult}`;
  warn(reason);
  block(reason);
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

**Error handling pattern:** The LLM can return garbage. Always `try/catch` JSON parsing and fail open (return early, default is `pass()`).

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
cargo new --lib safety-block-action
cd safety-block-action
```

Edit `Cargo.toml`:

```toml
[package]
name = "safety-block-action"
version = "0.1.0"
edition = "2024"

[lib]
crate-type = ["cdylib"]

[dependencies]
wit-bindgen = "0.41"
wit-bindgen-rt = "0.41"

[package.metadata.component]
package = "wanaku:safety-block"

[package.metadata.component.target]
path = "wit/evaluator.wit"
world = "evaluator-action"
```

Copy the WIT file into your crate:

```bash
cp /path/to/wanaku-praxis/features/evaluator/wit/evaluator.wit wit/evaluator.wit
```

### Step 2: Use wit-bindgen

In `src/lib.rs`:

```rust
#[allow(warnings)]
mod bindings;

use bindings::wanaku::evaluator::types::EvaluationContext;
use bindings::Guest;

struct SafetyBlock;

impl Guest for SafetyBlock {
    fn evaluate(ctx: EvaluationContext) {
        // Your logic here
    }
}

bindings::export!(SafetyBlock with_types_in bindings);
```

The `bindings` module is auto-generated by `cargo component build` — you don't write it.

### Step 3: Implement the Guest Trait

Access host imports via `bindings::wanaku::evaluator::{registry, response, log, conversation}`:

```rust
impl Guest for SafetyBlock {
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
use bindings::wanaku::evaluator::{registry, response, conversation, log};

// Registry
let tools = registry::list_tools();
let tool = registry::get_tool("restart-database");
let copied = registry::copy_tool_to_namespace("read-balance", "finance");

// Response (call exactly one)
response::pass();
response::block("reason");
response::warn("message");
response::filter_tools(&["tool-1", "tool-2"]);
response::set_metadata("key", "value");

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

Output: `target/wasm32-wasip1/release/safety_block_action.wasm`

The filename comes from your `[package] name` with `_` replacing `-`.

### Step 5: Deploy

Same as JavaScript — reference the WASM file in your evaluator config:

```yaml
rules:
  red: {path: "/wasm/safety_block_action.wasm"}
```

### Complete Rust Example

Full `src/lib.rs` for a safety blocker:

```rust
#[allow(warnings)]
mod bindings;

use bindings::wanaku::evaluator::types::EvaluationContext;
use bindings::Guest;

struct SafetyBlock;

impl Guest for SafetyBlock {
    fn evaluate(ctx: EvaluationContext) {
        let reason = format!(
            "Tool call blocked by safety classification: {}",
            ctx.llm_result
        );
        bindings::wanaku::evaluator::log::warn(&reason);
        bindings::wanaku::evaluator::response::block(&reason);
    }
}

bindings::export!(SafetyBlock with_types_in bindings);
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
        "labels": ["green", "yellow", "red"],
        "prompt": "...",
        "model": "llama3.2",
        "url": "http://localhost:11434/v1",
        "api_key": ""
      },
      "rules": {
        "green": "pass",
        "red": {"path": "/wasm/safety-block.wasm"}
      },
      "on_error": "continue"
    }
  ],
  "error": null
}
```

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
          "labels": ["green", "yellow", "red"],
          "prompt": "You are a safety classifier...",
          "model": "llama3.2",
          "url": "http://localhost:11434/v1"
        },
        "rules": {
          "green": "pass",
          "red": {"path": "/wasm/safety-block.wasm"}
        }
      }
    ]
  }'
```

**What happens:** The engine:
1. Parses the config
2. Compiles all WASM files (expensive — do this at startup or infrequently)
3. Replaces the active evaluators
4. Returns the new config in `{"data": [...], "error": null}`

**If a WASM file fails to compile:** That evaluator is skipped, the rest are loaded, and you get a warning in the response.

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
  -d '{"conversationId": "conv-finance-2024"}'
```

**Use case:** You want the `assembly-gate` evaluator to only fire for namespace `finance-team` when the conversation ID is `conv-finance-2024`. This binding makes that work.

### Unbind Namespace

```bash
curl -X DELETE http://localhost:8080/api/v1/evaluators/namespaces/finance-team
```

## Testing Action Scripts

### Quick Local Test

This script registers a test tool, configures an evaluator, makes a tool call, and verifies the response.

```bash
#!/usr/bin/env bash
set -euo pipefail

MGMT=http://localhost:8080
MCP=http://localhost:8081
WASM="$(pwd)/actions/dist/safety-block.wasm"

echo "Registering tool..."
curl -sf -X POST $MGMT/api/v1/tools -H "Content-Type: application/json" \
  -d '{"name":"restart-database","description":"Restart a production database","uri":"http://localhost:8080/mcp","type":"mcp-forward","inputSchema":{"type":"object","properties":{}}}' > /dev/null

echo "Configuring evaluator with WASM action..."
curl -sf -X PUT $MGMT/api/v1/evaluators -H "Content-Type: application/json" \
  -d '{
    "evaluators": [{
      "name": "js-safety",
      "trigger": {"method": "tools/call"},
      "llm": {
        "operation": "classify",
        "labels": ["green", "yellow", "red"],
        "prompt": "You are a safety classifier. You MUST classify every tool call as exactly one of: green, yellow, or red. Restarting any database is ALWAYS red. Respond with ONLY a JSON object, no other text: {\"level\": \"green|yellow|red\", \"reason\": \"brief\"}",
        "model": "llama3.2",
        "url": "http://localhost:11434/v1"
      },
      "rules": {
        "green": "pass",
        "yellow": "pass",
        "red": {"path": "'"$WASM"'"}
      }
    }]
  }' > /dev/null

echo "Making tool call..."
curl -sf -X POST $MCP/mcp -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"restart-database","arguments":{"target":"prod"}}}' | jq .

echo "Cleaning up..."
curl -sf -X PUT $MGMT/api/v1/evaluators -H "Content-Type: application/json" -d '{"evaluators":[]}' > /dev/null
curl -sf -X DELETE $MGMT/api/v1/tools/restart-database > /dev/null
echo "Done."
```

**Expected output:** JSON-RPC error with code `-32001` and message containing "blocked by safety classification".

### Integration Test

The full integration test lives in `scripts/test-evaluator.sh`. It:
1. Verifies prerequisites (curl, jq, Ollama, wanaku-praxis running)
2. Registers test tools
3. Configures evaluators via management API
4. Makes tool calls to trigger evaluations
5. Verifies responses
6. Checks server logs for expected trace messages

**Run it:**

```bash
./scripts/test-evaluator.sh
```

**What to look for in server logs:**

```
TRACE wanaku_praxis_evaluator: evaluator triggered method="tools/call" namespace="default" evaluator="safety-gate"
DEBUG wanaku_praxis_evaluator: evaluator LLM response llm_response="{\"level\":\"red\",\"reason\":\"database restart\"}"
INFO  wanaku_praxis_evaluator: evaluator action result action=Block("Tool call blocked by safety classification: red")
```

These traces confirm:
1. The filter matched the trigger
2. The LLM returned a classification
3. The WASM action executed and returned a decision

## How It Works (Internals)

You don't need to understand this to use the engine, but it helps when things go sideways.

### Execution Flow

1. **MCP request arrives** → praxis-ai MCP filter parses JSON-RPC, sets `mcp.method` metadata
2. **Namespace filter** sets `wanaku.namespace` metadata from URL path
3. **Evaluator filter** (`wanaku_evaluator` in the pipeline):
   - Reads metadata to get method, namespace, tool name, arguments
   - Finds matching evaluators (trigger.method matches, optional namespace/binding checks)
   - For each match:
     - Builds context prompt (conversation history + request details)
     - Calls LLM with your system prompt + context
     - Extracts result (classify → label, filter/augment → raw output)
     - Loads pre-compiled WASM module
     - Instantiates WASM with fresh host state (registry, interactions, result accumulator)
     - Calls `evaluate(ctx)` export
     - WASM calls host imports (e.g., `block()`, `copyToolToNamespace()`)
     - Host state accumulates the action result
     - Returns action (Pass, Block, Warn, FilterTools, SetMetadata)
   - Applies the action: Block → JSON-RPC error, FilterTools → synthetic response, etc.

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

- **LLM failure** (network error, timeout, invalid response): logged, evaluator skipped, request proceeds
- **WASM compile failure**: logged at hot-reload time, evaluator disabled
- **WASM runtime failure**: depends on `on_error`:
  - `continue` (default): logged, action is treated as `Pass`, request proceeds
  - `block`: logged, request blocked with JSON-RPC error

**Philosophy:** Fail open for operational resilience. Safety gates shouldn't become availability killers. If you want fail-closed, use `on_error: block`.

## Common Patterns

### Fail-Open Safety Gate

```yaml
on_error: continue  # LLM/WASM failures don't block production
rules:
  dangerous: {path: "/wasm/block.wasm"}
  safe: "pass"
```

If the LLM is down, requests proceed. Only active blocking happens when the LLM explicitly returns `dangerous`.

### Fail-Closed Safety Gate

```yaml
on_error: block  # Any failure blocks the request
rules:
  safe: "pass"
  dangerous: {path: "/wasm/block.wasm"}
```

If the LLM is down or WASM crashes, the request is blocked. Use this for high-security environments where availability is secondary to safety.

### Per-Conversation Tool Assembly

1. Bind namespace to conversation:
   ```bash
   curl -X PUT http://localhost:8080/api/v1/evaluators/namespaces/user-123 \
     -H "Content-Type: application/json" \
     -d '{"conversationId": "conv-abc-xyz"}'
   ```

2. Configure evaluator with binding:
   ```yaml
   trigger:
     method: "tools/list"
     binding: "conv-abc-xyz"
   ```

3. WASM action copies approved tools into `ctx.namespace` and calls `filterTools(approved)`

Result: Each conversation gets its own curated tool catalog, and the LLM only sees tools it approved for that user.

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

**Cause:** The `.wasm` file is invalid or doesn't match the WIT interface.

**Fix:**
1. Verify you compiled with the correct `--wit` path and `--world-name evaluator-action`
2. Check that your imports match the WIT exactly (namespace, function names, signatures)
3. For JavaScript, make sure you're using `jco componentize`, not `jco transpile`
4. For Rust, verify `cargo.toml` has the correct `[package.metadata.component.target]` path and world

### "Evaluator didn't trigger"

**Cause:** Trigger doesn't match the request.

**Fix:**
1. Check server logs for `evaluator filter` trace messages — they show what the filter sees
2. Verify `trigger.method` matches `mcp.method` metadata (e.g., `tools/call` not `tool/call`)
3. If using `trigger.namespace`, verify the request URL is `/namespace/mcp`, not `/mcp`
4. If using `trigger.binding`, verify the conversation ID is set and matches the binding

Enable trace logs: `RUST_LOG=wanaku_praxis_evaluator=trace`

### "LLM returned garbage"

**Cause:** LLM didn't follow your prompt format.

**Fix:**
1. Make your prompt more explicit: "Respond with ONLY a JSON object, no other text"
2. Add error handling in your WASM script:
   ```javascript
   try {
     const result = JSON.parse(ctx.llmResult);
   } catch (e) {
     warn('LLM returned invalid JSON, falling back to default');
     return;  // Default: pass
   }
   ```
3. Use `llm.labels` to constrain classify operations — the engine will fuzzy-match even if JSON parsing fails

### "WASM action did nothing"

**Cause:** You didn't call a response function, so the default `pass()` applied.

**Fix:** Explicitly call `block()`, `warn()`, `filterTools()`, or `setMetadata()` in your action. If you want to pass, you can call `pass()` explicitly, but it's not required.

### "Hot-reload didn't pick up my WASM changes"

**Cause:** You edited the WASM file but didn't call `PUT /api/v1/evaluators`.

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
- **WASM execution is fast** (sub-millisecond for simple logic). Once compiled, there's minimal overhead.
- **Conversation history is capped at 10 interactions** to keep LLM context bounded. If you need more, fetch it explicitly in your WASM action via `getHistory()`.

## Security Considerations

- **WASM is sandboxed** — actions can't access the filesystem, network, or system calls beyond what the WIT interface exposes.
- **LLM prompts can be attacked** — sanitize user input, use system prompts that are robust to injection, and fail open if unsure.
- **WASM actions are deterministic** — the same input always produces the same output. Use this property to test your logic thoroughly.
- **Namespace bindings are ephemeral** — they live in memory, not persisted. If the server restarts, you lose bindings (but evaluator definitions from `wanaku.yaml` are preserved).

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
- **Rust Example:** [`actions/safety-block/`](../actions/safety-block/)
- **Integration Test:** [`scripts/test-evaluator.sh`](../scripts/test-evaluator.sh)
- **jco Componentize Docs:** [Bytecode Alliance jco](https://github.com/bytecodealliance/jco)
- **cargo-component Docs:** [cargo-component](https://github.com/bytecodealliance/cargo-component)
