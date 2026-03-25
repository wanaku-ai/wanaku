# Wanaku Router Backend

## Overview

The main MCP server engine that receives MCP requests and routes them to appropriate capability services.

## Purpose

The router backend is the central component of Wanaku that:
- Serves MCP protocol requests from AI clients
- Routes tool invocations to appropriate tool services via gRPC
- Routes resource read requests to appropriate providers via gRPC
- Manages tool and resource registrations
- Provides HTTP management API for configuration
- Handles authentication and authorization
- Manages multiple MCP namespaces

## Key Features

- **MCP Protocol Support**: SSE and Streamable HTTP transports
- **Multi-Namespace**: Support for 10+ isolated namespaces
- **gRPC Communication**: Efficient communication with capability services
- **Management API**: REST API for router configuration
- **Web UI**: React-based administration interface
- **Authentication**: OIDC integration via Keycloak
- **Service Discovery**: Automatic registration and health monitoring of capabilities
- **Data Persistence**: Infinispan-based storage for router state

## Architecture

Built on:
- **Quarkus**: Modern Java framework for cloud-native applications
- **Quarkus MCP Server Extension**: MCP protocol implementation
- **gRPC**: Service communication protocol
- **Infinispan**: Embedded data grid for persistence

## Running

### Development Mode

```shell
mvn quarkus:dev
```

### Production Mode

```shell
java -jar target/quarkus-app/quarkus-run.jar
```

### Container

```shell
podman run -p 8080:8080 quay.io/wanaku/wanaku-router-backend:latest
```

## Configuration

Key configuration properties (see [Configuration Guide](../../docs/configurations.md) for complete reference):

```properties
# HTTP
quarkus.http.port=8080

# Authentication
auth.server=http://localhost:8543
quarkus.oidc.client-id=wanaku-mcp-router

# Persistence
wanaku.persistence.infinispan.base-folder=${user.home}/.wanaku/router/

# MCP
quarkus.mcp.server.traffic-logging.enabled=true
```

## API Endpoints

- **MCP (SSE)**: `http://localhost:8080/mcp/sse`
- **MCP (HTTP)**: `http://localhost:8080/mcp/`
- **Management API**: `http://localhost:8080/api/`
- **Web UI**: `http://localhost:8080/`
- **Health**: `http://localhost:8080/q/health`

## Related Documentation

- [Usage Guide](../../docs/usage.md)
- [Architecture](../../docs/architecture.md)
- [Router Internals](../../docs/wanaku-router-internals.md)
- [Configuration Reference](../../docs/configurations.md)