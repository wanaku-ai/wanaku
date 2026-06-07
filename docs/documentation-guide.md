# Wanaku Documentation Guide

This guide provides an overview of all Wanaku documentation and helps you find the right resource for your needs.

## Learning Path

Follow this recommended order to learn Wanaku progressively:

### Chapter 1: Getting Started

- **[1.00 Understanding MCP and Wanaku](https://github.com/wanaku-ai/wanaku-demos/tree/main/1.00-understanding-mcp-and-wanaku)** — Introduction to the Model Context Protocol and Wanaku's role as an MCP router
- **[1.01 Your First Tool](https://github.com/wanaku-ai/wanaku-demos/tree/main/1.01-your-first-tool)** — Register and invoke your first tool capability
- **[1.02 Basic Wanaku Operations](https://github.com/wanaku-ai/wanaku-demos/tree/main/1.02-basic-wanaku-operations)** — Essential CLI commands and concepts

### Chapter 2: Working with Capabilities

- **[2.01 Introduction to Capabilities](https://github.com/wanaku-ai/wanaku-demos/tree/main/2.01-introduction-to-capabilities)** — Understanding Wanaku's capability model
- **[2.02 Service Catalogs](https://github.com/wanaku-ai/wanaku-demos/tree/main/2.02-service-catalogs)** — Managing collections of capabilities
- **[2.03 Service Templates](https://github.com/wanaku-ai/wanaku-demos/tree/main/2.03-service-templates)** — Reusable capability templates
- **[2.04 Organizing with Namespaces](https://github.com/wanaku-ai/wanaku-demos/tree/main/2.04-organizing-with-namespaces)** — Namespace-based organization
- **[2.05 Aggregating MCP Servers with Forwards](https://github.com/wanaku-ai/wanaku-demos/tree/main/2.05-aggregating-mcp-servers-with-forwards)** — Federating multiple MCP servers

### Chapter 3: Security and Authentication

- **[3.01 Wanaku on the Cloud](https://github.com/wanaku-ai/wanaku-demos/tree/main/3.01-wanaku-on-the-cloud)** — Cloud deployment basics
- **[3.02 Camel Integration Capability](https://github.com/wanaku-ai/wanaku-demos/tree/main/3.02-camel-integration-capability)** — Apache Camel integration
- **[3.03 Authentication Deep Dive](https://github.com/wanaku-ai/wanaku-demos/tree/main/3.03-authentication-deep-dive)** — OAuth2/OIDC authentication

### Chapter 4: Building Custom Capabilities

- **[4.01 Plain Java Capability](https://github.com/wanaku-ai/wanaku-demos/tree/main/4.01-plain-java-capability)** — Build a capability in pure Java
- **[4.02 Exposing Existing Camel Routes](https://github.com/wanaku-ai/wanaku-demos/tree/main/4.02-exposing-existing-routes)** — Expose existing Camel routes as MCP tools
- **[4.03 Building a Resource Provider](https://github.com/wanaku-ai/wanaku-demos/tree/main/4.03-building-a-resource-provider)** — Implement a resource capability

### Chapter 5: Production Operations

- **[5.01 Camel Assistant](https://github.com/wanaku-ai/wanaku-demos/tree/main/5.01-camel-assistant)** — AI-powered Camel integration assistant
- **[5.02 Monitoring Wanaku in Production](https://github.com/wanaku-ai/wanaku-demos/tree/main/5.02-monitoring-wanaku-in-production)** — Observability and monitoring

## Reference Documentation

### Architecture and Internals

- **[Architecture Overview](architecture.md)** — System architecture and component interactions
- **[gRPC Bridge](grpc-bridge.md)** — gRPC protocol for capability communication
- **[Configurations](configurations.md)** — Complete configuration reference

### Kubernetes Operator

- **[Operator Guide](operator.md)** — Deploying and managing Wanaku with the Kubernetes Operator
  - Includes CRD field reference for `WanakuRouter`, `WanakuCapability`, and `WanakuServiceCatalog`

  ### Authentication and Security

  - **[Authentication Setup (usage.md)](usage.md#keycloak-setup-for-wanaku)** — Keycloak/OIDC configuration
  - **[Security Best Practices](https://github.com/wanaku-ai/wanaku-demos/tree/main/security-best-practices)** — Production security guide

  ### Service Management

  - **[Service Catalogs](usage.md#service-catalogs)** — Managing service catalogs
  - **[Service Templates](service-templates.md)** — Creating and using service templates
  - **[FAQ](faq.md)** — Frequently asked questions

  ## Guides and How-Tos

  | Guide | Description |
  |-------|-------------|
  | [Troubleshooting Guide](https://github.com/wanaku-ai/wanaku-demos/tree/main/troubleshooting) | Diagnose and fix common issues |
  | [Security Best Practices](https://github.com/wanaku-ai/wanaku-demos/tree/main/security-best-practices) | Secure your Wanaku deployment |
  | [Testing Capabilities](https://github.com/wanaku-ai/wanaku-demos/tree/main/testing-capabilities) | Test tools and resources with curl and integration tests |
  | [Contributing](../contributing.md) | How to contribute to Wanaku |

  ## Quick Links

  - **Install Wanaku**: `curl -L https://github.com/wanaku-ai/wanaku/raw/main/get-wanaku.sh | bash`
  - **Start locally**: `wanaku server start`
  - **GitHub**: <https://github.com/wanaku-ai/wanaku>
  - **Demos**: <https://github.com/wanaku-ai/wanaku-demos>
  - **Issues**: <https://github.com/wanaku-ai/wanaku/issues>
