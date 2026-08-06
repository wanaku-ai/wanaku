# Wanaku Praxis

A Rust-based MCP (Model Context Protocol) server built on the Praxis proxy framework. Routes MCP requests through a filter pipeline to provide namespace isolation, tool/resource/prompt management, and both local (gRPC) and remote (MCP forwarding) tool execution.

## Quick Start

```bash
# Build
cargo build

# Test
cargo test

# Run (MCP on :8081, management API on :9090)
cargo run

# Optional: specify custom config files
cargo run -- /path/to/praxis.yaml /path/to/wanaku.yaml
```

Default configs:
- `server/src/default.yaml` — praxis pipeline config (embedded at compile time)
- `wanaku.yaml` — tool/service registry bootstrap (optional, loaded at runtime)

**Gotcha:** Changes to `server/src/default.yaml` don't trigger cargo rebuilds due to `include_str!`. Touch `server/src/lib.rs` to force recompile after editing default.yaml.

## Build Info

- **Rust Edition:** 2024
- **MSRV:** 1.96
- **Lints:** `#![deny(unsafe_code)]`, `unwrap_used`, `expect_used`, `panic` in all crates
- **Jemalloc:** Enabled on Unix platforms (tikv-jemallocator)

## Architecture

### 3-Crate Workspace + Admin UI

```
wanaku-praxis/
├── apis/       — Shared types, gRPC client (GrpcPool), MCP client (rmcp), registry traits
├── filters/    — Praxis HttpFilter implementations for MCP methods
├── server/     — Binary, pipeline setup, management API (Pingora ServeHttp)
└── ui/admin/   — Admin UI (React 19 + Vite + Carbon Design System)
```

## Admin UI

The admin UI is a React + TypeScript frontend embedded into the server binary via `rust_embed`. It follows the same patterns as the classic Wanaku Java project's admin UI.

### Tech Stack

- **Framework:** React 19, TypeScript 5.7, Vite 6
- **Component Library:** IBM Carbon Design System (`@carbon/react`)
- **Icons:** `@carbon/icons-react`
- **Routing:** `react-router-dom` v6, hash-based (`createHashRouter`)
- **Styling:** SCSS with Carbon theme tokens (`$g10` light / `$g100` dark)
- **API Client:** Orval-generated from OpenAPI spec, `fetch`-based with `customFetch` mutator
- **Package Manager:** Yarn (classic)

### UI Build Commands

```bash
cd ui/admin
yarn install
yarn run dev          # Dev server
yarn run build        # Production build (Orval + TypeScript + Vite)
yarn run lint         # ESLint
```

### UI Code Conventions

- **Carbon components only** — use `@carbon/react` for all UI elements, never raw HTML buttons/inputs/tables
- **Page structure:** each page has 3 files: `<PageName>.tsx`, `index.ts` (re-exports from `router-exports.tsx`), `router-exports.tsx` (exports the page element for lazy loading)
- **Lazy loading:** all pages use `lazy: async () => import("./Pages/<PageName>")`
- **Route constants:** defined in `src/router/links.models.ts` as `const enum Links`
- **API hooks:** custom hooks in `src/hooks/api/` wrap Orval-generated functions with `useCallback`
- **Data access pattern:** `result.data.data` — `customFetch` wraps response as `{status, data, headers}`, and the backend wraps in `{"data": ..., "error": ...}`
- **Notifications:** Carbon `ToastNotification` with auto-dismiss
- **Error handling:** `ErrorBoundary` class component wraps page content with `InlineNotification`
- **Empty states:** use shared `EmptyTableState` component
- **DO NOT edit** `src/api/wanaku-router-api.ts` or `src/models/` — these are Orval-generated

### UI Source Organization

```
ui/admin/src/
  api/                    # Orval-generated API client (DO NOT EDIT)
  assets/                 # Static assets
  components/             # Shared layout: Header, SideNav, Content, ErrorBoundary
  constants/              # Shared constants
  hooks/api/              # Custom hooks wrapping API functions
  models/                 # Orval-generated TypeScript types (DO NOT EDIT)
  Pages/                  # Page components (capital P), each with 3-file pattern
  router/                 # Route path constants
  utils/                  # Utility functions
  custom-fetch.ts         # Fetch wrapper with auth redirect handling
  router.tsx              # Hash-based router configuration
  App.tsx                 # Root app component
  index.scss              # Global Carbon theme setup
```

### Dependencies

