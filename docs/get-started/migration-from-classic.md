---
title: Migrate from Classic Wanaku
description: "Move from Classic Wanaku, the Java-based wanaku-barn line, to the Rust-based Wanaku: what changed, what works differently, and what to do."
---

# Migrating from Classic Wanaku

This guide is for users moving from the Java-based Wanaku ("Classic Wanaku", `wanaku-barn`) to the Rust-based Wanaku. In this guide, "Wanaku" means the Rust-based server, and "Praxis" means only the proxy framework that it uses. It covers what changed, what works differently, and what you need to do to migrate.

If you're new to Wanaku and have never used the Java version, skip this doc — start with [Getting Started](./quickstart.md) instead.

Use this guide if you operate Classic Wanaku and need to plan or perform a migration. It compares the two systems, identifies compatibility limits, and provides a migration checklist.

## Contents

- [What Changed and Why](#what-changed-and-why)
- [At a Glance](#at-a-glance)
- [Installation](#installation)
- [Configuration](#configuration)
- [Namespaces](#namespaces)
- [MCP Endpoints](#mcp-endpoints)
- [Tool Management](#tool-management)
- [Forward Management](#forward-management)
- [Management API](#management-api)
- [Authentication](#authentication)
- [Persistence](#persistence)
- [Service Catalogs](#service-catalogs)
- [Kubernetes Deployment](#kubernetes-deployment)
- [CLI Compatibility](#cli-compatibility)
- [New Features in Wanaku](#new-features-in-wanaku)
- [Breaking Changes Checklist](#breaking-changes-checklist)

## What Changed and Why

Classic Wanaku is a Java/Quarkus application with Infinispan persistence, Camel route execution, service catalogs, and a Kubernetes operator. It's fully featured and production-proven.

Wanaku is a ground-up rewrite in Rust, built on the Praxis proxy framework (Cloudflare Pingora under the hood). The core idea is the same — a governed action proxy that sits between AI agents and backend systems — but the architecture is fundamentally different. Wanaku introduces a composable filter pipeline that enables pluggable governance features (evaluators, policy gates, interaction tracking) that would be difficult to express in the classic architecture.

**What carries over:**

- MCP protocol support (tools, resources, prompts)
- Management API (`/api/v1/`) with the same response envelope
- Namespace isolation
- Forward-based tool discovery
- Admin UI (React + Carbon Design System)
- CLI compatibility (the `wanaku` CLI works with both)

**What's new:**

- Composable filter pipeline with metadata-driven routing
- WASM-based evaluator engine for policy enforcement
- Raw inference proxy for OpenAI-compatible chat completions
- Interaction recording of inference traffic for conversation history
- In-memory metrics collection
- A single binary that needs only libc — no JVM

**What's not in Wanaku itself (still in wanaku-barn):**

- Service catalogs and service templates — managed by the wanaku-barn backend, which runs alongside Wanaku
- Camel route execution — use a Camel-based MCP server as a forward
- gRPC communication between services
- Infinispan persistence (Wanaku uses file-based snapshots)
- Built-in Keycloak integration (Wanaku uses oauth2-proxy as a sidecar)

## At a Glance

| Area | Classic Wanaku (Java) | Wanaku (Rust) |
|---|---|---|
| Runtime | Quarkus + GraalVM native | Pingora + Tokio async |
| Binary | JAR or native image + downstream MCP servers | A single binary that needs only libc |
| MCP endpoint | `/{namespace}/mcp/sse` or `/{namespace}/mcp/` | `/{namespace}/mcp` |
| Management API | Port 8180 (Quarkus HTTP, configurable) | Port 8080 (Pingora ServeHttp) |
| MCP port | 8180 (same as mgmt) | 8081 (separate listener) |
| Config format | `application.properties` | Environment variables, `wanaku.yaml`, a pipeline YAML file, and CLI flags |
| Persistence | Infinispan (embedded) | File-based JSON snapshots |
| Authentication | Built-in Keycloak OIDC | External oauth2-proxy |
| Namespaces | Fixed 10 slots (`ns-0`..`ns-9`) + `default` + `public` | DNS-label names, unlimited + `default` (no `public`) |
| Tool execution | Multiple provider types (HTTP, exec, Camel, MCP forward) | MCP forward only |
| Service catalogs | Full lifecycle (init/expose/package/deploy) | Managed by wanaku-barn (runs alongside Wanaku) |
| K8s operator | CRDs (`WanakuRouter`, `WanakuServiceCatalog`) | Same operator; `WanakuRouter` deploys Wanaku (`spec.server`) and, optionally, Wanaku Barn (`spec.barn`) |
| Policy enforcement | None | WASM evaluator + LLM classification |
| CLI | `wanaku` (Picocli + Quarkus) | Same `wanaku` CLI (compatible) |

## Installation

### Classic

Classic Wanaku runs multiple processes: the router backend, downstream MCP servers (tool invokers, resource providers), and optionally Keycloak.

Run the router JAR directly:

```bash
java -jar wanaku-barn-backend-runner.jar
```

### Wanaku

Wanaku is a single binary. No JVM, no downstream MCP server processes to manage (tools come from forwarded MCP servers).

```bash
# Download and run
wanaku-server
```

Or with containers:

```bash
docker run -p 8080:8080 -p 8081:8081 quay.io/wanaku/wanaku-server:early-access
```

**Key difference:** You run `wanaku-server` directly and register MCP servers as forwards. Wanaku does not start or manage downstream processes.

## Configuration

### Classic

Classic Wanaku uses Quarkus configuration — `application.properties` files with `-D` system property overrides and environment variable support (dots and hyphens become underscores).

```properties
# application.properties
quarkus.http.port=8180
wanaku.http.auth=keycloak
wanaku.persistence.infinispan.base-folder=/data/router
```

### Wanaku

Wanaku has no properties files. It reads configuration from these sources:

- Environment variables set runtime options, for example the management listener, persistence, and the inference upstream.
- `wanaku.yaml` sets forwards, governance, evaluators, and credential bindings. Pass it with `--wanaku-config`.
- A pipeline YAML file sets the MCP, inference, and A2A listeners and their filter chains. Pass it with `--pipeline-config`. Without this flag, Wanaku uses the embedded default pipeline.
- CLI flags set file locations, for example `--wanaku-config` and `--plugins-path`.

```bash
export WANAKU_MGMT_LISTEN=0.0.0.0:9080
export WANAKU_PERSIST_PATH=/var/lib/wanaku
wanaku-server --wanaku-config /etc/wanaku/wanaku.yaml
```

### Configuration Mapping

| Classic Property | Wanaku Env Var | Notes |
|---|---|---|
| `quarkus.http.port` (management) | `WANAKU_MGMT_LISTEN` | Wanaku requires `host:port`. |
| `quarkus.http.port` (MCP) | Pipeline YAML file | No environment variable. Copy `server/src/default.yaml`, edit the `mcp` listener address, and pass the file with `--pipeline-config`. |
| `wanaku.http.auth=keycloak` | `WANAKU_AUTH_ISSUER` | Use oauth2-proxy externally; see [Authentication](#authentication) |
| `wanaku.http.auth=none` | _(default)_ | Auth is off by default in Wanaku |
| `wanaku.persistence.infinispan.base-folder` | `WANAKU_PERSIST_PATH` | Wanaku defaults to `$HOME/.wanaku/server` |
| `WANAKU_HOME` | `WANAKU_PERSIST_PATH` | Wanaku uses `$HOME/.wanaku/server` by default (similar concept) |
| `quarkus.log.level` | `RUST_LOG` | Uses Rust `tracing` crate syntax (e.g., `RUST_LOG=info,wanaku_filters=trace`) |

See [Wanaku environment variables](../reference/environment-variables.md) for the full list of Wanaku environment variables.

## Namespaces

### Classic

Classic Wanaku provides a **fixed set of 10 namespace slots**, named `ns-0` through `ns-9`, plus a `default` namespace (used when none is specified) and a special `public` namespace (accessible without authentication).

Namespace names are labels assigned to these slots. When you provide a name like `"finance"`, Wanaku automatically assigns it to the first available slot (e.g., `ns-3`). The MCP endpoint uses the slot identifier, not the name:

```text
http://localhost:8180/ns-3/mcp/sse        # SSE transport
http://localhost:8180/ns-3/mcp/           # Streamable HTTP
http://localhost:8180/public/mcp/sse      # public namespace (no auth)
```

You cannot create more than 10 namespaces (beyond `default` and `public`).

### Wanaku

Wanaku uses DNS-label-style namespace names directly — no fixed slots, no numeric limit. Names must be lowercase alphanumeric with hyphens, 1-63 characters. The MCP endpoint uses the name itself:

```text
http://localhost:8081/finance/mcp             # Streamable HTTP only
```

Wanaku has no unauthenticated `public` namespace. When you use the authentication proxy, every namespace endpoint requires a token, including a namespace named `public`.

Create as many namespaces as you need:

```bash
wanaku namespaces create finance --no-auth
```

### Migration Steps

1. List your Classic namespace assignments (`wanaku namespaces list --no-auth`) — note which slot maps to which name
2. Create named namespaces in Wanaku using the label names you assigned in Classic (`wanaku namespaces create <name> --no-auth`)
3. Re-register forwards with the new namespace names
4. Update MCP client configurations: replace slot-based URLs (`/ns-3/mcp/sse`) with name-based URLs (`/finance/mcp`) on port `8081`

**The `public` namespace does not exist in Wanaku.** If you used unauthenticated public access, create a regular namespace and leave authentication disabled (the default).

**The 10-namespace limit is gone.** You can create as many namespaces as you need in Wanaku.

## MCP Endpoints

### Classic

Classic Wanaku serves MCP on the same port as the management API (default 8180) and supports both SSE and Streamable HTTP transports. Endpoints use the slot identifier (`ns-0`..`ns-9`), not the namespace name:

```text
http://localhost:8180/ns-3/mcp/sse              # SSE transport
http://localhost:8180/ns-3/mcp/                 # Streamable HTTP
http://localhost:8180/default/mcp/sse           # default namespace
```

### Wanaku

MCP runs on a dedicated port (8081) using Streamable HTTP only:

```text
http://localhost:8081/{namespace}/mcp            # Streamable HTTP
```

### Migration Steps

1. Update all MCP client configurations to point to port `8081` instead of `8180`
2. Remove the `/sse` suffix from endpoint URLs
3. If your clients use SSE transport exclusively, verify they support Streamable HTTP (most modern MCP clients do)

**Bare `/mcp` paths are rejected.** You must always include a namespace: `/default/mcp`, not `/mcp`.

## Tool Management

### Classic

Classic Wanaku routes tool calls to separate downstream MCP server processes. Each server specializes in one execution type:

- **MCP forward** (`mcp-remote-tool`) — forwarded to an external MCP server
- **HTTP** (`wanaku-tool-service-http`) — HTTP calls with URI templates, deployed as a separate process
- **Exec** (`wanaku-tool-service-exec`) — command execution, deployed as a separate process
- **Camel routes** (`camel-integration-capability`) — Apache Camel route execution, deployed as a separate process

The downstream MCP servers self-register with the router via the `/api/v1/management/discovery` endpoint and maintain health via periodic heartbeats.

Tools support URI template expressions for dynamic parameter injection:

```text
http://api.example.com/users/{parameter.value('id')}
```

Special argument prefixes control behavior:

- `wanaku_body` — sent as request body
- `wanaku_meta_*` — converted to HTTP headers
- `wanaku_auth_*` — converted to auth headers

### Wanaku

Wanaku supports **MCP forward only**. All tools execute by forwarding the MCP `tools/call` request to the address of the forward that owns the tool (`forwardId`). Wanaku creates tools only through forward discovery. The management API cannot create a tool.

There are no URI templates, no special argument prefixes, and no local execution. The upstream MCP server handles all execution logic.

### Migration Steps

**If your tools are already MCP forwards:** No changes needed. Register the same upstream MCP servers as forwards in Wanaku.

**If your tools use HTTP, exec, or Camel routes:** You need an upstream MCP server that wraps those backends. Options:

1. **Camel Integration Capability** — an Apache Camel-based MCP server that exposes Camel routes as MCP tools. Register it as a forward in Wanaku. See [camel-integration-capability](https://github.com/wanaku-ai/camel-integration-capability).

2. **Custom MCP server** — build an MCP server using any language/framework that wraps your HTTP/exec/custom tools. Register it as a forward.

3. **Wanaku Capabilities Java SDK** — use the [SDK and Maven archetypes](https://github.com/wanaku-ai/wanaku-capabilities-java-sdk) to create new MCP servers that wrap existing backends.

The key mental model shift: in Classic Wanaku, the router dispatches to managed downstream MCP servers that it discovers and monitors. In Wanaku, the router forwards to upstream MCP servers that you register explicitly. There is no self-registration endpoint. Wanaku discovers tools when you register a forward. A background check probes unavailable forwards again every 30 seconds (`WANAKU_FORWARD_HEALTHCHECK_INTERVAL`; `0` disables it).

## Forward Management

Forward management works similarly in both versions. The `wanaku` CLI is compatible with both.

### Classic

```bash
wanaku forwards add --service="http://echo-mcp:8080/mcp" --name echo --namespace default --no-auth
wanaku forwards list --no-auth
wanaku forwards remove --name echo --no-auth
wanaku forwards refresh --name echo --no-auth
```

### Wanaku

Same CLI commands work:

```bash
wanaku forwards add --service="http://echo-mcp:8080/mcp" --name echo --namespace default --no-auth
wanaku forwards list --no-auth
wanaku forwards remove --name echo --no-auth
wanaku forwards refresh --name echo --no-auth
```

Read [Management API](../reference/management-api.md) for the equivalent REST routes.

### Try a Forward

The hosts in the examples above are placeholders. To test forward discovery end to end, use the reference MCP server from the Model Context Protocol project as an upstream. This procedure needs Node.js.

1. Start the upstream MCP server. It listens on `http://localhost:3001/mcp`:

   ```bash
   npx -y @modelcontextprotocol/server-everything streamableHttp
   ```

2. In a different terminal, start Wanaku:

   ```bash
   wanaku-server
   ```

3. Register the upstream server as a forward:

   ```bash
   curl -X POST http://localhost:8080/api/v1/forwards \
     -H 'Content-Type: application/json' \
     -d '{"name": "everything", "address": "http://localhost:3001/mcp", "namespace": "default"}'
   ```

   The response contains the forward and the discovery result. Make sure that `available` is `true` and that `tools_discovered` is more than `0`:

   ```json
   {"data":{"forward":{"name":"everything","address":"http://localhost:3001/mcp","namespace":"default","available":true,...},"tools_discovered":10,"resources_discovered":...,"prompts_discovered":...},"error":null}
   ```

4. List the discovered tools:

   ```bash
   curl http://localhost:8080/api/v1/tools
   ```

   Each tool has `"forwardId": "everything"` and `"namespace": "default"`. MCP clients can now call the tools at `http://localhost:8081/default/mcp`.

If discovery fails, Wanaku still stores the forward. The response reports zero discovered entries, and the forward records the error in `statusMessage`.

### Bootstrap via wanaku.yaml

Wanaku can bootstrap forwards at startup from a `wanaku.yaml` file:

```yaml
forwards:
  - name: "echo-server"
    address: "http://echo-mcp:8080/mcp"
    namespace: "default"
```

```bash
wanaku-server --wanaku-config wanaku.yaml
```

There is no equivalent in Classic Wanaku — forwards must be registered after startup via CLI or API.

## Management API

The management API uses the same `/api/v1/` prefix and response envelope in both versions:

```json
{"data": {"name": "example"}, "error": null}
```

### Endpoint Comparison

| Endpoint | Classic | Wanaku | Notes |
|---|---|---|---|
| `GET /api/v1/tools` | Yes | Yes | |
| `GET /api/v1/tools/{name}` | Yes | Yes | |
| `DELETE /api/v1/tools/{name}` | No | Yes | |
| `GET /api/v1/resources` | Yes | Yes | |
| `GET /api/v1/resources/{name}` | Yes | Yes | |
| `DELETE /api/v1/resources/{name}` | No | Yes | |
| `GET /api/v1/prompts` | Yes | Yes | |
| `GET /api/v1/prompts/{name}` | No | Yes | |
| `DELETE /api/v1/prompts/{name}` | No | Yes | |
| `GET /api/v1/namespaces` | Yes | Yes | |
| `POST /api/v1/namespaces` | Yes | Yes | |
| `GET /api/v1/namespaces/{name}` | Yes | Yes | |
| `PUT /api/v1/namespaces/{name}` | Yes | Yes | |
| `DELETE /api/v1/namespaces/{name}` | Yes | Yes | |
| `GET /api/v1/forwards` | Yes | Yes | |
| `GET /api/v1/forwards/{name}` | Yes | Yes | |
| `POST /api/v1/forwards` | Yes | Yes | |
| `DELETE /api/v1/forwards/{name}` | Yes | Yes | |
| `POST /api/v1/forwards/{name}/refreshes` | Yes | Yes | |
| `GET /api/v1/management/statistics` | Yes | Yes | |
| `GET /healthz` or `/health` | No | Yes | Health check (returns WanakuResponse envelope) |
| `GET /openapi.json` | Yes | Yes | Classic may serve at `/q/openapi` instead |

### Removed Endpoints

These Classic Wanaku endpoints are not available in Wanaku:

| Endpoint | Purpose | Migration Path |
|---|---|---|
| `/api/v1/capabilities` | Service capability management | Not needed — no downstream MCP server registration |
| `/api/v1/data-store` | Shared data store | No equivalent — use external storage |
| `/api/v1/service-catalog` | Service catalog lifecycle | Served by wanaku-barn, not Wanaku — see [Service Catalogs](#service-catalogs) |
| `/api/v1/service-template` | Service template management | Served by wanaku-barn, not Wanaku |
| `PUT /api/v1/forwards/{name}` | Update a forward | Not available — delete and re-create instead |
| `/api/v1/management/discovery` | Service self-registration | Not needed — use forwards instead |
| `/api/v1/management/info/version` | Version info | Moved: `GET /api/v1/management/info` returns `name` and `version` |
| `GET /api/v1/namespaces/stale` | List stale namespaces | Not available |
| `DELETE /api/v1/namespaces/stale` | Cleanup stale namespaces | Not available |
| Label filter query parameter (`?labelFilter=...`) | Filter by labels | Not available on list endpoints |

### New Endpoints

These endpoints are new in Wanaku:

| Endpoint | Purpose |
|---|---|
| `GET /api/v1/management/info` | Server name and version |
| `PUT /api/v1/tools/{name}/enable` and `/disable` | Enable or disable a tool (the same routes exist for resources and prompts) |
| `/api/v1/evaluators` and sub-routes | Evaluator configuration, status, revisions, and rollback |
| `/api/v1/action-policies` | Deterministic action policy rules |
| `/api/v1/policy-simulations` | Policy simulation and audit replay |
| `/api/v1/audit/*` | Governance audit events |
| `/api/v1/bindings` | Credential bindings (read-only) |
| `/api/v1/agents` | A2A agents |
| `GET /api/v1/metrics` | In-memory metrics snapshot |
| `GET /api/v1/plugins` | Discovered UI plugins |

This list is not complete. See [Management API](../reference/management-api.md) for the full reference.

## Authentication

### Classic

Classic Wanaku has built-in Keycloak OIDC integration. Set `wanaku.http.auth=keycloak` and configure the Keycloak realm, client, and roles. The router validates JWT tokens directly.

```properties
wanaku.http.auth=keycloak
quarkus.oidc.auth-server-url=http://keycloak:8543/realms/wanaku
quarkus.oidc.client-id=wanaku-mcp-router
```

To disable: `wanaku.http.auth=none`.

### Wanaku

Wanaku has no built-in authentication. It delegates to [oauth2-proxy](https://github.com/oauth2-proxy/oauth2-proxy) running as a reverse proxy sidecar in front of both the MCP and management API ports.

```text
Client → oauth2-proxy → Wanaku
```

This means authentication is handled before requests reach Wanaku. The proxy validates tokens and forwards authenticated requests.

### Migration Steps

1. Deploy oauth2-proxy alongside Wanaku
2. Configure oauth2-proxy to use your existing Keycloak realm
3. Point clients at the oauth2-proxy port instead of directly at Wanaku
4. Update CLI commands to use `--host` pointing at the oauth2-proxy endpoint

See [Authentication](../operate/authentication.md) for detailed setup.

**When running without authentication:** Wanaku runs without auth by default. Use `--no-auth` with the CLI, same as setting `wanaku.http.auth=none` in Classic.

## Persistence

### Classic

Classic Wanaku uses Infinispan (embedded data grid) with SoftIndexFileStore. Data is stored under `<wanaku.home>/router/` using ProtoStream serialization.

```properties
wanaku.persistence.infinispan.base-folder=/data/router
```

### Wanaku

Wanaku uses file-based JSON snapshots. The entire registry (tools, resources, prompts, forwards, namespaces) is serialized to a single `registry.json` file using atomic writes.

**File persistence is enabled by default.** The registry persists to `$HOME/.wanaku/server/registry.json`. To customize the path:

```bash
export WANAKU_PERSIST_PATH=/data/registry
```

To disable persistence entirely (in-memory only):

```bash
export WANAKU_PERSIST_BACKEND=none
```

### Migration Steps

1. There is no data migration tool. Re-register your forwards in Wanaku — tool discovery will rebuild the registry
2. For Kubernetes deployments, mount a `PersistentVolume` at `WANAKU_PERSIST_PATH` (`/data/registry` in the container image)

## Service Catalogs

Service catalogs are not part of Wanaku — they remain in the [wanaku-barn](https://github.com/wanaku-ai/wanaku-barn) project. The wanaku-barn backend runs alongside Wanaku and continues to manage service catalogs, service templates, data stores, and the service discovery API.

The full service catalog workflow is unchanged:

- `wanaku service init/expose/package/deploy` CLI commands
- Service template instantiation
- The `/api/v1/service-catalog` and `/api/v1/service-template` API endpoints

These endpoints are served by wanaku-barn, not by Wanaku. When both are deployed together (the Kubernetes operator does this when `spec.barn.enabled` is `true`), the service catalog lifecycle works as before.

### What Changed

The architecture splits responsibilities: **wanaku-barn** manages the service catalog, persistence, and service discovery. **Wanaku** handles MCP routing, namespace isolation, and the governance pipeline. Tools deployed via service catalogs are still registered in wanaku-barn and routed through Wanaku.

If you deploy Wanaku standalone (without wanaku-barn), service catalog features are unavailable. In that case, package your Camel routes as standalone MCP servers using the [Camel Integration Capability](https://github.com/wanaku-ai/camel-integration-capability) or the [Wanaku Capabilities Java SDK](https://github.com/wanaku-ai/wanaku-capabilities-java-sdk), and register them as forwards in Wanaku.

## Kubernetes Deployment

### Using the Operator

The Wanaku Kubernetes operator deploys Wanaku from a `WanakuRouter` custom resource. The `spec.server` section configures Wanaku. The operator always deploys it. The optional `spec.barn` section deploys the Wanaku Barn backend next to Wanaku, for service catalogs.

To deploy Wanaku only:

```yaml
apiVersion: wanaku.ai/v1alpha1
kind: WanakuRouter
metadata:
  name: my-router
spec:
  server: {}
```

To deploy Wanaku and Wanaku Barn together:

```yaml
apiVersion: wanaku.ai/v1alpha1
kind: WanakuRouter
metadata:
  name: my-router
spec:
  server:
    image: quay.io/wanaku/wanaku-server:latest
  barn:
    enabled: true
    image: quay.io/wanaku/wanaku-barn-backend:latest
```

The operator creates the deployments, services, and ingress or routes for both components. The `spec.router` section is deprecated. Read the [operator documentation](https://github.com/wanaku-ai/wanaku-barn/blob/main/docs/operate/operator.md) for the full specification, including `spec.exposure` and `spec.auth`.

### Without the Operator

If you prefer not to use the operator, deploy Wanaku as a standard Kubernetes `Deployment`. Read [Configuration: Kubernetes](../operate/kubernetes.md) for complete ConfigMap, Secret, Deployment, and Service manifests.

### Migration Steps

**If using the operator:**

1. Add a `server` section to the `WanakuRouter` custom resource.
2. To keep service catalogs, add a `barn` section with `enabled: true`.
3. Remove the deprecated `router` section.
4. Apply the custom resource. The operator then deploys Wanaku.

**If not using the operator:**

1. Create a ConfigMap that contains `wanaku.yaml`.
2. Create a Deployment. Mount the ConfigMap at `/etc/wanaku`, which is the working directory of the image.
3. Mount a PersistentVolume at `/data/registry` for the registry snapshot.
4. Create a Service for the MCP port (`8081`).

## CLI Compatibility

The `wanaku` CLI works with both Classic and Wanaku. Most commands are identical.

### Commands That Work Unchanged

| Command | Notes |
|---|---|
| `wanaku tools list` | Same output format |
| `wanaku tools show` | Same output format |
| `wanaku resources list` | Same output format |
| `wanaku prompts list` | Same output format |
| `wanaku forwards add/list/remove/refresh` | Same behavior |
| `wanaku namespaces list/create/show/delete` | Same behavior |

### Commands Not Available Against Wanaku

| Command | Reason |
|---|---|
| `wanaku service init/expose/package/deploy` | Works against wanaku-barn, not Wanaku |
| `wanaku service catalog list/remove` | Works against wanaku-barn, not Wanaku |
| `wanaku service template list/instantiate` | Works against wanaku-barn, not Wanaku |
| `wanaku data-store *` | Data store not available |
| `wanaku configure *` | MCP client config helpers not available |
| `wanaku namespaces cleanup` | Stale namespace cleanup not available |
| `wanaku admin` | Moved to wanaku-keycloak-admin module in wanaku-barn |

### Important: The `--no-auth` Flag

When running Wanaku without oauth2-proxy (the default), always pass `--no-auth` to CLI commands:

```bash
wanaku tools list --no-auth
wanaku forwards add --service="http://localhost:3001/mcp" --name everything --namespace default --no-auth
```

This matches the Classic Wanaku behavior when `wanaku.http.auth=none`.

### wanaku admin commands

The admin command moved to wanaku-keycloak-admin module, so once the module is built you can use it like:

List the users:

```bash
java -jar apps/wanaku-keycloak-admin/target/quarkus-app/quarkus-run.jar users list --admin-username admin --admin-password admin --keycloak-url=http://localhost:8543
```

Add an user:

```bash
java -jar apps/wanaku-keycloak-admin/target/quarkus-app/quarkus-run.jar users add --admin-username admin --admin-password admin --username utest1 --password admin --email utest1@example.com --first-name UTest --last-name test --keycloak-url=http://localhost:8543
```

## New Features in Wanaku

These features have no Classic Wanaku equivalent.

### Evaluator Engine

WASM-based policy enforcement in the filter pipeline. Evaluators can classify tool calls using an LLM and execute WebAssembly action scripts to block, warn, filter, or modify requests.

See [Evaluator Engine](../govern/evaluators.md).

### Inference Proxy

A reverse proxy (port 8083) to an OpenAI-compatible backend. Wanaku forwards the caller's own `Authorization` header. Wanaku does not store or inject a credential for the backend. The default inference pipeline includes the `wanaku_intercept` filter, so Wanaku can change the request body before it forwards it (see [Interaction Recording](#interaction-recording)).

```bash
export WANAKU_INFERENCE_UPSTREAM=http://ollama:11434
```

### Interaction Recording

The `wanaku_intercept` filter records LLM request and response pairs that pass through the inference proxy (port 8083). It does not record MCP traffic. The evaluator engine uses these records as conversation history. The filter can also insert a system message into the `messages` array of a chat request. Read [Feature crates](../extend/feature-crates.md#intercept) for details. The store keeps the most recent 1000 interactions by default.

```bash
export WANAKU_INTERACTION_CAPACITY=1000
```

### In-Memory Metrics

Collects filter results, evaluator decisions, LLM calls, and WASM execution metrics. Exposed via `GET /api/v1/metrics`.

### UI Plugins

Load external UI plugins from a filesystem directory.

```bash
wanaku-server --plugins-path /path/to/plugins
```

## Breaking Changes Checklist

Use this checklist to verify your migration:

- [ ] **MCP endpoint port changed:** `8180` → `8081`
- [ ] **MCP endpoint path changed:** `/ns-N/mcp/sse` → `/{namespace}/mcp` (slot-based → name-based)
- [ ] **SSE transport removed:** Streamable HTTP only
- [ ] **No bare `/mcp` path:** Must include namespace (`/default/mcp`)
- [ ] **Management API port changed:** `8180` → `8080`
- [ ] **Config format changed:** `application.properties` → environment variables, `wanaku.yaml`, and a pipeline YAML file
- [ ] **Persistence changed:** Infinispan → file-based JSON (enabled by default at `$HOME/.wanaku/server/registry.json`)
- [ ] **Auth changed:** Built-in Keycloak → external oauth2-proxy
- [ ] **Tool execution:** MCP forward only — no HTTP, exec, or Camel route execution in the router
- [ ] **Service catalogs:** Managed by wanaku-barn, not Wanaku — deploy both together or package as standalone MCP servers
- [ ] **K8s operator:** `WanakuRouter` with `spec.server` (and optional `spec.barn`), or deploy as a standard Deployment
- [ ] **Data store API:** Not available
- [ ] **Service discovery API:** Not available — use forwards instead
- [ ] **Forward update API:** `PUT /api/v1/forwards/{name}` not available — delete and re-create
- [ ] **Label filter queries:** Not available on list endpoints
- [ ] **`public` namespace:** Does not exist — create a namespace and leave auth disabled

## Getting Help

- **Wanaku issues:** [github.com/wanaku-ai/wanaku/issues](https://github.com/wanaku-ai/wanaku/issues)
- **Classic Wanaku issues:** [github.com/wanaku-ai/wanaku-barn/issues](https://github.com/wanaku-ai/wanaku-barn/issues)
- **FAQ:** [FAQ](../operate/troubleshooting.md)
