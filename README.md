# Wanaku — A Governed Action Proxy for AI Agents

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Build](https://img.shields.io/github/actions/workflow/status/wanaku-ai/wanaku/main-build.yml?branch=main)](https://github.com/wanaku-ai/wanaku/actions)
[![Release](https://img.shields.io/github/v/release/wanaku-ai/wanaku)](https://github.com/wanaku-ai/wanaku/releases)

Wanaku is a governed action proxy for AI agents. It sits between agents and the systems they act on, intercepting tool calls, agent-to-agent messages, and inference traffic. Integration developers build [Apache Camel](https://camel.apache.org/) routes and publish them as tools; agents call those tools with parameters, but Wanaku runs the actual work — the agent never touches backend systems directly. Policy, identity, data controls, and audit happen in the proxy, not in the agent.

The project name comes from the origins of the word [Guanaco](https://en.wikipedia.org/wiki/Guanaco), a camelid native to
South America.

## Key Features

- **Agent Isolation** — Agents call tools through Wanaku; they never reach backend systems directly
- **Policy Enforcement** — LLM-powered evaluators + WASM action scripts classify, filter, and block tool calls in the proxy layer
- **Identity & Auth** — Authentication and authorization via oauth2-proxy and Keycloak, enforced before actions reach backends
- **Tool Discovery** — Auto-discover tools from upstream MCP servers; integration developers publish Camel routes as tools
- **Namespace Isolation** — Organize tools and resources across isolated namespaces per team, tenant, or environment
- **Extensible Architecture** — Plugin system via feature crates and a composable filter pipeline
- **Admin Dashboard** — Web UI for managing tools, resources, prompts, and forwards
- **Container-Ready** — Multi-arch images (x86_64, aarch64) published automatically

## Quick Start

### Install

Download the latest early-access build on Linux or macOS:

```bash
curl -fsSL https://raw.githubusercontent.com/wanaku-ai/wanaku/main/get-wanaku.sh | bash
```

The installer detects the host platform, verifies the release checksum, and installs `wanaku-server` into `$HOME/bin`. Override the destination with `WANAKU_INSTALL_DIR`.

### Container

```bash
podman run -p 8080:8080 -p 8081:8081 quay.io/wanaku/wanaku-server
```

To preload forwards, mount a `wanaku.yaml`:

```bash
podman run -p 8080:8080 -p 8081:8081 \
  -v ./wanaku.yaml:/etc/wanaku/wanaku.yaml \
  quay.io/wanaku/wanaku-server \
  --wanaku-config /etc/wanaku/wanaku.yaml
```

### From Source

> [!NOTE]
> Building from source requires: Rust 1.96+ and Yarn (for the admin UI).

```bash
cargo build
cargo run
```

The first `cargo build` automatically builds the admin UI via `yarn` if `ui/admin/dist/` is missing.

### Endpoints

| Endpoint | Address | Description |
|---|---|---|
| MCP | `http://localhost:8081/mcp` | MCP protocol endpoint (or `/{namespace}/mcp` for namespaced access) |
| Management API | `http://localhost:8080/api/v1/...` | CRUD for tools, resources, prompts, forwards, namespaces |
| Admin UI | `http://localhost:8080/admin/` | Web dashboard |

### Learn Wanaku

The easiest way to learn Wanaku is by following the **[guided tutorial](https://wanaku.ai/docs/demos/)**.

The reference documentation, including the complete installation and configuration instructions, is available in the [usage guide](https://wanaku.ai/docs/version/).

## Configuration

Drop a `wanaku.yaml` in the working directory to preload forward definitions:

```yaml
forwards:
  - name: "upstream-mcp"
    address: "http://remote.example.com/mcp"
```

If no config file is provided, the server starts with an empty registry that can be populated via the management API.

## Management API Examples

Register a remote MCP server as a forward (its tools are auto-discovered):
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

All tools from the remote server now appear in your local catalog. The client has no idea they're forwarded.

## Authentication

Authentication is handled externally by [oauth2-proxy](https://github.com/oauth2-proxy/oauth2-proxy). Two instances sit in front of the MCP and management ports, sharing an SSO cookie:

- **MCP proxy** (`:4180` → `:8081`) — protects MCP endpoints, any authenticated user
- **Management proxy** (`:4181` → `:8080`) — protects the admin UI and REST API, admin role required

Wanaku also serves [RFC 9728](https://datatracker.ietf.org/doc/rfc9728/) OAuth Protected Resource Metadata at `/.well-known/oauth-protected-resource/{namespace}/mcp`. Set `WANAKU_AUTH_ISSUER` to your Keycloak realm URL to populate the `authorization_servers` field.

See [`deploy/auth/README.md`](deploy/auth/README.md) for setup instructions (Docker Compose and local development).

## Documentation

The **[Wanaku Documentation](https://wanaku.ai/docs/)** website contains the full project documentation.

Contributors working on the project may want to refer to the development documentation:

- [Getting Started](docs/getting-started.md) - Development setup guide
- [Architecture](docs/architecture.md) - System architecture and components
- [Configuration](docs/configuration.md) - Environment variables and configuration reference
- [Management API](docs/management-api.md) - API reference
- [Admin UI](docs/contributing-admin-ui.md) - Admin dashboard development
- [Features / Plugins](docs/features.md) - Feature crate system
- [Plugin Development](docs/plugin-development-guide.md) - Guide for writing new feature crates
- [Evaluator Engine](docs/evaluator-engine.md) - WASM-based evaluator
- [Action Policies](docs/action-policies.md) - Deterministic MCP action rules
- [Contributing](CONTRIBUTING.md) - Contribution guidelines
- [Security](SECURITY.md) - Security policy

## Community

- [GitHub Issues](https://github.com/wanaku-ai/wanaku/issues) - Bug reports and feature requests
- [Discussions](https://github.com/wanaku-ai/wanaku/discussions) - Ask questions and share ideas
- [Examples](https://github.com/wanaku-ai/wanaku-examples) - Example capabilities and integrations

## Related Projects

- [Camel Integration Capability](https://github.com/wanaku-ai/camel-integration-capability/) — Build Apache Camel routes and publish them as tools that agents can call through Wanaku
- [Java SDK](https://github.com/wanaku-ai/wanaku-capabilities-java-sdk/) — SDK for building capability services in Java

## License

This project is licensed under the Apache 2.0 License - see the [LICENSE](LICENSE) file for details.
