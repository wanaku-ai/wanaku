# Migrating from Classic Wanaku

This guide is for users moving from the Java-based Wanaku ("Classic Wanaku", `wanaku-barn`) to the Rust-based Wanaku ("Wanaku Praxis"). It covers what changed, what works differently, and what you need to do to migrate.

If you're new to Wanaku and have never used the Java version, skip this doc — start with [Getting Started](./getting-started.md) instead.

Use this guide if you operate Classic Wanaku and need to plan or perform a migration. It compares the two systems, identifies compatibility limits, and provides a migration checklist.

## Contents

- [What Changed and Why](#what-changed-and-why)
- [Configuration](#configuration)
- [Namespaces](#namespaces)
- [MCP Endpoints](#mcp-endpoints)
- [Tool Management](#tool-management)
- [Forward Management](#forward-management)
- [Authentication](#authentication)
- [Persistence](#persistence)
- [Kubernetes Deployment](#kubernetes-deployment)
- [CLI Compatibility](#cli-compatibility)
- [Breaking Changes Checklist](#breaking-changes-checklist)

## What Changed and Why

Classic Wanaku is a Java/Quarkus application with Infinispan persistence, Camel route execution, service catalogs, and a Kubernetes operator. It's fully featured and production-proven.

Wanaku Praxis is a ground-up rewrite in Rust, built on the Praxis proxy framework (Cloudflare Pingora under the hood). The core idea is the same — a governed action proxy that sits between AI agents and backend systems — but the architecture is fundamentally different. Wanaku Praxis introduces a composable filter pipeline that enables pluggable governance features (evaluators, policy gates, interaction tracking) that would be difficult to express in the classic architecture.

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
- Interaction recording for conversation history
- In-memory metrics collection
- Single static binary — no JVM, no runtime dependencies

**What's not in Praxis itself (still in wanaku-barn):**
- Service catalogs and service templates — managed by the wanaku-barn backend, which runs alongside Praxis
- Camel route execution — use a Camel-based MCP server as a forward
- gRPC communication between services
- Infinispan persistence (Praxis uses file-based snapshots)
- Built-in Keycloak integration (Praxis uses oauth2-proxy as a sidecar)

## At a Glance

| Area | Classic Wanaku (Java) | Wanaku Praxis (Rust) |
|---|---|---|
| Runtime | Quarkus + GraalVM native | Pingora + Tokio async |
| Binary | JAR or native image + downstream MCP servers | Single static binary |
| MCP endpoint | `/{namespace}/mcp/sse` or `/{namespace}/mcp/` | `/{namespace}/mcp` |
| Management API | Port 8180 (Quarkus HTTP, configurable) | Port 8080 (Pingora ServeHttp) |
| MCP port | 8180 (same as mgmt) | 8081 (separate listener) |
| Config format | `application.properties` | Environment variables |
| Persistence | Infinispan (embedded) | File-based JSON snapshots |
| Authentication | Built-in Keycloak OIDC | External oauth2-proxy |
| Namespaces | Fixed 10 slots (`ns-0`..`ns-9`) + `default` + `public` | DNS-label names, unlimited + `default` (no `public`) |
| Tool execution | Multiple provider types (HTTP, exec, Camel, MCP forward) | MCP forward only |
| Service catalogs | Full lifecycle (init/expose/package/deploy) | Managed by wanaku-barn (runs alongside Praxis) |
| K8s operator | CRDs (`WanakuRouter`, `WanakuServiceCatalog`) | Same operator (experimental Praxis support via `WanakuRouter`) |
| Policy enforcement | None | WASM evaluator + LLM classification |
| CLI | `wanaku` (Picocli + Quarkus) | Same `wanaku` CLI (compatible) |

## Installation

### Classic

Classic Wanaku runs multiple processes: the router backend, downstream MCP servers (tool invokers, resource providers), and optionally Keycloak.

```bash
# Start everything locally via CLI
wanaku start local
```

Or run the router JAR directly:

```bash
java -jar wanaku-barn-backend-runner.jar
```

### Wanaku Praxis

Wanaku Praxis is a single binary. No JVM, no downstream MCP server processes to manage (tools come from forwarded MCP servers).

```bash
# Download and run
wanaku-server
```

Or with containers:

```bash
docker run -p 8080:8080 -p 8081:8081 quay.io/wanaku/wanaku-server:early-access
```

**Key difference:** There is no `wanaku start local` equivalent. You run `wanaku-server` directly and register MCP servers as forwards.

## Configuration

### Classic

Classic Wanaku uses Quarkus configuration — `application.properties` files with `-D` system property overrides and environment variable support (dots and hyphens become underscores).

```properties
# application.properties
quarkus.http.port=8180
wanaku.http.auth=keycloak
wanaku.persistence.infinispan.base-folder=/data/router
```

### Wanaku Praxis

Wanaku Praxis uses environment variables exclusively. There are no properties files.

```bash
export WANAKU_MGMT_LISTEN=0.0.0.0:8080
export WANAKU_PERSIST_BACKEND=file
export WANAKU_PERSIST_PATH=/data/registry
wanaku-server
```

### Configuration Mapping

| Classic Property | Wanaku Praxis Env Var | Notes |
|---|---|---|
| `quarkus.http.port` | `WANAKU_MGMT_LISTEN` | Praxis requires `host:port`. |
| `wanaku.http.auth=keycloak` | `WANAKU_AUTH_ISSUER` | Use oauth2-proxy externally; see [Authentication](#authentication) |
| `wanaku.http.auth=none` | _(default)_ | Auth is off by default in Praxis |
| `wanaku.persistence.infinispan.base-folder` | `WANAKU_PERSIST_PATH` | Praxis defaults to `$HOME/.wanaku/server` |
| `WANAKU_HOME` | `WANAKU_PERSIST_PATH` | Praxis uses `$HOME/.wanaku/server` by default (similar concept) |
| `quarkus.log.level` | `RUST_LOG` | Uses Rust `tracing` crate syntax (e.g., `RUST_LOG=info,wanaku_filters=trace`) |

See [Configuration](./configuration.md) for the full list of Wanaku Praxis environment variables.

## Namespaces

### Classic

Classic Wanaku provides a **fixed set of 10 namespace slots**, named `ns-0` through `ns-9`, plus a `default` namespace (used when none is specified) and a special `public` namespace (accessible without authentication).

Namespace names are labels assigned to these slots. When you provide a name like `"finance"`, Wanaku automatically assigns it to the first available slot (e.g., `ns-3`). The MCP endpoint uses the slot identifier, not the name:

```
http://localhost:8180/ns-3/mcp/sse        # SSE transport
http://localhost:8180/ns-3/mcp/           # Streamable HTTP
http://localhost:8180/public/mcp/sse      # public namespace (no auth)
```

You cannot create more than 10 namespaces (beyond `default` and `public`).

### Wanaku Praxis

Wanaku Praxis uses DNS-label-style namespace names directly — no fixed slots, no numeric limit. Names must be lowercase alphanumeric with hyphens, 1-63 characters. The MCP endpoint uses the name itself:

```
http://localhost:8081/finance/mcp             # Streamable HTTP only
```

Create as many namespaces as you need:

```bash
curl -X POST http://localhost:8080/api/v1/namespaces \
  -H "Content-Type: application/json" \
  -d '{"name": "finance"}'
```

### Migration Steps

1. List your Classic namespace assignments (`wanaku namespaces list --no-auth`) — note which slot maps to which name
2. Create named namespaces in Wanaku Praxis using the label names you assigned in Classic (`POST /api/v1/namespaces`)
3. Re-register forwards with the new namespace names
4. Update MCP client configurations: replace slot-based URLs (`/ns-3/mcp/sse`) with name-based URLs (`/finance/mcp`) on port `8081`

**The `public` namespace does not exist in Wanaku Praxis.** If you used unauthenticated public access, create a regular namespace and leave authentication disabled (the default).

**The 10-namespace limit is gone.** You can create as many namespaces as you need in Wanaku Praxis.

## MCP Endpoints

### Classic

Classic Wanaku serves MCP on the same port as the management API (default 8180) and supports both SSE and Streamable HTTP transports. Endpoints use the slot identifier (`ns-0`..`ns-9`), not the namespace name:

```
http://localhost:8180/ns-3/mcp/sse              # SSE transport
http://localhost:8180/ns-3/mcp/                 # Streamable HTTP
http://localhost:8180/default/mcp/sse           # default namespace
```

### Wanaku Praxis

MCP runs on a dedicated port (8081) using Streamable HTTP only:

```
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

```
http://api.example.com/users/{parameter.value('id')}
```

Special argument prefixes control behavior:
- `wanaku_body` — sent as request body
- `wanaku_meta_*` — converted to HTTP headers
- `wanaku_auth_*` — converted to auth headers

### Wanaku Praxis

Wanaku Praxis supports **MCP forward only**. All tools execute by forwarding the MCP `tools/call` request to an upstream MCP server specified in the tool's `uri` field.

There are no URI templates, no special argument prefixes, and no local execution. The upstream MCP server handles all execution logic.

### Migration Steps

**If your tools are already MCP forwards:** No changes needed. Register the same upstream MCP servers as forwards in Wanaku Praxis.

**If your tools use HTTP, exec, or Camel routes:** You need an upstream MCP server that wraps those backends. Options:

1. **Camel Integration Capability** — an Apache Camel-based MCP server that exposes Camel routes as MCP tools. Register it as a forward in Wanaku Praxis. See [camel-integration-capability](https://github.com/wanaku-ai/camel-integration-capability).

2. **Custom MCP server** — build an MCP server using any language/framework that wraps your HTTP/exec/custom tools. Register it as a forward.

3. **Wanaku Capabilities Java SDK** — use the [SDK and Maven archetypes](https://github.com/wanaku-ai/wanaku-capabilities-java-sdk) to create new MCP servers that wrap existing backends.

The key mental model shift: in Classic Wanaku, the router dispatches to managed downstream MCP servers that it discovers and monitors. In Wanaku Praxis, the router forwards to upstream MCP servers that you register explicitly — there is no service discovery or health monitoring.

## Forward Management

Forward management works similarly in both versions. The `wanaku` CLI is compatible with both.

### Classic

```bash
wanaku forwards add --service="http://echo-mcp:8080/mcp" --name echo --no-auth
wanaku forwards list --no-auth
wanaku forwards remove --name echo --no-auth
wanaku forwards refresh --name echo --no-auth
```

### Wanaku Praxis

Same CLI commands work:

```bash
wanaku forwards add --service="http://echo-mcp:8080/mcp" --name echo --no-auth
wanaku forwards list --no-auth
wanaku forwards remove --name echo --no-auth
wanaku forwards refresh --name echo --no-auth
```

Or use the management API directly:

```bash
# Add a forward
curl -X POST http://localhost:8080/api/v1/forwards \
  -H "Content-Type: application/json" \
  -d '{"name": "echo", "address": "http://echo-mcp:8080/mcp"}'

# Refresh (re-discover tools)
curl -X POST http://localhost:8080/api/v1/forwards/echo/refreshes

# Remove
curl -X DELETE http://localhost:8080/api/v1/forwards/echo
```

### Bootstrap via wanaku.yaml

Wanaku Praxis can bootstrap forwards at startup from a `wanaku.yaml` file:

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

| Endpoint | Classic | Praxis | Notes |
|---|---|---|---|
| `GET /api/v1/tools` | Yes | Yes | |
| `GET /api/v1/tools/{name}` | Yes | Yes | |
| `PUT /api/v1/tools/{name}` | No | Yes | Rename/update tools |
| `DELETE /api/v1/tools/{name}` | No | Yes | |
| `GET /api/v1/resources` | Yes | Yes | |
| `GET /api/v1/resources/{name}` | Yes | Yes | |
| `PUT /api/v1/resources/{name}` | No | Yes | |
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

These Classic Wanaku endpoints are not available in Wanaku Praxis:

| Endpoint | Purpose | Migration Path |
|---|---|---|
| `/api/v1/capabilities` | Service capability management | Not needed — no downstream MCP server registration |
| `/api/v1/data-store` | Shared data store | No equivalent — use external storage |
| `/api/v1/service-catalog` | Service catalog lifecycle | Served by wanaku-barn, not Praxis — see [Service Catalogs](#service-catalogs) |
| `/api/v1/service-template` | Service template management | Served by wanaku-barn, not Praxis |
| `PUT /api/v1/forwards/{name}` | Update a forward | Not available — delete and re-create instead |
| `/api/v1/management/discovery` | Service self-registration | Not needed — use forwards instead |
| `/api/v1/management/info/version` | Version info | Not available |
| `GET /api/v1/namespaces/stale` | List stale namespaces | Not available |
| `DELETE /api/v1/namespaces/stale` | Cleanup stale namespaces | Not available |
| Label filter query parameter (`?labelFilter=...`) | Filter by labels | Not available on list endpoints |

### New Endpoints

These endpoints are new in Wanaku Praxis:

| Endpoint | Purpose |
|---|---|
| `GET /api/v1/evaluators` | Evaluator configuration |
| `GET /api/v1/interactions` | Recorded MCP interactions |
| `GET /api/v1/metrics` | In-memory metrics snapshot |
| `GET /api/v1/plugins` | Discovered UI plugins |

See [Management API](./management-api.md) for the full reference.

## Authentication

### Classic

Classic Wanaku has built-in Keycloak OIDC integration. Set `wanaku.http.auth=keycloak` and configure the Keycloak realm, client, and roles. The router validates JWT tokens directly.

```properties
wanaku.http.auth=keycloak
quarkus.oidc.auth-server-url=http://keycloak:8543/realms/wanaku
quarkus.oidc.client-id=wanaku-mcp-router
```

To disable: `wanaku.http.auth=none`.

### Wanaku Praxis

Wanaku Praxis has no built-in authentication. It delegates to [oauth2-proxy](https://github.com/oauth2-proxy/oauth2-proxy) running as a reverse proxy sidecar in front of both the MCP and management API ports.

```
Client → oauth2-proxy → Wanaku Praxis
```

This means authentication is handled before requests reach Wanaku. The proxy validates tokens and forwards authenticated requests.

### Migration Steps

1. Deploy oauth2-proxy alongside Wanaku Praxis
2. Configure oauth2-proxy to use your existing Keycloak realm
3. Point clients at the oauth2-proxy port instead of directly at Wanaku
4. Update CLI commands to use `--host` pointing at the oauth2-proxy endpoint

See [Authentication](./auth.md) for detailed setup.

**When running without authentication:** Wanaku Praxis runs without auth by default. Use `--no-auth` with the CLI, same as setting `wanaku.http.auth=none` in Classic.

## Persistence

### Classic

Classic Wanaku uses Infinispan (embedded data grid) with SoftIndexFileStore. Data is stored under `<wanaku.home>/router/` using ProtoStream serialization.

```properties
wanaku.persistence.infinispan.base-folder=/data/router
```

### Wanaku Praxis

Wanaku Praxis uses file-based JSON snapshots. The entire registry (tools, resources, prompts, forwards, namespaces) is serialized to a single `registry.json` file using atomic writes.

**File persistence is enabled by default.** The registry persists to `$HOME/.wanaku/server/registry.json`. To customize the path:

```bash
export WANAKU_PERSIST_PATH=/data/registry
```

To disable persistence entirely (in-memory only):

```bash
export WANAKU_PERSIST_BACKEND=none
```

### Migration Steps

1. There is no data migration tool. Re-register your forwards in Wanaku Praxis — tool discovery will rebuild the registry
2. For Kubernetes deployments, mount a `PersistentVolume` at `WANAKU_PERSIST_PATH`

## Service Catalogs

Service catalogs are not part of Wanaku Praxis — they remain in the [wanaku-barn](https://github.com/wanaku-ai/wanaku-barn) project. The wanaku-barn backend runs alongside Praxis and continues to manage service catalogs, service templates, data stores, and the service discovery API.

The full service catalog workflow is unchanged:

- `wanaku service init/expose/package/deploy` CLI commands
- Service template instantiation
- The `/api/v1/service-catalog` and `/api/v1/service-template` API endpoints

These endpoints are served by wanaku-barn, not by Praxis. When both are deployed together (as the Kubernetes operator does by default), the service catalog lifecycle works as before.

### What Changed

The architecture splits responsibilities: **wanaku-barn** manages the service catalog, persistence, and service discovery. **Praxis** handles MCP routing, namespace isolation, and the governance pipeline. Tools deployed via service catalogs are still registered in wanaku-barn and routed through Praxis.

If you deploy Praxis standalone (without wanaku-barn), service catalog features are unavailable. In that case, package your Camel routes as standalone MCP servers using the [Camel Integration Capability](https://github.com/wanaku-ai/camel-integration-capability) or the [Wanaku Capabilities Java SDK](https://github.com/wanaku-ai/wanaku-capabilities-java-sdk), and register them as forwards in Praxis.

## Kubernetes Deployment

### Using the Operator (Experimental)

The Wanaku Kubernetes operator has experimental support for deploying Praxis. The `WanakuRouter` CRD has a `praxis` spec that deploys the Rust-based server alongside or instead of the Classic backend.

To deploy Praxis only (no Classic backend):

```yaml
apiVersion: wanaku.ai/v1alpha1
kind: WanakuRouter
metadata:
  name: my-router
spec:
  praxis: {}
  # router:          # omit or set enabled: false to skip the Classic backend
  #   enabled: true
```

To deploy both side-by-side during migration:

```yaml
apiVersion: wanaku.ai/v1alpha1
kind: WanakuRouter
metadata:
  name: my-router
spec:
  router:
    enabled: true
    image: quay.io/wanaku/wanaku-barn-backend:latest
  praxis:
    image: quay.io/wanaku/wanaku-praxis:latest
```

The operator creates deployments, services, and ingress/routes for both components.

### Without the Operator

If you prefer not to use the operator, deploy as a standard Kubernetes `Deployment`:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: wanaku-server
spec:
  replicas: 1  # File persistence supports one writer
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
        image: quay.io/wanaku/wanaku-praxis:latest
        ports:
        - containerPort: 8080  # Management API
        - containerPort: 8081  # MCP endpoint
        env:
        - name: WANAKU_PERSIST_PATH
          value: "/data/registry"
        volumeMounts:
        - name: data
          mountPath: /data
      volumes:
      - name: data
        persistentVolumeClaim:
          claimName: wanaku-registry
```

### Migration Steps

**If using the operator:**

1. Add `praxis: {}` to the `WanakuRouter` custom resource.
2. Remove the `router` section if you want to stop the Classic backend.
3. Apply the custom resource. The operator then deploys Praxis.

**If not using the operator:**

1. Create a standard Deployment resource.
2. Create a Service resource.
3. Create a ConfigMap that contains `wanaku.yaml`.
4. Mount the ConfigMap in the Deployment.
5. Use a PersistentVolume for the registry snapshot directory.

## CLI Compatibility

The `wanaku` CLI works with both Classic and Wanaku Praxis. Most commands are identical.

### Commands That Work Unchanged

| Command | Notes |
|---|---|
| `wanaku tools list` | Same output format |
| `wanaku tools show` | Same output format |
| `wanaku resources list` | Same output format |
| `wanaku prompts list` | Same output format |
| `wanaku forwards add/list/remove/refresh` | Same behavior |
| `wanaku namespaces list/create/show/delete` | Same behavior |

### Commands Not Available Against Wanaku Praxis

| Command | Reason |
|---|---|
| `wanaku start local` | No multi-process launcher in Praxis |
| `wanaku service init/expose/package/deploy` | Works against wanaku-barn, not Praxis |
| `wanaku service catalog list/remove` | Works against wanaku-barn, not Praxis |
| `wanaku service template list/instantiate` | Works against wanaku-barn, not Praxis |
| `wanaku data-store *` | Data store not available |
| `wanaku tools generate` | OpenAPI-to-tool generation not available |
| `wanaku configure *` | MCP client config helpers not available |
| `wanaku namespaces cleanup` | Stale namespace cleanup not available |

### Important: The `--no-auth` Flag

When running Wanaku Praxis without oauth2-proxy (the default), always pass `--no-auth` to CLI commands:

```bash
wanaku tools list --no-auth
wanaku forwards add --service="http://echo:8080/mcp" --name echo --no-auth
```

This matches the Classic Wanaku behavior when `wanaku.http.auth=none`.

## New Features in Wanaku Praxis

These features have no Classic Wanaku equivalent.

### Evaluator Engine

WASM-based policy enforcement in the filter pipeline. Evaluators can classify tool calls using an LLM and execute WebAssembly action scripts to block, warn, filter, or modify requests.

See [Evaluator Engine](./evaluator-engine.md).

### Inference Proxy

A raw, transparent reverse proxy (port 8083) to an OpenAI-compatible backend. Forwards requests as-is, including the caller's own `Authorization` header — Wanaku does not store or inject a credential for it.

```bash
export WANAKU_INFERENCE_UPSTREAM=http://ollama:11434
```

### Interaction Recording

Records MCP request/response pairs for conversation history and audit. Configurable capacity.

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
- [ ] **Config format changed:** `application.properties` → environment variables
- [ ] **Persistence changed:** Infinispan → file-based JSON (enabled by default at `$HOME/.wanaku/server/registry.json`)
- [ ] **Auth changed:** Built-in Keycloak → external oauth2-proxy
- [ ] **Tool execution:** MCP forward only — no HTTP, exec, or Camel route execution in the router
- [ ] **Service catalogs:** Managed by wanaku-barn, not Praxis — deploy both together or package as standalone MCP servers
- [ ] **K8s operator:** Experimental Praxis support via `WanakuRouter` CR, or deploy as standard Deployment
- [ ] **Data store API:** Not available
- [ ] **Service discovery API:** Not available — use forwards instead
- [ ] **Forward update API:** `PUT /api/v1/forwards/{name}` not available — delete and re-create
- [ ] **Label filter queries:** Not available on list endpoints
- [ ] **`public` namespace:** Does not exist — create a namespace and leave auth disabled

## Getting Help

- **Wanaku Praxis issues:** [github.com/wanaku-ai/wanaku/issues](https://github.com/wanaku-ai/wanaku/issues)
- **Classic Wanaku issues:** [github.com/wanaku-ai/wanaku-barn/issues](https://github.com/wanaku-ai/wanaku-barn/issues)
- **FAQ:** [FAQ](./faq.md)
