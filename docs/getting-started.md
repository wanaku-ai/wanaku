# Getting Started with Wanaku

Wanaku is a governed action proxy for AI agents. It sits between agents and backend systems, where it intercepts tool calls and enforces policy, identity, and data controls. Wanaku performs requested actions on behalf of agents, so agents never access backend systems directly.

This guide takes you from installation to a running Wanaku proxy in less than 10 minutes.

> **Migrating from Classic Wanaku (Java)?** Read [Migrating from Classic Wanaku](./migration-from-classic.md) for an overview of the changes, compatibility considerations, and migration process.

## Prerequisites

You need the following items:

- A terminal for running commands
- `jq` (optional, for formatting JSON output)

## Quick Start

### 1. Download the Server

Wanaku has two variants:

| Variant | Archive name | Description |
|---|---|---|
| **Full** | `wanaku-server-{version}-{platform}` | Includes the embedded admin UI at `http://localhost:8080/admin` |
| **Headless** | `wanaku-server-headless-{version}-{platform}` | Does not include the admin UI. Use the management API or CLI to manage Wanaku. You can also include Wanaku in other tools. |

Both variants provide the same MCP endpoint, management API, and filter pipeline. Only the full variant includes the admin UI.

Select a variant for your use case. Download it from the [early access release page](https://github.com/wanaku-ai/wanaku/releases/tag/early-access).

Make the binary executable. Move it to a directory in your `PATH`:

```bash
chmod +x wanaku-server
sudo mv wanaku-server /usr/local/bin/
```

You can also use a container image. Run the command for the selected variant:

```bash
# Full (with admin UI)
docker pull quay.io/wanaku/wanaku-server:early-access

# Headless (no admin UI)
docker pull quay.io/wanaku/wanaku-server-headless:early-access
```

### 2. Download the CLI

The Wanaku CLI is a separate package. Download the latest `wanaku` binary from the [wanaku-barn early access release page](https://github.com/wanaku-ai/wanaku-barn/releases/tag/early-access).

```bash
chmod +x wanaku
sudo mv wanaku /usr/local/bin/
```

Verify the CLI installation:

```bash
wanaku --version
```

> **Note:** The Wanaku CLI supports the Java-based Wanaku router and the Rust-based engine. Add the `--no-auth` flag when the router does not require authentication.

### 3. Run the Server

```bash
wanaku-server
```

The logs show these two services:

- **MCP endpoint:** `http://127.0.0.1:8081/default/mcp` (default namespace)
- **Management API:** `http://0.0.0.0:8080/api/v1`

### 4. Verify the Server

Open another terminal. Use the CLI to list the tools:

```bash
wanaku tools list --no-auth
```

The output contains an empty list. The server is running. It has not discovered tools yet.

### 5. Register a Forward and Discover Tools

Wanaku automatically discovers tools from upstream MCP servers. Use the CLI to register a forward to an MCP server:

```bash
wanaku forwards add --service="http://localhost:8180/mcp" --name my-mcp-server --no-auth
```

Wanaku connects to the upstream server. It discovers and registers all available tools.

List the discovered tools:

```bash
wanaku tools list --no-auth
```

The output lists the tools from the upstream server. Wanaku can now route MCP requests to these tools.

### 6. Test the MCP Endpoint

Send an MCP `tools/list` request:

```bash
curl -X POST http://localhost:8081/default/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc": "2.0", "method": "tools/list", "id": 1}'
```

The server returns a JSON-RPC response. This response lists the tools from the forward. The filter pipeline parses the request. It gets the namespace from the URL path and returns the applicable tools from the registry.

## How Wanaku Processes the Request

When you send a request to `/default/mcp`, the pipeline processes it in this sequence:

1. **CORS filter** — Adds CORS headers.
2. **MCP filter (praxis-ai)** — Parses JSON-RPC and sets metadata (`mcp.method = "tools/list"`).
3. **Namespace filter** — Extracts the `"default"` namespace from the URL path.
4. **Tool list filter** — Queries the registry for tools in the `"default"` namespace.
5. **Static response** — Returns a synthetic JSON-RPC response.

The pipeline does not call downstream services. It returns the tool list directly from the in-memory registry.

## Next Steps

Once the basic proxy is running, you can connect more MCP servers, isolate tools and resources with namespaces, and add the controls required by your environment.

### Register Additional Forwards

Register more forwards as described in step 5. Each forward connects to one upstream MCP server. Wanaku automatically discovers the tools, resources, and prompts on that server.

### Explore Namespaces

Namespaces isolate tools and resources. Each namespace has a **name**. Wanaku uses this name as the namespace segment in the URL path.

**Namespace naming rules:**

- Use only lowercase letters, numbers, and hyphens.
- Start and end the name with a letter or number.
- Use 1 to 63 characters.
- Examples: `finance`, `my-team`, `staging-42`

Create a `"finance"` namespace:

```bash
curl -X POST http://localhost:8080/api/v1/namespaces \
  -H "Content-Type: application/json" \
  -d '{"name": "finance"}'
```

Register a forward with `namespace` set to `"finance"`:

```bash
curl -X POST http://localhost:8080/api/v1/forwards \
  -H "Content-Type: application/json" \
  -d '{"name": "market-data-server", "address": "http://market-data-mcp:8080/mcp", "namespace": "finance"}'
```

Send a query to the MCP endpoint for the `finance` namespace:

```bash
curl -X POST http://localhost:8081/finance/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc": "2.0", "method": "tools/list", "id": 1}'
```

The response contains only the tools from the `market-data-server` forward. It does not contain tools from the default namespace.

All MCP endpoints use the `/{namespace}/mcp` pattern. Paths such as `/finance` and `/mcp` do not use this complete pattern. Wanaku rejects these paths.

### Use the Admin UI

> **Note:** Only the full distribution contains the admin UI. Use the CLI or the [Management API](./management-api.md) with the headless variant.

Open `http://localhost:8080` in your browser. The React-based admin UI communicates with the same management API as the CLI.

Use the admin UI to view and manage tools, namespaces, resources, prompts, and forwards.

### Enable Authentication

Wanaku uses [oauth2-proxy](https://github.com/oauth2-proxy/oauth2-proxy) for authentication. oauth2-proxy receives requests before it sends them to the MCP and management API ports.

Read [Authentication](./auth.md) for setup instructions. It explains Keycloak configuration and role-based access. It also explains how to run oauth2-proxy locally.

After you enable authentication, use a token to authenticate the CLI:

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

You can start the server with optional configuration files:

```bash
wanaku-server --pipeline-config /path/to/custom-praxis.yaml \
  --wanaku-config /path/to/wanaku.yaml
```

- **`--pipeline-config`:** Praxis filter pipeline config (listeners, filter chains)
- **`--wanaku-config`:** Wanaku bootstrap config (forwards, namespaces)

You can omit both files. The server then uses its embedded defaults.

### Environment Variables

You can configure Wanaku with environment variables. This table lists the most common variables:

| Variable | Default | Purpose |
|---|---|---|
| `WANAKU_MGMT_LISTEN` | `0.0.0.0:8080` | Management API listen address |
| `WANAKU_PERSIST_BACKEND` | `file` | File persistence backend. Set to `"none"` to disable persistence. |
| `WANAKU_PERSIST_PATH` | `$HOME/.wanaku/server` | Directory for `registry.json` |

Read [Configuration](./configuration.md) for the complete list.

## Where to Go Next

- **[Architecture](./architecture.md)** — See how the filter pipeline, registry, and routing work together.
- **[Configuration](./configuration.md)** — Explore the environment variables, YAML options, and configuration patterns.
- **[Authentication](./auth.md)** — Set up oauth2-proxy with Keycloak.
- **[Features](./features.md)** — Enable chat or create a custom feature.
- **[Management API](./management-api.md)** — Browse the complete REST API reference.
- **[FAQ](./faq.md)** — Find answers to common questions and problems.

With the proxy running, the next step is to configure Wanaku for your environment and connect the systems your agents need.
