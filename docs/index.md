# Wanaku Praxis Documentation

Wanaku Praxis is a Rust-based MCP (Model Context Protocol) server built on the Praxis proxy framework. It routes AI agent requests through a sophisticated filter pipeline to provide namespace isolation, tool/resource/prompt management, and both local (gRPC) and remote (MCP forwarding) tool execution.

## Why Praxis?

The classic Wanaku MCP Router (Java + Quarkus) is a fully-featured MCP server with service catalogs, Camel routes, Infinispan persistence, and OIDC integration. Praxis expands on that foundation with a composable filter pipeline architecture built on the Praxis proxy framework — enabling features like LLM-based safety classification and pluggable feature crates that would be difficult to express in the classic architecture.

Praxis shares the same MCP protocol and management API as classic Wanaku. It can run standalone or alongside the classic backend — point `WANAKU_CLASSIC_URL` at a classic instance to combine both.

## What You Get

- **MCP endpoint** (port 8081) — JSON-RPC over HTTP, compatible with any MCP client
- **Management API** (port 9090) — REST API for tools, resources, prompts, namespaces
- **Admin UI** — React-based web interface embedded in the binary
- **Namespace isolation** — different tools visible to different namespaces
- **Tool routing** — local gRPC services or remote MCP server forwarding
- **Feature system** — pluggable filters for safety checks, LLM chat, custom logic
- **File persistence** — optional registry snapshots to survive restarts

All in a single binary with no runtime dependencies (except libc).

## Quick Links

### Getting Started

- **[Getting Started](./getting-started.md)** — install, build, run your first MCP server
- **[Configuration](./configuration.md)** — all environment variables and YAML config options
- **[Management API](./management-api.md)** — REST API reference for tools, resources, etc.

### Understanding the System

- **[Architecture](./architecture.md)** — filter pipeline, registry, tool routing, deployment patterns
- **[Features](./features.md)** — safety checks, LLM chat, and how to create custom features

### Extending and Customizing

- **[Admin UI](./admin-ui.md)** — React + Carbon Design System frontend development

## Who This Is For

- **You want a composable filter pipeline** for MCP request processing
- **You want namespace isolation** without multi-tenancy complexity
- **You're building LLM apps** that need tool call safety filtering
- **You need pluggable features** like LLM-based classification or custom request processing
- **You want to run alongside classic Wanaku** to extend its capabilities

## Who This Isn't For (Yet)

- **You need service catalogs** — use classic Wanaku or build a custom feature
- **You need complex Camel routes** — delegate to classic backend or a gRPC service
- **You need OIDC/OAuth** — authentication isn't implemented (yet)
- **You need clustering** — Praxis is single-node only
- **You need metrics/tracing** — no Prometheus or OpenTelemetry integration

These are solvable, but they're not in scope for the initial release.

## Architecture at a Glance

```
┌────────────────────────────────────────────┐
│           LLM / AI Agent                    │
└──────────────┬─────────────────────────────┘
               │ MCP (JSON-RPC over HTTP)
               ▼
┌────────────────────────────────────────────┐
│       Praxis Filter Pipeline                │
│  CORS → MCP Parse → Namespace →             │
│  Tool List/Call → Resource → Prompt        │
└──────────────┬─────────────────────────────┘
               │
       ┌───────┴────────┐
       │                │
    gRPC            HTTP MCP
  (Local)          (Forward)
       │                │
       ▼                ▼
 ┌──────────┐    ┌──────────┐
 │  Tool    │    │ Upstream │
 │ Services │    │   MCP    │
 └──────────┘    └──────────┘
```

Requests flow through a chain of filters. Each filter reads metadata (method, namespace, tool name) and decides whether to continue, reject, or synthesize a response. The in-memory registry tracks tools, resources, prompts, and services. Tool calls are routed to gRPC services or forwarded to upstream MCP servers.

See [Architecture](./architecture.md) for the full story.

## Core Concepts

### Filters

Filters are middleware that processes requests. Each filter hooks into the Praxis pipeline and can:

- Read/write request metadata
- Inspect or modify the request body
- Synthesize a JSON-RPC response
- Reject the request with an error
- Continue to the next filter

**Example:** The `wanaku_namespace` filter extracts the namespace from the URL path (`/finance/mcp` → `"finance"`) and sets `wanaku.namespace` metadata. Downstream filters query this metadata to filter tools by namespace.

### Registry

The registry is the source of truth for tools, resources, prompts, namespaces, forwards, and services. It's an in-memory `DashMap` (concurrent hash map) shared between the filter pipeline and management API.

**Persistence:** By default, the registry is ephemeral. Enable file persistence with `WANAKU_PERSIST_BACKEND=file` to snapshot to `registry.json` on shutdown.

### Namespaces

Namespaces isolate tools. A tool registered with `namespace: "finance"` only appears in requests to `/finance/mcp`. This is how you serve different tool sets to different LLMs or teams without multi-tenancy infrastructure.

### Tool Routing

Tools can execute in two ways:

1. **gRPC (local):** Tool has `type != "mcp-forward"`. The filter looks up a gRPC service registered for that type and calls it.
2. **MCP forward (remote):** Tool has `type == "mcp-forward"`. The filter forwards the request to an upstream MCP server via HTTP.

### Features

Features are Rust crates that implement the `Feature` trait. They can:

- Register filters into the pipeline
- Expose management API routes
- Share state between filters and API handlers
- Load config from YAML or environment variables

