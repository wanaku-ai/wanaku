---
name: wanaku-praxis
description: "Bootstrap plan for a new Rust project built on praxis framework — architecture, extension points, and future goals (MCP→gRPC, admin API)"
metadata: 
  node_type: memory
  type: project
  originSessionId: b3abf2a3-5e91-43a6-8064-823d4c268b85
---

New project ("wanaku-praxis") that builds on the praxis proxy framework to add custom protocol handling, starting with MCP→gRPC transformation and eventually a management API.

**Why:** Praxis is designed as a framework — praxis-ai demonstrates the pattern. A new project follows the same model: depend on praxis core crates, implement domain-specific filters, register them, and call `run_server_with_registry()`.

**How to apply:** Use this file as the bootstrap reference when creating the project.

## Praxis Framework Overview

Praxis is a general-purpose high-performance reverse proxy framework built on Pingora. It provides:
- `HttpFilter` trait and `FilterPipeline` engine for request/response processing
- Built-in filters: routing, load balancing, rate limiting, IP ACLs, CORS, compression, circuit breaking, header manipulation, JSON-RPC parsing, gRPC detection, etc.
- Protocol layer: `PingoraHttp` and `PingoraTcp` adapters wrapping Pingora
- Hot-reload config via `ArcSwap` — atomic pipeline replacement without restart
- `FilterRegistry` with `register_filters!` / `export_filters!` macros for extensibility
- Admin endpoints: `/healthy`, `/ready`, `/metrics` (Prometheus), `/api/kv/*` (CRUD)
- Health check runner (HTTP, HTTP/2, TCP probes per cluster)
- Published as `praxis-proxy-*` crates: `praxis-proxy-core`, `praxis-proxy-filter`, `praxis-proxy-protocol`, `praxis-proxy-tls`

## Project Structure (follow praxis-ai's pattern)

```
wanaku-praxis/
├── Cargo.toml              # workspace root
├── server/                 # binary crate (wanaku-praxis binary)
│   └── src/
│       ├── main.rs         # entry point, CLI
│       ├── lib.rs          # register_filters! macro, build_full_registry()
│       ├── server.rs       # calls run_server_with_registry()
│       └── pipelines.rs    # resolve_pipelines() with custom PipelineExtensions
├── filters/                # filter implementations
│   └── src/
│       └── ...             # one module per filter
├── apis/                   # protocol types, parsers, clients
│   └── src/
│       └── ...
└── tests/
    └── integration/        # integration tests
```

## Dependencies on Praxis Core

```toml
[dependencies]
praxis-core = { package = "praxis-proxy-core", version = "0.4" }
praxis-filter = { package = "praxis-proxy-filter", version = "0.4" }
praxis-protocol = { package = "praxis-proxy-protocol", version = "0.4" }
praxis-tls = { package = "praxis-proxy-tls", version = "0.4" }
```

Or path deps during development:
```toml
praxis-core = { package = "praxis-proxy-core", path = "../praxis/core" }
praxis-filter = { package = "praxis-proxy-filter", path = "../praxis/filter" }
praxis-protocol = { package = "praxis-proxy-protocol", path = "../praxis/protocol" }
praxis-tls = { package = "praxis-proxy-tls", path = "../praxis/tls" }
```

## Key Extension Points

### 1. Custom Filter Registration
```rust
use praxis_filter::register_filters;

register_filters! {
    http "my_filter" => MyFilter::from_config,
}
```
Generates a `custom_registry()` function that starts with builtins and adds user filters. Pass to `run_server_with_registry()`.

### 2. HttpFilter Trait
Every filter implements:
- `name()` → filter name string
- `request_body_access()` / `response_body_access()` → `BodyAccess::None | ReadOnly | ReadWrite`
- `request_body_mode()` / `response_body_mode()` → `BodyMode::Stream | StreamBuffer { max_bytes } | SizeLimit { max_bytes }`
- `on_request()` / `on_request_body()` / `on_response()` / `on_response_body()` → `FilterAction`
- Constructor: `from_config(&serde_yaml::Value) -> Result<Box<dyn HttpFilter>, FilterError>`

