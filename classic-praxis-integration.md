# Integration Plan: Wanaku Praxis + Wanaku Classic

## Context

Praxis replaces Classic's MCP routing engine. Classic's MCP server gets removed. Classic becomes an internal backend service for service catalogs, templates, data stores, chat, and code execution. Praxis is the single entry point for MCP traffic, REST APIs, and the Admin UI.

## Architecture

```
    MCP clients          CLI / Admin UI
        |                     |
   port 8081 (MCP)     port 8080 (REST + static UI)
        |                     |
        +----[Wanaku Praxis (Rust)]----+
               |          |           |
       direct (in-mem     |      proxy to Classic
       + file persist)    |      catalogs, templates,
       tools, resources,  |      data stores, chat,
       prompts, forwards, |      code execution
       namespaces,        |
       services           |
               |          |
          gRPC (9000+)    |
               |     port 8080 (internal)
   [Capability Svcs]      |
                   [Wanaku Classic (Java)]
                    catalogs, templates,
                    data stores, chat,
                    code execution
```

**Praxis owns directly:** tools, resources, prompts, forwards, namespaces, services, interactions
**Praxis proxies to Classic:** service-catalog, service-template, data-store, chat, code-execution, capabilities
**Classic owns:** service catalogs, templates, data stores, chat proxy, code execution

**Service addresses (gRPC endpoints for capability services):**
- Static config via `wanaku.yaml` (existing)
- Later: dynamic registration API on Praxis (`/api/v1/services` CRUD), used by CLI and Operator
- No sync from Classic — Praxis is the source of truth for service routing

## REST API Routing (port 8080)

| Path prefix | Handled by |
|---|---|
| `/api/v1/tools` | Praxis (direct) |
| `/api/v1/resources` | Praxis (direct) |
| `/api/v1/prompts` | Praxis (direct) |
| `/api/v1/forwards` | Praxis (direct) |
| `/api/v1/namespaces` | Praxis (direct) |
| `/api/v1/services` | Praxis (direct) — new |
| `/api/v1/interactions` | Praxis (direct) |
| `/api/v1/config/inference` | Praxis (direct) |
| `/admin/*` | Praxis (static files) |
| `/healthz` | Praxis (direct) |
| `/api/v1/service-catalog` | Praxis → Classic proxy |
| `/api/v1/service-template` | Praxis → Classic proxy |
| `/api/v1/data-store` | Praxis → Classic proxy |
| `/api/v1/capabilities` | Praxis → Classic proxy |
| `/api/v1/chat` | Praxis → Classic proxy |
| `/api/v1/management/info` | Praxis → Classic proxy |
| `/api/v1/management/statistics` | Praxis → Classic proxy |
| `/api/v2/code-execution` | Praxis → Classic proxy |
| `/api/v2/tool-calls` | Praxis → Classic proxy |

## Changes in Praxis (wanaku-praxis repo)

### 1. Containerfile (new)
- Multi-stage Alpine build (pattern from praxis/Containerfile)
- EXPOSE 8081 (MCP) + 8083 (inference) + 8080 (mgmt/REST)
- Image: `quay.io/wanaku/wanaku-praxis:latest`

### 2. Configurable listen addresses
- `server/src/main.rs:78` — mgmt addr from `WANAKU_MGMT_LISTEN` (default `0.0.0.0:8080`)
- `server/src/default.yaml` — change `127.0.0.1` to `0.0.0.0` for all listeners
- Inference upstream via `WANAKU_INFERENCE_UPSTREAM` (default `127.0.0.1:11434`) + REST API at `POST /api/v1/config/inference`

### 3. Health endpoint
- `/healthz` on management API → `{"status":"ok"}`

### 4. Extensible persistence for registry
- Trait-based persistence backend behind the InMemoryRegistry
- Initial implementation: file-based (JSON or YAML on disk)
- Trait designed for future backends: database, remote Infinispan, etc.
- On writes: persist to backend. On startup: load from backend into memory.
- `apis/src/persistence.rs` (new) — trait definition + file backend
- Config via env: `WANAKU_PERSIST_BACKEND=file`, `WANAKU_PERSIST_PATH=/data/registry`

