# Features

Features are self-contained modules that extend Wanaku Praxis with new capabilities. They're not just configuration options—they're full-fledged Rust crates that can register filters into the pipeline, expose management API routes, and manage their own state.

Think of features as plugins. The core server provides the infrastructure (filter pipeline, registry, management API), and features build on top of it.

## Built-in Features

Wanaku Praxis ships with three features out of the box:

### MCP Metadata Feature (`features/mcp-metadata/`)

Exposes RFC 9728 OAuth Protected Resource Metadata to advertise authentication requirements to MCP clients. This is a read-only metadata endpoint — actual authentication is handled by oauth2-proxy as an external sidecar.

**How it works:**

1. MCP client queries `GET /.well-known/oauth-protected-resource/{namespace}/mcp`
2. Praxis returns JSON metadata with the OIDC issuer URL configured via `WANAKU_AUTH_ISSUER`
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

Praxis does NOT validate tokens or enforce authentication. That's handled by [oauth2-proxy](https://github.com/oauth2-proxy/oauth2-proxy), which runs as a reverse proxy in front of Praxis ports 8081 (MCP) and 9090 (management API).

For deployment details, see `deploy/auth/README.md` and [Configuration](./configuration.md#authentication-with-oauth2-proxy).

### Safety Feature (`features/safety/`)

Uses an LLM to classify tool calls as safe or dangerous before execution. This is a runtime defense layer—not a security boundary (LLMs are fallible), but useful for catching obviously malicious prompts.

**How it works:**

1. User calls a tool via MCP (`tools/call`)
2. The `wanaku_safety_check` filter intercepts the request
3. Filter sends tool name + arguments to an LLM for classification
4. LLM returns: `{"classification": "safe"}` or `{"classification": "dangerous", "reason": "..."}` 5. If dangerous, filter rejects the request with a JSON-RPC error
6. If safe, filter continues to the `wanaku_tool_call` filter

**Configuration:**

Set these environment variables:

```bash
export WANAKU_SAFETY_LLM_URL=http://localhost:11434/v1
export WANAKU_SAFETY_LLM_MODEL=llama3.1:8b
export WANAKU_SAFETY_LLM_API_KEY=your-api-key  # optional, for non-Ollama providers
```

**Management API:**

- `GET /api/v1/safety` — get current classifier config
- `PUT /api/v1/safety` — update classifier config
- `DELETE /api/v1/safety` — disable safety checks

**Example:**

```bash
# Get current config
curl http://localhost:9090/api/v1/safety

# Update config
curl -X PUT http://localhost:9090/api/v1/safety \
  -H "Content-Type: application/json" \
  -d '{"llm_url": "http://ollama:11434/v1", "model": "llama3.2:3b"}'
```

**Prompt template:**

The filter sends this prompt to the LLM:

```
You are a security classifier. Analyze this tool call and determine if it's safe.

Tool: {tool_name}
Arguments: {arguments}

Respond with JSON:
{"classification": "safe"} or {"classification": "dangerous", "reason": "..."}
```

You can customize the prompt by modifying `features/safety/src/classifier.rs`.

**Failure modes:**

- **LLM unreachable:** Filter fails open (allows the call). This prevents outages when the LLM is down.
- **LLM returns invalid JSON:** Filter fails closed (rejects the call). Better safe than sorry.
- **LLM timeout:** Filter fails open after the request timeout elapses.

### Chat Feature (`features/chat/`)

Proxies LLM chat completion requests to Ollama. This lets you use Praxis as a unified API gateway for both MCP tools and raw LLM chat.

**How it works:**

The chat feature exposes these management API routes:

- `GET /api/v1/chat/llms` — list available LLM backends (currently just Ollama)
- `GET /api/v1/chat/{llm}/models` — list models for an LLM
- `POST /api/v1/chat/completions` — proxy chat completion request to Ollama

**Configuration:**

The chat feature uses the same Ollama endpoint as the safety feature:

```bash
export WANAKU_OLLAMA_UPSTREAM=http://localhost:11434
```

**Example:**

```bash
# List available models
curl http://localhost:9090/api/v1/chat/ollama/models

# Send chat completion request
curl -X POST http://localhost:9090/api/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "llama3.1:8b",
    "messages": [{"role": "user", "content": "Hello!"}]
  }'
```

The request is proxied to `http://localhost:11434/v1/chat/completions`.

## Creating a Custom Feature

Let's build a simple feature that counts tool calls and exposes stats via the management API.

### 1. Create the Crate

```bash
cd features
mkdir tool-stats
cd tool-stats
```

Create `Cargo.toml`:

```toml
[package]
name = "wanaku-feature-tool-stats"
version = "0.1.0"
edition = "2024"

[dependencies]
wanaku-praxis-apis = { path = "../../apis" }
wanaku-praxis-filters = { path = "../../filters" }
praxis-filter = "0.4.1"
serde = { version = "1.0", features = ["derive"] }
serde_json = "1.0"
tracing = "0.1"
```

### 2. Implement the Feature Trait

Create `src/lib.rs`:

```rust
use wanaku_praxis_apis::Feature;
use praxis_filter::{FilterRegistry, FilterError};
use std::sync::Arc;
use std::sync::atomic::{AtomicU64, Ordering};
use std::any::Any;

pub struct ToolStatsFeature {
    counter: Arc<AtomicU64>,
}

impl ToolStatsFeature {
    pub fn new() -> Self {
        Self {
            counter: Arc::new(AtomicU64::new(0)),
        }
    }
}

impl Feature for ToolStatsFeature {
    fn register_filters(&self, registry: &mut FilterRegistry) -> Result<(), FilterError> {
        // Register a filter that increments the counter on every tool call
        let counter = self.counter.clone();
        registry.register("wanaku_tool_stats", move || {
            Box::new(ToolStatsFilter { counter: counter.clone() })
        })
    }

    fn pipeline_extensions(&self) -> Vec<Box<dyn Any + Send + Sync>> {
        // No shared state needed
        vec![]
    }

    fn handle_route(&self, req: &HttpRequest, path: &str, _body: &[u8]) -> Option<HttpResponse> {
        if req.method() == "GET" && path == "/api/v1/stats/tools" {
            let count = self.counter.load(Ordering::Relaxed);
            let response = serde_json::json!({
                "data": {"tool_calls": count},
                "error": null
            });
            Some(HttpResponse::ok(response.to_string()))
        } else {
            None  // Not our route
        }
    }

    fn load_yaml_config(&mut self, _config: &serde_yaml::Value) -> Result<(), Box<dyn std::error::Error>> {
        Ok(())  // No YAML config needed
    }

    fn load_env_config(&mut self) -> Result<(), Box<dyn std::error::Error>> {
        Ok(())  // No env vars needed
    }
}
```

### 3. Implement the Filter

Add to `src/lib.rs`:

```rust
use wanaku_praxis_filters::body_filter_boilerplate;
use praxis_filter::{HttpFilter, HttpFilterContext, FilterAction};
use bytes::Bytes;

struct ToolStatsFilter {
    counter: Arc<AtomicU64>,
}

body_filter_boilerplate!(ToolStatsFilter, 1_048_576);

impl ToolStatsFilter {
    async fn handle_body(
        &self,
        ctx: &mut HttpFilterContext<'_>,
        _body: &Bytes,
    ) -> Result<FilterAction, FilterError> {
        let method = ctx.get_metadata("mcp.method")?;
        if method == "tools/call" {
            self.counter.fetch_add(1, Ordering::Relaxed);
            tracing::info!("Tool call count: {}", self.counter.load(Ordering::Relaxed));
        }
        Ok(FilterAction::Continue)
    }
}
```

### 4. Wire It Into the Server

Add to workspace `Cargo.toml`:

```toml
[workspace.dependencies]
wanaku-feature-tool-stats = { path = "features/tool-stats" }
```

Add to `server/Cargo.toml`:

```toml
[dependencies]
wanaku-feature-tool-stats = { workspace = true }
```

Update `server/src/main.rs`:

```rust
let features: Vec<Box<dyn Feature>> = vec![
    Box::new(wanaku_feature_safety::SafetyFeature::new()),
    Box::new(wanaku_feature_chat::ChatFeature::new()),
    Box::new(wanaku_feature_tool_stats::ToolStatsFeature::new()),
];
```

Add filter to `server/src/default.yaml` (after `wanaku_tool_assembly`, before `wanaku_tool_list`):

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
curl -X POST http://localhost:8081/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc": "2.0", "method": "tools/call", "params": {"name": "echo", "arguments": {"message": "test"}}, "id": 1}'
```

Check stats:

```bash
curl http://localhost:9090/api/v1/stats/tools
```

Expected response:

```json
{"data": {"tool_calls": 1}, "error": null}
```

## Feature Patterns

### State Management

Features can share state between filters and management API handlers via `pipeline_extensions`:

```rust
fn pipeline_extensions(&self) -> Vec<Box<dyn Any + Send + Sync>> {
    vec![Box::new(self.counter.clone())]
}
```

Filters retrieve the state via:

```rust
let counter = ctx.extensions.get::<Arc<AtomicU64>>().unwrap();
```

### Hot-Reloadable Config

Use `HotSwap<T>` from `wanaku_praxis_apis::llm` for runtime-reconfigurable state:

```rust
use wanaku_praxis_apis::llm::HotSwap;

pub struct MyFeature {
    config: HotSwap<MyConfig>,
}

// In handle_route:
if path == "/api/v1/myfeature/config" && req.method() == "PUT" {
    let new_config: MyConfig = serde_json::from_slice(body)?;
    self.config.update(new_config);
    // ...
}
```

Filters read the config via:

```rust
let config = feature_state.config.read();
```

### LLM Integration

Use `LlmClient` from `wanaku_praxis_apis::llm` for OpenAI-compatible LLM calls:

```rust
use wanaku_praxis_apis::llm::LlmClient;

let client = LlmClient::new("http://localhost:11434/v1");
let response = client.chat_completion("llama3.1:8b", &messages, 30).await?;
```

See `features/safety/src/classifier.rs` for a full example.

### Management API Response Helpers

Features define their own JSON response helpers to avoid depending on the server crate:

```rust
fn json_ok<T: serde::Serialize>(data: T) -> HttpResponse {
    let body = serde_json::json!({"data": data, "error": null}).to_string();
    HttpResponse::ok(body)
}

fn json_err(msg: &str, status: u16) -> HttpResponse {
    let body = serde_json::json!({"data": null, "error": msg}).to_string();
    HttpResponse::with_status(status, body)
}
```

## Feature Discovery

Features self-register on server startup. The server doesn't scan for features—they're explicitly listed in `main.rs`:

```rust
let features: Vec<Box<dyn Feature>> = vec![
    Box::new(SafetyFeature::new()),
    Box::new(ChatFeature::new()),
    Box::new(MyFeature::new()),
];
```

This is intentional. No magic, no runtime discovery, no surprises. You see exactly what features are enabled by reading `main.rs`.

## Feature Lifecycle

1. **Creation:** Server calls `Feature::new()` in `main.rs`
2. **Config loading:** Server calls `load_yaml_config()` and `load_env_config()`
3. **Filter registration:** Server calls `register_filters()` to inject filters into the pipeline
4. **Extension injection:** Server calls `pipeline_extensions()` to get shared state
5. **Request handling:** For each request, server calls `handle_route()` for features that own the route

Features don't get a shutdown hook. Clean up in `Drop` if needed.

## Disabling Features

To disable a feature:

1. Remove it from the `features` vec in `main.rs`
2. Remove its filter from `server/src/default.yaml`
3. Rebuild

No env var or config flag. Features are compile-time dependencies.

## Related Docs

- [Architecture](./architecture.md) — how features fit into the filter pipeline
- [Configuration](./configuration.md) — feature-specific env vars
- [Management API](./management-api.md) — feature routes follow the same patterns
