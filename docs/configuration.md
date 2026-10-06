# Configuration

For registered A2A agents, the managed listener, and static proxy configuration, see [A2A Proxy](./a2a.md).

## Audit configuration

Use the `audit` section to configure audit retention, optional payload capture, payload size, and redaction rules. Payload capture is disabled by default. The default retention limit is 10,000 events. See [Governance audit trail](audit-trail.md) for the configuration fields, environment overrides, persistence behavior, and management API.

Wanaku uses environment variables and two optional YAML files. It does not use a properties file. Set environment variables in the container orchestrator or systemd unit. Specify configuration files only when you need custom settings.

## Configuration Sources (Precedence Order)

1. **Environment variables** — highest priority, always win
2. **Runtime YAML files** — loaded from CLI args (e.g., `wanaku-server --pipeline-config praxis.yaml --wanaku-config wanaku.yaml`)
3. **Embedded defaults** — compiled into the binary

## Core Environment Variables

These control core server behavior:

| Variable | Default | Purpose |
|---|---|---|
| `WANAKU_MGMT_LISTEN` | `0.0.0.0:8080` | Management API listen address (host:port) |
| `WANAKU_A2A_LISTEN` | `0.0.0.0:8084` | Registered A2A agents listen address in the embedded pipeline. |
| `WANAKU_A2A_PUBLIC_URL` | `http://127.0.0.1:8084/` | Public HTTP or HTTPS origin for registered agent proxy URLs and Agent Cards. |
| `WANAKU_INFERENCE_UPSTREAM` | `127.0.0.1:11434` | Upstream for the inference-proxy pipeline (port 8083), an OpenAI-compatible passthrough. Clients call this port directly and supply their own bearer token. |
| `WANAKU_PERSIST_BACKEND` | `file` | File-based registry persistence. Set to `"none"` to disable persistence. |
| `WANAKU_PERSIST_PATH` | `$HOME/.wanaku/server` | Directory where Wanaku reads and writes `registry.json` |
| `WANAKU_UI_PATH` | _(unset = embedded)_ | Filesystem path to admin UI override (use for local dev) |
| `WANAKU_CORS_ORIGIN` | `*` | Value for `Access-Control-Allow-Origin` on all HTTP responses (management API, MCP endpoint, inference proxy, and CORS preflight) |
| `WANAKU_AUTH_ISSUER` | _(unset = disabled)_ | OIDC issuer URL for RFC 9728 metadata endpoint |
| `WANAKU_AUTH_UPSTREAM_ISSUER` | `WANAKU_AUTH_ISSUER` | Internal OIDC issuer URL for token requests |
| `WANAKU_FORWARD_HEADERS` | _(unset = none)_ | Comma-separated list of HTTP header names to forward from incoming MCP requests to downstream tool invocations (e.g., `Authorization,DPoP`). Per-tool overrides via the `wanaku.forward_headers` label. |
| `WANAKU_FORWARD_HEALTHCHECK_INTERVAL` | `30` | Interval, in seconds, of the background loop that re-probes forwards currently marked unavailable and flips them back to available once they recover — no manual refresh required. Set to `0` to disable the loop. |

**Example:**

```bash
export WANAKU_MGMT_LISTEN=0.0.0.0:9091
export WANAKU_PERSIST_BACKEND=file
export WANAKU_PERSIST_PATH=/var/lib/wanaku/registry
wanaku-server
```

### Management API Listen Address

The `WANAKU_MGMT_LISTEN` variable controls where the management API binds. Format: `host:port`.

**Bind to all interfaces (default):**

```bash
export WANAKU_MGMT_LISTEN=0.0.0.0:8080
```

**Bind to localhost only:**

```bash
export WANAKU_MGMT_LISTEN=127.0.0.1:8080
```

Useful when running Wanaku behind a reverse proxy (nginx, Envoy) that handles external traffic.

**Bind to specific IP:**

```bash
export WANAKU_MGMT_LISTEN=10.0.1.42:8080
```

### Registry Persistence

File persistence is enabled by default. Wanaku reads and writes `$HOME/.wanaku/server/registry.json`. Set a different directory when the default location is not suitable:

```bash
export WANAKU_PERSIST_BACKEND=file
export WANAKU_PERSIST_PATH=/data/registry
```