- **Praxis Core:** praxis-proxy-{core,filter,protocol} 0.4.1 (crates.io)
- **Praxis AI:** praxis-ai-{apis,filters} (git dep at rev a6d8552 — NOT on crates.io)
- **MCP Client:** rmcp crate for upstream MCP calls
- **gRPC:** tonic with pooled connections (GrpcPool in apis/src/grpc.rs)

### Filter Pipeline

Defined in `server/src/default.yaml`:

```yaml
cors → mcp (praxis-ai) → wanaku_namespace → wanaku_mcp_init → 
  wanaku_tool_list → wanaku_tool_call → wanaku_resource_list → 
  wanaku_resource_read → wanaku_prompt_list → wanaku_prompt_get → 
  static_response (catch-all)
```

**Critical ordering:**
- **MCP filter must be first** (after CORS) to parse JSON-RPC and set `mcp.method`/`mcp.name` metadata
- **Namespace filter must run in `on_request_body`** (NOT `on_request`) because StreamBuffer mode processes body filters before request filters — it reads the path and sets `wanaku.namespace` metadata
- All wanaku filters read metadata set by MCP + namespace filters

**MCP filter config gotcha:**
```yaml
- filter: mcp
  on_invalid: continue   # REQUIRED — allows bodyless OPTIONS CORS preflight requests through
```

Without this, OPTIONS requests fail validation and never reach the CORS filter response path.

### Metadata Contract

Set by **praxis-ai MCP filter** in `on_request_body` pre-read phase:
- `mcp.method` — JSON-RPC method (e.g., `"tools/list"`, `"tools/call"`)
- `mcp.name` — tool/resource/prompt name (extracted from `params.name` or `params.arguments`)

Set by **wanaku_namespace filter** in `on_request_body` after body read:
- `wanaku.namespace` — extracted from URL path:
  - `/mcp` → `"default"`
  - `/{namespace}/mcp` → `{namespace}`
  - nested or malformed paths → `"default"`

All downstream filters query these via `ctx.get_metadata(key)`.

### Registry Architecture

**InMemoryRegistry** (`apis/src/registry.rs`):
- Implements 5 traits: ToolRegistry, ResourceRegistry, PromptRegistry, NamespaceRegistry, ForwardRegistry, ServiceRegistry
- **Clone-safe** via `Arc<DashMap>` — shared between filter pipeline and management API
- Injected into requests via `PipelineExtension` in `server/src/pipelines.rs`
- Filters access via `ctx.extensions.get::<InMemoryRegistry>()`

**Namespace defaulting:**
- All entries default to `namespace: "default"` if omitted
- Namespace IDs default to namespace name if not provided (Java CLI compat)

**Java CLI compatibility:**
- `ToolEntry` accepts both `input_schema` and `inputSchema` (serde alias)
- `ForwardEntry` uses `address` field (matches Java model)

### Tool Routing: gRPC vs. MCP Forward

Tools can execute in two ways:

1. **gRPC (local execution):**
   - Tool has `type_` != `"mcp-forward"` (e.g., `"echo-tool"`, `"camel"`)
   - Filter calls `registry.resolve_service(tool.type_, "tool-invoker")` to get gRPC address
   - Uses `GrpcPool` to invoke via tonic
   - See `filters/src/tool_call.rs:217-282`

2. **MCP forward (remote MCP server):**
   - Tool has `type_: "mcp-forward"`
   - Filter calls `mcp_client::call_tool(tool.uri, ...)` directly (no gRPC)
   - Uses rmcp crate for upstream HTTP+SSE connection
   - See `filters/src/tool_call.rs:17-58`

**Forward discovery:**
When you POST to `/api/v1/forwards`, the management API:
1. Registers the forward
2. Calls `mcp_client::list_tools(forward.address)`
3. Auto-registers discovered tools with `type_: "mcp-forward"` and `uri: forward.address`

Refreshing (`POST /api/v1/forwards/{name}/refreshes`) removes old tools and re-discovers.

### Management API (Port 9090)

**NOT axum** — uses Pingora's native `ServeHttp` trait (`server/src/management.rs`).

Routes (all under `/api/v1/`):
- `GET /tools`, `GET /tools/{name}`, `POST /tools`, `DELETE /tools/{name}`
- `GET /resources/{name}`, `POST /resources`, `DELETE /resources/{name}`
- `GET /prompts/{name}`, `POST /prompts`, `DELETE /prompts/{name}`
- `GET /namespaces/{name}`, `POST /namespaces`, `DELETE /namespaces/{name}`
- `GET /forwards`, `POST /forwards`, `DELETE /forwards/{name}`, `POST /forwards/{name}/refreshes`

