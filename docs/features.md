# Features

Features are self-contained Rust crates that extend Wanaku. A feature can register pipeline filters, expose management API routes, and manage state.

Think of features as plugins. The core server provides the infrastructure (filter pipeline, registry, management API), and features build on top of it.

## Built-in Features

Wanaku ships with six features:

### Metrics Feature (`features/metrics/`)

Collects in-memory filter, evaluator, LLM, and WASM metrics.

**Management API route:** `GET /api/v1/metrics`

### Intercept Feature (`features/intercept/`)

Records MCP request/response interactions in an in-memory store. Other features — especially the evaluator engine — use this history to provide conversation context to LLM operations.

**Filter:** `wanaku_intercept` (runs in the inference proxy pipeline)

**Management API routes:**

- `GET /api/v1/interactions` — list recorded interactions
- `DELETE /api/v1/interactions` — clear the interaction store

**Configuration:**

| Variable | Default | Purpose |
|---|---|---|
| `WANAKU_INTERACTION_CAPACITY` | `1000` | Maximum interactions kept in memory |

### MCP Metadata Feature (`features/mcp-metadata/`)

Exposes RFC 9728 OAuth Protected Resource Metadata to advertise authentication requirements to MCP clients. This is a read-only metadata endpoint — actual authentication is handled by oauth2-proxy as an external sidecar.

**How it works:**

1. MCP client queries `GET /.well-known/oauth-protected-resource/{namespace}/mcp`
2. Wanaku returns JSON metadata with the OIDC issuer URL configured via `WANAKU_AUTH_ISSUER`
3. MCP client uses the issuer URL to discover the authorization server and token endpoints

**Configuration:**

Set the OIDC issuer URL:

```bash
export WANAKU_AUTH_ISSUER=http://localhost:8543/realms/wanaku
```

If `WANAKU_AUTH_ISSUER` is unset, the metadata endpoint returns a 404 (feature disabled).

**Example response:**

```json
{
  "issuer": "http://localhost:8543/realms/wanaku",
  "authorization_endpoint": "http://localhost:8543/realms/wanaku/protocol/openid-connect/auth",
  "token_endpoint": "http://localhost:8543/realms/wanaku/protocol/openid-connect/token"
}
```

**Authentication architecture:**

