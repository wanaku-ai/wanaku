# Architecture

Wanaku is a governed action proxy for AI agents, built in Rust on the Praxis proxy framework. It sits between agents and the systems they act on, intercepting every tool call, agent-to-agent message, and inference request. Policy, identity, data controls, and audit are enforced in the proxy — agents never touch backend systems directly.

Under the hood, Wanaku is an HTTP filter pipeline. Actions flow through a chain of filters, each responsible for a specific governance concern — identity, policy evaluation, namespace isolation, tool dispatch. Unlike a gateway that just passes traffic, the proxy does the work: it resolves tools, forwards actions to backends, and returns results to the agent.

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      LLM / AI Agent                         │
└────────────────┬────────────────────────────────────────────┘
                 │ MCP (JSON-RPC over HTTP)
                 ▼
┌─────────────────────────────────────────────────────────────┐
│         Wanaku — Governed Action Proxy (Port 8081)          │
│  ┌────────────────────────────────────────────────────────┐ │
│  │           Governance Pipeline (Praxis)                 │ │
│  │  CORS → MCP Parse → Namespace → Evaluator →            │ │
│  │  Tool List/Call → Resource → Prompt → Static Response  │ │
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
2. Governance pipeline intercepts: identity, policy, namespace isolation
3. Filters query the in-memory registry for tools/resources/prompts
4. For tool calls: the proxy forwards the action to the backend system registered as the tool's forward address — the agent never reaches the backend directly
5. Response flows back through filters, wrapped in JSON-RPC

## The Filter Pipeline

Filters are the heart of Praxis. Each request passes through a chain of filters defined in `server/src/default.yaml`:

```yaml
filter_chains:
  - name: mcp_router
    filters:
      - filter: cors                    # Add CORS headers
      - filter: mcp                     # Parse JSON-RPC, set metadata
        on_invalid: continue
      - filter: wanaku_namespace        # Extract namespace from path
      - filter: wanaku_well_known       # RFC 9728 OAuth metadata (feature)
      - filter: wanaku_mcp_init         # Initialize MCP context
      - filter: wanaku_evaluator        # Evaluator engine (feature)
      - filter: wanaku_tool_list        # Handle tools/list
      - filter: wanaku_tool_call        # Handle tools/call
      - filter: wanaku_resource_list    # Handle resources/list
      - filter: wanaku_resource_read    # Handle resources/read
      - filter: wanaku_prompt_list      # Handle prompts/list
      - filter: wanaku_prompt_get       # Handle prompts/get
      - filter: static_response         # Catch-all (404)
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

The registry is the source of truth for tools, resources, prompts, namespaces, and forwards. `InMemoryRegistry` in `apis/src/registry.rs` implements this in-memory data structure.

**Key design:**

- **Clone-safe:** Uses `Arc<DashMap>` internally, so cloning is cheap (bumps refcount)
- **Shared state:** Injected into filter pipeline and management API via `PipelineExtension`
- **Trait-based:** Implements `ToolRegistry`, `ResourceRegistry`, `PromptRegistry`, `NamespaceRegistry`, `ForwardRegistry`

**Data structures:**

```rust
pub struct InMemoryRegistry {
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

On startup, the server loads `registry.json` from `WANAKU_PERSIST_PATH`. On an orderly shutdown, it writes the current registry. Set `WANAKU_PERSIST_BACKEND=none` to disable persistence. File persistence supports one writer; it is not a shared production database.

## Tool Routing

All tool execution in Wanaku happens via MCP forwarding. When a tool call arrives (`tools/call`), the tool_call filter:

1. Looks up the tool in the registry by name
2. Calls `mcp_client::call_tool(tool.uri, name, arguments)` to forward the request to the upstream MCP server
3. Uses the `rmcp` crate to send an HTTP POST
4. Upstream returns MCP `CallToolResult`
5. Filter wraps it in JSON-RPC and returns

Tools have `type: "mcp-forward"` and a `uri` pointing at the upstream MCP server. This is the only execution model — there is no built-in gRPC or local tool execution.

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
3. Auto-registers each tool with `type: "mcp-forward"` and `uri: <forward.address>`

Now when an LLM calls one of those tools, Wanaku forwards the request to the upstream server transparently.

**Refreshing:**

To re-discover tools after upstream changes:

```bash
curl -X POST http://localhost:8080/api/v1/forwards/upstream-mcp/refreshes
```

This removes all tools previously discovered from that forward and re-queries the upstream server.

## The Feature System

Features are self-contained modules that extend Wanaku with new capabilities. They live in `features/<name>/` and implement the `Feature` trait from `apis/src/feature.rs`:

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

1. The server creates feature instances in `main.rs`.
2. The server calls `load_yaml_config` and `load_env_config`.
3. The server calls `register_filters` to add filters to the pipeline.
4. The server calls `pipeline_extensions` to get shared state, such as an LLM client.
5. For each request, the server calls `handle_route` until a feature owns the route.

**Registered features** (in `main.rs`):

1. **Metrics** (`features/metrics/`) — exposes an in-memory metrics snapshot
2. **Intercept** (`features/intercept/`) — records request/response interactions for conversation history
3. **MCP Metadata** (`features/mcp-metadata/`) — RFC 9728 OAuth Protected Resource Metadata and well-known endpoints
4. **Evaluator** (`features/evaluator/`) — WASM-based LLM evaluation engine for trigger→evaluate→act pipelines
5. **Plugins** (`features/plugins/`) — loads external UI plugins from a filesystem directory

See [Features](./features.md) for how to create your own.

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
    Create,
    Delete(String),
    NotFound,
}

