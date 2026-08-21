# Configuration

Wanaku is configured entirely through environment variables and two optional YAML files. There's no properties file. This keeps deployment simple — set env vars in your container orchestrator or systemd unit, point at config files if needed, and you're done.

## Configuration Sources (Precedence Order)

1. **Environment variables** — highest priority, always win
2. **Runtime YAML files** — loaded from CLI args (e.g., `wanaku-server --pipeline-config praxis.yaml --wanaku-config wanaku.yaml`)
3. **Embedded defaults** — compiled into the binary

## Core Environment Variables

These control core server behavior:

| Variable | Default | Purpose |
|---|---|---|
| `WANAKU_MGMT_LISTEN` | `0.0.0.0:8080` | Management API listen address (host:port) |
| `WANAKU_INFERENCE_UPSTREAM` | `127.0.0.1:11434` | Inference backend for chat feature (OpenAI-compatible) |
| `WANAKU_PERSIST_BACKEND` | _(unset = disabled)_ | Set to `"file"` to enable file-based registry persistence |
| `WANAKU_PERSIST_PATH` | `/data/registry` | Directory where `registry.json` is read/written |
| `WANAKU_UI_PATH` | _(unset = embedded)_ | Filesystem path to admin UI override (use for local dev) |
| `WANAKU_CORS_ORIGIN` | `*` | Value for `Access-Control-Allow-Origin` on all HTTP responses (management API, MCP endpoint, and CORS preflight) |
| `WANAKU_AUTH_ISSUER` | _(unset = disabled)_ | OIDC issuer URL for RFC 9728 metadata endpoint |
| `WANAKU_INFERENCE_API_KEY` | _(unset = no auth)_ | Bearer token API key for the inference upstream. Empty means no auth. |
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

By default, the registry lives in RAM and is lost on restart. Enable file persistence to survive restarts:

```bash
export WANAKU_PERSIST_BACKEND=file
export WANAKU_PERSIST_PATH=/data/registry
```

On startup, the server loads `registry.json` from `WANAKU_PERSIST_PATH`. On shutdown (SIGTERM, SIGINT), it writes back.

**Format:**

```json
{
  "tools": [...],
  "resources": [...],
  "prompts": [...],
  "namespaces": [...],
  "forwards": [...]
}
```

**Gotcha:** This is a crude backup mechanism. If the server crashes (SIGKILL, OOM, panic), the registry is lost. For production, implement a custom persistence backend.

### Admin UI Override

The admin UI is embedded in the binary. To serve UI files from a directory on disk instead of the embedded bundle:

```bash
export WANAKU_UI_PATH=/absolute/path/to/ui/dist
wanaku-server
```

The server serves files from the specified directory instead of the embedded bundle.

**Warning:** Relative paths don't work. Use an absolute path.

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

**Use case:** A gateway (Envoy ExtProc, IBM ContextForge) sits between Wanaku and a protected downstream API. The gateway performs token exchange (e.g., Keycloak STS) using the `Authorization` header from the original request. Wanaku forwards the header so the gateway has a `subject_token` to exchange.

**Security considerations:**

- Headers that would corrupt the downstream HTTP request (`host`, `content-type`, `content-length`, `transfer-encoding`, `connection`) and rmcp-reserved headers (`accept`, `mcp-session-id`, `last-event-id`) are always blocked, even if they appear in the allowlist.
- When `Authorization` forwarding is enabled, anyone with management API access can register a tool pointing to an arbitrary URL — the caller's bearer token would then be forwarded to that URL. Secure the management API (authentication, network isolation) before enabling credential forwarding in production.

## Feature-Specific Environment Variables

Features (mcp-metadata, chat, etc.) define their own environment variables.

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

### Chat Feature

The chat feature proxies LLM chat completions to an inference backend (any OpenAI-compatible endpoint).

The chat feature uses the core `WANAKU_INFERENCE_UPSTREAM` env var — it doesn't define any of its own.

```bash
export WANAKU_INFERENCE_UPSTREAM=127.0.0.1:11434
```

The chat feature exposes these management API routes:

- `GET /api/v1/chat/llms` — list available LLMs
- `GET /api/v1/chat/{llm}/models` — list models for an LLM
- `POST /api/v1/chat/completions` — proxy chat completion request

## Pipeline Config File (praxis.yaml)

The pipeline config defines listeners, filter chains, and filter-specific settings. It's a YAML file that matches Praxis's native config format.

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

1. **CORS must be first** — otherwise CORS headers aren't added to error responses
2. **MCP must be before wanaku_namespace** — namespace filter reads `mcp.method` metadata
3. **wanaku_namespace must be before tool/resource/prompt filters** — they all read `wanaku.namespace`
4. **static_response must be last** — catch-all for unhandled requests

If you reorder filters and requests start failing, check the logs. The filter that needed metadata will error with "missing metadata key".

## Wanaku Config File (wanaku.yaml)

The Wanaku config bootstraps forwarded MCP servers on startup. It's optional — if omitted, the registry starts empty.

**Location:** Pass with `--wanaku-config`:

```bash
wanaku-server --pipeline-config /path/to/praxis.yaml --wanaku-config /path/to/wanaku.yaml
```

**Format:**

```yaml
forwards:
  - name: "upstream-mcp"
    address: "http://upstream:8080/mcp"
```

**Note:** Only `forwards` and `evaluators` are loaded from wanaku.yaml at startup. Tools, resources, and prompts are discovered from the forwarded MCP servers.

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
  replicas: 1  # each replica has its own in-memory registry; scale only with external persistence
  template:
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

The server doesn't validate env vars on startup. If you typo a var name, it silently uses the default. Enable trace logs to verify which values are being used.

## Related Docs

- [Architecture](./architecture.md) — understand the filter pipeline and registry
- [Authentication](./auth.md) — oauth2-proxy setup and Keycloak configuration
- [Features](./features.md) — configure chat and custom features
- [Management API](./management-api.md) — API routes that respect configuration
- [FAQ](./faq.md) — troubleshooting common issues
