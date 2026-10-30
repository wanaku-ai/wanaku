---
title: "Quickstart: install Wanaku and proxy an MCP tool call"
description: Install Wanaku, connect the GitHub MCP server through a credential binding, and proxy your first governed MCP tool call in under 10 minutes.
---

# Quickstart: install Wanaku and proxy an MCP tool call

Wanaku is a governed action proxy for AI agents. It sits between agents and backend systems, where it intercepts tool calls and enforces policy, identity, and data controls. Wanaku performs requested actions on behalf of agents, so agents never access backend systems directly.

This guide takes you from installation to a governed tool call in less than 10 minutes. You put the [GitHub MCP server](https://github.com/github/github-mcp-server) behind Wanaku. Wanaku keeps your GitHub token, and the MCP client never sees it.

> **Migrating from Classic Wanaku (Java)?** Read [Migrate from Classic Wanaku](./migration-from-classic.md) for an overview of the changes, compatibility considerations, and migration process.

## Prerequisites

You need the following items:

- A terminal for running commands
- A GitHub account
- `curl`
- `jq` (optional, for formatting JSON output)

## Quick Start

### 1. Install the Server

The installer downloads the early-access archive for your platform, verifies the checksum, and installs `wanaku-server` in `$HOME/bin`. It works on Linux and macOS:

```bash
curl -fsSL https://raw.githubusercontent.com/wanaku-ai/wanaku/main/get-wanaku.sh | bash
```

The `early-access` builds are development builds of the unreleased Wanaku 0.3.0. To use the headless variant, an archive, or a container image, read [Install the Wanaku server and CLI](../operate/install.md).

### 2. Create a GitHub Token

The GitHub MCP server needs a GitHub token on every request, including discovery. Wanaku does not do the OAuth flow that the GitHub MCP server offers. Use a personal access token.

1. Create a [fine-grained personal access token](https://github.com/settings/personal-access-tokens/new). For this guide, give it read-only access to public repositories.
2. Export the token in the terminal where you start Wanaku:

    ```bash
    export GITHUB_PAT=<your-token>
    ```

Use a read-only token for this guide. The configuration in the next step permits every tool call in the `default` namespace.

### 3. Write the Configuration

Create a file named `wanaku.yaml` in the current directory:

```yaml
forwards:
  - name: github
    address: "https://api.githubcopilot.com/mcp/"
    namespace: default
    credential_bindings:
      discovery: github-pat
      invocation: github-pat

bindings:
  - id: github-pat
    forward_id: github
    origin: "https://api.githubcopilot.com:443"
    mechanism:
      type: bearer
    secret_refs:
      - "env:GITHUB_PAT"
    allowed_purposes:
      - discovery
      - invocation

governance:
  namespaces:
    default:
      # Permit actions that no action-policy rule or evaluator matches.
      # Remove this override when you add policies for the namespace.
      no_match: allow
```

The file has three parts:

- The **forward** connects Wanaku to the hosted GitHub MCP server. Wanaku discovers its tools in the `default` namespace.
- The **binding** tells Wanaku how to authenticate to the server. Wanaku reads the token from `GITHUB_PAT` and sends it as `Authorization: Bearer <token>`. Wanaku sends the token only to the `origin` in the binding.
- The **governance** override permits the tool calls. Without it, Wanaku denies every tool call, resource read, and prompt request.

The `no_match: allow` override removes the default-deny baseline for the `default` namespace. It applies to `tools/call`, `resources/read`, and `prompts/get`. Use it only to get started.

The action-policy filter and the evaluator filter apply `no_match` independently. Each filter must allow a request. When you remove the override and keep `no_match: deny`, a governed request needs both of these:

- An [action-policy](../govern/action-policies.md) rule that allows it.
- An [evaluator](../govern/evaluators.md) whose trigger matches it, and whose processor passes it.

If only one of the filters matches, the other filter denies the request with its no-match error. For a complete example, see [Govern an MCP server](../govern/govern-an-mcp-server.md).

> [!NOTE]
> The `wanaku.yaml` file in the repository root is a sample for development. It registers test forwards on `localhost`. Do not use it as a start point for a deployment.

### 4. Run the Server

Start the server in the terminal where you exported `GITHUB_PAT`:

```bash
wanaku-server --wanaku-config ./wanaku.yaml
```

The server listens on these ports on all network interfaces:

- `8080`: management API and admin UI
- `8081`: MCP endpoint
- `8083`: inference proxy
- `8084`: A2A listener

The next steps use ports `8080` and `8081`. Read [Wanaku ports and endpoints](../reference/ports-and-endpoints.md) for the addresses.

To run Wanaku as a container or as a systemd service, pass `GITHUB_PAT` to the server and mount the file. Read [Pass secrets to the Wanaku server](../operate/secrets.md), [Run Wanaku in a container](../operate/container.md) or [Run Wanaku as a systemd service](../operate/systemd.md). For a production system, read [Deploy Wanaku on Kubernetes and OpenShift](../operate/kubernetes.md).

If the file does not exist, the server logs `wanaku config not found, using defaults` and starts with the fail-safe defaults.

### 5. Check the Forward

Open another terminal. Get the forward status from the management API:

```bash
curl -s http://localhost:8080/api/v1/forwards | jq '.data[] | {name, available, statusMessage}'
```

The `github` forward shows `"available": true`. Wanaku connected to the GitHub MCP server with your token and discovered its tools.

If `available` is `false`, the `statusMessage` field tells you the cause. For example, `requires authentication (HTTP 401)` means that the server process did not get a valid token. Read [Troubleshooting Authentication Failures](../govern/credential-brokerage.md#troubleshooting-authentication-failures).

### 6. List the Tools

Send an MCP `tools/list` request to the `default` namespace:

```bash
curl -s -X POST http://localhost:8081/default/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc": "2.0", "method": "tools/list", "id": 1}' | jq '.result.tools[].name'
```

The response lists the GitHub tools, for example `get_me` and `search_repositories`. Wanaku returns the list from its registry. It does not call GitHub for this request.

### 7. Call a Tool

Call the `get_me` tool. It returns the GitHub user that owns the token:

```bash
curl -s -X POST http://localhost:8081/default/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc": "2.0", "method": "tools/call", "id": 2, "params": {"name": "get_me", "arguments": {}}}' | jq .
```

The response contains your GitHub user. The request did not contain a token. Wanaku checked the governance posture, added the token from the binding, and sent the call to GitHub. Wanaku also removes the token from the response before it returns the result. The MCP client never sees the token.

To use the tools from an agent, configure your MCP client with the endpoint `http://localhost:8081/default/mcp`.

## How Wanaku Processes the Request

Each MCP request goes through the filter pipeline. The pipeline parses the JSON-RPC body and gets the namespace from the URL path. For `tools/list`, it returns the tools from the in-memory registry. For `tools/call`, the governance filters decide first, and then Wanaku forwards the allowed call to the upstream server with the brokered credential. Read [Architecture](architecture.md#the-filter-pipeline) for the filter order and [Tool Routing](architecture.md#tool-routing) for `tools/call`.

## Next steps

- [Govern AI agent tool calls](../govern/index.md): replace `no_match: allow` with action policies and evaluators.
- [Keep API keys away from AI agents with credential brokerage](../govern/credential-brokerage.md): learn how bindings scope a credential.
- [Connect upstream MCP servers with forwards](../connect/forwards.md): register more upstream servers.
- [Give each team its own MCP endpoint with namespaces](../connect/namespaces.md): isolate tools for each team.
- [Install the Wanaku server and CLI](../operate/install.md): install the CLI, the headless variant, or a container image.
- [Use the Wanaku Admin UI](../operate/admin-ui.md): open `http://localhost:8080` to see the forward and its tools in a browser.
- [FAQ and troubleshooting](../operate/troubleshooting.md): find the cause of common problems.
