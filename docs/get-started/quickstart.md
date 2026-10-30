---
title: "Quickstart: install Wanaku and proxy an MCP tool call"
description: Install Wanaku, start the server, register an upstream MCP server as a forward, and test the MCP endpoint, in a container or with systemd.
---

# Quickstart: install Wanaku and proxy an MCP tool call

Wanaku is a governed action proxy for AI agents. It sits between agents and backend systems, where it intercepts tool calls and enforces policy, identity, and data controls. Wanaku performs requested actions on behalf of agents, so agents never access backend systems directly.

This guide takes you from installation to a running Wanaku proxy in less than 10 minutes.

> **Migrating from Classic Wanaku (Java)?** Read [Migrate from Classic Wanaku](./migration-from-classic.md) for an overview of the changes, compatibility considerations, and migration process.

## Prerequisites

You need the following items:

- A terminal for running commands
- `jq` (optional, for formatting JSON output)

## Quick Start

### 1. Install the Server

The installer downloads the early-access archive for your platform, verifies the checksum, and installs `wanaku-server` in `$HOME/bin`. It works on Linux and macOS:

```bash
curl -fsSL https://raw.githubusercontent.com/wanaku-ai/wanaku/main/get-wanaku.sh | bash
```

The `early-access` builds are development builds of the unreleased Wanaku 0.3.0. To use the headless variant, an archive, or a container image, read [Install the Wanaku server and CLI](../operate/install.md).

### 2. Install the CLI

Download the latest `wanaku` binary from the [wanaku-barn release page](https://github.com/wanaku-ai/wanaku-barn/releases/), and put it in your `PATH`:

```bash
chmod +x wanaku
sudo mv wanaku /usr/local/bin/
```

Add the `--no-auth` flag to CLI commands when the server does not require authentication.

### 3. Run the Server

```bash
wanaku-server
```

The server listens on these ports on all network interfaces:

- `8080`: management API and admin UI
- `8081`: MCP endpoint
- `8083`: inference proxy
- `8084`: A2A listener

The next steps use ports `8080` and `8081`. Read [Wanaku ports and endpoints](../reference/ports-and-endpoints.md) for the addresses.

To run Wanaku as a container or as a systemd service, read [Run Wanaku in a container](../operate/container.md) or [Run Wanaku as a systemd service](../operate/systemd.md). For a production system, read [Deploy Wanaku on Kubernetes and OpenShift](../operate/kubernetes.md).

### 4. Verify the Server

Open another terminal. Use the CLI to list the tools:

```bash
wanaku tools list --no-auth
```

The output contains an empty list. The server is running. It has not discovered tools yet.

### 5. Register a Forward and Discover Tools

Wanaku automatically discovers tools from upstream MCP servers. Use the CLI to register a forward to an MCP server:

```bash
wanaku forwards add --service="http://localhost:8180/mcp" --name my-mcp-server --namespace default --no-auth
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

### 7. Allow Your First Tool Call

Without a `wanaku.yaml` file, the server uses the fail-safe governance defaults. Discovery works, but the server denies every tool call, resource read, and prompt request.

The `wanaku.yaml` file in the repository root is a sample for development. It registers test forwards on `localhost`. Do not use it as a start point for a deployment. Use this minimal file instead. It registers one forward that uses a bearer token. It also permits unmatched actions in the `default` namespace, so that tool calls work before you write action policies or evaluators:

```yaml
forwards:
  - name: "my-mcp-server"
    address: "http://localhost:8180/mcp"
    namespace: "default"
    credential_bindings:
      discovery: my-mcp-token
      invocation: my-mcp-token

bindings:
  - id: my-mcp-token
    forward_id: my-mcp-server
    origin: "http://localhost:8180"
    mechanism:
      type: bearer
    secret_refs:
      - "env:MY_MCP_TOKEN"
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

The `no_match: allow` override removes the default-deny baseline for the `default` namespace. It applies to `tools/call`, `resources/read`, and `prompts/get`. Use it only to get started.

The action-policy filter and the evaluator filter apply `no_match` independently. Each filter must allow a request. When you remove the override and keep `no_match: deny`, a governed request needs both of these:

- An [action-policy](../govern/action-policies.md) rule that allows it.
- An [evaluator](../govern/evaluators.md) whose trigger matches it, and whose processor passes it.

If only one of the filters matches, the other filter denies the request with its no-match error. For a complete example, see [`examples/governed-mcp`](https://github.com/wanaku-ai/wanaku/blob/main/examples/governed-mcp/README.md).

Export `MY_MCP_TOKEN` before you start the server. For a container or a systemd service, read [Pass secrets to the Wanaku server](../operate/secrets.md). Remove the `credential_bindings` and `bindings` keys if the upstream server does not require a token. In a container, replace `localhost` with the host address. Read [Run Wanaku in a container](../operate/container.md#troubleshooting).

Start the server with the file:

```bash
wanaku-server --wanaku-config ./wanaku.yaml
```

If the file does not exist, the server logs `wanaku config not found, using defaults` and starts with the fail-safe defaults.

## How Wanaku Processes the Request

Each MCP request goes through the filter pipeline. The pipeline parses the JSON-RPC body and gets the namespace from the URL path. For `tools/list`, it returns the tools from the in-memory registry and does not call the upstream server. Read [Architecture](architecture.md#the-filter-pipeline) for the filter order and [Tool Routing](architecture.md#tool-routing) for `tools/call`.

## Next steps

- [Connect upstream MCP servers with forwards](../connect/forwards.md): register more upstream servers.
- [Give each team its own MCP endpoint with namespaces](../connect/namespaces.md): isolate tools for each team.
- [Govern AI agent tool calls](../govern/index.md): replace `no_match: allow` with action policies and evaluators.
- [Use the Wanaku Admin UI](../operate/admin-ui.md): manage the server from a browser.
- [Authenticate MCP clients](../operate/authentication.md): put oauth2-proxy and Keycloak in front of Wanaku.
- [FAQ and troubleshooting](../operate/troubleshooting.md): find the cause of common problems.
