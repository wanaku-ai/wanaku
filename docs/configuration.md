# Configuration

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
| `WANAKU_INFERENCE_UPSTREAM` | `127.0.0.1:11434` | Upstream for the inference-proxy pipeline (port 8083), an OpenAI-compatible passthrough. Clients call this port directly and supply their own bearer token. |
| `WANAKU_PERSIST_BACKEND` | `file` | File-based registry persistence. Set to `"none"` to disable persistence. |
| `WANAKU_PERSIST_PATH` | `$HOME/.wanaku/server` | Directory where Wanaku reads and writes `registry.json` |
| `WANAKU_UI_PATH` | _(unset = embedded)_ | Filesystem path to admin UI override (use for local dev) |
| `WANAKU_CORS_ORIGIN` | `*` | Value for `Access-Control-Allow-Origin` on all HTTP responses (management API, MCP endpoint, inference proxy, and CORS preflight) |
| `WANAKU_AUTH_ISSUER` | _(unset = disabled)_ | OIDC issuer URL for RFC 9728 metadata endpoint |
| `WANAKU_FORWARD_HEADERS` | _(unset = none)_ | Comma-separated list of HTTP header names to forward from incoming MCP requests to downstream tool invocations (e.g., `Authorization,DPoP`). Per-tool overrides via the `wanaku.forward_headers` label. |

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

On startup, the server loads `registry.json` from `WANAKU_PERSIST_PATH`. On shutdown (SIGTERM, SIGINT), it writes back.

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

**Limitation:** Wanaku writes the snapshot during an orderly shutdown. If the process stops because of SIGKILL, an out-of-memory error, or a panic, changes since the last snapshot are lost. File persistence supports one writer. Use a shared external persistence implementation before you run multiple replicas.

To disable persistence:

```bash
export WANAKU_PERSIST_BACKEND=none
```

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

Wanaku uses [oauth2-proxy](https://github.com/oauth2-proxy/oauth2-proxy) for authentication. The only auth-related configuration in Wanaku itself is the OIDC issuer URL:

| Variable | Default | Purpose |
|---|---|---|
| `WANAKU_AUTH_ISSUER` | _(unset = disabled)_ | OIDC issuer URL (e.g., `http://localhost:8543/realms/wanaku`) |

When set, the endpoint `/.well-known/oauth-protected-resource/{namespace}/mcp` returns OAuth server metadata. When unset, the endpoint returns 404.

See [Authentication](./auth.md) for full oauth2-proxy setup instructions.

### Intercept Feature

The intercept feature records request/response interactions for conversation tracking. Evaluators use this history to provide context to LLM operations.

| Variable | Default | Purpose |
|---|---|---|
| `WANAKU_INTERACTION_CAPACITY` | `1000` | Maximum number of interactions kept in the in-memory store |

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

Because the proxy forwards the `Origin` header unchanged, the backend's own
origin policy still applies. Ollama, for example, rejects browser-origin
requests by default — set `OLLAMA_ORIGINS` on the Ollama side to allow the
Admin UI's origin. Wanaku's own CORS filter on port 8083 controls only the
response back to the browser; it does not affect what the backend accepts.

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
- `evaluators` — evaluator feature configuration
- `plugins` — plugin service mappings owned by the plugins feature

Wanaku discovers tools, resources, and prompts from the configured forwards.

**Evaluator configuration** (see [Evaluator Engine](./evaluator-engine.md) for full details):

```yaml
evaluators:
  - name: "safety-gate"
    trigger:
      method: "tools/call"
    llm:
      operation: classify
      prompt: "Classify this tool call..."
      model: "llama3.2"
      url: "http://localhost:11434/v1"
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
- [FAQ](./faq.md) — troubleshooting common issues
