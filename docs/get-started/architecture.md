---
title: Architecture
description: How Wanaku intercepts and governs tool calls, agent messages, and inference traffic, with the filter pipeline, registries, and features.
---

# Architecture

Wanaku is a governed action proxy for AI agents, built in Rust on the Praxis proxy framework. It sits between agents and the systems they act on, intercepting every tool call, agent-to-agent message, and inference request. Policy, identity, data controls, and audit are enforced in the proxy — agents never touch backend systems directly.

Under the hood, Wanaku is an HTTP filter pipeline. Actions flow through a chain of filters, each responsible for a specific governance concern — identity, policy evaluation, namespace isolation, tool dispatch. Unlike a gateway that just passes traffic, the proxy does the work: it resolves tools, forwards actions to backends, and returns results to the agent.

## High-Level Architecture

```text
┌─────────────────────────────────────────────────────────────┐
│                      LLM / AI Agent                         │
└────────────────┬────────────────────────────────────────────┘
                 │ MCP (JSON-RPC over HTTP)
                 ▼
┌─────────────────────────────────────────────────────────────┐
│         Wanaku — Governed Action Proxy (Port 8081)          │
│  ┌────────────────────────────────────────────────────────┐ │
│  │           Governance Pipeline (Praxis)                 │ │
│  │  CORS → MCP Parse → Namespace → Action Policy →        │ │
│  │  Evaluator → Tool/Resource/Prompt → Static Response    │ │
│  └────────────────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────────────────┐ │
│  │              In-Memory Registry (DashMap)              │ │
│  │  Tools │ Resources │ Prompts │ Forwards │ Namespaces   │ │
│  └────────────────────────────────────────────────────────┘ │
└────────────────────────────────┬────────────────────────────┘
                                 │
                        Action Forward (HTTP)
                                 │
                                 ▼
                   ┌───────────────────────┐
                   │   Backend Systems     │
                   │  (Upstream MCP/Camel) │
                   └───────────────────────┘
```

**Action flow:**

1. Agent sends an MCP action (tool call, resource read, prompt get) to `/{namespace}/mcp`
2. Governance pipeline intercepts: namespace resolution, action policy, evaluators. An external oauth2-proxy handles identity before the request reaches Wanaku
3. Filters query the in-memory registry for tools/resources/prompts
4. For tool calls: the proxy forwards the action to the backend system registered as the tool's forward address — the agent never reaches the backend directly
5. Response flows back through filters, wrapped in JSON-RPC

## The Filter Pipeline

