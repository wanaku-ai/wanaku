# Wanaku Documentation

Wanaku is a governed action proxy for AI agents. It sits between agents and the systems they act on, intercepting tool calls, agent-to-agent messages, and inference traffic. Wanaku forwards tool calls to upstream MCP servers, for example a Camel-based server that publishes Apache Camel routes as tools. The agent never touches backend systems directly. Policy, data controls, and audit happen in the proxy, not in the agent. An authenticating proxy such as oauth2-proxy handles identity in front of Wanaku.

## Why Rust?

The classic Wanaku (Java + Quarkus) is a fully-featured implementation with service catalogs, Camel routes, Infinispan persistence, and OIDC integration. Wanaku expands on that foundation with a composable filter pipeline architecture built on the Praxis proxy framework — enabling pluggable governance features (evaluators, policy gates, interaction tracking) that would be difficult to express in the classic architecture.

Wanaku shares the same MCP protocol and management API as classic Wanaku.

## What You Get

- **Agent isolation** — agents call tools through the proxy; they never reach backend systems directly
- **Policy enforcement** — LLM-powered evaluators + WASM action scripts classify, filter, and block tool calls
- **Identity & auth** — oauth2-proxy + Keycloak in front of Wanaku, enforced before actions reach backends
- **MCP endpoint** (port 8081) — JSON-RPC over HTTP, compatible with any MCP client
- **Management API** (port 8080) — REST API for tools, resources, prompts, namespaces
- **Admin UI** — React-based web interface embedded in the binary
- **Namespace isolation** — different tools visible to different namespaces per team, tenant, or environment
- **Tool discovery** — auto-discover tools from upstream MCP servers, for example a Camel-based MCP server that publishes Camel routes as tools
- **Feature system** — pluggable filters for evaluation, interaction tracking
- **File persistence** — registry snapshots enabled by default to preserve data across restarts

All in a single binary that needs only libc.

## Quick Links

### Getting Started

- **[Getting Started](./get-started/quickstart.md)** — download, install, and run your first Wanaku proxy
- **[Migrating from Classic Wanaku](./get-started/migration-from-classic.md)** — what changed, what broke, and how to migrate from the Java version
- **[Configuration](./reference/configuration.md)** — all environment variables and YAML config options
- **[Authentication](./operate/authentication.md)** — set up oauth2-proxy with Keycloak
- **[Management API](./reference/management-api.md)** — REST API reference for tools, resources, etc.
- **[Governance Posture](./govern/governance-posture.md)** — enforcement modes, fail-safe defaults, and namespace resolution
- **[Action Policies](./govern/action-policies.md)** — deterministic rules for MCP actions
- **[Semantic Routing](./connect/semantic-routing.md)** — Barn authoring, WSR deployment, and governed MCP acceptance checks
- **[Policy Simulation](./govern/policy-simulation.md)** — validate candidates and compare action decisions before activation
- **[Redaction Boundary](./govern/redaction.md)** — where Wanaku removes credentials and PII before logs, errors, and stored data
- **[Evaluator Engine](./govern/evaluators.md)** — LLM classification and WASM action scripts for policy enforcement
- **[Governance Audit Trail](./govern/audit-trail.md)** — audit events, redaction rules, and retention
- **[A2A](./connect/a2a.md)** — governed agent-to-agent traffic
- **[Container Build](./contributing/container-build.md)** — build the container images locally
- **[FAQ](./operate/troubleshooting.md)** — common issues and troubleshooting

### Understanding the System

- **[Architecture](./get-started/architecture.md)** — filter pipeline, registry, tool routing, deployment patterns
- **[Features](./extend/feature-crates.md)** — evaluators and how to create custom features
- **[Credential Brokerage](./govern/credential-brokerage.md)** — scoped, fail-closed upstream authentication for forwards

### Contributing

- **[Contributing](./contributing/index.md)** — development workflow, checks, and pull requests
- **[Admin UI Development](./contributing/admin-ui.md)** — React + Carbon Design System frontend development
- **[Build an Admin UI plugin](./extend/plugin-development-guide.md)** — write and package admin UI plugins
- **[Plugin Catalog](./extend/plugin-catalog.md)** — publish and install available UI plugins
- **[Web UI Plugin Guidelines](./extend/extensible-web-ui-plugin-guidelines.md)** — design rules for admin UI plugins

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