pub(super) fn resolve_tool_route(method: &str, path: &str) -> ToolRoute {
    let suffix = match path.strip_prefix("/api/v1/tools") {
        Some(s) => s,
        None => return ToolRoute::NotFound,
    };
    let name = suffix.strip_prefix('/').filter(|s| !s.is_empty());
    match (method, name) {
        ("GET", None) => ToolRoute::List,
        ("GET", Some(n)) => ToolRoute::GetByName(n.to_owned()),
        ("POST", None | Some("payloads")) => ToolRoute::Create,
        ("DELETE", Some(n)) => ToolRoute::Delete(n.to_owned()),
        _ => ToolRoute::NotFound,
    }
}
```

Feature routes follow the same pattern but live entirely inside the feature crate.

## Admin UI

The admin UI is a React 19 + TypeScript app built with Vite and embedded into the server binary via `rust_embed`. When you visit `http://localhost:8080`, the server serves static files from the embedded `ui/admin/dist` directory.

**API integration:**

The UI uses Orval to generate a TypeScript client from the OpenAPI spec (not yet implemented, currently hand-coded). All API calls go through `src/api/wanaku-router-api.ts` and use the `customFetch` wrapper for error handling.

**Data access pattern:**

```typescript
const result = await getTools();  // Orval-generated function
const tools = result.data.data;   // Unwrap: result.data (fetch wrapper) -> .data (server wrapper)
```

See [Contributing: Admin UI](./contributing-admin-ui.md) for development details.

## Deployment Patterns

### Standalone

Run Wanaku as a standalone proxy. Tools are registered via the management API or `wanaku.yaml`, and actions are forwarded to upstream servers.

**Pros:** Simple, no dependencies
**Cons:** No persistence beyond file snapshots

### Kubernetes

Deploy Wanaku as a `Deployment` with:

- **Service:** ClusterIP for MCP endpoint (port 8081)
- **Service:** LoadBalancer for management API (port 8080)
- **ConfigMap:** `wanaku.yaml` bootstrap config
- **Secret:** evaluator LLM connection credentials, mounted into `wanaku.yaml`

Mount `WANAKU_PERSIST_PATH` to a `PersistentVolume` for registry persistence across restarts.

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

- **oauth2-proxy-mcp** (port 4180 → 8081) — protects MCP endpoints, requires `mcp-user` role
- **oauth2-proxy-mgmt** (port 4181 → 8080) — protects admin UI and REST API, requires `admin` role

Users authenticate via oauth2-proxy's browser-based login flow (PKCE). CLI clients obtain tokens from Keycloak and pass them as `Authorization: Bearer <token>` headers — oauth2-proxy validates them before proxying to Wanaku.

**Wanaku-side metadata:**

The `features/mcp-metadata/` crate exposes RFC 9728 OAuth Protected Resource Metadata at `/.well-known/oauth-protected-resource/{namespace}/mcp`. This read-only endpoint returns the OIDC issuer URL configured through `WANAKU_AUTH_ISSUER`.

When auth is disabled:

- Run Wanaku standalone on ports 8081/8080 without oauth2-proxy
- **No authentication** on either endpoint

**CORS:**

CORS is enabled by default via the `cors` filter (allows all origins). Restrict origins in `server/src/default.yaml`:

```yaml
- filter: cors
  allow_origins: ["https://app.example.com"]
```

## What's Not Here (Yet)

- **Persistence beyond file snapshots** — no PostgreSQL/Redis integration
- **Multi-tenancy** — namespaces provide isolation, but no user/tenant association
- **Rate limiting** — no throttling on MCP or management API
- **External metrics export** — Wanaku collects in-memory metrics, but it does not provide a Prometheus exporter or distributed tracing integration
- **Clustering** — single-node only, no distributed registry

These are all solvable (implement traits, add filters), but they're not in scope for the initial release.

## Related Docs

- [Configuration](./configuration.md) — all env vars and YAML options
- [Features](./features.md) — enable evaluators, create custom features
- [Management API](./management-api.md) — REST API reference
- [Contributing: Admin UI](./contributing-admin-ui.md) — customize the embedded UI
