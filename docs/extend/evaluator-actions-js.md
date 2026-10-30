---
title: Write evaluator actions in JavaScript
description: Write a Wanaku evaluator action script in JavaScript, compile it to WebAssembly with jco, deploy it, and test it against a running server.
---

# Write evaluator actions in JavaScript

JavaScript is the fastest path from idea to working WASM. You write normal JS, compile it with `jco`, and the engine runs it.

## Prerequisites

```bash
npm install -g @bytecodealliance/jco
```

Install the TypeScript definitions to enable autocomplete:

```bash
# Copy from the repo
cp sdk/js/wanaku-actions.d.ts /path/to/your/action/
```

The `wanaku-evaluators-<version>.tar.gz` release asset also contains this file in `sdk/js/` and the matching WIT interface in `wit/`. Use the files from the release that matches your server version.

## Step 1: Import Host Functions

The host provides four namespaces. Import what you need:

```javascript
import { block, rejectMalformed, warn, pass, filterTools, setMetadata } from 'wanaku:evaluator/response';
import { verifyLlmResult } from 'wanaku:evaluator/validation';
import { listTools, getTool, copyToolToNamespace } from 'wanaku:evaluator/registry';
import { getHistory } from 'wanaku:evaluator/conversation';
import { info, warn, error } from 'wanaku:evaluator/log';
```

## Step 2: Implement `evaluate(ctx)`

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

## Step 3: Available Host APIs

### Registry (`wanaku:evaluator/registry`)

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

### Response (`wanaku:evaluator/response`)

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

// Return a filtered tools/list response (only for tools/list triggers).
// The response contains only the named tools that are in the request namespace.
filterTools(["tool-1", "tool-2", "tool-3"]);

// Set metadata for downstream filters
setMetadata("wanaku.risk_level", "high");
```

### Validation (`wanaku:evaluator/validation`)

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

### Conversation (`wanaku:evaluator/conversation`)

```javascript
import { getHistory } from 'wanaku:evaluator/conversation';

const messages = getHistory(ctx.conversationId);
// [{role: "user", content: "..."}, {role: "assistant", content: "..."}, ...]

// Empty array if no history or conversation ID is absent
```

### Logging (`wanaku:evaluator/log`)

```javascript
import { info, warn, error } from 'wanaku:evaluator/log';

info("Evaluator executed successfully");
warn("LLM returned unexpected format, falling back to default");
error("Failed to parse JSON from LLM result");
```

## Step 4: EvaluationContext Object

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

## Step 5: Compile to WASM

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

## Step 6: Deploy

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

## Worked examples

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
    engine:
      type: llm
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
      path: "actions/dist/safety_review_action.wasm"
```

**What happens:**

1. User calls `restart-database` tool
2. Engine sends tool name + args to LLM with your prompt
3. LLM returns `{"level": "red", "reason": "database restart is dangerous"}`
4. Engine runs `safety_review_action.wasm` with the engine result in `ctx.llmResult`
5. WASM script parses the JSON, sees `"red"`, calls `block("Tool call blocked: red")`
6. User gets JSON-RPC error instead of executing the tool

#### JavaScript action

The example uses the bundled Rust action. The following script is a smaller JavaScript safety gate. It blocks only `red` results.

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

### Example 2: Tool Assembly

Dynamically populate a namespace with only the tools the LLM approves.

```yaml
evaluators:
  - name: "assembly-gate"
    trigger:
      method: "tools/list"
      namespace: "curated"
    engine:
      type: llm
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
5. WASM script parses JSON. For each tool in the `default` namespace or in `ctx.namespace`, it calls `copyToolToNamespace(name, ctx.namespace)`. It skips tools from other namespaces.
6. WASM calls `filterTools(assembled)` to return only the assembled tools
7. User sees a curated tool list

#### JavaScript action

```javascript
import { copyToolToNamespace, getTool } from 'wanaku:evaluator/registry';
import { filterTools } from 'wanaku:evaluator/response';
import { info, warn } from 'wanaku:evaluator/log';

export function evaluate(ctx) {
  let approved;
  try {
    approved = JSON.parse(ctx.llmResult);
  } catch (e) {
    warn('Failed to parse LLM result as tool name array, returning all tools');
    return;
  }

  if (!Array.isArray(approved) || approved.length === 0) {
    info('LLM returned empty tool list, returning all tools (fail-open)');
    return;
  }

  // Assemble only from the shared catalog or the request namespace, so the
  // LLM result cannot pull in tools that belong to other namespaces.
  const assembled = [];
  for (const name of approved) {
    const tool = getTool(name);
    if (!tool) {
      continue;
    }
    const source = tool.namespace ?? 'default';
    if (source !== 'default' && source !== ctx.namespace) {
      warn(`Skipping tool '${name}' from namespace '${source}'`);
      continue;
    }
    copyToolToNamespace(name, ctx.namespace);
    assembled.push(name);
  }

  info(`Registered ${assembled.length} tools into namespace '${ctx.namespace}'`);
  filterTools(assembled);
}
```

**Error handling pattern:** The LLM can return invalid output. Use `try/catch` when you parse JSON. Return early to fail open; the default action is `pass()`.

## Try it locally

This script configures an evaluator, makes a tool call against a tool discovered from a forwarded MCP server, and verifies the response.

**Prerequisites:** You need an upstream MCP server that exposes at least one tool. Register it as a forward before running this script:

```bash
wanaku forwards add --service="http://<your-mcp-server>/mcp" --name my-mcp-server --namespace default --no-auth
```

Replace `restart-database` in the script below with a tool name discovered from your forward. Run `wanaku tools list --no-auth` to see the available tools.

The script also needs:

- The `local-llama` connection in `llm_connections` in the `wanaku.yaml` that the server loaded at startup. The management API cannot create connections.
- The built `actions/dist/safety_review_action.wasm` file.
- A governance posture that lets the call reach the evaluator. With the default `no_match: deny` and no action policy, the action-policy filter denies the call with `-32003` before the evaluator runs. For a local test, set `governance.namespaces.default.no_match: allow`.

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
      "engine": {
        "type": "llm",
        "operation": "classify",
        "prompt": "You are a safety classifier. You MUST classify every tool call as exactly one of: green, yellow, or red. Restarting any database is ALWAYS red. Respond with ONLY a JSON object, no other text: {\"level\": \"green|yellow|red\", \"reason\": \"brief\"}",
        "connection": "local-llama"
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

## Next steps

- [WIT interface](https://github.com/wanaku-ai/wanaku/blob/main/features/evaluator/wit/evaluator.wit) and [TypeScript definitions](https://github.com/wanaku-ai/wanaku/blob/main/sdk/js/wanaku-actions.d.ts): the authoritative API reference.
- [JavaScript examples](https://github.com/wanaku-ai/wanaku/blob/main/actions/js-examples): more example action scripts.
- [jco](https://github.com/bytecodealliance/jco): the componentize tool documentation.
- [Evaluators](../govern/evaluators.md): configure the evaluator that runs your action.
