---
title: Feature crates
description: Learn how Wanaku feature crates add filters, routes, and state, which feature crates ship with the server, and how the server loads them.
---

# Feature crates

A feature crate is a self-contained Rust crate that extends Wanaku. A feature crate can register pipeline filters, add management API routes, and keep state. The core server supplies the filter pipeline, the registry, and the management API. Feature crates build on top of them.

Feature crates are different from Admin UI plugins. A feature crate is compiled into the server binary. An Admin UI plugin is a JavaScript module that the Admin UI loads at runtime. For Admin UI plugins, read the [Build an Admin UI plugin](plugin-development-guide.md).

To write your own feature crate, follow [Build a feature crate](build-a-feature.md).

## Built-in feature crates

`build_features` in `server/src/app.rs` registers eight feature crates. Each crate lives in `features/<name>/`.

| Crate | Purpose | Filters | Management API | Read more |
|---|---|---|---|---|
| `audit` | Keeps the governance audit store. Action policy, evaluator, simulation, and credential brokerage write decisions to it. | None | `/api/v1/audit/...` | [Governance Audit Trail](../govern/audit-trail.md) |
| `action-policy` | Applies allow and deny rules to MCP and A2A actions before the evaluator runs. | `wanaku_action_policy`, `wanaku_a2a` | `/api/v1/action-policies` | [Action Policies](../govern/action-policies.md) |
| `metrics` | Collects in-memory filter, evaluator, LLM, and WASM metrics. | None | `GET /api/v1/metrics` | [Management API](../reference/management-api.md) |
| `intercept` | Records LLM request and response pairs for evaluator conversation history. | `wanaku_intercept` | None | [Intercept](#intercept) |
| `mcp-metadata` | Serves RFC 9728 OAuth protected resource metadata and the issuer discovery routes. | None | None (MCP port routes) | [Authentication](../operate/authentication.md) |
| `evaluator` | Runs trigger, evaluate, and act pipelines with LLM, TypeSafe, or OPA engines and WASM actions. | `wanaku_evaluator` | `/api/v1/evaluators` | [Evaluators](../govern/evaluators.md) |
| `simulation` | Tests policy candidates against synthetic and replayed actions. | None | `/api/v1/policy-simulations` | [Policy Simulation](../govern/policy-simulation.md) |
| `plugins` | Loads Admin UI plugins from the plugins path and installs plugins from the catalog. | None | `/api/v1/plugins` | [Build an Admin UI plugin](plugin-development-guide.md) |

### Intercept

The `wanaku_intercept` filter runs only in the inference proxy pipeline on port 8083. It does not record MCP traffic on port 8081. It reads OpenAI-style chat requests that have a `messages` array. It can insert a system message into the request before Wanaku forwards it. `WANAKU_INTERACTION_CAPACITY` sets the number of interactions in the store. The default is `1000`.

### Authentication

The `mcp-metadata` crate does not validate tokens. [oauth2-proxy](https://github.com/oauth2-proxy/oauth2-proxy) validates tokens in front of ports 8080 and 8081. Read [Authentication](../operate/authentication.md) for the metadata routes and the deployment.

### A2A proxy support

The action-policy crate registers the `wanaku_a2a` filter. The managed A2A pipeline selects a registered agent and extracts protocol metadata with the Praxis `a2a` filter. Then it applies the shared `wanaku_action_policy` filter. Read [A2A Proxy](../connect/a2a.md) for configuration and supported operations.

## Register a feature

The server does not scan for features. List each feature explicitly in `build_features` in `server/src/app.rs`:

```rust
let features: Vec<Box<dyn Feature>> = vec![
    // ... existing features ...
    Box::new(MyFeature::new()),
];
```

The explicit list makes feature registration visible. Read `server/src/app.rs` to identify the enabled features.

The server rejects unknown keys in `wanaku.yaml`. If your feature reads a new top-level section, add the section and its configuration type to `reject_unknown_keys` in `server/src/config_keys.rs`. Otherwise, the server does not start when the section is present.

## Feature lifecycle

1. **Creation:** `App::build` in `server/src/app.rs` constructs each feature.
2. **Config loading:** The server calls `load_yaml_config()` and `load_env_config()`.
3. **Filter registration:** The server calls `register_filters()` to add filters to the pipeline.
4. **Extension injection:** The server calls `pipeline_extensions()` to get shared state.
5. **Request handling:** The server calls `handle_route()` for each feature until a feature owns the route.

The `Feature` trait does not provide a shutdown hook. Implement `Drop` when the feature must release resources.

## Startup validation

The server loads feature configuration before it starts listeners. It calls `Feature::validate_startup` after YAML and environment configuration. The default implementation returns success. A feature can return an error to stop startup when its required configuration is invalid.

## Disable a feature

To disable a feature:

1. Remove the feature from the `features` vector in `server/src/app.rs`.
2. Remove its filter from `server/src/default.yaml`.
3. Rebuild the server.

No environment variable or configuration flag disables a feature. Features are compile-time dependencies.

## Next steps

- [Build a feature crate](build-a-feature.md): create, wire, and test a new feature crate.
- [Architecture](../get-started/architecture.md): see how feature crates fit into the filter pipeline.
- [Wanaku environment variables](../reference/environment-variables.md#feature-specific-environment-variables): read the feature-specific environment variables.
