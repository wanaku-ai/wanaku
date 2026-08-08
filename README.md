# Wanaku Praxis

A Rust-based MCP router built on the [Praxis](https://github.com/praxis-proxy/praxis) proxy framework. Think of it as the proof-of-concept sibling to [Wanaku](https://github.com/opiske/wanaku)—same routing logic, different runtime, zero JVM overhead.

## What It Does

Wanaku Praxis sits between AI clients and your backend capability services. When Claude (or any MCP client) calls a tool, it routes that request to the appropriate gRPC service, gets the result, and hands it back. It also does something more interesting: it can forward tools to remote MCP servers, auto-discover their capabilities, and serve them through a unified catalog. Your client sees one MCP endpoint; behind it, you've got a mix of local gRPC services and forwarded remote tools.

It handles namespaces, too. Register tools under `/finance/mcp` and they're isolated from `/legal/mcp`. Same server, different catalogs.

## Why This Exists

The original Wanaku runs on Quarkus and does this job well—and will continue to. The JVM ecosystem is a strength, not a problem: Quarkus, Camel integration capabilities, and the rest of the stack aren't going anywhere. But the routing engine sits at the bottom of every request path, and that layer benefits from the kind of low-level control that Rust and Praxis provide. This project moves just the routing engine to a stack better suited for that work. It's wire-compatible with Wanaku's protobuf definitions, so your existing capability services don't need to change.

Fair warning: this is a PoC. It proves the architecture holds up in Rust. It doesn't have all the polish, observability hooks, or battle scars of the Java version.

## Architecture

Three crates, clean separation:

- **`apis/`** — The glue layer. gRPC protobuf types (tonic-built from Wanaku's `.proto` files), a pooled gRPC client, an `rmcp`-based MCP client, and an in-memory registry with traits for tools/resources/prompts/forwards/namespaces/services.
- **`filters/`** — Praxis `HttpFilter` implementations. Namespace extraction, `tools/list`, `tools/call`, `resources/list`, `resources/read`, `prompts/list`, `prompts/get`, MCP initialization—standard MCP protocol stuff, plumbed into the Praxis filter pipeline.
- **`server/`** — The binary. Wires up the filters, boots a Pingora-based management REST API on port 8080, loads static config from `wanaku.yaml`, and starts listening for MCP traffic on 8081.

The interesting bit: filters are stateless. All the state lives in `InMemoryRegistry` (a `DashMap`-backed concurrent registry). CRUD operations happen via the management API; filters query the registry when routing requests.

## Running It

### Container

```bash
podman run -p 8080:8080 -p 8081:8081 quay.io/wanaku/wanaku-praxis
```

To preload tools and services, mount a `wanaku.yaml`:

```bash
podman run -p 8080:8080 -p 8081:8081 \
  -v ./wanaku.yaml:/etc/wanaku-praxis/wanaku.yaml \
  quay.io/wanaku/wanaku-praxis \
  --wanaku-config /etc/wanaku-praxis/wanaku.yaml
```

Multi-arch images (x86_64 and aarch64) are published to `quay.io/wanaku/wanaku-praxis` on every push to `main`.

### From Source

```bash
cargo build
cargo run
```

The first `cargo build` automatically builds the admin UI via `yarn` if `ui/admin/dist/` is missing (requires Node.js and Yarn installed).

> [!NOTE]
> You need to have the following software installed to build this project: 
> * protobuf 
> * yarn

### Endpoints

MCP endpoint: `http://localhost:8081/mcp` (or `/{namespace}/mcp` if you've registered namespaces).

Management API: `http://localhost:8080/api/v1/...` — CRUD for tools, resources, prompts, forwards, namespaces.

Admin UI: `http://localhost:8080/admin/`

### Configuration

Optional: drop a `wanaku.yaml` in the working directory to preload static tool and service definitions. Format:

```yaml
tools:
  - name: "search-docs"
    type: "doc-search"
    uri: "doc-search://semantic"
    description: "Searches documentation"
    input_schema:
      type: object
      properties:
        query:
          type: string
      required: [query]

services:
  - name: "doc-search"
    address: "localhost:9191"
    service_type: "tool-invoker"
```

## Management API Examples

Register a tool:
```bash
curl -X POST http://localhost:8080/api/v1/tools \
  -H "Content-Type: application/json" \
  -d '{
    "name": "calculator",
    "type": "math",
    "uri": "math://add",
    "description": "Adds numbers",
    "input_schema": {
      "type": "object",
      "properties": {
        "a": {"type": "number"},
        "b": {"type": "number"}
      }
    }
  }'
```

Register a remote MCP server as a forward:
```bash
curl -X POST http://localhost:8080/api/v1/forwards \
  -H "Content-Type: application/json" \
  -d '{
    "name": "upstream-mcp",
    "address": "http://remote.example.com/mcp"
  }'
```

Refresh tools from the forward (auto-discover):
```bash
curl -X POST http://localhost:8080/api/v1/forwards/upstream-mcp/refreshes
```

Now all tools from `remote.example.com` appear in your local catalog. The client has no idea they're forwarded.

## What's Different From Wanaku Java

- **No Quarkus:** Pingora + Praxis instead of Vert.x HTTP.
- **No Kubernetes config:** This is a standalone binary. Deploy it however you want.
- **Simpler observability:** Tracing via `tracing` crate, but no built-in metrics export yet.
- **Same gRPC contract:** Uses the exact same `.proto` files. Your backend services speak the same language.

## Key Dependencies

- `praxis-proxy-core`, `praxis-proxy-filter` — HTTP filter pipeline
- `praxis-ai` — MCP protocol filter support
- `tonic`/`prost` — gRPC client/server
- `rmcp` — MCP client for forwarding
- `dashmap` — Concurrent in-memory registry

## Authentication

Authentication is handled externally by [oauth2-proxy](https://github.com/oauth2-proxy/oauth2-proxy) — Praxis itself contains no auth code. Two oauth2-proxy instances sit in front of the MCP and management ports, sharing an SSO cookie:

- **MCP proxy** (`:4180` → `:8081`) — protects MCP endpoints, any authenticated user
- **Management proxy** (`:4181` → `:8080`) — protects admin UI and REST API, admin role required

See [`deploy/auth/README.md`](deploy/auth/README.md) for setup instructions (Docker Compose and local development).

Praxis serves [RFC 9728](https://datatracker.ietf.org/doc/rfc9728/) OAuth Protected Resource Metadata at `/.well-known/oauth-protected-resource/{namespace}/mcp` via the `mcp-metadata` feature crate. Set `WANAKU_AUTH_ISSUER` to your Keycloak realm URL to populate the `authorization_servers` field.

## Contributing

### Prerequisites

- Rust 1.96+ and Cargo
- Node.js and Yarn (for admin UI)
- Keycloak (for auth testing)
- oauth2-proxy (for auth testing): `brew install oauth2-proxy`

### Development Setup

```bash
cargo build    # builds Rust + admin UI
cargo test     # runs all tests
cargo run      # starts Praxis (MCP on :8081, management on :8080)
```

### Testing with Authentication

To test with auth locally, run Keycloak and two oauth2-proxy instances alongside Praxis. See [`deploy/auth/README.md`](deploy/auth/README.md) for the full setup and Keycloak client configuration requirements.

## What's Missing (and Known)

- Persistence. Registry is in-memory by default. Set `WANAKU_PERSIST_BACKEND=file` to enable file-based persistence.
- Metrics. Logs and traces, yes. Prometheus endpoint, no.
- Graceful config reload. Edit `wanaku.yaml`, restart the binary.

## License

Apache 2.0
