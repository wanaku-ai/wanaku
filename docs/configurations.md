# Wanaku Configuration

This document provides a comprehensive overview of the configuration options for all components of the Wanaku project. 

Described here are both Wanaku-specific configurations, prefixed with `wanaku`, and relevant [Quarkus-specific](https://quarkus.io/guides/all-config) 
configurations, prefixed with `quarkus`.

> [!NOTE]
> Quarkus is the ultimate source for their descriptions. In case the description here conflicts with the ones from
> Quarkus, please consider the ones from them as being the actual correct value.

Properties are typically stored in `application.properties` files within each module and can be set in runtime using 
`-D<property.name>=<value>` or by exporting equivalent environment variables (i.e.: `PROPERTY_NAME=<value>`).

> [!IMPORTANT]
> Some of the settings can only be set at build time. 

## 1. Router Backend

Configuration for the main Wanaku Router Backend (`wanaku-router-backend`), which orchestrates all services.

### General & HTTP

| Property | Description |
| --- | --- |
| `quarkus.http.port` | `8080` - The primary HTTP port for the router backend. |
| `quarkus.http.cors.enabled` | `true` - Enables Cross-Origin Resource Sharing (CORS). |
| `quarkus.http.cors.origins` | A comma-separated list of allowed origins for CORS requests (e.g., for the admin UI). |
| `quarkus.http.access-log.enabled` | `true` - Enables the HTTP access log for monitoring requests. |

### Multi-Component Protocol (MCP) Server

| Property | Description |
| --- | --- |
| `quarkus.grpc.server.use-separate-server` | `false` - The gRPC server shares the main HTTP server, avoiding the need for a separate port. |
| `quarkus.mcp.server.wanaku-internal.sse.root-path` | `/wanaku-internal/mcp` - The SSE endpoint path for the internal MCP namespace. |
| `quarkus.mcp.server.ns-*.sse.root-path` | `/ns-*/mcp` - The SSE endpoint paths for the 10 available external namespaces (`ns-1` to `ns-10`). |
| `quarkus.mcp.server.traffic-logging.enabled` | `true` - Enables logging of all MCP traffic for debugging. |
| `quarkus.mcp.server.traffic-logging.text-limit` | `1000000` - The maximum length of the body to log for MCP traffic. |
| `quarkus.mcp.server.server-info.name` | `Wanaku` - The name of the server. |
| `quarkus.mcp.server.server-info.version` | The version of the server, taken from the project version. |
| `quarkus.mcp.server.client-logging.default-level` | `debug` - The default logging level for MCP clients. |

### Authentication & Authorization (OIDC)

| Property | Description |
| --- | --- |
| `auth.server` | The base address of the Keycloak authentication server (e.g., `http://localhost:8543`). |
| `auth.proxy` | The public-facing address of the OIDC proxy (e.g., `http://localhost:8080`). |
| `quarkus.oidc.auth-server-url` | The full URL to the Keycloak realm, derived from `auth.server`. |
| `quarkus.oidc.client-id` | `wanaku-mcp-router` - The OIDC client ID for the router backend itself. |
| `quarkus.oidc.application-type` | `hybrid` - Allows the backend to act as both a web app (for the admin UI) and a service. |
| `quarkus.oidc.tls.verification` | `none` - Disables TLS verification for the OIDC provider (for development). |
| `quarkus.oidc-proxy.enabled` | `true` - Enables the OIDC proxy feature, which simplifies OIDC integration. |
| `quarkus.http.auth.permission.*.paths` | Defines path patterns for different security policies (`permit`, `authenticated`). |
| `quarkus.http.auth.permission.*.policy` | Assigns a security policy to the corresponding path pattern. |

### Persistence (`core-persistence-infinispan`)

| Property | Description                                                                   |
| --- |-------------------------------------------------------------------------------|
| `wanaku.persistence.infinispan.base-folder` | Where to store Infinispan files (defaults to `${user.home}/.wanaku/router/`). |
| `wanaku.infinispan.max-state-count` | `10` - The maximum number of historical states to keep for each service.      |

## 2. Capabilities (Tool Services)

### Common Capability Settings (`core-capabilities-base`)

These settings apply to most tool services and are foundational for their operation.

| Property | Description |
| --- | --- |
| `quarkus.http.host-enabled` | `false` - Disables the standard HTTP server for most capabilities, as they use gRPC for communication. |
| `quarkus.grpc.server.host` | `0.0.0.0` - Binds the gRPC server to all available network interfaces. |
| `quarkus.grpc.server.port` | A unique port for each capability's gRPC server (e.g., `9009` for `exec`). |
| `quarkus.qute.strict-rendering` | `false` - Allows for more lenient Qute template rendering. |
| `wanaku.service.name` | The unique, lowercase name of the service (e.g., `exec`, `http`). |
| `wanaku.service.base-uri` | The base URI scheme for tools provided by this service (e.g., `exec://`). |
| `quarkus.oidc-client.auth-server-url` | The URL of the Keycloak realm for authentication. |
| `quarkus.oidc-client.client-id` | `wanaku-service` - The shared OIDC client ID for all capabilities. |
| `quarkus.oidc-client.credentials.secret` | The OIDC client secret for the capability. **Must be replaced with a real secret.** |

### Common Service Registration Settings

These `wanaku.service.registration.*` properties are available for all capabilities to manage their discovery and lifecycle.

| Property | Description |
| --- | --- |
| `wanaku.service.registration.enabled` | `true` - Enables the service registration feature. Found in archetypes. |
| `wanaku.service.registration.uri` | The URI of the router backend for registration (e.g., `http://localhost:8080`). |
| `wanaku.service.registration.interval` | `10s` - The interval at which the service should ping the router to show it's alive. |
| `wanaku.service.registration.retries` | `3` - Number of times to retry a failed registration. |
| `wanaku.service.registration.retry-wait-seconds` | `1` - Seconds to wait before retrying a failed registration. |
| `wanaku.service.registration.delay-seconds` | `3` - Seconds to delay the initial registration after startup. |
| `wanaku.service.registration.announce-address` | A custom address to announce to the router, overriding the auto-detected one. |

### Secret Encryption

Secrets can be encrypted at rest using AES-256. Set both environment variables to enable:

| Environment Variable | Description |
| --- | --- |
| `WANAKU_SECRETS_ENCRYPTION_PASSWORD` | Password for key derivation |
| `WANAKU_SECRETS_ENCRYPTION_SALT` | Salt for key derivation |

When both are set, secrets are automatically encrypted when written and decrypted when read.

## 3. CLI

Configuration for the Wanaku command-line interface (`wanaku-cli`).

| Property | Description |
| --- | --- |
| `wanaku.cli.tool.create-cmd` | The full Maven command to execute when creating a new tool service via `wanaku tool create`. |
| `wanaku.cli.resource.create-cmd` | The full Maven command to execute when creating a new resource provider via `wanaku resource create`. |
| `wanaku.cli.mcp.create-cmd` | The full Maven command to execute when creating a new MCP server via `wanaku mcp create`. |
| `wanaku.cli.components.*` | URL templates for downloading various Wanaku components. `%s` is replaced with the version number. |
| `wanaku.cli.default-services` | A comma-separated list of default services to start automatically when running the router. |

## 4. Archetypes

These properties are found in the project archetypes and serve as templates for new services.

### `wanaku-mcp-servers-archetype`

| Property | Description |
| --- | --- |
| `wanaku.mcp.service.name` | The name of the new MCP service, typically derived from the `name` variable. |
| `wanaku.mcp.service.namespace` | The namespace the MCP service will operate on. |
| `wanaku.service.registration.mcp-forward-address` | The address to forward MCP messages to. |

### `wanaku-provider-archetype` & `wanaku-tool-service-archetype`

| Property | Description |
| --- | --- |
| `wanaku.service.service.configurations.*` | A way to define user-exposable configurations for a service. The key becomes the configuration name. |
| `wanaku.service.service.defaults.*` | Defines default values for the corresponding `configurations`. |

## 5. Testing

Properties primarily used when running tests.

| Property | Description |
| --- | --- |
| `keycloak.docker.image` | Overrides the default Keycloak Docker image used for tests. This is set via a system property in the `pom.xml`, not in `application.properties`. |
| `%test.quarkus.log.file.enable` | `true` - Enables logging to a file during tests. |
| `%test.quarkus.log.file.path` | `target/wanaku.log` - The path to the log file for test runs. |

## Global Concepts

### Quarkus Profiles

Quarkus uses profiles to manage environment-specific configurations. You will see properties prefixed with `%dev`, `%test`, or other custom profiles. These properties are only active when that profile is enabled.

-   **`%dev`**: Used when running in development mode (`quarkus dev`).
-   **`%test`**: Used when running automated tests.
-   **`%prod`**: Used for production deployments (default when no profile is specified).

### Environment Variables

Most properties can be set via environment variables by converting the property name:

1. Convert to uppercase
2. Replace dots (`.`) with underscores (`_`)
3. Replace hyphens (`-`) with underscores (`_`)

**Example:**

```properties
quarkus.http.port=8080
```

Becomes:

```shell
QUARKUS_HTTP_PORT=8080
```

## Configuration Examples

### Example: Router Backend with Custom OIDC

```properties
# application.properties for router backend
quarkus.http.port=8080
quarkus.http.cors.enabled=true
quarkus.http.cors.origins=http://localhost:3000,https://my-frontend.example.com

auth.server=https://keycloak.example.com
auth.proxy=https://wanaku.example.com

quarkus.oidc.client-id=wanaku-mcp-router
quarkus.oidc.application-type=hybrid
quarkus.oidc.tls.verification=required

wanaku.persistence.infinispan.base-folder=/var/lib/wanaku/data
wanaku.infinispan.max-state-count=20
```

### Example: Tool Service with Custom Registration

```properties
# application.properties for a tool service
quarkus.http.host-enabled=false
quarkus.grpc.server.host=0.0.0.0
quarkus.grpc.server.port=9010

wanaku.service.name=my-custom-tool
wanaku.service.base-uri=custom://

wanaku.service.registration.enabled=true
wanaku.service.registration.uri=http://wanaku-router:8080
wanaku.service.registration.interval=15s
wanaku.service.registration.announce-address=my-custom-tool.example.com:9010

quarkus.oidc-client.auth-server-url=https://keycloak.example.com/realms/wanaku
quarkus.oidc-client.client-id=wanaku-service
quarkus.oidc-client.credentials.secret=${WANAKU_SERVICE_SECRET}
```

### Example: CLI Configuration

```properties
# ~/.wanaku/cli.properties
wanaku.cli.default-services=http,exec,tavily
```

### Example: Enabling Secret Encryption

```shell
export WANAKU_SECRETS_ENCRYPTION_PASSWORD="your-strong-password"
export WANAKU_SECRETS_ENCRYPTION_SALT="unique-salt-value"
```

## Additional Resources

- [Quarkus Configuration Guide](https://quarkus.io/guides/config) - Comprehensive Quarkus configuration documentation
- [Keycloak Documentation](https://www.keycloak.org/documentation) - OIDC and authentication setup
- [Infinispan Configuration](https://infinispan.org/docs/stable/titles/configuring/configuring.html) - Persistence layer configuration
