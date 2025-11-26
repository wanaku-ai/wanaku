# Frequently Asked Questions (FAQ)

## General Questions

### What is Wanaku?

Wanaku is an MCP (Model Context Protocol) Router that acts as a centralized hub for managing and governing how AI agents access tools and resources. It doesn't host tools or resources directly but instead routes requests to specialized capability services, providing unified access, security, and resource management for AI-enabled applications.

### What does "Wanaku" mean?

The project name comes from the origins of the word "Guanaco," a camelid native to South America. The connection to Apache Camel (the integration framework used by Wanaku) makes this name particularly fitting.

### What is the Model Context Protocol (MCP)?

The Model Context Protocol (MCP) is an open protocol that standardizes how applications provide context to Large Language Models (LLMs). It defines how tools, resources, and prompts are exposed and accessed by AI agents. Learn more at [modelcontextprotocol.io](https://modelcontextprotocol.io/).

### Is Wanaku open source?

Yes, Wanaku is open source and licensed under Apache 2.0. You can find the source code at [github.com/wanaku-ai/wanaku](https://github.com/wanaku-ai/wanaku).

### What's the difference between tools and resources in Wanaku?

- **Tools** operate in a request/reply mode, performing processing on input (e.g., making HTTP requests, executing commands, querying databases)
- **Resources** provide read access to data without necessarily processing input (e.g., reading files, accessing S3 objects, reading Kafka topics)

## Architecture and Design

### Why does Wanaku use a router architecture instead of hosting tools directly?

The router architecture provides several advantages:
- **Isolation**: Each capability service runs independently, improving security and reliability
- **Scalability**: Services can be scaled independently based on demand
- **Flexibility**: Easy to add, remove, or update capabilities without affecting the router
- **Language independence**: Capability services can be written in any language that supports gRPC

### What is a "capability" in Wanaku?

A capability is a service that provides specific functionality to the Wanaku router. Capabilities can be:
- **Tool services**: Provide executable tools (HTTP client, exec, Tavily search, etc.)
- **Resource providers**: Provide read access to resources (files, S3, FTP, etc.)
- **MCP servers**: Bridge to other MCP servers via HTTP

### How does Wanaku communicate with capability services?

Wanaku uses gRPC for communication between the router backend and capability services. This provides efficient, type-safe communication with built-in support for streaming and bidirectional communication.

### What is the role of Keycloak in Wanaku?

Keycloak provides authentication and authorization for:
- Router management API and web UI access
- Service-to-service authentication between the router and capabilities
- Future support for fine-grained access control to tools and resources

## Installation and Setup

### What are the prerequisites for running Wanaku?

**For development:**
- Java 17 or later
- Maven 3.x
- Keycloak instance (can run via Podman/Docker)

**For production deployment:**
- OpenShift or Kubernetes cluster (optional but recommended)
- Keycloak instance
- Container runtime (Podman/Docker)

### Do I need to install Keycloak separately?

Yes, Wanaku requires a Keycloak instance for authentication. You can:
- Run Keycloak locally using Podman/Docker (for development)
- Deploy Keycloak to OpenShift/Kubernetes (for production)
- Use an existing Keycloak instance

See the [Usage Guide](usage.md#keycloak-setup-for-wanaku) for setup instructions.

### Can I run Wanaku without Kubernetes?

Yes, Wanaku can run standalone on any machine with Java. You can:
- Download pre-built binaries from the releases page
- Build from source
- Run locally for development and testing

Kubernetes/OpenShift deployment is recommended for production but not required.

### How do I install the Wanaku CLI?

There are two main options:

**Via JBang (recommended):**
```shell
jbang app install wanaku@wanaku-ai/wanaku
```

**Via binary download:**
Download the latest release from [GitHub releases](https://github.com/wanaku-ai/wanaku/releases) and extract to your PATH.

## Usage and Configuration

### How do I add a new tool to Wanaku?

```shell
# First ensure the capability service providing the tool is running
wanaku capabilities list

# Then add the tool
wanaku tools add --uri <tool-uri> --service <service-name>
```

See the [Usage Guide](usage.md#managing-mcp-tools) for detailed instructions.

### How do I add a new resource?

```shell
# Ensure the resource provider service is running
wanaku capabilities list

# Add the resource
wanaku resources add --uri <resource-uri> --service <service-name>
```

### What are namespaces and when should I use them?

Namespaces allow you to organize tools and resources into isolated groups. Use cases include:
- Separating tools by environment (dev, staging, prod)
- Organizing by team or project
- Isolating tools by security level
- Providing different tool sets to different AI agents

Wanaku provides 10 namespaces (ns-1 through ns-10) plus a default namespace and a public one.

### How do I connect an MCP client to Wanaku?

**For SSE transport:**
```
http://localhost:8080/mcp/sse
```

**For Streamable HTTP:**
```
http://localhost:8080/mcp/
```

**For a specific namespace:**
```
http://localhost:8080/ns-1/mcp/sse
```

See [Supported/Tested Clients](usage.md#supportedtested-clients) for client-specific configuration.

### Can I use Wanaku with Claude Desktop?

Yes, Wanaku works with Claude Desktop and other MCP-compatible clients. See the [Usage Guide](usage.md#claude) for configuration examples.

## Development and Extension

### How do I create a custom capability?

Use the Wanaku CLI to generate a project template:

**For a tool service:**
```shell
wanaku services create tool --name my-tool
```

**For a resource provider:**
```shell
wanaku services create provider --name my-provider
```

See the [Contributing Guide](contributing.md) for detailed instructions.

### Should I use Camel or plain Quarkus for my capability?

**Use the Camel Integration Capability** (recommended for most cases):
- When you need to integrate with external systems
- To leverage 300+ Apache Camel components
- For rapid development of common integration patterns

**Use plain Quarkus** when:
- You need very specific custom logic
- You want minimal dependencies
- Performance is critical and you want full control

### Can I write capability services in languages other than Java?

Yes! Any language that supports gRPC can be used to create capability services. The service must implement the appropriate gRPC protocol defined in the Wanaku core-exchange module.

### Where should I put my custom capabilities?

Custom capabilities should generally go in the [Wanaku Examples](https://github.com/wanaku-ai/wanaku-examples) repository, not in the main Wanaku project. The main project is reserved for core, widely-applicable capabilities.

## Security

### Is Wanaku secure for production use?

Wanaku provides security features including:
- OIDC-based authentication via Keycloak
- Service-to-service authentication
- TLS support for external endpoints
- Network isolation via Kubernetes

However, you must properly configure these features. See the [Security Guide](../SECURITY.md) for best practices.

### How do I secure API keys and secrets used by tools?

Use the provisioning system to securely provide secrets to capability services:
- Store secrets in Kubernetes Secrets
- Configure tools to reference secrets via environment variables
- Never commit secrets to version control

### Does Wanaku support fine-grained access control?

Currently, all authenticated users have admin access to tools and resources. Fine-grained access control is planned for future versions.

### Can I disable authentication for development?

While not recommended, you can configure Keycloak in dev mode with simplified settings. However, authentication cannot be completely disabled.

## Troubleshooting

### Why aren't my capability services showing up?

Common causes:
1. Service registration is not enabled or misconfigured
2. Network connectivity issues between service and router
3. Incorrect OIDC credentials
4. Service is not running or is crashing

See the [Troubleshooting Guide](usage.md#troubleshooting) for detailed solutions.

### Why can't my MCP client connect to Wanaku?

Check:
1. Router is running and accessible
2. Correct endpoint URL (include `/sse` for SSE transport)
3. Firewall rules allow traffic
4. CORS is properly configured (for web clients)

### How do I enable debug logging?

Add to `application.properties`:
```properties
quarkus.log.level=DEBUG
quarkus.log.category."ai.wanaku".level=DEBUG
quarkus.mcp.server.traffic-logging.enabled=true
```

### Where can I get help?

- Check the [Troubleshooting Guide](usage.md#troubleshooting)
- Search [GitHub Issues](https://github.com/wanaku-ai/wanaku/issues)
- Ask in [GitHub Discussions](https://github.com/wanaku-ai/wanaku/discussions)
- Review the full [documentation](https://github.com/wanaku-ai/wanaku/tree/main/docs)

## Performance and Scaling

### What are the resource requirements for Wanaku?

**Minimum for development:**
- Router backend: 512MB RAM, 1 CPU
- Each capability service: 256MB RAM, 0.5 CPU
- Keycloak: 512MB RAM, 1 CPU

**Recommended for production:**
- Router backend: 1-2GB RAM, 2 CPU
- Capability services: 512MB-1GB RAM per service
- Adequate resources for Keycloak

### Can Wanaku handle multiple concurrent requests?

Yes, Wanaku is built on Quarkus and designed for concurrent request handling. Performance depends on:
- Available system resources
- Number and type of capability services
- Network latency between components

### Can I scale Wanaku horizontally?

The router backend can be scaled horizontally in Kubernetes. Capability services can also be scaled independently based on demand.

## Compatibility

### Which MCP clients are supported?

Any MCP client compliant with the MCP protocol is supported. 

Wanaku was tested with different agents frameworks and MCP clients that include:
- Claude Desktop
- Langflow
- LangChain4j MCP client
- HyperChat
- LibreChat
- Witsy
- Embedded LLMChat
- And many others

Any MCP-compliant client should work with Wanaku.

### What MCP protocol versions does Wanaku support?

Wanaku supports the current stable MCP protocol specification. Check the releases page for version compatibility information.

### Can Wanaku act as a bridge to other MCP servers?

Yes! Wanaku includes an MCP-to-MCP bridge feature that allows it to forward requests to other MCP servers using HTTP transport, effectively aggregating multiple MCP servers behind a single endpoint.

## Miscellaneous

### What's the difference between Wanaku and running MCP servers directly?

Benefits of using Wanaku:
- Centralized management and governance
- Unified authentication and authorization
- Tool and resource organization via namespaces
- Service discovery and health monitoring
- Ability to aggregate multiple MCP servers
- Consistent interface regardless of backend services

### Is there a web UI for Wanaku?

Yes, Wanaku includes a React-based web UI for managing the router, accessible by default at `http://localhost:8080`. However, some features are currently only available via the CLI.

### How often is Wanaku updated?

Check the [releases page](https://github.com/wanaku-ai/wanaku/releases) for the latest versions and release notes. The project follows semantic versioning.

### How can I contribute to Wanaku?

See the [Contributing Guide](../CONTRIBUTING.md) for information on:
- Setting up your development environment
- Creating new capabilities
- Submitting pull requests
- Reporting issues

### Where can I find examples and tutorials?

- [Getting Started Video](https://www.youtube.com/watch?v=-fuNAo2j4SA)
- [Wanaku Examples Repository](https://github.com/wanaku-ai/wanaku-examples)
- [Wanaku Demos](https://github.com/wanaku-ai/wanaku-demos)
- Project documentation in the `docs/` directory
