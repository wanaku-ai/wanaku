# Getting Started with Wanaku Praxis

Wanaku Praxis is a Rust-based MCP (Model Context Protocol) server that routes AI agent requests through a sophisticated filter pipeline. Think of it as the bouncer at the club: it checks IDs, enforces the rules, and makes sure everyone gets to the right place.

This guide gets you from zero to running MCP server in under 10 minutes.

## Prerequisites

You'll need:

- **Rust 1.96 or later** — check with `rustc --version`
- **Cargo** (comes with Rust)
- A terminal where you're comfortable running commands
- Optional: **Docker** or **Podman** if you want to run the classic Wanaku backend alongside Praxis

That's it. The admin UI dev workflow also uses Yarn, but that's only needed if you're modifying the frontend.

## Quick Start: Get It Running

### 1. Clone and Build

```bash
git clone https://github.com/wanaku-ai/wanaku-praxis.git
cd wanaku-praxis
cargo build --release
```

The first build takes a few minutes—Rust is compiling everything from scratch. Grab a coffee. Subsequent builds are fast.

### 2. Run the Server

```bash
cargo run --release
```

You should see log output indicating two services started:
- **MCP endpoint:** `http://127.0.0.1:8081/mcp`
- **Management API:** `http://0.0.0.0:8080/api/v1`

### 3. Verify It's Alive

Open another terminal and hit the management API:

```bash
curl http://localhost:8080/api/v1/tools
```

Expected response:

```json
{"data": [], "error": null}
```

That empty array means the server is running, but you haven't registered any tools yet. Let's fix that.

### 4. Register Your First Tool

Create a simple echo tool:

```bash
curl -X POST http://localhost:8080/api/v1/tools \
  -H "Content-Type: application/json" \
  -d '{
    "name": "echo",
    "type": "echo-tool",
    "uri": "echo-tool://echo",
    "description": "Echoes back whatever you send it",
    "input_schema": {
      "type": "object",
      "properties": {
        "message": {"type": "string"}
      },
      "required": ["message"]
    }
  }'
```

Now list tools again:

```bash
curl http://localhost:8080/api/v1/tools
```

You'll see your echo tool in the response. The server is ready to route MCP requests.

### 5. Test the MCP Endpoint

Send an MCP `tools/list` request:

```bash
curl -X POST http://localhost:8081/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc": "2.0", "method": "tools/list", "id": 1}'
```

You'll get a JSON-RPC response listing your echo tool. The filter pipeline parsed your request, checked the namespace (defaulted to `"default"`), and returned the registered tools.

## What Just Happened?

When you hit `/mcp`, your request flowed through this pipeline:

1. **CORS filter** — added CORS headers
2. **MCP filter (praxis-ai)** — parsed JSON-RPC, set metadata (`mcp.method = "tools/list"`)
3. **Namespace filter** — extracted namespace from URL path (default: `"default"`)
4. **Tool list filter** — queried the registry for tools in the `"default"` namespace
5. **Static response** — synthetic JSON-RPC reply

No downstream services were called. The tool list is served directly from the in-memory registry.

## Next Steps

### Add a gRPC Tool Service

The echo tool above is a stub—it doesn't actually execute. To call a real service, you need to:

1. Run a gRPC service that implements the tool invoker protocol (see `apis/src/proto/toolrequest.proto`)
2. Register the service in the registry
3. Create a tool with `type` matching the service name

See [Architecture](./architecture.md) for how gRPC tool routing works.

### Explore Namespaces

Namespaces isolate tools. Create a tool in the `"finance"` namespace:

```bash
curl -X POST http://localhost:8080/api/v1/namespaces \
  -H "Content-Type: application/json" \
  -d '{"name": "finance"}'

curl -X POST http://localhost:8080/api/v1/tools \
  -H "Content-Type: application/json" \
  -d '{
    "name": "get-stock-price",
    "namespace": "finance",
    "type": "market-data",
    "uri": "market://stocks",
    "description": "Retrieves current stock prices",
    "input_schema": {"type": "object", "properties": {"symbol": {"type": "string"}}}
  }'
```

Now query the finance namespace:

```bash
curl -X POST http://localhost:8081/finance/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc": "2.0", "method": "tools/list", "id": 1}'
```

Only the `get-stock-price` tool appears. The default namespace tools are invisible here.

### Use the Admin UI

Open `http://localhost:8080` in your browser. You'll see the React-based admin UI embedded in the server binary. It talks to the same management API you just used via curl.

From here you can view and manage tools, namespaces, resources, and services.

### Enable Authentication