### 3. PipelineExtension Trait
Inject per-request resources that filters retrieve from `RequestExtensions`:
```rust
impl PipelineExtension for MyRegistry {
    fn prepare(&self, extensions: &mut RequestExtensions) {
        extensions.insert(self.clone());
    }
}
```
Register via `FilterPipeline::add_pipeline_extension()`.

### 4. Body Rewriting Pattern (from anthropic_to_openai precedent)
- Request: `BodyAccess::ReadWrite` + `BodyMode::StreamBuffer { max_bytes }` → full body arrives in single `on_request_body()` call at EOS → parse → transform → `*body = Some(Bytes::from(new_bytes))`
- Response: start as `BodyMode::Stream`, dynamically upgrade to `StreamBuffer` in `on_response()` for non-streaming → transform at EOS
- Streaming responses: process per-chunk in `on_response_body()` with `BodyMode::Stream`
- Header mutation: `ctx.extra_request_headers` (request), `ctx.response_header.headers` (response)

### 5. Inter-filter Communication
- `ctx.set_metadata(key, value)` — flat string KV (64-byte key, 256-byte value limits)
- `ctx.set_structured_metadata(namespace, key, value)` — nested JSON per namespace
- `ctx.filter_results` — classifier → router communication
- `ctx.insert_filter_state::<T>()` / `ctx.get_filter_state::<T>()` — typed per-filter state

### 6. Synthetic Responses
Filters can short-circuit with locally-generated responses:
```rust
FilterAction::Reject(Rejection::status(200).with_body(json_bytes).with_header("Content-Type", "application/json"))
```

## Classify → Route → Branch Pattern

Classifier filters inspect requests, promote facts to filter results. The core router matches those results to select backend clusters. Downstream filters branch on the classification.

Example flow: `my_classifier` (writes `format=grpc` to filter_results) → `router` (matches `format=grpc` → grpc-cluster) → `load_balancer` → backend.

## Future Goals

### MCP→gRPC Transformation (see [[mcp-to-grpc-transformation]])
- Body rewriting: JSON-RPC → protobuf + gRPC framing (request), reverse on response
- **Blocker**: praxis core has no upstream HTTP/2 selection API. Needs cluster-level `upstream_http_version: h2` or filter-level `ctx.set_upstream_http_version()`. Pingora supports it; praxis doesn't expose it.
- Needs `prost` dependency and `.proto` definitions for target gRPC service
- `tonic`/`tonic-prost` already in praxis core workspace deps (test-only currently)

### Admin Management API (see [[admin-management-api]])
- Register a separate Pingora service on a management port
- Needs `Arc` handles to `ListenerPipelines`, `Config`, `FilterRegistry`, `KvStoreRegistry`
- Config mutation: either write YAML to disk (file watcher picks up) or call `resolve_pipelines()` + `swap()` directly
- No praxis core changes needed initially; `AdminExtension` trait could be upstreamed later

## Config System

YAML config maps to `Config` struct. Key sections:
- `listeners` — bind addresses, protocol, TLS, filter chain references
- `filter_chains` — named ordered lists of filter entries
- `clusters` — backend endpoint groups with health checks
- `admin` — admin port config (loopback-only by default)
- `metrics` — Prometheus config
- `runtime` — connection/memory limits

## Conventions (inherited from praxis)

- Rust stable 1.96+, edition 2024
- `#![deny(unsafe_code)]` in all crate roots
- `#[expect(...)]` with `reason` instead of `#[allow(...)]`
- Clippy with `-D warnings`
- Errors via `thiserror`, logging via `tracing`
- New features require: unit tests, integration tests, example config, functional integration test
- Body rewriting: prefer streaming over buffering; keep buffer limits explicit
- No holding locks across `.await` points
