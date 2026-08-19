# Getting Started with Wanaku

Wanaku is a governed action proxy for AI agents. It sits between agents and the systems they act on, intercepting tool calls and enforcing policy, identity, and data controls before actions reach backend systems. The agent never touches backends directly — Wanaku runs the work on its behalf.

This guide gets you from zero to a running Wanaku proxy in under 10 minutes.

## Prerequisites

You'll need:

- A terminal where you're comfortable running commands
- `jq` (optional, for formatting JSON output)

## Quick Start: Get It Running

### 1. Download the Server

Wanaku ships in two variants:

| Variant | Archive name | Description |
|---|---|---|
| **Full** | `wanaku-server-{version}-{platform}` | Includes the embedded admin UI at `http://localhost:8080/admin` |
| **Headless** | `wanaku-server-headless-{version}-{platform}` | No admin UI — API and CLI only. Aimed at builders and integrators who embed Wanaku into their own tooling or manage it entirely through the management API and CLI. |

Both variants are functionally identical: same MCP endpoint, same management API, same filter pipeline. The only difference is whether the admin UI is served.

Download the variant that fits your use case from the [early access release page](https://github.com/wanaku-ai/wanaku/releases/tag/early-access).

Make it executable and place it in your PATH:

```bash
chmod +x wanaku-server
sudo mv wanaku-server /usr/local/bin/
```

Container images are also available for both variants:

```bash
# Full (with admin UI)
docker pull quay.io/wanaku/wanaku-server:early-access

# Headless (no admin UI)
docker pull quay.io/wanaku/wanaku-server-headless:early-access
```

### 2. Download the CLI

The Wanaku CLI is distributed separately. Download the latest `wanaku` binary from the [wanaku-barn early access release page](https://github.com/wanaku-ai/wanaku-barn/releases/tag/early-access).

```bash
chmod +x wanaku
sudo mv wanaku /usr/local/bin/
```

Verify the CLI is installed:

```bash
wanaku --version
```

> **Note:** The Wanaku CLI was originally built for the Java-based Wanaku router and is compatible with the Rust-based engine. When running against a router without authentication enabled, use the `--no-auth` flag with CLI commands.

### 3. Run the Server

```bash
wanaku-server
```

You should see log output indicating two services started:
- **MCP endpoint:** `http://127.0.0.1:8081/default/mcp` (default namespace)
- **Management API:** `http://0.0.0.0:8080/api/v1`

### 4. Verify It's Alive

Open another terminal and use the CLI to check for tools:

```bash
wanaku tools list --no-auth
```

The output should show an empty list — the server is running, but no tools have been discovered yet. Let's fix that.

### 5. Register a Forward (Auto-Discover Tools)

Tools are discovered automatically from upstream MCP servers. Register a forward to an MCP server using the CLI:

```bash
wanaku forwards add --service="http://localhost:8180/mcp" --name my-mcp-server --no-auth
```

Wanaku connects to the upstream server, discovers all available tools, and registers them automatically.

List the discovered tools:

```bash
wanaku tools list --no-auth
```

You'll see the tools discovered from the upstream server. The server is now ready to route MCP requests to those tools.

### 6. Test the MCP Endpoint

Send an MCP `tools/list` request:

```bash
curl -X POST http://localhost:8081/default/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc": "2.0", "method": "tools/list", "id": 1}'
```

You'll get a JSON-RPC response listing the tools discovered from your forward. The filter pipeline parsed your request, extracted the namespace from the URL path, and returned the tools from the registry.

## What Just Happened?

When you hit `/default/mcp`, your request flowed through this pipeline:

1. **CORS filter** — added CORS headers
2. **MCP filter (praxis-ai)** — parsed JSON-RPC, set metadata (`mcp.method = "tools/list"`)
3. **Namespace filter** — extracted namespace `"default"` from URL path
4. **Tool list filter** — queried the registry for tools in the `"default"` namespace
5. **Static response** — synthetic JSON-RPC reply

No downstream services were called. The tool list is served directly from the in-memory registry.

## Next Steps

### Register Additional Forwards

To discover tools from additional MCP servers, register more forwards the same way you did in step 5. Each forward connects to an upstream MCP server and auto-discovers its tools, resources, and prompts.

### Explore Namespaces

Namespaces isolate tools and resources. Each namespace is identified by a **name** that doubles as its URL path segment.

**Namespace naming rules:**
- Lowercase letters, numbers, and hyphens only
- Must start and end with a letter or number
- 1 to 63 characters long
- Examples: `finance`, `my-team`, `staging-42`

Create a `"finance"` namespace and register a forward that assigns discovered tools to it:

```bash
curl -X POST http://localhost:8080/api/v1/namespaces \
  -H "Content-Type: application/json" \
  -d '{"name": "finance"}'

wanaku forwards add --service="http://market-data-mcp:8080/mcp" --name market-data-server --no-auth
```

Query the finance namespace by hitting the namespace-specific MCP endpoint:

```bash
curl -X POST http://localhost:8081/finance/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc": "2.0", "method": "tools/list", "id": 1}'
```

Only tools from the `market-data-server` forward appear. The default namespace tools are invisible here.

All MCP endpoints follow the `/{namespace}/mcp` pattern — there is no shortcut. Bare paths like `/finance` or `/mcp` are rejected.

### Use the Admin UI

> **Note:** The admin UI is only available in the full distribution. If you're running the headless variant, manage everything through the CLI or the [Management API](./management-api.md).

Open `http://localhost:8080` in your browser. You'll see the React-based admin UI embedded in the server binary. It talks to the same management API you just used via the CLI.

From here you can view and manage tools, namespaces, resources, prompts, and forwards.

### Enable Authentication

Wanaku uses [oauth2-proxy](https://github.com/oauth2-proxy/oauth2-proxy) for authentication. oauth2-proxy runs as a reverse proxy in front of the MCP and management API ports.

See [Authentication](./auth.md) for detailed setup instructions, including Keycloak configuration, role-based access, and running oauth2-proxy locally.

Once authentication is enabled, you can authenticate the CLI:

```bash
# Get a token from Keycloak
TOKEN=$(curl -s -X POST http://localhost:8543/realms/wanaku/protocol/openid-connect/token \
  -d grant_type=password \
  -d client_id=wanaku-mcp-router \
  -d client_secret=<your-secret> \
  -d username=test \
  -d password=test | jq -r .access_token)

# Use the CLI with a token
wanaku tools list --host http://localhost:4181 --token $TOKEN
```

### Custom Configuration

The server accepts optional configuration files:

```bash
wanaku-server --pipeline-config /path/to/custom-praxis.yaml \
  --wanaku-config /path/to/wanaku.yaml
```

- **`--pipeline-config`:** Praxis filter pipeline config (listeners, filter chains)
- **`--wanaku-config`:** Wanaku bootstrap config (forwards, namespaces)

Both are optional. If omitted, embedded defaults are used.

### Environment Variables

All configuration is environment-first. Common vars:

| Variable | Default | Purpose |
|---|---|---|
| `WANAKU_MGMT_LISTEN` | `0.0.0.0:8080` | Management API listen address |
| `WANAKU_PERSIST_BACKEND` | _(unset)_ | Set to `"file"` to persist registry to disk |
| `WANAKU_PERSIST_PATH` | `/data/registry` | Directory for `registry.json` |

See [Configuration](./configuration.md) for the full list.

## Where to Go Next

- **[Architecture](./architecture.md)** — understand the filter pipeline, registry, and routing
- **[Configuration](./configuration.md)** — all env vars, YAML options, and config patterns
- **[Authentication](./auth.md)** — set up oauth2-proxy with Keycloak
- **[Features](./features.md)** — enable chat and create custom features
- **[Management API](./management-api.md)** — full REST API reference
- **[FAQ](./faq.md)** — common issues and troubleshooting

You now have a running Wanaku proxy. The rest is about configuring it to match your environment.
