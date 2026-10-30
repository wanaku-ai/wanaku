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

> [!IMPORTANT]
> The `early-access` builds are development builds of the unreleased Wanaku 0.3.0. They change often. Wanaku 0.3.0 will be the first stable release of this server. If you need a slower cadence of changes, watch the [releases page](https://github.com/wanaku-ai/wanaku/releases) for it. Earlier releases contain the Java-based Wanaku, which is deprecated.

Select a variant for your use case. You can install it in one of three ways.

**Option A: Use the server installer (Linux and macOS).** The installer downloads the early-access archive for your platform, verifies the checksum, and installs `wanaku-server` in `$HOME/bin`:

```bash
curl -fsSL https://raw.githubusercontent.com/wanaku-ai/wanaku/main/get-wanaku.sh | bash
```

Set `WANAKU_INSTALL_DIR` to install in a different directory. To run Wanaku as a systemd service, install in `/usr/local/bin`, because the unit template uses that path:

```bash
curl -fsSL https://raw.githubusercontent.com/wanaku-ai/wanaku/main/get-wanaku.sh | sudo WANAKU_INSTALL_DIR=/usr/local/bin bash
```

> **Note:** The wanaku-barn repository has a different `get-wanaku.sh` script. That script installs the Wanaku CLI, not the server.

**Option B: Download the archive manually.** Download it from the [early access release page](https://github.com/wanaku-ai/wanaku/releases/tag/early-access). The archive contains a top-level directory with the same name as the archive. Extract the archive and move the binary to a directory in your `PATH`. Replace `<archive>` with the name of the archive that you downloaded, without the `.tar.gz` extension:

```bash
tar -xzf <archive>.tar.gz
sudo install -m 0755 <archive>/wanaku-server /usr/local/bin/wanaku-server
```

**Option C: Use a container image.** Pull the image for the selected variant:

```bash
# Full (with admin UI)
podman pull quay.io/wanaku/wanaku-server:early-access

# Headless (no admin UI)
podman pull quay.io/wanaku/wanaku-server-headless:early-access
```

You can use `docker` instead of `podman` in all commands. Read [Run in a Container](#run-in-a-container) to start the image.

### 2. Download the CLI

The Wanaku CLI is a separate package. Download the latest `wanaku` binary from the [wanaku-barn release page](https://github.com/wanaku-ai/wanaku-barn/releases/).

The full variant of the server also has a **CLI Downloads** page in the admin UI. After you start the server in step 3, go to `http://localhost:8080` and select **CLI Downloads** in the side navigation. The headless variant does not include the admin UI.

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

The server listens on these ports on all network interfaces:

- `8080`: management API and admin UI
- `8081`: MCP endpoint
- `8083`: inference proxy
- `8084`: A2A listener

The next steps use ports `8080` and `8081`. Read [Endpoints](#endpoints) for the addresses.

To run Wanaku as a container or as a systemd service, read [Run in a Container](#run-in-a-container) or [Run as a systemd Service](#run-as-a-systemd-service). For a production system on Kubernetes or OpenShift, read [Production Deployment on Kubernetes or OpenShift](#production-deployment-on-kubernetes-or-openshift).

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

## Run in a Container

The container image runs the same server as the binary. The image uses these paths and ports:

| Item | Value | Notes |
|---|---|---|
| Configuration file | `/etc/wanaku/wanaku.yaml` | The working directory of the image is `/etc/wanaku`. The server ignores a file that you mount at a different path, for example `/etc/wanaku.yaml`. |
| Persistence directory | `/data/registry` | The image sets `WANAKU_PERSIST_PATH=/data/registry`. Mount a volume on this directory, not on `/data`. |
| Management API and admin UI | `8080` | |
| MCP endpoint | `8081` | |
| Inference proxy | `8083` | |
| A2A listener | `8084` | |
| User | UID `1001`, GID `0` | Mounted files must be readable by this user. |

The server can start without a `wanaku.yaml` file, but it then uses the fail-safe governance defaults. Discovery works, but the server denies every tool call, resource read, and prompt request. Without an action policy and an evaluator, nothing allows these requests. This is sufficient for a first try. For real use, write your own configuration. Start from the [minimal wanaku.yaml](#minimal-wanakuyaml) and read [Governance Posture](../govern/governance-posture.md).

Podman and Docker do not create a file for a bind mount, so create the file before you start the container:

```bash
mkdir -p ./wanaku
touch ./wanaku/wanaku.yaml
```

Start the container:

```bash
podman run -d --name wanaku \
  -p 8080:8080 -p 8081:8081 -p 8083:8083 -p 8084:8084 \
  -v wanaku-data:/data/registry \
  -v ./wanaku/wanaku.yaml:/etc/wanaku/wanaku.yaml:ro,Z \
  quay.io/wanaku/wanaku-server:early-access
```

Make sure that the server is healthy:

```bash
podman logs wanaku
curl -s http://localhost:8080/healthz
```

The `early-access` tag changes when the project publishes a new build. To update the server, pull the new image, then remove the old container and start a new one with the same `podman run` command. The `wanaku-data` volume keeps the registry data:

```bash
podman pull quay.io/wanaku/wanaku-server:early-access
podman stop wanaku
podman rm wanaku
```

### Container Troubleshooting

- **Bind mount sources:** Start a host path with `./` or `/`. Podman reads a path such as `etc/wanaku.yaml` as a named volume and fails with `names must match [a-zA-Z0-9][a-zA-Z0-9_.-]*`.
- **SELinux:** On hosts with SELinux (Fedora, RHEL), add `:Z` to each bind mount. Without it, the container cannot read the file.
- **Secrets:** The server reads `env:` references from the container environment. Pass each variable into the container. Read [Secrets and Environment Variables](#secrets-and-environment-variables).
- **Services on the host:** In the container, `localhost` is the container itself. Use `host.containers.internal` (Podman) or `host.docker.internal` (Docker) in forward addresses.
- **Connection refused from the host:** A host service that listens only on `127.0.0.1` refuses connections from the container. Configure the service to listen on `0.0.0.0`. Alternatively, start Wanaku with `--network host`. With host networking, remove the `-p` options and use `localhost` in forward addresses.
- **HTTP 403 from an upstream MCP server:** Some MCP servers accept only known `Host` header values, for DNS rebinding protection. Add the host name that Wanaku uses (for example `host.containers.internal:3000`) to the allowed hosts of the upstream server, or use `--network host`.

## Secrets and Environment Variables

The `wanaku.yaml` file refers to secrets with `env:NAME` references, for example in `llm_connections` and in credential `bindings`. The server reads each value from its own process environment when it starts.

Pass the variables to the server:

- **Container:** Add `-e NAME` to `podman run` to copy the variable from your shell. Export the variable before you run the command. You can also use `--env-file` or `podman secret create` with `--secret NAME,type=env`.
- **systemd:** Put the variables in `/etc/wanaku/wanaku.env`. Set the file mode to `0600`.

A missing variable has different results:

| Configuration | Result |
|---|---|
| `llm_connections` or `typesafe_system_one_connections` | The server rejects all connections of that type and stops with `evaluator startup failed: connection_configuration_invalid`. |
| Credential `bindings` | The server logs `secret_not_found` and skips discovery for that forward. The server continues to run. |

For credential bindings, also check these items:

- Each binding must have an `origin` with the scheme, host, and port of the forward address.
- The forward field is `credential_bindings`. When the server finds an unknown key in a forward or binding entry, it logs `ignoring unknown key in wanaku.yaml entry`. Look for this message in the startup log.

Read [Credential Brokerage](../govern/credential-brokerage.md) for the complete binding reference.

### Minimal wanaku.yaml

The `wanaku.yaml` file in the repository root is a sample for development. It registers test forwards on `localhost`. Do not use it as a start point for a deployment. Use this minimal file instead. It registers one forward that uses a bearer token. It also permits unmatched actions in the `default` namespace, so that tool calls work before you write action policies or evaluators:

```yaml
forwards:
  - name: "my-mcp-server"
    address: "http://host.containers.internal:3000/mcp"
    namespace: "default"
    credential_bindings:
      discovery: my-mcp-token
      invocation: my-mcp-token

bindings:
  - id: my-mcp-token
    forward_id: my-mcp-server
    origin: "http://host.containers.internal:3000"
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

If only one of the filters matches, the other filter denies the request with its no-match error. For a complete example, see [`examples/governed-mcp`](../../examples/governed-mcp/README.md).

Start the container with `-e MY_MCP_TOKEN` to pass the token. Remove the `credential_bindings` and `bindings` keys if the upstream server does not require a token.

## Run as a systemd Service

The [`deploy/systemd/wanaku.service`](../../deploy/systemd/wanaku.service) file is a template for convenience. Review it and adapt it to your system before you use it.

The template uses these paths:

| Item | Path |
|---|---|
| Binary | `/usr/local/bin/wanaku-server` |
| Configuration file | `/etc/wanaku/wanaku.yaml` (optional) |
| Environment file | `/etc/wanaku/wanaku.env` (optional) |
| Persistence directory | `/var/lib/wanaku` |

Install the service:

1. Install the binary in `/usr/local/bin`. Read [Download the Server](#1-download-the-server).
2. Create the `wanaku` system user:

    ```bash
    sudo useradd --system --home-dir /var/lib/wanaku --shell /usr/sbin/nologin wanaku
    ```

3. Install the unit file:

    ```bash
    sudo install -m 0644 deploy/systemd/wanaku.service /etc/systemd/system/wanaku.service
    ```

4. Optional: create `/etc/wanaku/wanaku.yaml` and `/etc/wanaku/wanaku.env`. Put secrets in `wanaku.env` and set its mode to `0600`.
5. Start the service:

    ```bash
    sudo systemctl daemon-reload
    sudo systemctl enable --now wanaku
    ```

6. Examine the logs:

    ```bash
    journalctl -u wanaku -f
    ```

## Production Deployment on Kubernetes or OpenShift

The container and systemd setups in this guide are for evaluation and small installations. For a full production system, use the Wanaku Operator. The operator is part of the [wanaku-barn](https://github.com/wanaku-ai/wanaku-barn) repository.

The operator deploys the Wanaku server and its related components from custom resources. It creates the deployments, services, configuration, secrets, routes or ingress, and RBAC resources.

Read these guides to install it:

- [Kubernetes Operator Guide](https://github.com/wanaku-ai/wanaku-barn/blob/main/docs/operator.md): the custom resources and how to deploy them.
- [Wanaku Operator on OperatorHub](https://github.com/wanaku-ai/wanaku-barn/blob/main/docs/operator-olm.md): installation with the Operator Lifecycle Manager (OLM) on OpenShift or OKD.

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
wanaku namespaces create finance --no-auth
```

Register a forward in the `finance` namespace:

```bash
wanaku forwards add --service="http://market-data-mcp:8080/mcp" --name market-data-server --namespace finance --no-auth
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

> **Note:** Only the full distribution contains the admin UI. Use the CLI or the [Management API](../reference/management-api.md) with the headless variant.

Open `http://localhost:8080` in your browser. The React-based admin UI communicates with the same management API as the CLI.

Use the admin UI to view and manage tools, namespaces, resources, prompts, and forwards.

> **Note:** Port `8080` does not require authentication. On a remote host, do not expose it to the network. Use an SSH tunnel (`ssh -L 8080:localhost:8080 <host>`) and open `http://localhost:8080`, or [enable authentication](#enable-authentication). Some browser features are available only on HTTPS or `localhost` origins. Plugins can fail when you open the UI over plain HTTP on a remote address.

### Enable Authentication

Wanaku uses [oauth2-proxy](https://github.com/oauth2-proxy/oauth2-proxy) for authentication. oauth2-proxy receives requests before it sends them to the MCP and management API ports.

Follow [Authenticated local deployment](../../deploy/auth/README.md) for the complete Docker Compose workflow. It starts Keycloak, Wanaku, and both oauth2-proxy instances, then verifies CLI access.

After setup, log in with the local CLI client and use one token with both APIs:

```bash
wanaku auth login --auth-server http://localhost:8543 --realm wanaku \
  --client-id mcp-client --username alice --password
TOKEN=$(wanaku auth token --get --plain --unmask)
wanaku mcp tool list --verbose --uri http://localhost:4181/default/mcp --token "$TOKEN"
wanaku tools list --verbose --host http://localhost:4180 --token "$TOKEN"
```

Set the namespace in the MCP endpoint path. Replace `default` with the namespace that contains the tools you want to access.

Use the password grant only for local development. The login command prompts for the user's password.

### Custom Configuration

You can start the server with optional configuration files:

```bash
wanaku-server --pipeline-config /path/to/custom-praxis.yaml \
  --wanaku-config /path/to/wanaku.yaml
```

- **`--pipeline-config`:** Praxis filter pipeline config (listeners, filter chains)
- **`--wanaku-config`:** Wanaku bootstrap config (forwards, bindings, connections, evaluators, action policies, governance). The default is `wanaku.yaml` in the working directory.

You can omit both files. The server then uses its embedded defaults. If the `--wanaku-config` file does not exist, the server logs `wanaku config not found, using defaults` and starts with the fail-safe defaults, also when you give the path explicitly.

### Environment Variables

You can configure Wanaku with environment variables. This table lists the most common variables:

| Variable | Default | Purpose |
|---|---|---|
| `WANAKU_MGMT_LISTEN` | `0.0.0.0:8080` | Management API listen address |
| `WANAKU_PERSIST_BACKEND` | `file` | File persistence backend. Set to `"none"` to disable persistence. |
| `WANAKU_PERSIST_PATH` | `$HOME/.wanaku/server` | Directory for `registry.json` |

Read [Configuration](../reference/configuration.md) for the complete list.

### Endpoints

| Endpoint       | Address                                 | Description                                                         |
|----------------|-----------------------------------------|---------------------------------------------------------------------|
| MCP            | `http://localhost:8081/{namespace}/mcp` | MCP protocol endpoint (or `/{namespace}/mcp` for namespaced access) |
| Management API | `http://localhost:8080/api/v1/...`      | CRUD for tools, resources, prompts, forwards, namespaces            |
| Admin UI       | `http://localhost:8080/admin/`          | Web dashboard                                                       |

## Authentication

Authentication is handled externally by [oauth2-proxy](https://github.com/oauth2-proxy/oauth2-proxy). Two instances sit in front of the MCP and management ports, sharing an SSO cookie:

- **Management proxy** (`:4180` → `:8080`) — protects the admin UI and REST API, admin role required
- **MCP proxy** (`:4181` → `:8081`) — protects MCP endpoints, any authenticated user

Wanaku also serves [RFC 9728](https://datatracker.ietf.org/doc/rfc9728/) OAuth Protected Resource Metadata at `/.well-known/oauth-protected-resource/{namespace}/mcp`. Set `WANAKU_AUTH_ISSUER` to your Keycloak realm URL to populate the `authorization_servers` field.

See [`deploy/auth/README.md`](../../deploy/auth/README.md) for setup instructions (Docker Compose and local development).

## Where to Go Next

- **[Architecture](./architecture.md)** — See how the filter pipeline, registry, and routing work together.
- **[Configuration](../reference/configuration.md)** — Explore the environment variables, YAML options, and configuration patterns.
- **[Authentication](../operate/authentication.md)** — Set up oauth2-proxy with Keycloak.
- **[Features](../extend/feature-crates.md)** — Enable evaluators or create a custom feature.
- **[Management API](../reference/management-api.md)** — Browse the complete REST API reference.
- **[FAQ](../operate/troubleshooting.md)** — Find answers to common questions and problems.

With the proxy running, the next step is to configure Wanaku for your environment and connect the systems your agents need.
