# Wanaku Documentation

Wanaku is a governed action proxy for AI agents. It sits between agents and the systems they act on, intercepting tool calls, agent-to-agent messages, and inference traffic. Integration developers build Apache Camel routes and publish them as tools; agents call those tools with parameters, but Wanaku runs the actual work — the agent never touches backend systems directly. Policy, identity, data controls, and audit happen in the proxy, not in the agent.

## Why Rust?

The classic Wanaku (Java + Quarkus) is a fully-featured implementation with service catalogs, Camel routes, Infinispan persistence, and OIDC integration. Wanaku expands on that foundation with a composable filter pipeline architecture built on the Praxis proxy framework — enabling pluggable governance features (evaluators, policy gates, interaction tracking) that would be difficult to express in the classic architecture.

Wanaku shares the same MCP protocol and management API as classic Wanaku.

## What You Get

- **Agent isolation** — agents call tools through the proxy; they never reach backend systems directly
- **Policy enforcement** — LLM-powered evaluators + WASM action scripts classify, filter, and block tool calls
- **Identity & auth** — oauth2-proxy + Keycloak, enforced before actions reach backends
- **MCP endpoint** (port 8081) — JSON-RPC over HTTP, compatible with any MCP client
- **Management API** (port 8080) — REST API for tools, resources, prompts, namespaces
- **Admin UI** — React-based web interface embedded in the binary
- **Namespace isolation** — different tools visible to different namespaces per team, tenant, or environment
- **Tool discovery** — auto-discover tools from upstream MCP servers; integration developers publish Camel routes as tools
- **Feature system** — pluggable filters for evaluation, LLM chat, interaction tracking
- **File persistence** — registry snapshots enabled by default to preserve data across restarts

All in a single binary with no runtime dependencies (except libc).

## Quick Links

### Getting Started

- **[Getting Started](./getting-started.md)** — download, install, and run your first Wanaku proxy
- **[Migrating from Classic Wanaku](./migration-from-classic.md)** — what changed, what broke, and how to migrate from the Java version
- **[Configuration](./configuration.md)** — all environment variables and YAML config options
- **[Authentication](./auth.md)** — set up oauth2-proxy with Keycloak
- **[Management API](./management-api.md)** — REST API reference for tools, resources, etc.
- **[FAQ](./faq.md)** — common issues and troubleshooting

### Understanding the System

- **[Architecture](./architecture.md)** — filter pipeline, registry, tool routing, deployment patterns
- **[Features](./features.md)** — LLM chat and how to create custom features

### Contributing

- **[Admin UI Development](./contributing-admin-ui.md)** — React + Carbon Design System frontend development

## Who This Is For

- **You need governed agent actions** — policy, identity, and audit enforced in the proxy, not in the agent or the LLM
- **You want agent isolation** — agents call tools but never touch backends directly
- **You want namespace isolation** — different tool catalogs per team, tenant, or environment without multi-tenancy complexity
- **You need pluggable governance** — evaluators, safety gates, and custom filters in the action path

## Who This Isn't For (Yet)

- **You need clustering** — Wanaku is single-node only
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
│     Wanaku — Governed Action Proxy          │
│  Identity → Policy → Namespace →            │
│  Evaluator → Tool/Resource/Prompt          │
└──────────────┬─────────────────────────────┘
               │
          Action forwarding
               │
               ▼
        ┌──────────┐
        │ Backend  │
        │ Systems  │
        └──────────┘
```

Agents send tool calls through Wanaku. The proxy intercepts every action, enforces identity and policy, resolves the target namespace, and forwards the action to the appropriate backend system. The agent never reaches backends directly — governance is enforced in the proxy layer.

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

The registry is the source of truth for tools, resources, prompts, namespaces, and forwards. An in-memory `DashMap` shares this data between the filter pipeline and management API.

**Persistence:** File persistence is enabled by default. Wanaku writes `registry.json` under `$HOME/.wanaku/server` on shutdown. Set `WANAKU_PERSIST_BACKEND=none` to use an in-memory registry without persistence.

### Namespaces

Namespaces isolate tools. A tool registered with `namespace: "finance"` only appears in requests to `/finance/mcp`. This is how you serve different tool sets to different LLMs or teams without multi-tenancy infrastructure.

### Tool Routing

Tools execute via MCP forwarding. When an LLM calls a tool, Wanaku forwards the request to the upstream MCP server specified in the tool's `uri` field. The upstream server handles actual execution and returns the result, which Wanaku wraps in a JSON-RPC response.

### Features

Features are Rust crates that implement the `Feature` trait. They can:

- Register filters into the pipeline
- Expose management API routes
- Share state between filters and API handlers
- Load config from YAML or environment variables

## Common Tasks

### Run Locally

Download `wanaku-server` from the [early access release page](https://github.com/wanaku-ai/wanaku/releases/tag/early-access), then:

```bash
wanaku-server
```

Server starts on:

- MCP: `http://127.0.0.1:8081/default/mcp`
- Management API: `http://0.0.0.0:8080/api/v1`

