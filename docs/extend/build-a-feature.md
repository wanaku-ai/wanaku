---
title: Build a feature crate
description: Build a Wanaku feature crate that adds a pipeline filter and a management API route, wire it into the server, and test it locally.
---

# Build a feature crate

This tutorial builds a small feature crate. The feature counts MCP tool calls and shows the count through the management API. At the end, the page shows patterns that most feature crates use.

## Prerequisites

- A clone of the Wanaku repository.
- A Rust toolchain that matches the MSRV in `Cargo.toml`.
- Knowledge of the `Feature` trait. Read [Feature crates](feature-crates.md) first.

The procedure creates the crate in `features/tool-stats`.

## Step 1: Create the crate

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

## Step 2: Implement the Feature trait

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

## Step 3: Implement the filter

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

## Step 4: Wire it into the server

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

## Step 5: Test it

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

## Feature patterns

### Share state with filters

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

### Reload configuration at runtime

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

### Call an LLM

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

### Build management API responses

Use the response helpers from `wanaku_types`:

```rust
use http::StatusCode;
use wanaku_types::http_response::{json_err, json_ok};

let response = json_ok(&serde_json::json!({"status": "ready"}));
let error = json_err(StatusCode::BAD_REQUEST, "invalid configuration");
```

## Next steps

- [Feature crates](feature-crates.md): read the feature lifecycle and the startup rules.
- [Architecture](../get-started/architecture.md): see where your filter runs in the pipeline.
- [Management API](../reference/management-api.md): follow the same route and response patterns.
- [Testing](../contributing/testing.md): run the test suites before you open a pull request.