### 5. Services CRUD API (new)
- Add `/api/v1/services` endpoints to management API (GET list, GET by name, POST, DELETE)
- Matches existing pattern for tools/resources/prompts
- Covers the `ServiceEntry` type already in `apis/src/registry.rs`
- Used by CLI and Operator to register capability gRPC addresses dynamically

### 6. REST proxy module (new: `server/src/proxy.rs`)
- Reverse-proxy for paths that Classic owns
- Configured via `WANAKU_CLASSIC_URL` (e.g. `http://classic-svc:8080`)
- When unset, proxied paths return 503 (standalone mode)
- Simple path-prefix matching, forwards method/headers/body

### 7. Admin UI static file serving
- Serve React SPA from configurable directory at `/admin/*`
- Env: `WANAKU_UI_PATH` (default `/opt/wanaku/admin`)
- Container: mount or bake Classic's UI build artifact into this path
- SPA's API calls go to same origin (port 8080), no CORS issues

### 8. CI: container build workflow
- `.github/workflows/container.yaml` — build + push to quay.io

## Changes in Classic (wanaku repo)

### 9. Remove MCP server
- Remove `quarkus-mcp-server-http` extension from router-backend
- Remove MCP namespace path configs from `application.properties`
- Remove MCP-related JAX-RS resources (tools, resources, prompts, forwards, namespaces)
- Remove service discovery endpoint (registration moves to Praxis)
- Keep: service catalog, templates, data stores, capabilities, chat, code execution

### 10. Operator: separate Deployments
- Praxis gets its own Deployment + ClusterIP Service (ports 8081, 8080)
- Classic gets its own Deployment + internal-only ClusterIP Service (port 8080)
- Praxis env: `WANAKU_CLASSIC_URL=http://internal-{name}-classic:8080`
- Operator registers capability service addresses with Praxis via `POST /api/v1/services`
- Ingress/Route points to Praxis only

### 11. docker-compose
- Praxis as user-facing service, Classic as internal backend
- Ports exposed: 8081 (MCP), 8080 (REST/UI)
- Classic port 8080 not exposed externally

## What Stays Unchanged

- **gRPC protocol**: identical proto files, same wire format
- **Capability services**: still serve gRPC on their ports, just register with Praxis instead of Classic

## Implementation Order

| # | What | Repo | Blocks |
|---|---|---|---|
| 1 | Containerfile | praxis | 8, 10, 11 |
| 2 | Configurable addresses + inference config | praxis | — |
| 3 | Health endpoint | praxis | — |
| 4 | Extensible persistence | praxis | — |
| 5 | Services CRUD API | praxis | — |
| 6 | REST proxy module | praxis | — |
| 7 | Admin UI static serving | praxis | — |
| 8 | CI container workflow | praxis | 1 |
| 9 | Remove MCP server from Classic | classic | — |
| 10 | Operator update | classic | 1, 5, 9 |
| 11 | docker-compose update | classic | 1 |

Steps 2-7 can proceed in parallel. Steps 9-11 can proceed in parallel (9 is independent of Praxis).

## Verification

- `cargo test` on praxis after steps 2-7
- Container build: `docker build -f Containerfile -t wanaku-praxis:test .`
- docker-compose: start both, verify MCP on 8081, REST on 8080, proxied paths reach Classic
- Register service via `POST /api/v1/services`, restart Praxis, verify service persisted
- Invoke tool via MCP, confirm gRPC call reaches capability service

## Deferred Questions

1. **Authentication:** How auth works end-to-end is TBD. Classic currently uses Keycloak/OIDC. Need to decide: does Praxis validate tokens, delegate to Classic, or use a different mechanism?
2. **Port consolidation:** Praxis currently opens 8081 (MCP) + 8083 (inference) + 8080 (REST/UI). Evaluate merging some onto a single port with path-based routing.
3. **CLI migration:** CLI currently defaults to `--host :8080`. Needs retargeting to `:8080`. Evaluate how to make this smooth for existing users.