### Register a Forward

Tools, resources, and prompts are obtained by registering a forwarded MCP server. When you register a forward, Wanaku auto-discovers all tools from the upstream server.

Using the [Wanaku CLI](https://github.com/wanaku-ai/wanaku-barn/releases/tag/early-access):

```bash
wanaku forwards add --service="http://echo-mcp:8080/mcp" --name echo-server --no-auth
```

This automatically discovers and registers all tools exposed by the upstream MCP server.

### Deploy to Kubernetes

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: wanaku-server
spec:
  replicas: 1  # File snapshots support one writer. Do not share this volume across replicas.
  selector:
    matchLabels:
      app: wanaku-server
  template:
    metadata:
      labels:
        app: wanaku-server
    spec:
      containers:
      - name: wanaku
        image: wanaku-server:latest
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

Run Wanaku as a standalone proxy. Tools are discovered from upstream MCP servers registered as forwards via the management API or `wanaku.yaml`.

**Pros:** Simple, no dependencies
**Cons:** No persistence beyond file snapshots

### Kubernetes

Deploy Wanaku as a `Deployment` with a `PersistentVolume` for the registry. Use `ConfigMap` for `wanaku.yaml` and `Secret` for LLM API keys.

**Pros:** Declarative configuration and managed restarts
**Cons:** File persistence supports one replica; horizontal scaling requires a shared external persistence implementation

## What's Different from Classic Wanaku?

| Capability | Classic (Java) | Wanaku (Rust) |
|---|---|---|
| **MCP protocol** | ✅ Full support | ✅ Full support |
| **Namespace isolation** | ✅ Yes | ✅ Yes |
| **Action forwarding** | ✅ Yes | ✅ Yes (with auto-discovery) |
| **Governance pipeline** | — | ✅ Composable filter chain with policy enforcement |
| **WASM evaluators** | — | ✅ LLM + WASM action scripts for policy gates |
| **Feature crates** | — | ✅ Pluggable Rust crates |
| **Service catalogs** | ✅ Yes | ❌ Not yet |
| **Camel routes as tools** | ✅ Direct | ⚠️ Via MCP forward to Camel-based upstream |
| **OIDC/OAuth** | ✅ Keycloak | ✅ oauth2-proxy + Keycloak |
| **Persistence** | ✅ Infinispan | ⚠️ File snapshots only |
| **Admin UI** | ✅ React + Orval | ✅ React + Orval |

## Performance Characteristics

Wanaku is built on Pingora (Cloudflare's proxy framework) and uses async I/O throughout. The filter pipeline adds minimal overhead to each request. Actual throughput and latency depend on your downstream services (upstream MCP servers, LLM endpoints).

The binary is a single statically-linked executable with low memory footprint.

## Debugging

Enable trace logs:

```bash
RUST_LOG=trace wanaku-server
```

Check what filters see:

```bash
RUST_LOG=wanaku_filters=trace wanaku-server
```

## Contributing

Wanaku is open source (Apache 2.0). Contributions welcome.

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

- **GitHub:** https://github.com/wanaku-ai/wanaku
- **Issues:** https://github.com/wanaku-ai/wanaku/issues
- **Classic Wanaku:** https://github.com/wanaku-ai/wanaku-barn

## License

Apache 2.0. See LICENSE for details.

---

**Next Steps:**

- New to Wanaku? Start with [Getting Started](./getting-started.md)
- Want to understand the internals? Read [Architecture](./architecture.md)
- Need to configure it? See [Configuration](./configuration.md)
- Building custom features? Check [Features](./features.md)