Wanaku Praxis uses [oauth2-proxy](https://github.com/oauth2-proxy/oauth2-proxy) for authentication. oauth2-proxy runs as a reverse proxy in front of the MCP and management API ports.

**Quick start with docker-compose:**

```bash
cd deploy/auth

# Generate a cookie secret
openssl rand -base64 32

# Edit oauth2-proxy-shared.env:
#   - Set OAUTH2_PROXY_COOKIE_SECRET to the generated secret
#   - Set OAUTH2_PROXY_CLIENT_SECRET to your Keycloak client secret

# Place your Keycloak realm export as wanaku-realm.json in this directory

# Start the stack (Keycloak + oauth2-proxy + Praxis)
docker compose -f docker-compose-auth.yml up
```

**Access:**

- Admin UI: `http://localhost:4181/admin/`
- MCP endpoint: `http://localhost:4180/mcp`
- Public MCP (no auth): `http://localhost:4180/public/mcp`

**Test with CLI:**

```bash
# Get a token from Keycloak
TOKEN=$(curl -s -X POST http://localhost:8543/realms/wanaku/protocol/openid-connect/token \
  -d grant_type=password \
  -d client_id=wanaku-mcp-router \
  -d username=test \
  -d password=test | jq -r .access_token)

# Use with MCP endpoint
curl -H "Authorization: Bearer $TOKEN" http://localhost:4180/mcp \
  -d '{"jsonrpc":"2.0","method":"tools/list","id":1}'
```

**Architecture:**

Two oauth2-proxy instances with shared SSO:
- **oauth2-proxy-mcp** (port 4180 → 8081) — MCP endpoint, requires `mcp-user` role
- **oauth2-proxy-mgmt** (port 4181 → 8080) — management API/UI, requires `admin` role

See `deploy/auth/README.md` for detailed setup, role-based access, and local development without Docker.

## Building for Production

The `cargo run` command uses the debug build. For production, use:

```bash
cargo build --release
./target/release/wanaku-praxis
```

The release binary is optimized compared to debug builds.

### Custom Configuration

The server embeds `server/src/default.yaml` at compile time. To override:

```bash
./target/release/wanaku-praxis --praxis-config /path/to/custom-praxis.yaml \
  --wanaku-config /path/to/wanaku.yaml
```

- **`--praxis-config`:** Praxis filter pipeline config (listeners, filter chains)
- **`--wanaku-config`:** Wanaku bootstrap config (tools, services, namespaces)

Both are optional. If omitted, embedded defaults are used.

### Environment Variables

All configuration is environment-first. Common vars:

| Variable | Default | Purpose |
|---|---|---|
| `WANAKU_MGMT_LISTEN` | `0.0.0.0:8080` | Management API listen address |
| `WANAKU_PERSIST_BACKEND` | _(unset)_ | Set to `"file"` to persist registry to disk |
| `WANAKU_PERSIST_PATH` | `/data/registry` | Directory for `registry.json` |
| `WANAKU_CLASSIC_URL` | _(unset)_ | Classic proxy base URL (for hybrid mode) |

See [Configuration](./configuration.md) for the full list.

## Common Gotchas

### 1. "Address already in use" on startup

Something else is using port 8081 or 8080. Check with:

```bash
lsof -ti :8081
lsof -ti :8080
```

Kill the process, or change the management listen address via `WANAKU_MGMT_LISTEN`. The MCP listener address is defined in `server/src/default.yaml` — use a custom config file to override it.

### 2. Changing `default.yaml` doesn't trigger rebuild

Cargo doesn't track `include_str!` dependencies. After editing `server/src/default.yaml`, run:

```bash
touch server/src/lib.rs
cargo build
```

### 3. Filter returns empty JSON-RPC response

Check that:
- The MCP filter has `on_invalid: continue` in `default.yaml`
- You're sending valid JSON-RPC (must have `"jsonrpc": "2.0"`, `"method"`, and `"id"`)
- The URL path matches a configured namespace (`/mcp` → `"default"`, `/{namespace}/mcp` → `"{namespace}"`)

Enable trace logs to see what the filters are doing:

```bash
RUST_LOG=wanaku_praxis_filters=trace cargo run
```

### 4. Management API returns 404 for valid routes

The management API uses Pingora's `ServeHttp` trait, not axum. Routes are dispatched via a guard pattern in `server/src/management/mod.rs`. If you added a new route but forgot to register it in the dispatcher, it 404s.

Check the route enum in `routes.rs` and the `dispatch` function in `mod.rs`.

## Troubleshooting

**Server crashes with "thread 'main' panicked":**

Praxis denies `unsafe_code`, `unwrap_used`, `expect_used`, and `panic` at the crate level. A panic means a logic bug. File an issue with the stack trace and steps to reproduce.

**Tool calls time out:**

gRPC calls have a default deadline. If your service takes too long, check the gRPC pool configuration or optimize the service.

**Tools don't appear in `/mcp` but show up in `/api/v1/tools`:**

Check the namespace. Tools registered with `namespace: "finance"` only appear in `/finance/mcp`, not `/mcp`.

**LLM-based features (chat) don't work:**

Verify:
- The LLM endpoint is reachable (`curl http://localhost:11434/v1/models`)
- Environment variables are set correctly
- The feature is configured via the management API or `wanaku.yaml`

## Where to Go Next

- **[Architecture](./architecture.md)** — understand the filter pipeline, registry, and routing
- **[Configuration](./configuration.md)** — all env vars, YAML options, and config patterns
- **[Features](./features.md)** — enable chat and create custom features
- **[Management API](./management-api.md)** — full REST API reference
- **[Admin UI](./admin-ui.md)** — customize the embedded React admin interface

You now have a running MCP server. The rest is about configuring it to match your environment.