**Example:** The safety feature registers a filter that intercepts tool calls and sends them to an LLM for classification. It also exposes `GET/PUT/DELETE /api/v1/safety` to configure the classifier.

## Common Tasks

### Run Locally

```bash
git clone https://github.com/wanaku-ai/wanaku-praxis.git
cd wanaku-praxis
cargo run --release
```

Server starts on:
- MCP: `http://127.0.0.1:8081/mcp`
- Management API: `http://0.0.0.0:9090/api/v1`

### Register a Tool

```bash
curl -X POST http://localhost:9090/api/v1/tools \
  -H "Content-Type: application/json" \
  -d '{
    "name": "echo",
    "type": "echo-tool",
    "uri": "echo-tool://echo",
    "description": "Echoes a message",
    "input_schema": {
      "type": "object",
      "properties": {"message": {"type": "string"}}
    }
  }'
```

### Enable Safety Checks

```bash
export WANAKU_SAFETY_LLM_URL=http://localhost:11434/v1
export WANAKU_SAFETY_LLM_MODEL=llama3.1:8b
cargo run --release
```

Now all tool calls are classified by the LLM before execution.

### Deploy to Kubernetes

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: wanaku-praxis
spec:
  replicas: 1  # each replica has its own in-memory registry; scale only with external persistence
  template:
    spec:
      containers:
      - name: wanaku
        image: wanaku-praxis:latest
        env:
        - name: WANAKU_PERSIST_BACKEND
          value: "file"
        - name: WANAKU_PERSIST_PATH
          value: "/data/registry"
        volumeMounts:
        - name: data
          mountPath: /data
      volumes:
      - name: data
        persistentVolumeClaim:
          claimName: wanaku-registry
```

See [Configuration](./configuration.md) for details.

## Deployment Patterns

### Standalone

Run Praxis as the only MCP server. All tools are gRPC-based, registered via the management API or `wanaku.yaml`.

**Pros:** Simple, no dependencies
**Cons:** No persistence beyond file snapshots, no advanced features

### Hybrid (Praxis + Classic Backend)

Run Praxis alongside a classic Wanaku backend. Point `WANAKU_CLASSIC_URL` at the classic instance to combine Praxis's filter pipeline with classic's persistence and service catalogs.

**Pros:** Filter pipeline features + classic backend capabilities
**Cons:** Two servers to manage

### Kubernetes

Deploy Praxis as a `Deployment` with a `PersistentVolume` for the registry. Use `ConfigMap` for `wanaku.yaml` and `Secret` for LLM API keys.

**Pros:** Horizontal scaling, declarative config
**Cons:** Requires k8s infrastructure

## What's Different from Classic Wanaku?

| Feature | Classic (Java) | Praxis (Rust) |
|---|---|---|
| **MCP protocol** | ✅ Full support | ✅ Full support |
| **Namespace isolation** | ✅ Yes | ✅ Yes |
| **Tool routing (gRPC)** | ✅ Yes | ✅ Yes |
| **MCP forwarding** | ✅ Yes | ✅ Yes |
| **Filter pipeline** | — | ✅ Composable filter chain |
| **Feature crates** | — | ✅ Pluggable Rust crates |
| **LLM safety filtering** | — | ✅ Built-in |
| **Service catalogs** | ✅ Yes | ❌ Not yet |
| **Camel routes** | ✅ Yes | ⚠️ Delegate to gRPC service |
| **OIDC/OAuth** | ✅ Keycloak | ❌ Not yet |
| **Persistence** | ✅ Infinispan | ⚠️ File snapshots only |
| **Admin UI** | ✅ React + Orval | ✅ React + Orval |

## Performance Characteristics

Praxis is built on Pingora (Cloudflare's proxy framework) and uses async I/O throughout. The filter pipeline adds minimal overhead to each request. Actual throughput and latency depend on your downstream services (gRPC, upstream MCP servers, LLM endpoints).

The binary is a single statically-linked executable with low memory footprint.

## Debugging

Enable trace logs:

```bash
RUST_LOG=trace cargo run
```

Check what filters see:

```bash
RUST_LOG=wanaku_praxis_filters=trace cargo run
```

Verify environment variables:

```rust
// Add to main.rs
println!("WANAKU_MGMT_LISTEN: {:?}", wanaku_praxis_apis::config::ENV.mgmt_listen);
```

## Contributing

Praxis is open source (Apache 2.0). Contributions welcome.

**Code style:**
- `cargo fmt` before committing
- `cargo clippy -- -D warnings` must pass
- No `unwrap()`, `expect()`, or `panic!()` (enforced by `#![deny(...)]`)
- Trace logs for filter decisions, debug for data, warn for errors

**Adding a feature:**
1. Create a new crate under `features/<name>/`
2. Implement the `Feature` trait
3. Wire it into `server/src/main.rs`
4. Add filter to `server/src/default.yaml` if needed
5. Write tests
6. Update docs

## Support and Community

- **GitHub:** https://github.com/wanaku-ai/wanaku-praxis
- **Issues:** https://github.com/wanaku-ai/wanaku-praxis/issues
- **Classic Wanaku:** https://github.com/wanaku-ai/wanaku

## License

Apache 2.0. See LICENSE for details.

---

**Next Steps:**

- New to Praxis? Start with [Getting Started](./getting-started.md)
- Want to understand the internals? Read [Architecture](./architecture.md)
- Need to configure it? See [Configuration](./configuration.md)
- Building custom features? Check [Features](./features.md)