On startup, the server loads `registry.json` from `WANAKU_PERSIST_PATH`. Each registry change sends a full snapshot to one background writer. The writer keeps only the newest pending snapshot. On an orderly Pingora shutdown, Wanaku waits for pending persistence for up to 10 seconds.

**Format:**

```json
{
  "tools": [],
  "resources": [],
  "prompts": [],
  "namespaces": [],
  "forwards": []
}
```

**Limitation:** File persistence has one in-process writer. It does not coordinate separate Wanaku processes. Do not run multiple replicas against the same persistence directory. If the process stops because of SIGKILL, an out-of-memory error, or a panic, changes since the latest completed snapshot are lost. Use a shared external persistence implementation before you run multiple replicas.

To disable persistence:

```bash
export WANAKU_PERSIST_BACKEND=none
```

### Evaluator Revision Persistence

Evaluator revision history uses the same persistence configuration as the registry. When `WANAKU_PERSIST_BACKEND=file`, the server reads and writes `evaluator-revisions.json` in `WANAKU_PERSIST_PATH`. When persistence is disabled, revision history stays in memory and is lost on restart.

The server writes the file after every revision change, not only at shutdown. A revision created or activated through the management API therefore survives an abrupt stop.

On startup the server loads the persisted history. The `wanaku.yaml` configuration is the source of truth for the live runtime. If the startup configuration matches the persisted active revision, the server re-validates and recompiles that revision and installs it, without recording a new revision. If the startup configuration differs, the server activates it as a new revision.

When the server re-applies an existing active revision, it re-validates the evaluator names, triggers, and LLM connection references and recompiles the WASM processors and result schemas. If the revision no longer validates or compiles on the current host, the server marks the evaluator runtime invalid and logs the error. Startup stops by default. The top-level `evaluator_startup_failure: deny` option permits startup with an invalid runtime. Governed actions are then blocked in enforce mode. The server does not append a revision in this case.

The server keeps a bounded history of the most recent 50 revisions. Older revisions are dropped. Rejected revisions are also persisted, so a failed activation stays visible in the history.

If the startup `wanaku.yaml` evaluator configuration is identical to the persisted active revision, the server keeps that revision active and creates no new revision. A restart with an unchanged configuration does not grow the history.

**Format:**

```json
{
  "revisions": [],
  "active_id": null,
  "next_id": 1
}
```

**Limitation:** File persistence supports one writer. Use a shared external persistence implementation before you run multiple replicas.

### Update an earlier pre-release configuration

Wanaku 0.3.0 is unreleased. Evaluator configuration can change between pre-release builds. This change removes `on_error` without an automatic migration. The field is rejected in YAML, API requests, and all persisted evaluator revisions, including inactive revisions.

Before you start the updated build:

1. Stop the server.
2. Back up the configuration and `evaluator-revisions.json` in `WANAKU_PERSIST_PATH`. The default directory is `$HOME/.wanaku/server`.
3. Remove `on_error` from each evaluator in the startup YAML and each definition in the persisted revision history.
4. Set `governance.default.on_failure` or a namespace override in the startup YAML. Use `allow` for the former `continue` behavior. Use `deny` for the former `block` behavior. The setting applies to shared namespace governance, not to one evaluator. Evaluators in the same namespace must use the same failure behavior.
5. Start the server with the updated configuration.

To discard pre-release evaluator history instead, move the backed-up `evaluator-revisions.json` out of the persistence directory while the server is stopped. Restore the required evaluator definitions in the startup YAML before restart. This resets evaluator revision history. Keep `registry.json` and other persistence files in place.

The server stops at startup if legacy fields remain in the revision file. `evaluator_startup_failure: deny` does not migrate this file or restore evaluator readiness.

### Action Policy Revision Persistence

Action-policy revision history uses the same persistence settings. Wanaku writes `action-policy-revisions.json` in `WANAKU_PERSIST_PATH`. The action-policy stream is independent from the evaluator stream.

Wanaku writes the file after each revision change. Wanaku validates and installs the persisted active policy during startup. See [Action Policies](./action-policies.md) for the policy schema and lifecycle.

### Admin UI Override

The admin UI is embedded in the binary. To serve UI files from a directory on disk instead of the embedded bundle:

```bash
export WANAKU_UI_PATH=/absolute/path/to/ui/dist
wanaku-server
```

The server serves files from the specified directory instead of the embedded bundle.

**Warning:** Relative paths do not work. Use an absolute path.

### Header Forwarding

