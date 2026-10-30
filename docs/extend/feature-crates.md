# Features

Features are self-contained Rust crates that extend Wanaku. A feature can register pipeline filters, expose management API routes, and manage state.

Think of features as plugins. The core server provides the infrastructure (filter pipeline, registry, management API), and features build on top of it.

## Built-in Features

Wanaku includes these built-in features. `build_features` in `server/src/app.rs` registers eight features: audit, metrics, intercept, MCP metadata, action policy, evaluator, policy simulation, and plugins.

### Audit Feature (`features/audit/`)

Creates the governance audit store. The action-policy, evaluator, simulation, and credential brokerage components write their decisions to this store.

**Management API routes:** `GET /api/v1/audit/events`, `GET /api/v1/audit/events/{id}`, `GET /api/v1/audit/schema`, `GET /api/v1/audit/health`, and `POST /api/v1/audit/semantic` (only when `WANAKU_AUDIT_INGESTION_ENABLED=true`).

**Configuration:** the top-level `audit` section in `wanaku.yaml`. See [Governance Audit Trail](../govern/audit-trail.md).

### Action Policy Feature (`features/action-policy/`)

Applies deterministic allow and deny rules to MCP and A2A actions before the evaluator runs.

**Filters:** `wanaku_action_policy` and `wanaku_a2a`

**Management API prefix:** `/api/v1/action-policies`

**Configuration:** the top-level `action_policy` section in `wanaku.yaml`. See [Action Policies](../govern/action-policies.md).

### Metrics Feature (`features/metrics/`)

Collects in-memory filter, evaluator, LLM, and WASM metrics.

**Management API route:** `GET /api/v1/metrics`

### Intercept Feature (`features/intercept/`)

Records LLM request/response interactions in an in-memory store. Other features — especially the evaluator engine — use this history to provide conversation context to LLM operations.

**Filter:** `wanaku_intercept`. The filter runs only in the inference proxy pipeline (port 8083). It does not record MCP traffic on port 8081. It reads OpenAI-style chat requests with a `messages` array. It can insert a system message into the request before Wanaku forwards it.

**Configuration:**

| Variable | Default | Purpose |
|---|---|---|
| `WANAKU_INTERACTION_CAPACITY` | `1000` | Maximum interactions kept in memory |

### MCP Metadata Feature (`features/mcp-metadata/`)

Exposes RFC 9728 OAuth Protected Resource Metadata to advertise authentication requirements to MCP clients. This is a read-only metadata endpoint — actual authentication is handled by oauth2-proxy as an external sidecar.

**How it works:**

1. An MCP client queries `GET /.well-known/oauth-protected-resource/{namespace}/mcp` on the MCP listener (port 8081).
2. Wanaku returns the protected resource metadata. When `WANAKU_AUTH_ISSUER` is set, `authorization_servers` contains the Wanaku host itself.
3. The MCP client reads `/.well-known/openid-configuration` or `/.well-known/oauth-authorization-server` from the Wanaku host. Wanaku serves this discovery document. Its endpoints point to Wanaku.
4. Wanaku redirects `/authorize` and `/register` to the issuer, and proxies `/token` to the issuer.

**Configuration:**

Set the OIDC issuer URL:

```bash
export WANAKU_AUTH_ISSUER=http://localhost:8543/realms/wanaku
```

If `WANAKU_AUTH_ISSUER` is unset, the metadata route still returns 200, with an empty `authorization_servers` list. The discovery documents, `/authorize`, `/token`, and `/register` are not served.

**Example response** (issuer set):

```json
{
  "resource": "http://localhost:8081/mcp",
  "authorization_servers": ["http://localhost:8081"],
  "bearer_methods_supported": ["header"]
}
```

**Authentication architecture:**