```text
┌────────────────────────────────────────────┐
│           LLM / AI Agent                    │
└──────────────┬─────────────────────────────┘
               │ MCP (JSON-RPC over HTTP)
               ▼
┌────────────────────────────────────────────┐
│     Wanaku — Governed Action Proxy          │
│  Namespace → Policy → Evaluator →           │
│  Tool/Resource/Prompt                      │
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

Agents send tool calls through Wanaku. The proxy intercepts every action, resolves the target namespace, enforces policy, and forwards the action to the appropriate upstream MCP server. Authentication happens before Wanaku, in an authenticating proxy such as oauth2-proxy. The agent never reaches backends directly — governance is enforced in the proxy layer.

See [Architecture](./get-started/architecture.md) for the full story.

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

**Persistence:** File persistence is enabled by default. Wanaku writes `registry.json` under `$HOME/.wanaku/server` after each registry change. Set `WANAKU_PERSIST_BACKEND=none` to use an in-memory registry without persistence.

### Namespaces

Namespaces isolate tools. A tool registered with `namespace: "finance"` only appears in requests to `/finance/mcp`. This is how you serve different tool sets to different LLMs or teams without multi-tenancy infrastructure.

### Tool Routing

Tools execute via MCP forwarding. When an LLM calls a tool, Wanaku forwards the request to the address of the forward that owns the tool (`forwardId`). The upstream server handles actual execution and returns the result, which Wanaku wraps in a JSON-RPC response.

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

Connect clients to these URLs:

- MCP: `http://localhost:8081/default/mcp`
- Management API: `http://localhost:8080/api/v1`

The server listens on all network interfaces. It also opens port 8083 (inference proxy) and port 8084 (A2A). None of these ports require authentication by default. Do not expose them on an untrusted network without an authenticating proxy.

### Register a Forward

Tools, resources, and prompts are obtained by registering a forwarded MCP server. When you register a forward, Wanaku auto-discovers all tools from the upstream server.

Using the [Wanaku CLI](https://github.com/wanaku-ai/wanaku-barn/releases/tag/early-access):

```bash
wanaku forwards add --service="http://echo-mcp:8080/mcp" --name echo-server --namespace default --no-auth
```

This automatically discovers and registers all tools exposed by the upstream MCP server. The host in this example is a placeholder. For a procedure that you can run end to end, see [Try a Forward](./get-started/migration-from-classic.md#try-a-forward).

### Deploy to Kubernetes

For production installs, use the Wanaku Operator. See [Production Deployment on Kubernetes or OpenShift](./get-started/quickstart.md#production-deployment-on-kubernetes-or-openshift). For plain manifests (ConfigMap, Secret, Deployment, and Service), see [Configuration: Kubernetes](./operate/kubernetes.md).

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
| **Service catalogs** | ✅ Yes | ⚠️ Served by Wanaku Barn, which runs next to Wanaku |
| **Camel routes as tools** | ✅ Direct | ⚠️ Via MCP forward to Camel-based upstream |
| **OIDC/OAuth** | ✅ Keycloak | ✅ oauth2-proxy + Keycloak |
| **Persistence** | ✅ Infinispan | ⚠️ File snapshots only |
| **Admin UI** | ✅ React + Orval | ✅ React + Orval |

## Performance Characteristics

Wanaku is built on Pingora (Cloudflare's proxy framework) and uses async I/O throughout. The filter pipeline adds minimal overhead to each request. Actual throughput and latency depend on your downstream services (upstream MCP servers, LLM endpoints).

The binary is a single executable that needs only libc, with a low memory footprint.

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
3. Wire it into `build_features` in `server/src/app.rs`
4. Add the crate to the cache-build stage of `Containerfile`
5. Add filter to `server/src/default.yaml` if needed
6. Write tests
7. Update docs

See [Build a feature crate](./extend/build-a-feature.md) for the full tutorial.

## Support and Community

- **GitHub:** <https://github.com/wanaku-ai/wanaku>
- **Issues:** <https://github.com/wanaku-ai/wanaku/issues>
- **Classic Wanaku:** <https://github.com/wanaku-ai/wanaku-barn>

## License

Apache 2.0. See LICENSE for details.

---

**Next Steps:**

- New to Wanaku? Start with [Getting Started](./get-started/quickstart.md)
- Want to understand the internals? Read [Architecture](./get-started/architecture.md)
- Need to configure it? See [Configuration](./reference/configuration.md)
- Building custom features? Check [Features](./extend/feature-crates.md)