By default, Wanaku does not forward any HTTP headers from incoming MCP requests to downstream tool invocations. To enable header forwarding (e.g., for gateway-mediated token exchange), configure an allowlist of header names.

**Global allowlist** — applies to all tool calls:

```bash
export WANAKU_FORWARD_HEADERS=Authorization,DPoP
```

**Per-tool override** — set the `wanaku.forward_headers` label on a `ToolEntry`:

```json
{
  "name": "github-api",
  "uri": "http://mcp-gateway:8080/mcp",
  "type": "mcp-forward",
  "labels": {
    "wanaku.forward_headers": "Authorization,X-Third-Party-Token"
  }
}
```

Both lists are merged at runtime — a header is forwarded if it appears in either the global allowlist or the per-tool label. Header names are case-insensitive.

**SEP-2243 argument injection** — When a tool's input schema has properties annotated with `x-mcp-header` (SEP-2243), Wanaku automatically injects matching forwarded header values as tool arguments before forwarding. This ensures downstream MCP servers using `@McpParamHeader` receive the value correctly. This behavior is enabled by default and can be disabled per-tool:

```json
{
  "labels": {
    "wanaku.forward_headers": "Authorization",
    "wanaku.inject_header_args": "false"
  }
}
```

When disabled, headers are forwarded only as raw HTTP headers on the downstream connection — suitable for gateway scenarios where the gateway reads headers directly.

**Use case:** A gateway (Envoy ExtProc, IBM ContextForge) sits between Wanaku and a protected downstream API. The gateway performs token exchange (e.g., Keycloak STS) using the `Authorization` header from the original request. Wanaku forwards the header so the gateway has a `subject_token` to exchange.

**Security considerations:**

- Headers that would corrupt the downstream HTTP request (`host`, `content-type`, `content-length`, `transfer-encoding`, `connection`) and rmcp-reserved headers (`accept`, `mcp-session-id`, `last-event-id`) are always blocked, even if they appear in the allowlist.
- When `Authorization` forwarding is enabled, anyone with management API access can register a tool pointing to an arbitrary URL — the caller's bearer token would then be forwarded to that URL. Secure the management API (authentication, network isolation) before enabling credential forwarding in production.

## Feature-Specific Environment Variables

Features (mcp-metadata, evaluator, etc.) define their own environment variables.

### Authentication with oauth2-proxy

