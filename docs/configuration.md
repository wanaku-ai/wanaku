# Configuration

Wanaku Praxis is configured entirely through environment variables and two optional YAML files. There's no properties file. This keeps deployment simple—set env vars in your container orchestrator or systemd unit, point at config files if needed, and you're done.

## Configuration Sources (Precedence Order)

1. **Environment variables** — highest priority, always win
2. **Runtime YAML files** — loaded from CLI args (e.g., `cargo run -- --praxis-config praxis.yaml --wanaku-config wanaku.yaml`)
3. **Embedded defaults** — `server/src/default.yaml` compiled into the binary

## Core Environment Variables

These are defined in `apis/src/config.rs` and accessed via `wanaku_praxis_apis::config::ENV`:

| Variable | Default | Purpose |
|---|---|---|
| `WANAKU_MGMT_LISTEN` | `0.0.0.0:8080` | Management API listen address (host:port) |
| `WANAKU_INFERENCE_UPSTREAM` | `127.0.0.1:11434` | Inference backend for chat feature (OpenAI-compatible) |
| `WANAKU_PERSIST_BACKEND` | _(unset = disabled)_ | Set to `"file"` to enable file-based registry persistence |
| `WANAKU_PERSIST_PATH` | `/data/registry` | Directory where `registry.json` is read/written |
| `WANAKU_CLASSIC_URL` | _(unset = disabled)_ | Classic Wanaku backend base URL (e.g., `http://classic:8080`) |
| `WANAKU_UI_PATH` | _(unset = embedded)_ | Filesystem path to admin UI override (use for local dev) |
| `WANAKU_AUTH_ISSUER` | _(unset = disabled)_ | OIDC issuer URL for RFC 9728 metadata endpoint |
| `WANAKU_INFERENCE_API_KEY` | _(unset = no auth)_ | Bearer token API key for the inference upstream. Empty means no auth. |

**Example:**

```bash
export WANAKU_MGMT_LISTEN=0.0.0.0:9091
export WANAKU_PERSIST_BACKEND=file
export WANAKU_PERSIST_PATH=/var/lib/wanaku/registry
export WANAKU_CLASSIC_URL=http://localhost:8080
cargo run --release
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

Useful when running Praxis behind a reverse proxy (nginx, Envoy) that handles external traffic.

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
  "forwards": [...],
  "services": [...]
}
```

**Gotcha:** This is a crude backup mechanism. If the server crashes (SIGKILL, OOM, panic), the registry is lost. For production, use hybrid mode (see below) or implement a custom persistence backend.

### Classic Wanaku Integration

Point `WANAKU_CLASSIC_URL` at a classic Wanaku backend to offload persistence and advanced features:

```bash
export WANAKU_CLASSIC_URL=http://classic-wanaku:8080
```

When set, Praxis proxies certain management API calls (e.g., service catalog operations) to the classic backend. The MCP endpoint remains pure Praxis—no proxying.

**Hybrid mode workflow:**

1. Register tools/services in classic Wanaku via its REST API
2. Praxis queries classic backend on startup to populate its registry
3. MCP requests hit Praxis (port 8081), tool calls are routed to gRPC services
4. Management API writes go to Praxis AND classic (eventual consistency)

This is experimental. Not all classic features are supported.

### Admin UI Override

The admin UI is embedded in the binary via `rust_embed`. To develop the UI locally without rebuilding the server:

```bash
cd ui/admin
yarn run build
```

Then start the server with:

```bash
export WANAKU_UI_PATH=/absolute/path/to/ui/admin/dist
cargo run
```

The server serves files from `dist/` instead of the embedded bundle. Changes to the UI (after `yarn run build`) are visible without restarting.

**Warning:** Relative paths don't work. Use an absolute path or the server panics on startup.

## Feature-Specific Environment Variables

Features (mcp-metadata, chat, etc.) own their env vars. They're NOT in `apis/src/config.rs`. Each feature reads its own config in `load_env_config()`.

### Authentication with oauth2-proxy