Request/response wrapper:
```json
{"data": <payload>, "error": null}  // success
{"data": null, "error": "message"}  // error
```

### Management API Route Pattern

Every entity MUST follow this pattern — no inline `if path.starts_with(...)` blocks:

1. Define a route enum:
```rust
#[derive(Debug, PartialEq, Eq)]
enum FooRoute {
    List,
    GetByName(String),
    Create,
    Delete(String),
    NotFound,
}
```

2. Define a resolver function:
```rust
fn resolve_foo_route(method: &str, path: &str) -> FooRoute {
    let suffix = match path.strip_prefix("/api/v1/foos") {
        Some(s) => s,
        None => return FooRoute::NotFound,
    };
    let name = suffix.strip_prefix('/').filter(|s| !s.is_empty());
    match (method, name) {
        ("GET", None) => FooRoute::List,
        ("GET", Some(n)) => FooRoute::GetByName(n.to_owned()),
        ("POST", None) => FooRoute::Create,
        ("DELETE", Some(n)) => FooRoute::Delete(n.to_owned()),
        _ => FooRoute::NotFound,
    }
}
```

3. Dispatch in `ServeHttp::response` using the guard pattern:
```rust
let foo_route = resolve_foo_route(&method, &path);
if foo_route != FooRoute::NotFound {
    return match foo_route {
        FooRoute::List => handle_foo_list(&self.registry),
        // ...
        FooRoute::NotFound => json_err(404, "not found"),
    };
}
```

4. Implement each handler as a standalone function: `handle_foo_list`, `handle_foo_get`, etc.

## Filter Implementation Patterns

### Synthetic MCP Responses

Use `FilterAction::Reject(Rejection)` with CORS headers:

```rust
use crate::response::json_response;

let response = serde_json::json!({
    "jsonrpc": "2.0",
    "id": parsed.id,
    "result": { ... }
});
Ok(FilterAction::Reject(json_response(Bytes::from(response.to_string()))))
```

This skips remaining filters and returns immediately. `filters/src/response.rs` adds:
- `content-type: application/json`
- `access-control-allow-origin: *`

### Body Access Pattern

All wanaku filters use:
```rust
fn request_body_access(&self) -> BodyAccess {
    BodyAccess::ReadOnly
}

fn request_body_mode(&self) -> BodyMode {
    BodyMode::StreamBuffer { max_bytes: Some(self.max_body_bytes) }
}

async fn on_request_body(
    &self,
    ctx: &mut HttpFilterContext<'_>,
    body: &mut Option<Bytes>,
    end_of_stream: bool,
) -> Result<FilterAction, FilterError> {
    if !end_of_stream {
        return Ok(FilterAction::Continue);
    }
    // Process body here
}
```

StreamBuffer accumulates the body, then calls `on_request_body` with the full buffer once.

## Known Gotchas

### 1. Phase Ordering: `on_request_body` Before `on_request`

In StreamBuffer mode, Praxis runs body filters in the **pre-read phase** (before buffering completes), then **post-read phase** (after buffering), then finally request-phase filters.

**Why namespace filter uses `on_request_body` not `on_request`:**
The MCP filter sets `mcp.method` metadata in its `on_request_body` handler. If namespace ran in `on_request`, it would execute BEFORE the MCP filter's body handler, so metadata wouldn't exist yet.

Running both in `on_request_body` ensures they execute in pipeline order during the post-read phase.

### 2. Killing Pingora Workers

Pingora forks worker processes. `kill -9 <parent-pid>` may not kill workers.

Safe cleanup:
```bash
lsof -ti :8081 | xargs kill -9
lsof -ti :9090 | xargs kill -9
```

Or use `SIGTERM` to let Pingora gracefully shutdown workers.

### 3. `include_str!` and Cargo Rebuilds

`server/src/lib.rs` embeds `default.yaml` via:
```rust
const DEFAULT_CONFIG: &str = include_str!("default.yaml");
```

Cargo doesn't track this file dependency. If you edit `default.yaml`, run:
```bash
touch server/src/lib.rs
cargo build
```

Otherwise your changes won't be compiled in.

### 4. praxis-ai Git Dependency

`praxis-ai-*` crates are NOT on crates.io. Always use git deps:
```toml
praxis-ai-filters = { git = "https://github.com/praxis-proxy/ai", rev = "a6d8552" }
```

Pin to a specific `rev` for reproducibility. HEAD may break.

## Testing

Run from workspace root:
```bash
cargo test                    # all tests
cargo test -p wanaku-praxis-apis      # single crate
cargo test -- --nocapture     # show tracing output
```

