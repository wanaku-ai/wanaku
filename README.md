# Wanaku - A MCP Router that connects everything

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Build](https://img.shields.io/github/actions/workflow/status/wanaku-ai/wanaku/build.yml?branch=main)](https://github.com/wanaku-ai/wanaku/actions)
[![Release](https://img.shields.io/github/v/release/wanaku-ai/wanaku)](https://github.com/wanaku-ai/wanaku/releases)

The Wanaku MCP Router is a router for AI-enabled applications powered by the [Model Context Protocol (MCP)](https://modelcontextprotocol.io/).

This protocol is an open protocol that standardizes how applications provide context to LLMs.

The project name comes from the origins of the word [Guanaco](https://en.wikipedia.org/wiki/Guanaco), a camelid native to
South America.

## Key Features

- **Unified Access** - Centralized routing and resource management for AI agents
- **MCP-to-MCP Bridge** - Act as a gateway or proxy for other MCP servers
- **Extensive Connectivity** - Leverage 300+ Apache Camel components for integration
- **Secure by Default** - Built-in authentication and authorization via Keycloak
- **Kubernetes-Native** - First-class support for OpenShift and Kubernetes deployments
- **Extensible Architecture** - Easy to add custom tools and resource providers
- **Multi-Namespace Support** - Organize tools and resources across isolated namespaces

## Quick Start

[![Getting Started With Wanaku](https://img.youtube.com/vi/-fuNAo2j4SA/0.jpg)](https://www.youtube.com/watch?v=-fuNAo2j4SA)

### Wanaku CLI Installation

```shell
# Install via JBang
jbang app install wanaku@wanaku-ai/wanaku

# Or download the latest binary from releases
# https://github.com/wanaku-ai/wanaku/releases
```

### Basic Usage

```shell
# Authenticate with your Wanaku router
wanaku auth login --url http://localhost:8080

# List available tools
wanaku tools list

# Add a new tool
wanaku tools add --uri http://example.com/api --service http

# List available resources
wanaku resources list
```

For complete installation and configuration instructions, see the [Usage Guide](docs/usage.md).

## Documentation

- **[Usage Guide](docs/usage.md)** - Installation, deployment, and CLI usage
- **[Architecture](docs/architecture.md)** - System architecture and components
- **[Building](docs/building.md)** - Build and package the project
- **[Contributing](CONTRIBUTING.md)** - Contribution guidelines
- **[Configuration](docs/configurations.md)** - Configuration reference
- **[Security](SECURITY.md)** - Security policy and best practices

## Community

- [GitHub Issues](https://github.com/wanaku-ai/wanaku/issues) - Bug reports and feature requests
- [Discussions](https://github.com/wanaku-ai/wanaku/discussions) - Ask questions and share ideas
- [Examples](https://github.com/wanaku-ai/wanaku-examples) - Example capabilities and integrations

## License

This project is licensed under the Apache 2.0 License - see the [LICENSE](LICENSE) file for details.
