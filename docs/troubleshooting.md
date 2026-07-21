# First-Run Troubleshooting Guide

This guide covers the most common problems new users encounter when setting up and running Wanaku for the first time. Issues are grouped by category and ranked by how likely you are to hit them.

For operational troubleshooting (authentication errors, tool invocation failures, performance tuning), see the [Troubleshooting section in the Usage Guide](usage.md#troubleshooting).

## Authentication and Keycloak

### Router rejects all requests when Keycloak is unreachable

**Symptoms:**

- Router starts but management, admin, and MCP endpoints return HTTP 401
- Health check at `/q/health/ready` reports OIDC as DOWN

**Why this happens:**

Authentication is enabled by default (`wanaku.http.auth=keycloak` in `application.properties`). If Keycloak is not running or unreachable at the configured `auth.server` URL, the router starts but cannot validate tokens, so all authenticated endpoints reject requests.

**Fix:**

Either start Keycloak first, or disable authentication entirely:

```shell
# Option 1: Use the noauth docker-compose
docker-compose -f deploy/docker-compose/docker-compose-noauth.yml up

# Option 2: Set environment variable
export WANAKU_HTTP_AUTH=none

# Option 3: Set system property
java -Dwanaku.http.auth=none -jar wanaku-router-backend-runner.jar
```

### No human user account exists in Keycloak

**Symptoms:**

- Keycloak login page appears but you have no username/password
- Admin UI and CLI authentication fail

**Why this happens:**

The realm import (`deploy/auth/wanaku-config.json`) creates the realm structure and a service account, but no human user. However, self-registration is enabled in the realm configuration.

**Fix:**

Either register a new account or create one manually:

1. **Self-register:** Navigate to the Keycloak login page and click "Register" to create a new account
2. **Manual creation:** Go to the Keycloak admin console at `http://localhost:8543/admin` (login: `admin`/`admin`), select the `wanaku` realm, navigate to Users, and create a new user with a password

### Capability services fail to authenticate to the router

**Symptoms:**

- Capability service logs show repeated 401 errors during registration
- Registration retries exhaust and the service gives up silently

**Why this happens:**

The capabilities SDK ships with a placeholder OIDC client secret: `quarkus.oidc-client.credentials.secret=<insert key here>`. This is a syntactically valid but non-functional value. When authentication is enabled, capability services need a real client secret to obtain bearer tokens for communicating with the router.

**Fix:**

Set the OIDC client secret as an environment variable on each capability service:

```shell
# Use the default secret from docker-compose, or your actual Keycloak secret
export QUARKUS_OIDC_CLIENT_CREDENTIALS_SECRET=mypasswd
```

For unauthenticated mode, set `WANAKU_HTTP_AUTH=none` on both the router and all capability services.

### CLI commands return 401 Unauthorized

**Symptoms:**

- All `wanaku` CLI commands fail with authentication errors
- No clear indication that login is required

**Why this happens:**

When the router has authentication enabled, the CLI requires an active session. The CLI does not prompt for login automatically.

**Fix:**

```shell
# Log in first (through the router OIDC proxy)
wanaku auth login \
  --auth-server http://localhost:8080 \
  --username alice \
  --password

# Or log in directly against Keycloak (bypasses the router)
wanaku auth login \
  --auth-server http://keycloak-host \
  --realm wanaku \
  --username alice \
  --password

# For routers running without authentication, use --no-auth
wanaku tools list --no-auth
```

### CLI commands fail with: An error occurred: Failed to communicate with Keycloak

**Symptoms:**

- `wanaku` CLI commands fail to communicate with Keycloak

Example:

```shell
An error occurred: Failed to communicate with Keycloak at https://127.0.0.1:8543

Run with --verbose flag for more details

```

If you set `--verbose` and then shows the cause as:

```shell
sun.security.validator.ValidatorException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target
```

**Why this happens:**

The security code (JSSE) in wanaku-cli doesn't recognize the certificate that serves the Keycloak HTTPS endpoint.

**Fix:**

You have two options:

- You can skip the certificate checks in wanaku-cli by using the `--insecure` parameter. In this case, it will show a warning `WARNING: TLS certificate verification is disabled. This is insecure and should only be used for development.`.

- You can use the CA that signed the Keycloak HTTPS endpoint, by either importing it into the default Java truststore `$JAVA_HOME/lib/security/cacerts` or have a particular keystore file and use the `-Djavax.net.ssl.trustStore` and `-Djavax.net.ssl.trustStorePassword` parameters to refer to this custom truststore.

NOTE: You can set these `-D` to the wanaku-cli as in this example:

```shell
java "-Djavax.net.ssl.trustStore=my-truststore.p12" "-Djavax.net.ssl.trustStorePassword=changeit" -jar quarkus-run.jar  admin credentials show --admin-username=admin --admin-password=admin --keycloak-url=https://keycloak.192.168.49.2.nip.io --show-secret --client-id wanaku-service
```

### Token issuer mismatch causes 401 on OpenShift / Kubernetes

**Symptoms:**

- External clients obtain tokens from Keycloak and send them to the router
- The router rejects all tokens with HTTP 401
- Keycloak and the router are both running, and the realm is configured correctly

**Why this happens:**

When Keycloak is accessed via an external route (e.g. `http://keycloak-wanaku-test.<cluster>/realms/wanaku`), tokens are stamped with that route URL as the issuer. The router, however, validates tokens against the internal service URL (`http://keycloak:8080/realms/wanaku`). Because the issuers don't match, every token is rejected.

**Fix:**

Set `KC_HOSTNAME` on the Keycloak deployment to match the external route or ingress host. This forces Keycloak to stamp the same issuer in all tokens regardless of how it is accessed:

```shell
# OpenShift
KEYCLOAK_HOST=$(oc get route keycloak -n "${WANAKU_NAMESPACE}" -o jsonpath='{.spec.host}')
oc set env deployment/keycloak \
  KC_HOSTNAME="${KEYCLOAK_HOST}" \
  KC_HOSTNAME_STRICT=false \
  -n "${WANAKU_NAMESPACE}"

# Kubernetes (use the Ingress host instead)
KEYCLOAK_HOST=$(kubectl get ingress keycloak -n "${WANAKU_NAMESPACE}" -o jsonpath='{.spec.rules[0].host}')
kubectl set env deployment/keycloak \
  KC_HOSTNAME="${KEYCLOAK_HOST}" \
  KC_HOSTNAME_STRICT=false \
  -n "${WANAKU_NAMESPACE}"
```

The `deploy/auth/keycloak.yaml` manifest includes a `KEYCLOAK_HOST` placeholder for `KC_HOSTNAME` — replace it with your actual hostname before applying. See also the [Keycloak setup test plan](../tests/plans/common/keycloak-setup.md) for the full automated procedure.

## Capability Service Registration

### Capability services start but don't appear in the router

**Symptoms:**

- Capability service starts successfully and passes health checks
- `wanaku capabilities list` shows no registered services
- Service logs show warnings like `Unable to register service because of: Connection refused`

**Why this happens:**

The registration URI defaults to `http://localhost:8080` via the `@WithDefault` annotation in `WanakuServiceConfig`. This works when the router runs on the same host, but fails in Docker, Kubernetes, or multi-host setups. After 12 failed retries (approximately 60 seconds), the service gives up permanently with no re-registration mechanism.

**Fix:**

Set the registration URI to the router's actual address:

```shell
export WANAKU_SERVICE_REGISTRATION_URI=http://<router-host>:8080/
```

Ensure the router is started and healthy before starting capability services. In Docker Compose, use `depends_on` with `condition: service_healthy`.

### Capability registers but router cannot call it back

**Symptoms:**

- Capability appears in `wanaku capabilities list`
- Tool invocations fail with gRPC connection timeouts
- Router logs show connection errors to an unexpected IP address

**Why this happens:**

The `announce-address` configuration defaults to `auto`, which picks the first non-loopback network interface. In Docker, this is often an internal bridge IP. In multi-NIC environments, it may select a VPN tunnel interface.

**Fix:**

Explicitly set the announce address to an IP the router can reach:

```properties
# In the capability's application.properties
wanaku.service.registration.announce-address=<reachable-ip>
```

## Docker Compose and Deployment

### Docker Compose does not work on macOS with Podman

**Symptoms:**

- Services fail to start or cannot communicate with each other

**Why this happens:**

All services in the provided `docker-compose.yml` use `network_mode: host`, which is not supported on macOS with Podman (see [podman#15664](https://github.com/containers/podman/issues/15664)).

**Fix:**

Use Docker Desktop on macOS instead of Podman, or modify the compose file to use bridge networking with explicit port mappings.

### Router health check is silently ignored in docker-compose.yml

**Symptoms:**

- Capability services start before the router is ready
- Registration fails due to race conditions
- Startup succeeds on retry but is unreliable

**Why this happens:**

In `deploy/docker-compose/docker-compose.yml`, the `healthcheck:` block for the router is at the wrong YAML indentation level. It appears as a top-level key rather than nested under the `wanaku-router` service, so Docker Compose ignores it entirely.

**Fix:**

If you experience startup race conditions, add a delay before starting capabilities, or fix the indentation in your local copy of the compose file so the healthcheck is nested under the `wanaku-router` service definition.

### Insecure default credentials in docker-compose

**Symptoms:**

No error, which is the problem. The provided docker-compose ships with well-known credentials.

**Why this happens:**

The development docker-compose uses default credentials for convenience:

- Keycloak admin: `admin` / `admin`
- OIDC client secret for `wanaku-service`: `mypasswd`

**Fix:**

Before any non-local deployment, rotate these credentials:

1. Change `KEYCLOAK_ADMIN_PASSWORD` in the compose file
2. Update the `wanaku-service` client secret in Keycloak
3. Update `QUARKUS_OIDC_CLIENT_CREDENTIALS_SECRET` on all capability services to match

## SDK and Capability Development

### Two separate SDKs exist with no guidance on which to use

**Symptoms:**

- Confusion when searching for "Wanaku SDK"
- Build failures when mixing artifacts from different SDKs

**Why this happens:**

Two capability SDK ecosystems coexist:

| | Java SDK (standalone) | Quarkus SDK (in-tree) |
|---|---|---|
| **GroupId** | `ai.wanaku.sdk` | `ai.wanaku` |
| **Version** | 0.1.x | 0.2.x |
| **Runtime** | Picocli + gRPC | Quarkus CDI |
| **Archetype** | `capabilities-archetypes-java-tool` | `wanaku-tool-service-archetype` |

**Fix:**

- Use the **Java SDK** (`ai.wanaku.sdk`) for standalone capabilities that run independently
- Use the **Quarkus SDK** (`ai.wanaku`) for capabilities developed within the Wanaku monorepo

### Port 8080 conflict between router and new capabilities

**Symptoms:**

- `java.net.BindException: Address already in use` when starting a capability alongside the router

**Why this happens:**

The archetype-generated `application.properties` comments out `quarkus.http.port`. Quarkus defaults to port 8080, which is the same as the router. Built-in capabilities use specific ports (9000, 9001, 9009, 9010), but the archetype does not set one.

**Fix:**

Set a non-conflicting port in the generated capability's `application.properties`:

```properties
# Use a specific port
quarkus.http.port=9090

# Or let Quarkus pick a random available port
quarkus.http.port=0
```

### Generated coerceResponse() throws at runtime

**Symptoms:**

- Capability compiles and starts successfully
- First tool invocation fails with `InvalidResponseTypeException`

**Why this happens:**

The archetype generates a `coerceResponse()` method that unconditionally throws an exception. This is a deliberate placeholder, but the project compiles without errors, so the issue is only discovered at runtime.

**Fix:**

Before deploying, implement the `coerceResponse()` method in your generated delegate class. Replace the `throw` statement with your actual response conversion logic.

## Frontend and Admin UI

### CORS errors when frontend runs on a non-default port

**Symptoms:**

- Browser console shows CORS preflight failures
- API calls from the frontend are blocked

**Why this happens:**

The router's CORS allowlist is configured for specific origins: `localhost:8080`, `127.0.0.1:8080`, `localhost:5173`, and `localhost:6274`. If Vite's dev server runs on a different port (which happens automatically when 5173 is already in use), requests are blocked.

**Fix:**

Add your dev server's origin to the router's configuration:

```properties
# In apps/wanaku-router-backend/src/main/resources/application.properties
quarkus.http.cors.origins=http://localhost:8080,http://127.0.0.1:8080,http://localhost:5173,http://localhost:6274,http://localhost:<your-port>
```

## gRPC and Performance

### Tool invocations time out after 10 seconds

**Symptoms:**

- Tool calls that invoke slow APIs (LLM inference, large data processing) fail with "Service X did not respond within a reasonable time frame"
- HTTP 502 Bad Gateway response

**Why this happens:**

The router's gRPC transport uses a 10-second deadline for all calls to capability services (`wanaku.bridge.grpc.transport.deadline-seconds=10`). This is too short for many real-world workloads, especially AI-related tools.

**Fix:**

Increase the deadline on the router:

```shell
export WANAKU_BRIDGE_GRPC_TRANSPORT_DEADLINE_SECONDS=60
```

Or set it in the router's `application.properties`:

```properties
wanaku.bridge.grpc.transport.deadline-seconds=60
```

### Opaque "Generic error" responses

**Symptoms:**

- API responses contain only `{"error": "Generic error"}` with HTTP 500
- No indication of what went wrong

**Why this happens:**

The router's `BaseExceptionMapper` catches all unhandled exceptions and returns a sanitized error message. The actual exception details are logged server-side but not exposed in the API response.

**Fix:**

Check the router's server logs for the real stack trace:

- **Dev mode:** `target/logs/wanaku.log`
- **Docker:** `docker logs <container-name>`
- **Kubernetes:** `kubectl logs <pod-name>`

Enable debug logging for more detail:

```properties
quarkus.log.category."ai.wanaku".level=DEBUG
```

## System-Level Issues

### Module access errors on Java 25

**Symptoms:**

- `wanaku --version` or any `wanaku` command fails immediately with `IllegalAccessError`, `InaccessibleObjectException`, or a stack trace mentioning `java.base/java.lang` module access
- Running with `JAVA_TOOL_OPTIONS='--add-opens=java.base/java.lang=ALL-UNNAMED'` resolves the error

**Why this happens:**

Java 25 tightened strong encapsulation of internal JDK classes. Some dependencies used by Quarkus and the CLI attempt reflective access to `java.lang` internals that are no longer automatically open.

**Fix:**

The Wanaku CLI and router scripts already pass `--add-opens=java.base/java.lang=ALL-UNNAMED` by default on Java 25. If you run the JAR directly (e.g., `java -jar quarkus-run.jar`), add the option manually:

```shell
java --add-opens=java.base/java.lang=ALL-UNNAMED -jar quarkus-run.jar --version
```

If you still encounter module access errors for other packages, add the corresponding `--add-opens` or `--add-exports` flag. Report the full stack trace as a new issue so we can extend the launcher defaults.

### Infinispan data directory permissions in containers

**Symptoms:**

- Router fails at startup with file permission errors
- Persistence data is silently lost between restarts

**Why this happens:**

The Infinispan data directory defaults to `${wanaku.home}/router/`. In container images, this resolves to `/home/jboss/.wanaku/router/`. Running the container with a different UID or mounting a host directory with incompatible permissions causes Infinispan's `SoftIndexFileStore` to fail.

**Fix:**

Use named Docker volumes (which handle permissions automatically) rather than host-mounted directories. If you must use host mounts, ensure the directory is owned by UID 185 (the `jboss` user in the container image).

### Multiple router replicas have independent state

**Symptoms:**

- Changes made on one router instance (tool registrations, resource additions) are not visible on other instances
- Inconsistent behavior depending on which replica handles the request

**Why this happens:**

Infinispan is configured in `LOCAL` cache mode. Each router instance maintains its own isolated data store. There is no replication or synchronization between instances.

**Fix:**

Currently, Wanaku supports single-instance deployments only. Running multiple replicas requires architectural changes to enable distributed Infinispan cache modes. For high availability, use a single replica with persistent storage.

### Crashed capabilities are not detected for up to 60 seconds

**Symptoms:**

- A capability crashes but tool calls to it continue failing for about a minute before the router marks it as unhealthy

**Why this happens:**

The router's periodic health check runs every 60 seconds (`wanaku.router.health-check.interval-seconds=60`). Between checks, the router assumes previously registered capabilities are still healthy.

**Fix:**

Reduce the health check interval for faster detection (at the cost of increased network traffic):

```properties
wanaku.router.health-check.interval-seconds=15
```

## Getting Help

If your issue is not covered here:

1. Check the [operational troubleshooting section](usage.md#troubleshooting) in the Usage Guide
2. Review the [FAQ](faq.md) for common questions
3. Search [GitHub Issues](https://github.com/wanaku-ai/wanaku/issues) for similar problems
4. Open a new issue with your Wanaku version, deployment environment, steps to reproduce, and relevant log excerpts