Wanaku uses [oauth2-proxy](https://github.com/oauth2-proxy/oauth2-proxy) for authentication. Wanaku provides OAuth metadata and forwards token requests to the configured issuer.

| Variable | Default | Purpose |
|---|---|---|
| `WANAKU_AUTH_ISSUER` | _(unset = disabled)_ | Public OIDC issuer URL (for example, `http://localhost:8543/realms/wanaku`) |
| `WANAKU_AUTH_UPSTREAM_ISSUER` | `WANAKU_AUTH_ISSUER` | Internal OIDC issuer URL for token requests (for example, `http://keycloak:8080/realms/wanaku`) |

When `WANAKU_AUTH_ISSUER` is set, the endpoint `/.well-known/oauth-protected-resource/{namespace}/mcp` returns OAuth server metadata. When it is unset, the metadata contains an empty `authorization_servers` list.

The public issuer URL must be accessible to clients and browsers. Wanaku uses this URL in discovery metadata, authorization redirects, registration redirects, and the JWKS URL. Set `WANAKU_AUTH_UPSTREAM_ISSUER` when Wanaku must use a different address to reach the same issuer. Only the `/token` proxy uses this internal address. An unset or empty upstream issuer uses the public issuer URL.

See [Authentication](./auth.md) for full oauth2-proxy setup instructions.

### Intercept Feature

The intercept feature records request/response interactions for conversation tracking. Evaluators use this history to provide context to LLM operations.

| Variable | Default | Purpose |
|---|---|---|
| `WANAKU_INTERACTION_CAPACITY` | `1000` | Maximum number of interactions kept in the in-memory store |

The intercept feature captures the LLM request body and response body. The feature redacts sensitive data from both bodies before it stores them. The feature preserves the conversation content that intent analysis needs. See [Redaction boundary](./redaction.md) for the redaction rules and limitations.

Configure the redaction rules in the intercept filter node of the pipeline configuration:

```yaml
- name: wanaku_intercept
  filter: wanaku_intercept
  capture_payloads: true
  payload_max_bytes: 4194304
  include_default_redaction_rules: true
  sensitive_fields:
    - private_value
  sensitive_json_pointers:
    - /messages/0/content
  credential_markers:
    - "credential="
  token_prefixes:
    - custom_
```

`capture_payloads` controls request and response body capture. The default is `true`. Intent analysis needs the conversation content. Set this value to `false` to keep only the envelope (path, status, model, timing). This setting disables intent analysis for the affected interactions.

`payload_max_bytes` sets the maximum serialized body size after redaction. The default is `4194304`. Wanaku replaces a body that exceeds this limit with a single redaction marker.

`include_default_redaction_rules` controls the built-in redaction rules. The default is `true`. Set this value to `false` only when you must replace or disable the built-in rules.

`sensitive_fields`, `sensitive_json_pointers`, `credential_markers`, and `token_prefixes` extend the built-in rules. These controls use the same behavior as the audit configuration. See [Governance audit trail](./audit-trail.md).

You can use these environment variables to override the YAML values:

| Variable | Default | Purpose |
|---|---|---|
| `WANAKU_INTERCEPT_CAPTURE_PAYLOADS` | `true` | Capture request and response bodies |
| `WANAKU_INTERCEPT_MAX_BODY_BYTES` | `4194304` | Maximum buffered body size, in bytes |
| `WANAKU_INTERCEPT_PAYLOAD_MAX_BYTES` | `4194304` | Maximum retained body size after redaction, in bytes |

`WANAKU_INTERCEPT_CAPTURE_PAYLOADS` enables capture only for the values `1`, `true`, or `yes`. Any other value disables capture. When the variable is unset, the YAML value applies.

### Inference Proxy

The inference proxy is a raw, transparent reverse proxy (port 8083) to an
OpenAI-compatible backend. It forwards requests as-is, including the caller's
own `Authorization` header — Wanaku does not inject or store a credential for
it. Point it at a backend with `WANAKU_INFERENCE_UPSTREAM`:

```bash
export WANAKU_INFERENCE_UPSTREAM=127.0.0.1:11434
```

The Admin UI's LLM Chat page calls this port directly with a key you supply in
the browser. See [Management API](./management-api.md) for the endpoint shape.

By default, the LLM Chat page keeps the API key in memory for the current
session only. The page does not save the key to local storage. To keep the key
across sessions, first enable local storage for the LLM settings. Then enable
the API key storage option. The page writes the key to browser local storage. A
script or a browser extension on the page can then read the key. Enable this
option only on a trusted device.

Because the proxy forwards the `Origin` header unchanged, the backend's own
origin policy still applies. Ollama, for example, rejects browser-origin
requests by default — set `OLLAMA_ORIGINS` on the Ollama side to allow the
Admin UI's origin. Wanaku's own CORS filter on port 8083 controls only the
response back to the browser; it does not affect what the backend accepts.

Wanaku also rewrites the outgoing `Host` header to match
`WANAKU_INFERENCE_UPSTREAM`, instead of forwarding the browser's original
Host. Backends that route by hostname (for example, a TLS-terminating
ingress in front of a hosted LLM API) reject a mismatched Host. **Note:**
like `WANAKU_CORS_ORIGIN`, this override applies only to the embedded
default pipeline config. A custom `--pipeline-config` must define its own
`headers` filter with `request_set` on the `inference_proxy` chain to get
this behavior.

`WANAKU_INFERENCE_UPSTREAM` may include a path, for example
`https://openrouter.ai/api`. Wanaku prepends that path to every request it
forwards — a request to `/v1/chat/completions` on port 8083 reaches
`https://openrouter.ai/api/v1/chat/completions` upstream. The same
custom-`--pipeline-config` caveat applies: this requires a `path_rewrite`
filter with `add_prefix` on the `inference_proxy` chain.

## Pipeline Config File (praxis.yaml)

The pipeline configuration defines listeners, filter chains, and filter-specific settings. This YAML file uses the native Praxis configuration format.

**Override:** Pass with `--pipeline-config`:

```bash
wanaku-server --pipeline-config /path/to/custom-praxis.yaml
```

**Format:**

```yaml
listeners:
  - name: mcp
    address: "127.0.0.1:8081"
    filter_chains: [mcp_router]

filter_chains:
  - name: mcp_router
    filters:
      - filter: cors
        allow_origins: ["*"]
      - filter: mcp
        on_invalid: continue
      - filter: wanaku_namespace
      - filter: wanaku_well_known
      - filter: wanaku_mcp_init
      - filter: wanaku_action_policy
      - filter: wanaku_evaluator
      - filter: wanaku_tool_list
      - filter: wanaku_tool_call
      - filter: wanaku_resource_list
      - filter: wanaku_resource_read
      - filter: wanaku_prompt_list
      - filter: wanaku_prompt_get
      - filter: static_response
```

### Listener Configuration

**Change MCP port:**

```yaml
listeners:
  - name: mcp
    address: "0.0.0.0:8083"  # Bind to all interfaces, port 8083
    filter_chains: [mcp_router]
```

**Add TLS:**

```yaml
listeners:
  - name: mcp
    address: "0.0.0.0:8081"
    tls:
      cert_path: /etc/wanaku/cert.pem
      key_path: /etc/wanaku/key.pem
    filter_chains: [mcp_router]
```

(Note: TLS support depends on Praxis version. Check `praxis-proxy-core` docs.)

### Filter Configuration

**CORS filter:**

```yaml
- filter: cors
  allow_origins: ["http://localhost:3000", "https://app.example.com"]
  allow_methods: ["GET", "POST", "OPTIONS"]
  allow_headers: ["Content-Type", "Authorization"]
```

**Note:** The `WANAKU_CORS_ORIGIN` env var overrides `allow_origins` in the embedded default pipeline config at startup. If you provide a custom pipeline config via `--pipeline-config`, `allow_origins` in that file is used as-is — the env var only applies to the embedded default.

**MCP filter (praxis-ai):**

```yaml
- filter: mcp
  on_invalid: continue  # REQUIRED for OPTIONS preflight
  max_body_bytes: 1048576  # 1MB limit
```

The `on_invalid: continue` setting allows OPTIONS requests (which have no body) to pass through without failing validation. Without it, CORS preflight fails.

**Custom filter:**

```yaml
- filter: wanaku_custom_feature
  enabled: true
  config:
    some_option: value
```

Feature filters read their config from this section. The exact schema depends on the feature.

### Filter Ordering

The order in `filters:` matters. The pipeline executes filters top-to-bottom.

**Critical rules:**

1. Put CORS first. Otherwise, error responses do not contain CORS headers.
2. **MCP must be before wanaku_namespace** — namespace filter reads `mcp.method` metadata
3. **wanaku_namespace must be before tool/resource/prompt filters** — they all read `wanaku.namespace`
4. **static_response must be last** — catch-all for unhandled requests

If you reorder filters and requests start failing, check the logs. The filter that needed metadata will error with "missing metadata key".

## Wanaku Config File (wanaku.yaml)

The Wanaku configuration bootstraps core registry entries and feature settings at startup. This file is optional. If you omit it, Wanaku can still restore the registry from the default file snapshot. If no snapshot exists, the registry starts empty.

**Location:** Pass with `--wanaku-config`:

```bash
wanaku-server --pipeline-config /path/to/praxis.yaml --wanaku-config /path/to/wanaku.yaml
```

**Format:**

```yaml
forwards:
  - name: "upstream-mcp"
    address: "http://upstream:8080/mcp"
plugins:
  - id: "customer-management"
    services:
      customer-api:
        target: "http://customer-service:8080"
```

Wanaku loads these top-level sections from `wanaku.yaml`:

- `forwards` — core forward bootstrap configuration
- `bindings` — credential bindings that forwards reference for brokered authentication. See [Credential Brokerage](./credential-brokerage.md#configuring-credentials-in-wanakuyaml)
- `llm_connections` — named LLM connections (model/url/api_key) for evaluators; config-only, never exposed via the management API
- `typesafe_system_one_connections` — named TypeSafe System One connections for evaluators; config-only, never exposed via the management API
- `opa_connections` — named Open Policy Agent connections (url/token/timeout_ms/ca_cert) for evaluators; config-only, never exposed via the management API. See [Open Policy Agent](./evaluator-engine.md#open-policy-agent)
- `evaluators` — evaluator feature configuration
- `action_policy` — declarative action-policy rules
- `governance` — global governance posture and namespace overrides
- `plugins` — plugin service mappings owned by the plugins feature

The [Governance Posture](./governance-posture.md) model defines fail-safe defaults and namespace overrides. Wanaku validates the section during startup. Invalid governance configuration stops startup.

Wanaku discovers tools, resources, and prompts from the configured forwards.

**Evaluator configuration** (see [Evaluator Engine](./evaluator-engine.md) for full details):

```yaml
llm_connections:
  - name: "local-llama"
    model: "llama3.2"
    url: "http://localhost:11434/v1"

evaluators:
  - name: "safety-gate"
    trigger:
      method: "tools/call"
    llm:
      operation: classify
      prompt: "Classify this tool call..."
      connection: "local-llama"
      result_schema:              # Optional JSON Schema for LLM output validation
        type: object
        properties:
          level: { type: string }
          reason: { type: string }
        required: ["level", "reason"]
    processor:
      path: "/wasm/safety-gate.wasm"
```

When `result_schema` is set, the host validates LLM output against the schema and retries once with a correction prompt on mismatch.

## Common Configuration Patterns

### Development (Local Machine)

```bash
# No persistence, embedded UI, inference backend for LLMs
export WANAKU_PERSIST_BACKEND=none
export WANAKU_INFERENCE_UPSTREAM=http://localhost:11434
wanaku-server
```

### Kubernetes

**ConfigMap:**

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: wanaku-config
data:
  praxis.yaml: |
    listeners:
      - name: mcp
        address: "0.0.0.0:8081"
        filter_chains: [mcp_router]
    filter_chains:
      - name: mcp_router
        filters:
          - filter: cors
          - filter: mcp
            on_invalid: continue
          # ... rest of pipeline

  wanaku.yaml: |
    forwards:
      - name: "upstream-mcp"
        address: "http://upstream:8080/mcp"
```

**Deployment:**

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
        - name: WANAKU_MGMT_LISTEN
          value: "0.0.0.0:8080"
        - name: WANAKU_PERSIST_BACKEND
          value: "file"
        - name: WANAKU_PERSIST_PATH
          value: "/data/registry"
        volumeMounts:
        - name: config
          mountPath: /etc/wanaku
        - name: data
          mountPath: /data
      volumes:
      - name: config
        configMap:
          name: wanaku-config
      - name: data
        persistentVolumeClaim:
          claimName: wanaku-registry
```

## Debugging Configuration

### Enable Trace Logs

```bash
RUST_LOG=trace wanaku-server
```

This logs all filter decisions, metadata reads/writes, and registry operations. Output is verbose — use sparingly.

**Filter-specific logs:**

```bash
RUST_LOG=wanaku_filters=trace wanaku-server
```

### Verify Environment Variables

The server does not reject unknown environment variable names. A misspelled name has no effect, and the server uses the default value. Enable trace logs to verify the values that the server uses.

## Related Docs

- [Architecture](./architecture.md) — understand the filter pipeline and registry
- [Authentication](./auth.md) — oauth2-proxy setup and Keycloak configuration
- [Features](./features.md) — enable evaluators and create custom features
- [Management API](./management-api.md) — API routes that respect configuration
- [Redaction boundary](./redaction.md) — where Wanaku removes sensitive data
- [FAQ](./faq.md) — troubleshooting common issues

## Evaluator governance startup

Evaluator execution uses the global and namespace [governance posture](governance-posture.md). The default failure behavior is `deny`. The evaluator `on_error` field is not accepted.

Invalid evaluator configuration stops startup by default. Set the top-level `evaluator_startup_failure: deny` option to start with an invalid runtime that blocks evaluation in enforce mode. Valid management updates can replace this invalid runtime. Audit and disabled modes retain their configured behavior.

### Plugin catalog

The default catalog is the JSON file embedded from `features/plugins/plugin-catalog.json`. It contains the curated Wanaku Barn entry.

Set `WANAKU_PLUGIN_CATALOG_PATH` to load a local JSON file. Set `WANAKU_PLUGIN_CATALOG_URL` to load a JSON file from an HTTP or HTTPS URL. A local path takes precedence over a URL. Each custom source replaces the complete embedded catalog. Custom sources can contain plugins from multiple publishers and archive hosts.

The server selects the source at startup. It reads a local file or remote URL on each catalog request. An update to a local file or remote document does not require a server restart. An update to the embedded catalog requires a new build.

A catalog must contain a JSON array of plugin entries. The size limit is 1 MiB. The server returns HTTP 502 if the selected source is unavailable or invalid. It does not fall back to the embedded catalog. See [Plugin Catalog](plugin-catalog.md) for the entry format and publication instructions.

A catalog request reads only catalog JSON. It does not download plugin archives or install plugins.
