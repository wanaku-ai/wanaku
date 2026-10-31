---
title: Evaluators
description: "Configure Wanaku evaluators: triggers, processors, resource limits, bundled action scripts, runtime management, and how evaluation works."
---

# Evaluators

The evaluator engine lets you build **trigger→evaluate→act** pipelines. When a request matches a trigger, an LLM, TypeSafe System One, or Open Policy Agent (OPA) engine evaluates the request. Wanaku then executes a WebAssembly action script that has access to the registry, conversation history, and response control.

You write the logic in JavaScript or Rust. The engine compiles it to WASM and runs it in a sandboxed environment with a clean, versioned API defined by the [WIT interface](https://github.com/wanaku-ai/wanaku/blob/main/features/evaluator/wit/evaluator.wit).

**Why this exists:** MCP tools can be dangerous. You want to block "restart production database" calls, or dynamically assemble a per-conversation tool catalog based on what the LLM thinks the user needs. Pure LLM-based gates are too brittle (hallucination, prompt injection). Pure rule-based gates are too rigid. This engine gives you both: LLM cognitive work + deterministic WASM execution.

This page covers the evaluator configuration, the bundled action scripts, runtime management, and the internals. [Evaluator engines](evaluator-engines.md) describes each engine. To write your own action script, read [Write evaluator actions in JavaScript](../extend/evaluator-actions-js.md) or [Write evaluator actions in Rust](../extend/evaluator-actions-rust.md).

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
    engine:                           # Evaluation engine
      type: llm
      operation: classify             # classify | filter | augment
      prompt: "You are a safety classifier..."
      connection: "local-llama"       # References an entry in llm_connections
    processor:                        # WASM action script
      path: "/wasm/safety-gate.wasm"
```

### Trigger Fields

| Field | Type | Purpose |
|-------|------|---------|
| `method` | string | MCP method to match (e.g., `tools/call`, `tools/list`, `resources/read`) |
| `namespace` | string | Optional. Only trigger for this namespace (extracted from URL path `/finance/mcp` → `finance`) |

The `trigger` and `processor` objects reject unknown keys. A misspelled `namespace` key, for example `namepsace`, stops startup. In a `PUT /api/v1/evaluators` request, it returns `400`.

For each request, Wanaku runs only the first evaluator in the list whose trigger matches. It does not run the other matching evaluators. Put namespace-scoped evaluators before an evaluator without a `namespace`, because an evaluator without a `namespace` matches every namespace and hides the evaluators after it.

### Processor

A WASM action script processes the engine output. The script receives the raw result in `ctx.llmResult` and decides what to do:

```yaml
processor:
  path: "/wasm/safety-gate.wasm"
```

The WASM script can call `block()`, `pass()`, `warn()`, `filterTools()`, or `setMetadata()` based on its analysis of the LLM output.

### Processor Resource Limits

Production processors run on a bounded runner. The runner keeps slow or runaway processors away from the async request workers.

- The runner executes each processor invocation on the Tokio blocking thread pool.
- The runner admits a limited number of concurrent invocations. Each invocation holds one permit.
- An invocation keeps its permit until the processor work stops. A cancelled request or a request timeout does not release the permit early.
- When all permits are in use, the runner does not queue the invocation. The runner rejects it with the reason `processor_overloaded`.
- Each invocation gets a fuel budget, a memory budget, and a wall-clock deadline. The processor stops with `processor_execution_failed` when it exceeds one of these budgets.
- A failed invocation does not affect the next invocation. Each invocation gets a fresh instance.

| Limit | Default | Environment variable |
|---|---|---|
| Concurrent invocations | Number of available CPUs | `WANAKU_EVALUATOR_PROCESSOR_CONCURRENCY` |
| Wall-clock deadline | `1000` ms | `WANAKU_EVALUATOR_PROCESSOR_TIMEOUT_MS` |
| Linear memory | `67108864` bytes (64 MiB) | `WANAKU_EVALUATOR_PROCESSOR_MEMORY_BYTES` |
| Fuel | `10000000` | `WANAKU_EVALUATOR_PROCESSOR_FUEL` |

The `processor_overloaded`, `processor_execution_failed`, and `processor_instantiation_failed` reasons follow the [Error Policy](#error-policy). With `on_failure: deny`, enforce mode blocks the request. With `on_failure: allow`, enforce mode permits the request. Audit mode records the failure and permits the request. Audit events show the reason with the `evaluator_` prefix, for example `evaluator_processor_overloaded`.

The wall-clock deadline stops guest code. It cannot stop a host import that blocks. The current host imports do not block.

### Error Policy

- `governance.default.on_failure: allow` permits requests after evaluator failures.
- `governance.default.on_failure: deny` blocks requests after evaluator failures. This is the default.
- Namespace overrides can change the failure behavior. See [Governance Posture](governance-posture.md).

## Bundled Evaluators

Wanaku ships three prebuilt action scripts, so you can use evaluators without a WASM toolchain.

| File | Engine input | Behavior | Source |
|------|--------------|----------|--------|
| `safety_review_action.wasm` | `{"level": "green\|yellow\|red", "reason": "..."}` from an `llm` engine, or a TypeSafe System One result | Blocks `red`, warns on `yellow`, and passes `green`. For a TypeSafe System One result, blocks a probability below 0.5. Rejects any other result as malformed (`-32002`). | [`actions/safety-review`](https://github.com/wanaku-ai/wanaku/blob/main/actions/safety-review/src/lib.rs) |
| `assembly_filter_action.wasm` | A JSON array of tool names | Moves the named tools from the `default` namespace into the request namespace and limits the tool list to them. Skips tools from other namespaces. Passes all tools if the input is not a JSON array. | [`actions/assembly-filter`](https://github.com/wanaku-ai/wanaku/blob/main/actions/assembly-filter/src/lib.rs) |
| `opa_allow_action.wasm` | A normalized OPA decision from an `opa` engine | Passes only when the decision is `"allow": true`. Blocks all other input. | [`actions/opa-allow`](https://github.com/wanaku-ai/wanaku/blob/main/actions/opa-allow/src/lib.rs) |

The location of the files depends on how you install Wanaku:

| Install method | Location | Example `processor.path` |
|----------------|----------|--------------------------|
| Release archive (`wanaku-server-<version>-<platform>`) | `evaluators/` next to the `wanaku-server` binary | `evaluators/safety_review_action.wasm` |
| Container image (`quay.io/wanaku/wanaku-server`) | `/data/evaluators/` | `/data/evaluators/safety_review_action.wasm` |
| Evaluator bundle (`wanaku-evaluators-<version>.tar.gz`) | `evaluators/` | Any path where you extract the files |
| Source tree | `actions/dist/` after you build the actions | `actions/dist/safety_review_action.wasm` |

The server resolves a relative `processor.path` against its working directory. Start the server from the directory that contains `evaluators/`, or use an absolute path.

Each GitHub release also includes `wanaku-evaluators-<version>.tar.gz`. This archive is the same for all platforms. It contains:

- `evaluators/`: the three `.wasm` files.
- `wit/evaluator.wit`: the WIT interface for this Wanaku version. Use this file to build your own action scripts.
- `sdk/js/wanaku-actions.d.ts`: the TypeScript definitions for JavaScript action scripts.
- `examples/js/`: the JavaScript example sources. These examples are not compiled.

In the container image, the bundled files are in `/data/evaluators/`. If you mount a volume on `/data`, the volume hides these files. Mount the persistence volume on `/data/registry` instead. To add your own action scripts, mount them on a different path, for example `/data/evaluators-custom/`.

## Manage evaluators at runtime

Hot-reload evaluators without restarting the server.

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
      "trigger": {"method": "tools/call", "namespace": null},
      "engine": {
        "type": "llm",
        "operation": "classify",
        "prompt": "...",
        "connection": "local-llama",
        "result_schema": null
      },
      "processor": {"path": "actions/dist/safety_review_action.wasm"}
    }
  ],
  "error": null
}
```

This response has no `model`, `url`, or `api_key` field. Those fields live only in
`llm_connections` in `wanaku.yaml`. They never transit the management API. The
`engine.connection` field names the connection the evaluator uses.

### Update Evaluators (Hot-Reload)

Use the `wanaku` CLI from wanaku-barn. The command reads a YAML or JSON file. The file can contain a list of evaluator definitions or an object with an `evaluators` list, so you can pass a `wanaku.yaml` file directly. The command ignores other top-level keys:

```bash
wanaku evaluators update evaluators.yaml
wanaku evaluators update evaluators.yaml --expected-revision 4
```

See [Manage evaluators](https://github.com/wanaku-ai/wanaku-barn/blob/main/docs/cli/evaluators.md) in the CLI documentation for the revision and rollback commands.

The CLI sends this request to the management API:

```bash
curl -X PUT http://localhost:8080/api/v1/evaluators \
  -H "Content-Type: application/json" \
  -d '{
    "evaluators": [
      {
        "name": "safety-gate",
        "trigger": {"method": "tools/call"},
        "engine": {
          "type": "llm",
          "operation": "classify",
          "prompt": "You are a safety classifier...",
          "connection": "local-llama"
        },
        "processor": {"path": "actions/dist/safety_review_action.wasm"}
      }
    ],
    "expected_revision": 4
  }'
```

`expected_revision` is optional. When it is set and a different revision is active, the server rejects the update with `409 Conflict`.

**What happens:** The engine:

1. Parses the request. A malformed body, or an unknown key in an evaluator definition, returns `400`.
2. Validates the evaluator names, the triggers, and the engine connection references. An unknown connection name returns `422`.
3. Compiles all WASM files and result schemas. If one file is missing or does not compile, the server rejects the complete update with `422`. It does not load the other evaluators.
4. Records a new revision and replaces the active evaluators.
5. Returns the new revision:

```json
{
  "data": {
    "revision": {
      "id": 5,
      "created_at": "2026-10-29T14:00:00Z",
      "activated_at": "2026-10-29T14:00:00Z",
      "status": "active",
      "checksum": "...",
      "origin": "api",
      "actor": null,
      "failure_reason": null
    },
    "evaluators": [ ... ]
  },
  "error": null
}
```

A rejected update does not change the active evaluators. The server records the rejected configuration as a revision with the `rejected` status and the failure reason.

**Legacy payloads:** a request with a top-level `llm` key, or with `model`/`url`/`api_key` inline in the engine, is rejected with `400` rather than silently dropping those fields.

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

## How It Works (Internals)

You do not need these internal details to use the engine. Use them to diagnose execution problems.

### Execution Flow

1. **MCP request arrives** → praxis-ai MCP filter parses JSON-RPC, sets `mcp.method` metadata
2. **Namespace filter** sets `wanaku.namespace` metadata from URL path
3. **Evaluator filter** (`wanaku_evaluator` in the pipeline):
   - Reads metadata to get method, namespace, tool name, arguments
   - Finds the first evaluator in the list whose trigger matches (trigger.method matches, optional namespace check). Later evaluators do not run.
   - For that evaluator:
     - Selects the configured evaluation engine
     - Produces a normalized result for the processor
     - The LLM engine builds a context prompt, calls the LLM, and validates `result_schema` when configured
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

Evaluator execution uses the shared [governance posture](governance-posture.md). The `on_error` field is no longer accepted.

Users of earlier 0.3.0 pre-release builds must update both startup configuration and persisted evaluator revisions. See [Update an earlier pre-release configuration](../operate/persistence.md#update-an-earlier-pre-release-configuration).

- LLM, TypeSafe, and OPA failures follow `on_failure`.
- Empty engine output is a failure.
- Invalid LLM output gets one schema-correction attempt. If correction fails, the processor does not run.
- Missing or failed processors, registry state, and internal state follow the failure policy.
- Processor budget failures and runner overload follow the failure policy. See [Processor Resource Limits](#processor-resource-limits).
- Invalid startup configuration stops startup by default. The `evaluator_startup_failure: deny` option retains an explicit invalid runtime instead.
- Invalid management updates do not replace the working revision.
- Audit mode does not block traffic or apply processor mutations. Basic audit skips external engines. Full audit permits external calls only by explicit opt-in.

### Evaluator governance startup

Evaluator execution uses the global and namespace [governance posture](governance-posture.md). The default failure behavior is `deny`. The evaluator `on_error` field is not accepted.

Invalid evaluator configuration stops startup by default. Set the top-level `evaluator_startup_failure: deny` option to start with an invalid runtime that blocks evaluation in enforce mode. Valid management updates can replace this invalid runtime. Audit and disabled modes retain their configured behavior.

## Common Patterns

### Fail-Open Safety Gate

Set the failure behavior in the top-level governance configuration:

```yaml
governance:
  default:
    on_failure: allow
```

Requests continue if an evaluator cannot produce a decision. Successful evaluations still apply their processor actions in enforce mode.

### Fail-Closed Safety Gate

```yaml
governance:
  default:
    on_failure: deny
```

Requests are blocked if evaluation fails. This is the default failure behavior.

### Namespace-Scoped Evaluation

Restrict evaluators to specific namespaces:

```yaml
trigger:
  method: "tools/list"
  namespace: "production"
```

This evaluator only fires for requests to `/production/mcp`. The WASM action can use `filterTools()` to curate the tool catalog for that namespace.

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

## Performance Notes

- **LLM calls are slow** (100-500ms per classification). Evaluators add latency to the request path. Use them sparingly, or only for high-stakes operations (e.g., `tools/call`, not `tools/list`).
- **WASM compilation is expensive** (10-50ms per module). The engine caches compiled modules, so only the first load (or hot-reload) is slow.
- **WASM execution is fast** (sub-millisecond for simple logic). A compiled action has minimal overhead.
- **WASM execution is bounded.** Processors run on the blocking thread pool with a concurrency limit. See [Processor Resource Limits](#processor-resource-limits).
- **Conversation history is capped at 10 interactions** to keep LLM context bounded. If you need more, fetch it explicitly in your WASM action via `getHistory()`.

## Security Considerations

- **WASM is sandboxed** — actions cannot access the filesystem, network, or system calls beyond what the WIT interface exposes.
- **LLM prompts can be attacked** — sanitize user input, use system prompts that are robust to injection, and fail open if unsure.
- **WASM actions are deterministic** — the same input always produces the same output. Use this property to test your logic thoroughly.
- **Evaluator revisions persist when persistence is enabled** — revision history and the active revision survive a restart. The server writes `evaluator-revisions.json` after every change and restores the active revision at startup. See [Evaluator Revision Persistence](../operate/persistence.md#evaluator-revision-persistence). When persistence is disabled, revisions live only in memory and the server rebuilds the active revision from `wanaku.yaml` on each restart.
- **OPA connection credentials never transit the management API.** The `url`, `token`, and `ca_cert` fields live only in `opa_connections` in `wanaku.yaml`. The OPA input document does not contain headers or credentials.
- **LLM credentials never transit the management API.** `model`, `url`, and `api_key` live only in `llm_connections` in `wanaku.yaml`, loaded once at startup. Evaluators refer to a connection by name. No route can set, update, or read an `api_key`, not on evaluator create or update, and not in evaluator revision history. To rotate a credential, edit `wanaku.yaml` and restart the server.

## Next steps

- [Evaluator engines](evaluator-engines.md): configure the LLM, TypeSafe System One, OPA, and passthrough engines.
- [Write evaluator actions in JavaScript](../extend/evaluator-actions-js.md): build your own processor.
- [Governance Posture](governance-posture.md): set the failure behavior for evaluators.
- [Action Policies](action-policies.md): combine evaluators with deterministic rules.