Filters are the heart of Praxis. Each request passes through a chain of filters defined in `server/src/default.yaml`. This listing shows the filter order of the `mcp_router` chain. It leaves out the filter options. Refer to [`server/src/default.yaml`](https://github.com/wanaku-ai/wanaku/blob/main/server/src/default.yaml) for the complete configuration:

```yaml
filter_chains:
  - name: mcp_router
    filters:
      - filter: cors                    # Add CORS headers
      - filter: mcp                     # Parse JSON-RPC, set metadata
        on_invalid: continue
      - filter: wanaku_mcp_id           # Read the JSON-RPC request ID
      - filter: wanaku_namespace        # Extract namespace from path
      - filter: wanaku_well_known       # RFC 9728 OAuth metadata (feature)
      - filter: wanaku_mcp_init         # Initialize MCP context
      - filter: wanaku_action_policy    # Deterministic static action policy
      - filter: wanaku_evaluator        # Evaluator engine (feature)
      - filter: wanaku_tool_list        # Handle tools/list
      - filter: wanaku_tool_call        # Handle tools/call
      - filter: wanaku_resource_list    # Handle resources/list
      - filter: wanaku_resource_read    # Handle resources/read
      - filter: wanaku_prompt_list      # Handle prompts/list
      - filter: wanaku_prompt_get       # Handle prompts/get
      - filter: static_response         # Catch-all: HTTP 200 with JSON-RPC error -32601
```

### Filter Execution Model

Filters implement the `HttpFilter` trait from `praxis-filter`. Each filter can hook into multiple phases:

- `on_request` — called after headers are parsed, before body read
- `on_request_body` — called as body chunks arrive (or after full buffer in StreamBuffer mode)
- `on_response` — called before sending response to client

Most Wanaku filters use **StreamBuffer mode** for body access:

```rust
fn request_body_mode(&self) -> BodyMode {
    BodyMode::StreamBuffer { max_bytes: Some(1_048_576) }  // 1MB limit
}
```

Praxis buffers the entire request body (up to the limit), then calls `on_request_body` once with the complete payload. This simplifies JSON-RPC parsing—you get the full message in one shot.

### Critical Ordering: Why Namespace Runs in `on_request_body`

This is non-obvious and causes bugs if you get it wrong.

In StreamBuffer mode, Praxis executes filters in this order:

1. **Pre-read phase** — `on_request_body` called for all filters, but `body` is `None` (buffering not complete)
2. **Post-read phase** — `on_request_body` called again, `body` is `Some(bytes)` (buffer complete)
3. **Request phase** — `on_request` called for all filters

The MCP filter sets `mcp.method` metadata in its `on_request_body` handler during step 2. If the namespace filter ran in `on_request` during step 3, it would execute before the MCP filter's body handler. The metadata would not exist at that time.

Running both in `on_request_body` ensures they execute in pipeline order during the post-read phase.

**Guard pattern:**

```rust
async fn on_request_body(
    &self,
    ctx: &mut HttpFilterContext<'_>,
    body: &mut Option<Bytes>,
    end_of_stream: bool,
) -> Result<FilterAction, FilterError> {
    if !end_of_stream {
        return Ok(FilterAction::Continue);  // Not ready yet
    }
    // Process body here
}
```

### Metadata Contract

Filters communicate via metadata keys set on the request context.

**Set by MCP filter (praxis-ai):**

- `mcp.method` → `"tools/list"`, `"tools/call"`, `"resources/read"`, etc.
- `mcp.name` → tool/resource/prompt name (extracted from `params.name` or `params.arguments`)

**Set by namespace filter:**

- `wanaku.namespace` → extracted from URL path:
  - `/default/mcp` → `"default"`
  - `/finance/mcp` → `"finance"`

Wanaku rejects bare `/mcp` paths and nested namespace paths such as `/nested/or/malformed/mcp`.

**Querying metadata:**

```rust
let method = ctx.get_metadata("mcp.method")?;
let namespace = ctx.get_metadata("wanaku.namespace")?;
```

All downstream filters (`tool_list`, `tool_call`, and others) use these keys. If a key is missing, the filter returns an error.

## Why Custom Filters Instead of Praxis-AI's MCP Broker?

Wanaku uses the praxis-ai `McpFilter` classifier to parse JSON-RPC and set the `mcp.method` and `mcp.name` metadata. All downstream MCP handling uses custom Wanaku filters instead of the praxis-ai `McpBrokerFilter`. This includes `tools/list`, `tools/call`, `resources/list`, `resources/read`, `prompts/list`, and `prompts/get`. Wanaku uses custom filters for these reasons:

**Praxis-ai's `McpBrokerFilter` is a static catalog broker.** Tools are declared in YAML at deploy time with preconfigured backend clusters. In stateless mode it routes `tools/call` via Pingora's L7 proxy by matching tool names to cluster endpoints; in current mode `tools/call` is not even implemented. It has no concept of namespaces, dynamic discovery, or runtime registration.

**Wanaku needs dynamic, namespace-aware routing:**

| Capability | Praxis-ai Broker | Wanaku Filters |
|---|---|---|
| Tool catalog | Static YAML config | Dynamic `InMemoryRegistry`, populated at runtime via management API |
| Namespace isolation | None | Per-namespace filtering (`/finance/mcp` sees only `finance` tools) |
| Tool execution | L7 proxy forwarding to Pingora clusters (stateless profile only; unsupported in current profile) | MCP-to-MCP forwarding via `rmcp` crate |
| Forward discovery | None | Auto-discovers tools from upstream MCP servers on registration |
| Resources & Prompts | Not supported | Full `resources/*` and `prompts/*` filter support |

**What we reuse from praxis-ai:**

- `McpFilter` (classifier) — JSON-RPC parsing, metadata extraction (`mcp.method`, `mcp.name`)
- Praxis builtins — `cors`, `static_response`, `router`, `load_balancer`

**What is custom:**

- `wanaku_namespace` — path-based namespace extraction
- `wanaku_mcp_init` — handles `initialize` (capability negotiation), `ping`, and `notifications/initialized`
- `wanaku_tool_list` / `wanaku_tool_call` — registry-backed tool routing with MCP forwarding
- `wanaku_resource_list` / `wanaku_resource_read` — registry-backed resource handling
- `wanaku_prompt_list` / `wanaku_prompt_get` — registry-backed prompt handling
- Feature filters (`wanaku_well_known`, `wanaku_evaluator`) — registered by their respective feature crates, not by core filter registration

## The Registry

The registry is the source of truth for tools, resources, prompts, namespaces, and forwards. `InMemoryRegistry` in `infra/src/registry.rs` implements this in-memory data structure.

**Key design:**

- **Clone-safe:** Uses `Arc<DashMap>` internally, so cloning is cheap (bumps refcount)
- **Shared state:** Injected into filter pipeline and management API via `PipelineExtension`
- **Trait-based:** Implements `ToolRegistry`, `ResourceRegistry`, `PromptRegistry`, `NamespaceRegistry`, `ForwardRegistry`

**Data structures:**

This listing shows the main maps. Refer to `infra/src/registry.rs` for the complete struct.

```rust
pub struct InMemoryRegistry {
    agents: Arc<DashMap<String, RegisteredAgent>>,
    tools: Arc<DashMap<String, ToolEntry>>,
    resources: Arc<DashMap<String, ResourceEntry>>,
    prompts: Arc<DashMap<String, PromptEntry>>,
    forwards: Arc<DashMap<String, ForwardEntry>>,
    namespaces: Arc<DashMap<String, NamespaceEntry>>,
    persistence: Option<Arc<dyn PersistenceBackend>>,
    inject_request_id: Arc<AtomicBool>,
}
```

**Namespace isolation:**

Tools, resources, and prompts all have a `namespace` field (defaults to `"default"`). When a filter queries the registry, it filters by namespace:

```rust
let tools = registry.list_tools()?;
let tools_in_namespace: Vec<_> = tools
    .into_iter()
    .filter(|t| t.namespace == namespace)
    .collect();
```

This is how `/finance/mcp` only sees tools registered in the `"finance"` namespace.

**Persistence:**

The registry operates in memory. File persistence is enabled by default and writes snapshots to `$HOME/.wanaku/server/registry.json`. Set a different path when necessary:

```bash
export WANAKU_PERSIST_BACKEND=file
export WANAKU_PERSIST_PATH=/data/registry
```

On startup, the server loads `registry.json` from `WANAKU_PERSIST_PATH`. After each registry change, the server queues a snapshot and writes it in the background. On an orderly shutdown, it waits up to 10 seconds for the last write. Set `WANAKU_PERSIST_BACKEND=none` to disable persistence. File persistence supports one writer; it is not a shared production database.

## The Audit Store

Audit code has two owners: a contract in `wanaku-types` and a runtime in `wanaku-infra`. This split keeps storage changes inside the runtime, so audit producers do not change when the store changes.

**Contract ownership (`types/src/audit.rs`):**

- The audit DTOs: `AuditEvent`, `AuditQuery`, `AuditPage`, and `AuditHealth`. These types define the audit wire schema.
- The `AuditStore` trait: `record`, `query`, `get`, and `health`.
- The `AuditPersistence` and `AuditFailureObserver` traits.
- `AuditHandle`: a cloneable handle around `Arc<dyn AuditStore>`.

**Runtime ownership (`infra/src/audit.rs`):**

- `InMemoryAuditStore`: retention, native and external capacity, redaction, and query matching.
- The persistence worker. The last store owner flushes and joins the worker when it drops.
- Checked semantic admission: `InMemoryAuditStore::accept_semantic`. This method deduplicates external events and enforces the external capacity.

**Rules:**

- The audit feature (`features/audit/`) creates the store. The feature puts an `AuditHandle` into the request extensions.
- Audit producers get the handle with `ctx.extensions.get::<AuditHandle>()`. The action-policy, evaluator, simulation, and credential audit producers use only the handle.
- Producers do not use `InMemoryAuditStore` in production code.
- `AuditHandle` does not give access to semantic ingestion. Only the audit feature calls `accept_semantic` for `POST /api/v1/audit/semantic`. The ingestion route does not use `record`.
- Tests can wrap a recording fake in `AuditHandle`. A recording fake does not start a persistence worker.

## Tool Routing

All tool execution in Wanaku happens via MCP forwarding. When a tool call arrives (`tools/call`), the tool_call filter:

1. Looks up the tool in the registry by name
2. Resolves the upstream address from the tool `forwardId` provenance
3. Maps allowed client headers to arguments, then injects brokered credentials into the outbound headers
4. Calls `McpInvoker::call_tool(address, name, arguments, headers)` to forward the request to the upstream MCP server
5. Upstream returns MCP `CallToolResult`
6. Filter wraps it in JSON-RPC and returns

Tools have `type: "mcp-forward"` and a `forwardId` that identifies the owning forward. This is the only execution model — there is no built-in gRPC or local tool execution.

**Invocation adapter:**

The `tools/call`, `resources/read`, and `prompts/get` filters call upstream MCP servers only through the `McpInvoker` trait (`infra/src/mcp_client.rs`). The trait has three operations: `call_tool`, `read_resource`, and `get_prompt`. It uses only `serde_json` and `http` types. The rmcp types stay in the adapter.

- The server inserts the production adapter, `RmcpInvoker`, into the request extensions as `Arc<dyn McpInvoker>` (`server/src/pipelines.rs`).
- `RmcpInvoker` calls the existing `mcp_client` functions. The wiremock tests in `infra/src/mcp_client.rs` cover these functions.
- If the request extensions do not contain an adapter, the filter returns a JSON-RPC internal error. The filter does not resolve credentials in this case.
- Filter tests insert `RecordingInvoker` from `filters/src/invoker.rs`. This fake records each call and needs no network.

Discovery, listing, LLM, and other outbound HTTP calls do not use this trait.

**Forward discovery:**

When you POST to `/api/v1/forwards`:

```json
{
  "name": "upstream-mcp",
  "address": "http://upstream-server:8080/mcp"
}
```

The management API:

1. Registers the forward
2. Calls `mcp_client::list_tools(address)` to discover tools
3. Auto-registers each tool with `type: "mcp-forward"` and a `forwardId` that identifies the forward

Now when an LLM calls one of those tools, Wanaku resolves the owning forward from `forwardId` and forwards the request to its address.

**Refreshing:**

To re-discover tools after upstream changes:

```bash
wanaku forwards refresh --name upstream-mcp --no-auth
```

This removes all tools previously discovered from that forward and re-queries the upstream server.

## The Feature System

Features are self-contained modules that extend Wanaku with new capabilities. They live in `features/<name>/` and implement the `Feature` trait from `types/src/feature.rs`:

```rust
#[async_trait::async_trait]
pub trait Feature: Send + Sync {
    fn name(&self) -> &'static str;
    fn register_filters(&self, registry: &mut FilterRegistry);
    fn pipeline_extensions(&self) -> Vec<Box<dyn PipelineExtension>>;
    async fn handle_route(&self, ctx: &HttpContext<'_>) -> Option<Response<Vec<u8>>>;
    fn load_yaml_config(&self, root: &serde_yaml::Value);
    fn load_env_config(&self);
}
```

**Lifecycle:**

1. `App::build` in `server/src/app.rs` creates the feature instances.
2. The server calls `load_yaml_config` and `load_env_config`.
3. The server calls `register_filters` to add filters to the pipeline.
4. The server calls `pipeline_extensions` to get shared state, such as an LLM client.
5. For each request, the server calls `handle_route` until a feature owns the route.

**Registered features** (in `server/src/app.rs`):

1. **Audit** (`features/audit/`) — governance audit store, audit routes, and semantic audit ingestion
2. **Metrics** (`features/metrics/`) — exposes an in-memory metrics snapshot
3. **Intercept** (`features/intercept/`) — records LLM request/response interactions on the inference proxy (port 8083) for conversation history
4. **MCP Metadata** (`features/mcp-metadata/`) — RFC 9728 OAuth Protected Resource Metadata and well-known endpoints
5. **Action policy** (`features/action-policy/`) — deterministic allow and deny rules for MCP actions
6. **Evaluator** (`features/evaluator/`) — WASM-based LLM evaluation engine for trigger→evaluate→act pipelines
7. **Policy simulation** (`features/simulation/`) — validates candidates and compares decisions without activation
8. **Plugins** (`features/plugins/`) — loads external UI plugins from a filesystem directory

See [Build a feature crate](../extend/build-a-feature.md) for how to create your own.

### Application Assembly

`App::build` in `server/src/app.rs` assembles the production runtime graph. The binary and the server tests call the same function, so both use the same registry, policy, evaluator, and audit wiring.

The function receives two inputs:

- `AppSettings`: the resolved startup settings. These are the Praxis pipeline configuration, the parsed bootstrap YAML, the governance posture, and the plugin directory.
- `AppResources`: the runtime resources. These are the optional persistence backends (`AppPersistence`) and the shared credential broker.

The function creates the features, loads the bootstrap configuration, validates the features, registers the filters, and resolves the listener pipelines. It returns an `App` value. `App` exposes the shared registry, the audit store, the listener pipelines, and the management service. `WanakuManagementService::dispatch` runs one management request without a socket.

`server/src/main.rs` is the startup adapter. It parses the command line, reads the environment, constructs the persistence backends and the credential broker, and calls `App::build`. Then it registers the listeners and the background services, and it starts the server. It stops the process when assembly fails.

**Composition tests:** `server/tests/app_assembly.rs` builds an `App` from the embedded pipeline configuration with in-memory persistence. The tests send management requests through `app.management.dispatch` and run MCP requests through the `mcp` listener pipeline in process. To run a request, the tests pin the pipeline on a `PingoraRequestCtx`, build the filter context, and call `execute_http_request_body` with the complete body. This is the same sequence that the protocol layer uses for a `StreamBuffer` request. The tests open no ports, change no environment variables, and contact no external services. Each `App` has its own state, so the tests can run at the same time. Run them with `cargo test -p wanaku-server --test app_assembly`.

These tests do not replace the Playwright suite or the external `wanaku-tests` suite.

### A2A proxy

A managed pipeline proxies A2A JSON-RPC requests to registered upstream agents. The registry selector finds an agent by namespace and name and selects its live Praxis upstream. The Praxis `a2a` filter extracts protocol metadata. The `wanaku_a2a` filter limits the supported operations. The shared `wanaku_action_policy` filter applies governance posture, policy decisions, and audit records before dispatch.

Agent entries use shared registry persistence. The management API and the **A2A agents** page support registration, editing, and deletion. Explicit agent paths preserve namespace and agent routing boundaries. The managed pipeline does not use the global Praxis task-ID routing store. A separate custom pipeline can still use static upstreams and Praxis task-owner routing.

The upstream agent owns task execution and state. Wanaku does not translate A2A requests into MCP calls. The initial implementation does not support streaming, push notifications, trusted actor identity, or approval workflows. See [A2A Proxy](../connect/a2a.md) for configuration and follow-up work.

## Management API

The management API runs on port 8080 and uses Pingora's `ServeHttp` trait (not axum).

**Request flow:**

1. Pingora calls `handle_request` in `server/src/management/mod.rs`
2. Dispatcher tries core routes (tools, resources, prompts, namespaces, forwards)
3. If no match, iterates over registered features and calls `feature.handle_route()`
4. If still no match, returns 404

**Response wrapper:**

Management JSON responses generally use this format:

```text
{"data": <payload>, "error": null}  # success
{"data": null, "error": "message"}  # error
```

This matches the classic Wanaku API format for CLI compatibility.

**Route pattern:**

Core routes use a guard pattern defined in `routes.rs`:

```rust
pub(super) enum ToolRoute {
    List,
    GetByName(String),
    Delete(String),
    SetEnabled(String, bool),
    NotFound,
}

pub(super) fn resolve_tool_route(method: &str, path: &str) -> ToolRoute {
    let Some(name) = collection_child(path, "/api/v1/tools") else {
        return ToolRoute::NotFound;
    };
    match (method, name) {
        ("GET", None) => ToolRoute::List,
        ("GET", Some(n)) => ToolRoute::GetByName(n.to_owned()),
        ("DELETE", Some(n)) => ToolRoute::Delete(n.to_owned()),
        ("PUT", Some(n)) => match resolve_set_enabled(n) {
            Some((name, enabled)) => ToolRoute::SetEnabled(name, enabled),
            None => ToolRoute::NotFound,
        },
        _ => ToolRoute::NotFound,
    }
}
```

`collection_child` matches the collection path on a segment boundary. It returns `Some(None)` for the collection path, with or without a trailing slash. It returns `Some(Some(child))` for a path below the collection. It returns `None` for all other paths. A sibling path that shares the prefix, for example `/api/v1/tools-export`, does not match a core route. The dispatcher then sends that path to the features.

Feature routes follow the same pattern but live entirely inside the feature crate.

## Admin UI

The admin UI is a React 19 + TypeScript app built with Vite and embedded into the server binary via `rust_embed`. The server serves the UI under `/admin/` and redirects `/` to `/admin/`. When you visit `http://localhost:8080/admin/`, the server serves static files from the embedded `ui/admin/dist` directory. A headless build (`--no-default-features`) has no admin UI.

**API integration:**

The UI uses Orval to generate a TypeScript client from the committed OpenAPI spec (`yarn run generate-api`). All API calls go through `src/api/wanaku-router-api.ts` and use the `customFetch` wrapper for error handling. `customFetch` removes the server `{"data": ..., "error": ...}` envelope.

**Data access pattern:**

```typescript
const result = await listTools();  // Orval-generated function
const tools = result.data;         // customFetch already removed the server envelope
```

See [Contributing: Admin UI](../contributing/admin-ui.md) for development details.

## Deployment Patterns

### Standalone

Run Wanaku as a standalone proxy. Tools are registered via the management API or `wanaku.yaml`, and actions are forwarded to upstream servers.

**Pros:** Simple, no dependencies
**Cons:** No persistence beyond file snapshots

### Kubernetes

Deploy Wanaku as a `Deployment` with:

- **Service:** ClusterIP for the MCP endpoint (port 8081). Expose it outside the cluster only through an authenticating proxy, such as oauth2-proxy.
- **Service:** ClusterIP for the management API (port 8080). The management API has no built-in authentication. Do not expose it with a LoadBalancer Service.
- **ConfigMap:** `wanaku.yaml` bootstrap config
- **Secret:** credential values, passed to the container as environment variables. `wanaku.yaml` refers to them with `env:VARIABLE_NAME` (in `llm_connections[].api_key`, `typesafe_system_one_connections[].api_key`, and `bindings[].secret_refs`). Do not put secret values in `wanaku.yaml`. See [Configuration](../operate/kubernetes.md)

Mount a `PersistentVolume` on `WANAKU_PERSIST_PATH` (`/data/registry` in the image) for registry persistence across restarts. Refer to [Configuration](../operate/kubernetes.md) for the complete manifests.

All listeners bind to all interfaces by default: the management API (8080), MCP (8081), the inference proxy (8083), and A2A (8084).

## Performance Characteristics

**Throughput:**

Wanaku uses Pingora's async worker pool. Each worker handles requests concurrently. Throughput scales linearly with worker count (default: CPU core count).

**Latency breakdown:**

| Component | Typical Latency | Notes |
|---|---|---|
| Filter pipeline | ~1ms | CORS + MCP parse + namespace + tool lookup |
| MCP forward | ~20ms | HTTP roundtrip to upstream MCP server |

**Memory:**

Registry is in-memory. Each tool/resource/prompt is ~1KB. A deployment with 10,000 tools uses ~10MB RAM for the registry.

## Security Model

**Authentication:**

Wanaku delegates authentication to [oauth2-proxy](https://github.com/oauth2-proxy/oauth2-proxy), an external reverse proxy that sits in front of the MCP and management API ports. Wanaku itself contains zero authentication code.

**oauth2-proxy sidecar pattern:**

Two oauth2-proxy instances with a shared cookie provide SSO across both endpoints:

- **oauth2-proxy-mgmt** (port 4180 → 8080) — protects admin UI and REST API
- **oauth2-proxy-mcp** (port 4181 → 8081) — protects MCP endpoints

The shipped configuration accepts each authenticated user of the realm. It does not require a role. To restrict the management proxy to a role, set `OAUTH2_PROXY_ALLOWED_ROLES` (commented out in `deploy/auth/oauth2-proxy-mgmt.env`). See [Authentication](../operate/authentication.md).

Users authenticate via oauth2-proxy's browser-based login flow (PKCE). CLI clients obtain tokens from Keycloak and pass them as `Authorization: Bearer <token>` headers — oauth2-proxy validates them before proxying to Wanaku.

**Wanaku-side metadata:**

The `features/mcp-metadata/` crate exposes RFC 9728 OAuth Protected Resource Metadata at `/.well-known/oauth-protected-resource/{namespace}/mcp`. When `WANAKU_AUTH_ISSUER` is set, the `authorization_servers` list of this read-only endpoint contains the Wanaku host, which proxies the OAuth routes to the issuer. When it is unset, the list is empty.

When auth is disabled:

- Run Wanaku standalone on ports 8081/8080 without oauth2-proxy
- **No authentication** on either endpoint

**CORS:**

CORS is enabled by default via the `cors` filter (allows all origins). To restrict the origin, set `WANAKU_CORS_ORIGIN`:

```bash
export WANAKU_CORS_ORIGIN=https://app.example.com
```

The variable applies to the embedded pipeline configuration. A custom `--pipeline-config` file must set `allow_origins` in its own `cors` filters. See [Configuration](../reference/pipeline-yaml.md#filter-configuration).

## What's Not Here (Yet)

- **Persistence beyond file snapshots** — no PostgreSQL/Redis integration
- **Multi-tenancy** — namespaces provide isolation, but no user/tenant association
- **Rate limiting** — no throttling on MCP or management API
- **External metrics export** — Wanaku collects in-memory metrics, but it does not provide a Prometheus exporter or distributed tracing integration
- **Clustering** — single-node only, no distributed registry

These are all solvable (implement traits, add filters), but they're not in scope for the initial release.

## Next steps

- [Configuration](../reference/configuration.md) — all env vars and YAML options
- [Action Policies](../govern/action-policies.md) — schema, matching, decisions, and revisions
- [Feature crates](../extend/feature-crates.md) — enable evaluators, create custom features
- [Management API](../reference/management-api.md) — REST API reference
- [Contributing: Admin UI](../contributing/admin-ui.md) — customize the embedded UI