Wanaku Praxis uses [oauth2-proxy](https://github.com/oauth2-proxy/oauth2-proxy) for authentication, not embedded code. Two oauth2-proxy instances run as sidecars in front of ports 8081 (MCP) and 8080 (management API).

**MCP Metadata Feature:**

The only auth-related configuration in Praxis itself is the OIDC issuer URL for RFC 9728 metadata:

| Variable | Default | Purpose |
|---|---|---|
| `WANAKU_AUTH_ISSUER` | _(unset = disabled)_ | OIDC issuer URL (e.g., `http://localhost:8543/realms/wanaku`) |

**Example:**

```bash
export WANAKU_AUTH_ISSUER=http://localhost:8543/realms/wanaku
```

When set, the endpoint `/.well-known/oauth-protected-resource/{namespace}/mcp` returns OAuth server metadata. When unset, the endpoint returns 404.

**oauth2-proxy deployment:**

For oauth2-proxy configuration, cookie secrets, role-based access, and docker-compose setup, see `deploy/auth/README.md`.

**Quick start:**

```bash
cd deploy/auth
# Edit oauth2-proxy-shared.env with your Keycloak client secret
docker compose -f docker-compose-auth.yml up
```

Access:
- Admin UI: `http://localhost:4181/admin/`
- MCP endpoint: `http://localhost:4180/mcp`
- Public MCP (no auth): `http://localhost:4180/public/mcp`

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

## Praxis Config File (praxis.yaml)

The Praxis config defines listeners, filter chains, and filter-specific settings. It's a YAML file that matches Praxis's native config format.

**Default location:** `server/src/default.yaml` (embedded at compile time)

**Override:** Pass with `--praxis-config`:

```bash
cargo run -- --praxis-config /path/to/custom-praxis.yaml
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
      - filter: wanaku_mcp_init
      - filter: wanaku_tool_assembly
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
      cert_path: /etc/praxis/cert.pem
      key_path: /etc/praxis/key.pem
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

The Wanaku config bootstraps tools, resources, prompts, namespaces, and services on startup. It's optional—if omitted, the registry starts empty.

**Location:** Pass with `--wanaku-config`:

```bash
cargo run -- --praxis-config /path/to/praxis.yaml --wanaku-config /path/to/wanaku.yaml
```

**Format:**

```yaml
tools:
  - name: "echo"
    type: "echo-tool"
    uri: "echo-tool://echo"
    description: "Echoes a message"
    namespace: "default"
    input_schema:
      type: object
      properties:
        message:
          type: string
      required: [message]

resources:
  - name: "readme"
    type: "file"
    uri: "file:///README.md"
    description: "Project README"
    namespace: "default"

prompts:
  - name: "code-review"
    description: "Review code for issues"
    namespace: "default"
    messages:
      - role: "user"
        content:
          type: "text"
          text: "Review this code: {{code}}"

namespaces:
  - name: "finance"
    description: "Financial tools and resources"

forwards:
  - name: "upstream-mcp"
    address: "http://upstream:8080/mcp"

services:
  - name: "echo-tool"
    address: "localhost:9191"
    service_type: "tool-invoker"
```

### Tool Definitions

**Minimal:**

```yaml
tools:
  - name: "my-tool"
    type: "my-service"
    uri: "my-service://operation"
    description: "Does a thing"
```

**With input schema:**

```yaml
tools:
  - name: "http-get"
    type: "http"
    uri: "http://example.com/api/{path}"
    description: "HTTP GET request"
    input_schema:
      type: object
      properties:
        path:
          type: string
          description: "API endpoint path"
      required: [path]
```

**Namespace isolation:**

```yaml
tools:
  - name: "get-stock-price"
    type: "market-data"
    uri: "market://stocks"
    namespace: "finance"
    input_schema:
      type: object
      properties:
        symbol:
          type: string
```

This tool only appears in `/finance/mcp`, not `/mcp`.

**MCP forward:**

```yaml
tools:
  - name: "upstream-tool"
    type: "mcp-forward"
    uri: "http://upstream:8080/mcp"
    description: "Tool from upstream MCP server"
```

When called, Praxis forwards the request to `http://upstream:8080/mcp` via HTTP POST.

### Service Definitions

Services are gRPC endpoints that implement the `ToolInvoker` or `ResourceProvider` protocol.

**Format:**

```yaml
services:
  - name: "echo-tool"
    address: "localhost:9191"
    service_type: "tool-invoker"
```

**Fields:**

- `name` — must match the `type` field in tool definitions
- `address` — `host:port` of the gRPC server
- `service_type` — one of: `"tool-invoker"`, `"resource-provider"`, `"multi-capability"`

**Example with multiple services:**

```yaml
services:
  - name: "http"
    address: "http-service:9000"
    service_type: "tool-invoker"
  - name: "camel"
    address: "camel-service:9001"
    service_type: "tool-invoker"
  - name: "file"
    address: "file-provider:9002"
    service_type: "resource-provider"
```

## Common Configuration Patterns

### Development (Local Machine)

```bash
# No persistence, embedded UI, inference backend for LLMs
export WANAKU_INFERENCE_UPSTREAM=http://localhost:11434
cargo run
```

### Production (Docker)

```dockerfile
FROM rust:1.96 as builder
WORKDIR /build
COPY . .
RUN cargo build --release

FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y ca-certificates && rm -rf /var/lib/apt/lists/*
COPY --from=builder /build/target/release/wanaku-praxis /usr/local/bin/
COPY praxis.yaml /etc/praxis/praxis.yaml
COPY wanaku.yaml /etc/praxis/wanaku.yaml

ENV WANAKU_MGMT_LISTEN=0.0.0.0:8080
ENV WANAKU_PERSIST_BACKEND=file
ENV WANAKU_PERSIST_PATH=/data/registry

VOLUME /data
EXPOSE 8081 8080
CMD ["/usr/local/bin/wanaku-praxis", "--praxis-config", "/etc/praxis/praxis.yaml", "--wanaku-config", "/etc/praxis/wanaku.yaml"]
```

Run with:

```bash
docker run -v /var/lib/wanaku:/data \
  -p 8081:8081 -p 8080:8080 \
  wanaku-praxis:latest
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
    tools:
      - name: "echo"
        type: "echo-tool"
        uri: "echo-tool://echo"
        description: "Echoes a message"
```

**Deployment:**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: wanaku-praxis
spec:
  replicas: 1  # each replica has its own in-memory registry; scale only with external persistence
  template:
    spec:
      containers:
      - name: wanaku
        image: wanaku-praxis:latest
        env:
        - name: WANAKU_MGMT_LISTEN
          value: "0.0.0.0:8080"
        - name: WANAKU_PERSIST_BACKEND
          value: "file"
        - name: WANAKU_PERSIST_PATH
          value: "/data/registry"
        volumeMounts:
        - name: config
          mountPath: /etc/praxis
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
RUST_LOG=trace cargo run
```

This logs all filter decisions, metadata reads/writes, and registry operations. Output is verbose—use sparingly.

**Filter-specific logs:**

```bash
RUST_LOG=wanaku_praxis_filters=trace cargo run
```

### Verify Environment Variables

The server doesn't validate env vars on startup. If you typo a var name, it silently uses the default.

To check what the server sees:

```rust
// Add to main.rs before server start
println!("WANAKU_MGMT_LISTEN: {:?}", wanaku_praxis_apis::config::ENV.mgmt_listen);
println!("WANAKU_PERSIST_BACKEND: {:?}", wanaku_praxis_apis::config::ENV.persist_backend);
```

Rebuild and run. The server prints the effective config.

### Rebuild Gotcha: include_str!

Changes to `server/src/default.yaml` don't trigger rebuilds because Cargo doesn't track `include_str!` dependencies.

After editing `default.yaml`:

```bash
touch server/src/lib.rs
cargo build
```

## Related Docs

- [Architecture](./architecture.md) — understand the filter pipeline and registry
- [Features](./features.md) — configure chat and custom features
- [Management API](./management-api.md) — API routes that respect configuration