Wanaku does not validate tokens or enforce authentication. [oauth2-proxy](https://github.com/oauth2-proxy/oauth2-proxy) provides these controls. It runs as a reverse proxy in front of the MCP port (8081) and management API port (8080).

For deployment details, see `deploy/auth/README.md` and [Configuration](../operate/authentication.md).

### Evaluator Feature (`features/evaluator/`)

A WASM-based evaluation engine that lets you build trigger→evaluate→act pipelines. When an MCP request matches a trigger, the engine calls an LLM to classify or filter, then runs a WebAssembly action script that can block requests, filter tool lists, or set metadata.

**Filter:** `wanaku_evaluator`

**Management API routes:**

- `GET /api/v1/evaluators` — list configured evaluators
- `PUT /api/v1/evaluators` — hot-reload evaluator configuration
- `GET /api/v1/evaluators/status` — runtime readiness, revision, and effective posture
- `GET /api/v1/evaluators/llm-connections` — configured LLM connection names
- `GET /api/v1/evaluators/revisions` and related routes — revision history and rollback (see [Management API](../reference/management-api.md#evaluators))
- `GET /api/v1/evaluators/namespaces` — list namespace-to-conversation bindings
- `PUT /api/v1/evaluators/namespaces/{namespace}` — bind a namespace to a conversation ID
- `DELETE /api/v1/evaluators/namespaces/{namespace}` — unbind

See the [Evaluator Engine](../govern/evaluators.md) guide for configuration details and WASM action script development.

### Policy Simulation Feature (`features/simulation/`)

Validates transient policy candidates and evaluates synthetic MCP actions. Asynchronous replay compares the active policy with a candidate. The feature reuses production policy parsing and matching. It records proposed WASM actions with read-only host capabilities.

**Management API prefix:** `/api/v1/policy-simulations`

**Configuration:** The top-level `simulation` section in `wanaku.yaml` sets resource and retention limits.

See [Policy Simulation](../govern/policy-simulation.md) for the report schema and CI procedure.

### Plugins Feature (`features/plugins/`)

Loads external UI plugins from a filesystem directory. Plugins are ES modules that can add navigation entries, pages, and backend service integrations to the admin UI at runtime.

**Configuration:**

The server reads plugins from `<WANAKU_PERSIST_PATH>/plugins` (for example, `~/.wanaku/server/plugins`). The server creates this directory when you install the first plugin.

To use a different directory, start the server with `--plugins-path`:

```bash
cargo run -- --plugins-path /path/to/plugins
```

When `WANAKU_PERSIST_BACKEND=none`, the plugins feature is disabled. Set `--plugins-path` to enable it.

See the [Plugin Development Guide](./plugin-development-guide.md) for details.

### A2A proxy support

The action-policy feature registers the `wanaku_a2a` filter. The managed A2A pipeline selects a registered agent, extracts protocol metadata with the Praxis `a2a` filter, and applies the shared `wanaku_action_policy` filter. Operators manage agents through the management API and the **A2A agents** page. See [A2A Proxy](../connect/a2a.md) for configuration and supported operations.

## Creating a Custom Feature

The following procedure creates a feature that counts tool calls and exposes the count through the management API.

### 1. Create the Crate

```bash
cd features
mkdir -p tool-stats/src
cd tool-stats
```

Create `Cargo.toml`:

```toml
[package]
name = "wanaku-feature-tool-stats"
version = "0.1.0"
edition = "2024"

[dependencies]
wanaku-types = { workspace = true }
wanaku-infra = { workspace = true }  # required by wanaku-filters' body_filter_boilerplate! macro
wanaku-filters = { workspace = true }
praxis-filter = { workspace = true }
async-trait = { workspace = true }
http = { workspace = true }
serde_json = { workspace = true }
serde_yaml = { workspace = true }
bytes = { workspace = true }
tracing = { workspace = true }

[lints]
workspace = true
```

### 2. Implement the Feature Trait

Create `src/lib.rs`:

```rust
use wanaku_types::feature::{Feature, HttpContext};
use praxis_filter::{FilterRegistry, PipelineExtension, RequestExtensions};
use http::Response;
use std::sync::Arc;
use std::sync::atomic::{AtomicU64, Ordering};

pub struct ToolStatsFeature {
    counter: Arc<AtomicU64>,
}

struct ToolStatsExtension {
    counter: Arc<AtomicU64>,
}

impl PipelineExtension for ToolStatsExtension {
    fn prepare(&self, extensions: &mut RequestExtensions) {
        extensions.insert(self.counter.clone());
    }
}

impl ToolStatsFeature {
    pub fn new() -> Self {
        Self {
            counter: Arc::new(AtomicU64::new(0)),
        }
    }
}

impl Default for ToolStatsFeature {
    fn default() -> Self {
        Self::new()
    }
}

#[async_trait::async_trait]
impl Feature for ToolStatsFeature {
    fn name(&self) -> &'static str {
        "tool-stats"
    }

    fn register_filters(&self, registry: &mut FilterRegistry) {
        praxis_filter::register_filters!(
            @register registry,
            http "wanaku_tool_stats" => ToolStatsFilter::from_config
        );
    }

    fn pipeline_extensions(&self) -> Vec<Box<dyn PipelineExtension>> {
        vec![Box::new(ToolStatsExtension {
            counter: self.counter.clone(),
        })]
    }

    async fn handle_route(&self, ctx: &HttpContext<'_>) -> Option<Response<Vec<u8>>> {
        if ctx.method == "GET" && ctx.path == "/api/v1/stats/tools" {
            let count = self.counter.load(Ordering::Relaxed);
            let body = serde_json::json!({"tool_calls": count});
            Some(wanaku_types::http_response::json_ok(&body))
        } else {
            None
        }
    }

    fn load_yaml_config(&self, _root: &serde_yaml::Value) {}

    fn load_env_config(&self) {}
}
```

### 3. Implement the Filter

Add to `src/lib.rs`:

```rust
use wanaku_filters::body_filter_boilerplate;
use praxis_filter::{HttpFilterContext, FilterAction, FilterError};
use bytes::Bytes;

body_filter_boilerplate!(ToolStatsFilter, "wanaku_tool_stats");

impl ToolStatsFilter {
    async fn handle_body(
        &self,
        ctx: &mut HttpFilterContext<'_>,
        _body: &mut Option<Bytes>,
    ) -> Result<FilterAction, FilterError> {
        if ctx.get_metadata("mcp.method") != Some("tools/call") {
            return Ok(FilterAction::Continue);
        }
        let Some(counter) = ctx.extensions.get::<Arc<AtomicU64>>() else {
            return Ok(FilterAction::Continue);
        };
        counter.fetch_add(1, Ordering::Relaxed);
        tracing::info!("Tool call count: {}", counter.load(Ordering::Relaxed));
        Ok(FilterAction::Continue)
    }
}
```

### 4. Wire It Into the Server

Add to workspace `Cargo.toml`:

```toml
[workspace]
members = [
    # ... existing members ...
    "features/tool-stats",
]

[workspace.dependencies]
wanaku-feature-tool-stats = { path = "features/tool-stats" }
```

Add to `server/Cargo.toml`:

```toml
[dependencies]
wanaku-feature-tool-stats = { workspace = true }
```

Add the crate to the cache-build stage of `Containerfile`. Otherwise, `cargo build-image` fails. The stage has three lists. Add one entry to each list:

```dockerfile
COPY features/tool-stats/Cargo.toml features/tool-stats/Cargo.toml
# In the RUN mkdir -p list:
    features/tool-stats/src
# In the stub list:
    && echo '//! stub' > features/tool-stats/src/lib.rs \
```

Add the feature to the vector returned by `build_features` in `server/src/app.rs`:

```rust
let features: Vec<Box<dyn Feature>> = vec![
    // ... existing features ...
    Box::new(wanaku_feature_tool_stats::ToolStatsFeature::new()),
];
```

Add the filter to the `mcp_router` chain in `server/src/default.yaml`, after `wanaku_namespace` and before `wanaku_well_known`:

```yaml
      - filter: wanaku_namespace
      - filter: wanaku_tool_stats
      - filter: wanaku_well_known
```

The filter must run before `wanaku_action_policy` and `wanaku_evaluator`. A rejection in those filters skips all filters after them. With the default governance posture (`no_match: deny`), the action policy rejects a call that no rule allows. A filter after it does not see that call.

`default.yaml` is embedded with `include_str!`. Touch `server/src/lib.rs` after you edit it, so that `cargo build` picks up the change.

### 5. Test It

```bash
cargo build
cargo run
```

Call a tool. The tool does not need to exist. The filter counts each `tools/call` request:

```bash
curl -X POST http://localhost:8081/default/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc": "2.0", "method": "tools/call", "params": {"name": "echo", "arguments": {"message": "test"}}, "id": 1}'
```

Check stats:

```bash
curl http://localhost:8080/api/v1/stats/tools
```

Expected response:

```json
{"data": {"tool_calls": 1}, "error": null}
```

The tool call itself returns a JSON-RPC error. On a server without forwards and with the default posture, the action policy denies it with `-32003`. The count still increases, because the new filter runs first.

## Feature Patterns

### State Management

Features can share state between filters and management API handlers via `pipeline_extensions`. Implement `PipelineExtension` on a wrapper type:

```rust
struct CounterExtension {
    counter: Arc<AtomicU64>,
}

impl PipelineExtension for CounterExtension {
    fn prepare(&self, extensions: &mut RequestExtensions) {
        extensions.insert(self.counter.clone());
    }
}

fn pipeline_extensions(&self) -> Vec<Box<dyn PipelineExtension>> {
    vec![Box::new(CounterExtension { counter: self.counter.clone() })]
}
```

Filters retrieve the state via:

```rust
let counter = ctx.extensions.get::<Arc<AtomicU64>>();
```

### Hot-Reloadable Config

Use `HotSwap<T>` from `wanaku_infra::llm` for runtime-reconfigurable state:

```rust
use wanaku_infra::llm::HotSwap;

pub struct MyFeature {
    config: HotSwap<MyConfig>,
}

// In handle_route:
if ctx.path == "/api/v1/myfeature/config" && ctx.method == "PUT" {
    let body = ctx.body.unwrap_or("");
    let new_config: MyConfig = serde_json::from_str(body).ok()?;
    self.config.set(new_config);
}
```

Filters read the config via:

```rust
let config = feature_state.config.get();
```

### LLM Integration

Use `LlmClient` from `wanaku_infra::llm` for OpenAI-compatible LLM calls:

```rust
use wanaku_infra::llm::LlmClient;

// `new` returns `None` when the HTTP client cannot be built.
let Some(client) = LlmClient::new("http://localhost:11434/v1", "llama3.1:8b", "") else {
    return Err(MyError::LlmClient);
};
// `chat` returns `Result<String, LlmError>`.
let response = client.chat("You are a concise assistant.", user_prompt).await?;
```

`LlmClient` appends `/chat/completions` to the base URL. See `features/evaluator/src/engines/llm.rs` for a full example.

### Management API Response Helpers

Use the response helpers from `wanaku_types`:

```rust
use http::StatusCode;
use wanaku_types::http_response::{json_err, json_ok};

let response = json_ok(&serde_json::json!({"status": "ready"}));
let error = json_err(StatusCode::BAD_REQUEST, "invalid configuration");
```

## Feature Discovery

The server does not scan for features. List each feature explicitly in `build_features` in `server/src/app.rs`:

```rust
let features: Vec<Box<dyn Feature>> = vec![
    // ... existing features ...
    Box::new(MyFeature::new()),
];
```

The explicit list makes feature registration visible. Read `server/src/app.rs` to identify the enabled features.

The server rejects unknown keys in `wanaku.yaml`. If your feature reads a new top-level section, add the section and its configuration type to `reject_unknown_keys` in `server/src/config_keys.rs`. Otherwise, the server does not start when the section is present.

## Feature Lifecycle

1. **Creation:** `App::build` in `server/src/app.rs` constructs each feature.
2. **Config loading:** The server calls `load_yaml_config()` and `load_env_config()`.
3. **Filter registration:** The server calls `register_filters()` to add filters to the pipeline.
4. **Extension injection:** The server calls `pipeline_extensions()` to get shared state.
5. **Request handling:** The server calls `handle_route()` for each feature until a feature owns the route.

The `Feature` trait does not provide a shutdown hook. Implement `Drop` when the feature must release resources.

## Disabling Features

To disable a feature:

1. Remove the feature from the `features` vector in `server/src/app.rs`.
2. Remove its filter from `server/src/default.yaml`.
3. Rebuild the server.

No env var or config flag. Features are compile-time dependencies.

## Related Docs

- [Architecture](../get-started/architecture.md) — how features fit into the filter pipeline
- [Wanaku environment variables](../reference/environment-variables.md#feature-specific-environment-variables) — feature-specific env vars
- [Management API](../reference/management-api.md) — feature routes follow the same patterns

### Startup validation

The server loads feature configuration before it starts listeners. It calls `Feature::validate_startup` after YAML and environment configuration. The default implementation returns success. A feature can return an error to stop startup when its required configuration is invalid.