Unit tests are in each module (`#[cfg(test)]` blocks). Integration tests would go in `server/tests/` (currently none).

## Configuration

### Environment Variables (`apis/src/config.rs`)

All environment variables are centralized in `WanakuEnv` (`apis/src/config.rs`), backed by `std::sync::LazyLock`. Access via `wanaku_praxis_apis::config::ENV`. Never read `WANAKU_*` env vars directly with `std::env::var` outside this module.

| Variable | Default | Purpose |
|---|---|---|
| `WANAKU_MGMT_LISTEN` | `0.0.0.0:9090` | Management API listen address |
| `WANAKU_OLLAMA_UPSTREAM` | `127.0.0.1:11434` | Ollama backend address |
| `WANAKU_PERSIST_BACKEND` | _(unset = disabled)_ | Set to `"file"` to enable file persistence |
| `WANAKU_PERSIST_PATH` | `/data/registry` | Directory for `registry.json` |
| `WANAKU_CLASSIC_URL` | _(unset = disabled)_ | Classic proxy base URL |
| `WANAKU_UI_PATH` | _(unset = embedded)_ | Filesystem path to admin UI override |

### Praxis Config (server/src/default.yaml)

```yaml
listeners:
  - name: mcp
    address: "127.0.0.1:8081"
    filter_chains: [mcp_router]

filter_chains:
  - name: mcp_router
    filters:
      - filter: cors
        allow_origins: ["*"]
      - filter: mcp
        on_invalid: continue  # CRITICAL for OPTIONS
      - filter: wanaku_namespace
      - filter: wanaku_mcp_init
      # ... rest of pipeline
```

### Wanaku Config (wanaku.yaml — optional)

```yaml
tools:
  - name: "echo-tool"
    type: "echo-tool"
    uri: "echo-tool://echo"
    description: "Echoes a message"
    input_schema:
      type: object
      properties:
        wanaku_body:
          type: string

services:
  - name: "echo-tool"
    address: "localhost:9191"
    service_type: "tool-invoker"
```

If missing, server starts with empty registry (can populate via management API).

## Common Tasks

### Adding a New MCP Method Filter

1. Create filter in `filters/src/<method>.rs`
2. Implement `HttpFilter` trait with metadata checks:
   ```rust
   let method = ctx.get_metadata("mcp.method")?;
   if method != "your/method" {
       return Ok(FilterAction::Continue);
   }
   ```
3. Register in `server/src/lib.rs::register_wanaku_filters`:
   ```rust
   praxis_filter::register_filters!(
       @register registry,
       http "wanaku_your_method" => YourMethodFilter::from_config
   );
   ```
4. Add to pipeline in `server/src/default.yaml`

### Testing Filter Locally

```bash
# Start server
cargo run

# MCP request (port 8081)
curl -X POST http://localhost:8081/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"tools/list","id":1}'

# Management API (port 9090)
curl http://localhost:9090/api/v1/tools
```

### Namespace Isolation Example

```bash
# Default namespace
curl -X POST http://localhost:8081/mcp \
  -d '{"jsonrpc":"2.0","method":"tools/list","id":1}'

# Finance namespace
curl -X POST http://localhost:8081/finance/mcp \
  -d '{"jsonrpc":"2.0","method":"tools/list","id":1}'
```

Tools registered with `namespace: "finance"` only appear in `/finance/mcp` requests.

## Style Guidelines

- Use `#[derive(Debug, Clone)]` on registry entry types
- Never `unwrap()` or `panic!()` — return `FilterAction::Reject` with JSON-RPC error
- Trace logging for filter decisions, debug for parsed data, warn for errors, info for registration events
- Keep filter logic in `on_request_body` with `end_of_stream` guard
- Prefer `match` over `if let` chains when handling multiple cases
- Use `#[expect(..., reason = "...")]` for allowed lints (e.g., static response builders)

## Debugging

Enable trace logs:
```bash
RUST_LOG=trace cargo run
```

Watch MCP metadata flow:
```bash
RUST_LOG=wanaku_praxis_filters=trace cargo run
```

Check what filters see:
```rust
tracing::debug!(
    method = ?ctx.get_metadata("mcp.method"),
    namespace = ?ctx.get_metadata("wanaku.namespace"),
    "filter context"
);
```

## Future Extensibility

Registry traits are designed for pluggable backends:
- Could add PostgresRegistry, RedisRegistry, EtcdRegistry
- Trait bounds: `Send + Sync` (required for async filters)
- Clone required for pipeline extension (wraps in Arc if needed)

Only InMemoryRegistry exists today — but the abstraction is there.
