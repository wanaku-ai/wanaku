---
title: WASM tool handlers
description: Implement MCP tools with the versioned Wanaku WASM host API, register components at runtime, and call registered MCP targets through governance.
---

# WASM tool handlers

WASM tool handlers extend Wanaku with custom MCP tools. A handler can read authorized catalogs, call another tool, read a resource, and retrieve a prompt. Wanaku keeps routing, policy enforcement, credential brokerage, and audit on the host.

## Contract and compatibility

The contract is [`features/tool-handler/wit/tool-handler.wit`](https://github.com/wanaku-ai/wanaku/blob/8d6fd30a0ab2211f1bd0360279794c8fcdee984a/features/tool-handler/wit/tool-handler.wit). The package is `wanaku:tool-handler@1.0.0`. The world is `tool-handler`.

Build guest bindings from the WIT file that matches the server. A breaking signature or behavior change requires a new major package version. A compatible addition requires a minor version. The evaluator world remains separate. Existing evaluator components keep their existing interface.

The guest exports `handle(request) -> result<json, host-error>`. Each invocation receives a fresh WASM instance. Guest memory does not persist between calls.

| Request field | Meaning |
|---|---|
| `namespace` | Concrete namespace that owns the handler. |
| `name` | Original registered handler tool name. |
| `arguments` | Complete JSON arguments object, encoded as a string. |

The `json` WIT type is a string that contains one complete JSON document. Decode it with a JSON library. Do not convert individual argument values to strings. Numbers, booleans, nulls, arrays, and objects retain their JSON types.

Return the complete MCP tool result. Do not return a JSON-RPC envelope. Preserve `content`, `structuredContent`, `isError`, `_meta`, and other supported result fields. All MCP content types can pass through the interface without conversion to text. Binary content uses its MCP encoding. Tool definitions retain `inputSchema`, `outputSchema`, `annotations`, `title`, `_meta`, and additional MCP definition fields.

If the handler declares an `outputSchema`, successful results must contain matching `structuredContent`. A result with `isError: true` does not require matching success output. Invalid schemas reject activation. External schema retrieval is disabled.

```json
{
  "content": [{"type": "text", "text": "Complete"}],
  "structuredContent": {"complete": true, "count": 3},
  "isError": false
}
```

## Host API reference

All operations select an explicit namespace. A namespace argument does not grant access. Catalog operations read the registered, governed view. They do not open arbitrary upstream connections.

### `catalog`

| Function | Arguments | JSON result |
|---|---|---|
| `list-tools` | `namespace` | MCP tool-list result with a `tools` array. |
| `get-tool` | `namespace`, `name` | Object with `identity` (`namespace` and `name`), `forward_id`, and the complete MCP `definition`. |
| `list-resources` | `namespace` | MCP resource-list result with a `resources` array. |
| `list-resource-templates` | `namespace` | MCP template-list result with a `resourceTemplates` array. |
| `list-prompts` | `namespace` | MCP prompt-list result with a `prompts` array. |

Each function returns `result<json, host-error>`. Use original names within concrete namespaces. Names shared by different namespaces remain distinct.

### `mcp`

| Function | Arguments | JSON result |
|---|---|---|
| `call-tool` | `namespace`, `name`, `arguments` | Complete MCP tool result. `arguments` must encode a JSON object. |
| `read-resource` | `namespace`, `uri` | Complete MCP resource-read result. |
| `get-prompt` | `namespace`, `name`, `arguments` | Complete MCP prompt result. `arguments` must encode a JSON object. |

Each function returns `result<json, host-error>`. The host resolves the registered target and its current forward. Guests cannot pass upstream URLs, authentication headers, or credential references to these operations.

Target calls run through the configured MCP filter pipeline. Target policies, evaluators, argument validation, credential restrictions, availability checks, redaction, and audit remain active. An evaluator metadata change cannot redirect a call.

### `log`

`info(message)`, `warn(message)`, and `error(message)` write to host tracing. Each function takes a string and returns no value. Do not include sensitive request data in log messages.

### Errors

`host-error` has three fields: `kind`, `message`, and optional signed 32-bit `code`. The code carries a JSON-RPC error code when one is available.

| Kind | Meaning |
|---|---|
| `denied` | The handler grant, originating request scope, or target governance denies access. |
| `invalid_arguments` | JSON arguments or their schema are invalid. |
| `not_found` | The requested definition or target does not exist. |
| `unavailable` | The target or required host service is unavailable. |
| `timeout` | The execution or nested-dispatch deadline expired. |
| `limit` | Fuel, concurrency, recursion, depth, or call-budget limits prevent execution. |
| `protocol` | A request or result violates the supported MCP contract. |
| `internal` | The host cannot complete the operation. |
| `execution` | The guest traps or cannot execute. |

A tool result with `isError: true` is a successful host API return that contains an MCP tool execution error. It is distinct from `host-error`. A handler can return that result unchanged.

## Build the example

The [`actions/tool-proxy`](https://github.com/wanaku-ai/wanaku/blob/8d6fd30a0ab2211f1bd0360279794c8fcdee984a/actions/tool-proxy/src/lib.rs) example inspects `backend/echo`, calls it with the original arguments, and returns the complete result.

Install `cargo-component`:

```bash
cargo install cargo-component
```

Install the build target:

```bash
rustup target add wasm32-wasip1
```

Build from the repository root:

```bash
cargo component build --release --manifest-path actions/tool-proxy/Cargo.toml
```

The output is `actions/tool-proxy/target/wasm32-wasip1/release/wanaku_tool_proxy_example.wasm`.

Deploy the resulting component to `/wasm/tool_proxy.wasm` on the server. Register an MCP forward that provides a tool named `echo` in the concrete `backend` namespace. The example requires this target.

Configure governance to permit the handler and its backend target. Calls through the example virtual endpoint require permission in `integration`, `integration-tools`, and `backend`. The handler grant below does not override a policy denial. See [action policies](../govern/action-policies.md) and [evaluators](../govern/evaluators.md) for the applicable configuration.

The component file is portable WASM. Wanaku compiles it with Wasmtime before activation and retains the compiled component in memory. It does not require a serialized native Wasmtime artifact. A restart recompiles the referenced files. Deploy these files at the configured paths on each server instance.

## Register and expose a handler

Create the concrete handler namespace:

```bash
curl -X POST http://localhost:8080/api/v1/namespaces \
  -H 'Content-Type: application/json' \
  -d '{"name":"integration-tools"}'
```

Create a virtual namespace that exposes the handler and its target:

```bash
curl -X POST http://localhost:8080/api/v1/namespaces \
  -H 'Content-Type: application/json' \
  -d '{"name":"integration","members":["integration-tools","backend"]}'
```

Save this definition as `handlers.json`:

```json
[
  {
    "tool": {
      "name": "proxy-echo",
      "description": "Inspect and invoke backend echo",
      "uri": "",
      "type": "wasm",
      "namespace": "integration-tools",
      "inputSchema": {"type": "object"},
      "annotations": {"readOnlyHint": true}
    },
    "path": "/wasm/tool_proxy.wasm",
    "allowed_namespaces": ["backend"],
    "limits": {
      "fuel": 10000000,
      "memory_bytes": 67108864,
      "timeout_ms": 1000,
      "max_concurrent": 8
    }
  }
]
```

Activate the configuration:

```bash
curl -X PUT http://localhost:8080/api/v1/tool-handlers \
  -H 'Content-Type: application/json' --data-binary @handlers.json
```

Call the handler through the virtual endpoint:

```bash
curl -X POST http://localhost:8081/virtual-ns/integration/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"integration-tools/proxy-echo","arguments":{"value":true}}}'
```

Use a tool's actual side effects when you set its annotations. The example `readOnlyHint` applies only if the backend echo operation is read-only.

## Permissions and limits

`allowed_namespaces` contains explicit concrete namespace grants. The default empty list grants no MCP host access. The host also restricts access to the originating request scope. A grant cannot expand the caller's scope. A virtual request can select authorized concrete members. A concrete request remains in its concrete namespace.

Both virtual and concrete target governance apply to nested calls from a virtual endpoint. Audit records retain the originating virtual namespace and actual target identity. Credentials remain in the host. Guests receive results after the normal response controls.

Nested action-policy and evaluator audit events include `handler_ancestry`. Each entry identifies the concrete handler namespace, original name, and runtime binding ID. The binding ID is generated by the host. It is not a credential.

Each definition has independent fuel, memory, timeout, and concurrency limits. Host-call waiting uses the handler deadline. Overload rejects a call instead of waiting for a handler permit. A permit remains held until the synchronous guest work exits, including after request cancellation.

Nested calls share the host invocation deadline and call budget. The host rejects cyclic handler calls and excessive nesting. This prevents recursive dispatch from waiting indefinitely on occupied handler permits.

The default host invocation budget is 30 seconds and 64 host operations. Handler nesting is limited to eight levels. Each handler's shorter deadline also applies. Repeated calls to the same concrete handler in one ancestry chain are rejected.

## Management and lifecycle

| Method and route | Behavior |
|---|---|
| `GET /api/v1/tool-handlers` | List handler definitions. |
| `PUT /api/v1/tool-handlers` | Replace the full configuration with an array of definitions. |
| `GET /api/v1/tool-handlers/{namespace}/{name}` | Read one handler definition. |
| `DELETE /api/v1/tool-handlers/{namespace}/{name}` | Remove one handler. |

Activation validates definitions and compiles components before publishing the replacement. A failed replacement retains the active configuration. Calls that already selected a component retain that component. Replacement applies to subsequent calls. Removal prevents subsequent calls from selecting the removed handler.

A request can select its registry binding before it enters the runtime. If replacement changes that binding before runtime entry, the request fails with `unavailable`. Retry the request to select the new binding. This check prevents execution of a component that differs from the definition used for policy evaluation.

Use an empty array with `PUT` to remove all handlers. Use this operation only when the array represents the intended complete configuration.

Use the handler lifecycle API to change WASM tools. The generic tool delete, enable, and disable routes return `409` for these entries. To disable a handler, replace its definition with `tool.enabled: false`. To remove it, use the handler delete route. These operations keep the runtime, catalog, and persisted bindings consistent.

The YAML configuration key is `tool_handlers`. Its value has the same definition array as the `PUT` body. Configuration keys use `snake_case`. Tool definitions retain their existing management API field names, including `inputSchema` and `outputSchema`.

With file persistence enabled, definitions use `tool-handlers.json` in `WANAKU_PERSIST_PATH`. The file contains component paths, not component bytes. An absent startup `tool_handlers` key retains restored bindings. An explicit list replaces them. Invalid restoration or startup configuration stops server startup. See [persistence](../operate/persistence.md#wasm-tool-handler-persistence).

## Rust integration

`wanaku_feature_tool_handler::ToolHandlerFeature` implements the existing `Feature` contract. Construct it with the shared registry. Configure persistence before the feature enters the application graph. Use `state()` to obtain its lifecycle handle. This follows the evaluator feature pattern.

```rust
use wanaku_feature_tool_handler::ToolHandlerFeature;
use wanaku_infra::registry::RuntimeRegistry;

fn configure_handlers(registry: RuntimeRegistry) -> Result<ToolHandlerFeature, String> {
    ToolHandlerFeature::new(registry)
        .with_persistence("/data/wanaku/tool-handlers.json")
}
```

Create or restore the concrete namespaces before you restore handler definitions. Add the feature to the same feature graph as the other built-in features. The graph uses `pipeline_extensions()` to install the runtime adapter and `handle_route()` to dispatch its management routes. `load_yaml_config()` loads `tool_handlers`. `validate_startup()` rejects an invalid startup configuration.

Construct one handler feature for each shared registry. Construction removes stale `wasm` catalog entries before restoration. A registry entry without a restored runtime binding is not executable.

The clone-safe `state::HandlerState` exposes these lifecycle methods:

| Method | Result |
|---|---|
| `list()` | `Result<Vec<HandlerDefinition>, String>` |
| `get(namespace, name)` | `Result<Option<HandlerDefinition>, String>` |
| `replace(definitions)` | `Result<(), String>`; validates, compiles, persists, and replaces the complete configuration. |
| `remove(namespace, name)` | `Result<bool, String>`; returns `false` when no binding exists. |

Compilation and file persistence are synchronous. Call lifecycle mutation methods from a blocking worker when the embedding application uses an asynchronous request executor. The HTTP activation route does this before it calls `replace()`.

The shared `wanaku_types::wasm` contracts separate the guest runtime from server dispatch:

- `McpHostClient` exposes request-scoped MCP operations.
- `McpHostFactory` constructs clients from trusted request context.
- `ToolHandler` supplies the runtime invocation adapter.
- `ToolTarget` identifies a concrete namespace and original tool name.
- `InvocationContext` carries trusted ancestry and a shared dispatch budget.
- `HostError` and `HostErrorKind` identify host failures independently of MCP results.

`McpHostClient::request(method, namespace, params)` is asynchronous. It returns `Result<serde_json::Value, HostError>`. The server accepts only the documented catalog and MCP methods. `tools/get` is a host catalog operation, not an upstream MCP protocol method.

`McpHostFactory::create(ctx)` returns an `Arc<dyn McpHostClient>` for trusted request context. `ToolHandler::call(target, arguments, client)` is asynchronous and returns the complete MCP result. The runtime uses the host-provided binding identity to detect replacement between target selection and execution.

Embedding applications must supply governed dispatch when they implement these contracts. A raw forwarding adapter does not perform the target policy stages. The built-in server supplies the configured pipeline adapter.

## Verification

Run the handler tests:

```bash
cargo test -p wanaku-feature-tool-handler
```

Run production assembly tests:

```bash
cargo test -p wanaku-server --test app_assembly
```

Use two concrete namespaces with equal tool names to verify exact target selection. Test target denial and invalid arguments before enabling side-effecting backend tools. Test component replacement and restart with the deployed artifact paths.