Wanaku does not validate tokens or enforce authentication. [oauth2-proxy](https://github.com/oauth2-proxy/oauth2-proxy) provides these controls. It runs as a reverse proxy in front of the MCP port (8081) and management API port (8080).

For deployment details, see `deploy/auth/README.md` and [Configuration](./configuration.md#authentication-with-oauth2-proxy).

### Chat Feature (`features/chat/`)

Proxies LLM chat completion requests to an inference backend (any OpenAI-compatible endpoint). This lets you proxy LLM chat completions alongside MCP tool actions through a single Wanaku deployment.

**How it works:**

The chat feature exposes these management API routes:

- `GET /api/v1/chat/llms` — list available LLM backends
- `GET /api/v1/chat/{llm}/models` — list models for an LLM
- `POST /api/v1/chat/completions` — proxy chat completion request to the inference backend

**Configuration:**

The chat feature uses the core inference endpoint:

```bash
export WANAKU_INFERENCE_UPSTREAM=http://localhost:11434
```

**Example:**

```bash
# List available models
curl http://localhost:8080/api/v1/chat/inference/models

# Send chat completion request
curl -X POST http://localhost:8080/api/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "llama3.1:8b",
    "messages": [{"role": "user", "content": "Hello!"}]
  }'
```

The request is proxied to `WANAKU_INFERENCE_UPSTREAM/v1/chat/completions`.

### Evaluator Feature (`features/evaluator/`)

A WASM-based evaluation engine that lets you build trigger→evaluate→act pipelines. When an MCP request matches a trigger, the engine calls an LLM to classify or filter, then runs a WebAssembly action script that can block requests, filter tool lists, or set metadata.

**Filter:** `wanaku_evaluator`

**Management API routes:**

- `GET /api/v1/evaluators` — list configured evaluators
- `PUT /api/v1/evaluators` — hot-reload evaluator configuration
- `GET /api/v1/evaluators/namespaces` — list namespace-to-conversation bindings
- `PUT /api/v1/evaluators/namespaces/{namespace}` — bind a namespace to a conversation ID
- `DELETE /api/v1/evaluators/namespaces/{namespace}` — unbind

See the [Evaluator Engine](./evaluator-engine.md) guide for configuration details and WASM action script development.

### Plugins Feature (`features/plugins/`)

Loads external UI plugins from a filesystem directory. Plugins are ES modules that can add navigation entries, pages, and backend service integrations to the admin UI at runtime.

**Configuration:**

Start the server with `--plugins-path`:

```bash
cargo run -- --plugins-path /path/to/plugins
```

See the [Plugin Development Guide](./plugin-development-guide.md) for details.

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
wanaku-apis = { workspace = true }
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
use wanaku_apis::feature::{Feature, HttpContext};
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
            Some(wanaku_apis::http_response::json_ok(&body))
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

Add the feature to the vector returned by `build_features` in `server/src/main.rs`:

```rust
let features: Vec<Box<dyn Feature>> = vec![
    // ... existing features ...
    Box::new(wanaku_feature_tool_stats::ToolStatsFeature::new()),
];
```

Add filter to `server/src/default.yaml` (after `wanaku_evaluator`, before `wanaku_tool_list`):

```yaml
      - filter: wanaku_tool_stats
```

### 5. Test It

```bash
cargo build
cargo run
```

Call a tool:

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

Use `HotSwap<T>` from `wanaku_apis::llm` for runtime-reconfigurable state:

```rust
use wanaku_apis::llm::HotSwap;

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

Use `LlmClient` from `wanaku_apis::llm` for OpenAI-compatible LLM calls:

```rust
use wanaku_apis::llm::LlmClient;

let client = LlmClient::new("http://localhost:11434/v1", "llama3.1:8b", "")?;
let response = client.chat("You are a concise assistant.", user_prompt).await?;
```

See `features/chat/src/lib.rs` for a full example.

### Management API Response Helpers

Use the response helpers from `wanaku_apis`:

```rust
use http::StatusCode;
use wanaku_apis::http_response::{json_err, json_ok};

let response = json_ok(&serde_json::json!({"status": "ready"}));
let error = json_err(StatusCode::BAD_REQUEST, "invalid configuration");
```

## Feature Discovery

The server does not scan for features. List each feature explicitly in `main.rs`:

```rust
let features: Vec<Box<dyn Feature>> = vec![
    // ... existing features ...
    Box::new(MyFeature::new()),
];
```

The explicit list makes feature registration visible. Read `main.rs` to identify the enabled features.

## Feature Lifecycle

1. **Creation:** The server constructs each feature in `main.rs`.
2. **Config loading:** The server calls `load_yaml_config()` and `load_env_config()`.
3. **Filter registration:** The server calls `register_filters()` to add filters to the pipeline.
4. **Extension injection:** The server calls `pipeline_extensions()` to get shared state.
5. **Request handling:** The server calls `handle_route()` for each feature until a feature owns the route.

The `Feature` trait does not provide a shutdown hook. Implement `Drop` when the feature must release resources.

## Disabling Features

To disable a feature:

1. Remove the feature from the `features` vector in `main.rs`.
2. Remove its filter from `server/src/default.yaml`.
3. Rebuild the server.

No env var or config flag. Features are compile-time dependencies.

## Related Docs

- [Architecture](./architecture.md) — how features fit into the filter pipeline
- [Configuration](./configuration.md) — feature-specific env vars
- [Management API](./management-api.md) — feature routes follow the same patterns
