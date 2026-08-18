# Management API

The management API runs on port 8080 (configurable via `WANAKU_MGMT_LISTEN`) and provides REST endpoints for managing tools, resources, prompts, namespaces, and forwards.

This isn't axum or actix-web. It's Pingora's native `ServeHttp` trait. Requests are dispatched via a guard pattern defined in `server/src/management/routes.rs`, and responses are wrapped in a standard envelope.

## Response Format

All responses follow this structure:

```json
{"data": <payload>, "error": null}  // success
{"data": null, "error": "message"}  // error
```

This matches the classic Wanaku API format for CLI compatibility. The `data` field contains the actual response payload (array, object, or null). The `error` field is `null` on success or a string on error.

**Example success:**

```json
{
  "data": [
    {"name": "echo", "type": "mcp-forward", "uri": "http://echo-mcp:8080/mcp", "namespace": "default"}
  ],
  "error": null
}
```

**Example error:**

```json
{
  "data": null,
  "error": "Tool 'unknown-tool' not found"
}
```

## Core Routes

### Tools

Tools are automatically discovered and registered when you add a forwarded MCP server via `POST /api/v1/forwards`. You cannot create tools directly — they are populated by querying upstream MCP servers.

**List all tools:**

```bash
GET /api/v1/tools
```

Returns an array of all registered tools.

**Get a specific tool:**

```bash
GET /api/v1/tools/{name}
```

Returns a single tool by name. 404 if not found.

**Delete a tool:**

```bash
DELETE /api/v1/tools/{name}
```

Returns 204 on success, 404 if not found.

### Resources

Resources are automatically discovered and registered when you add a forwarded MCP server via `POST /api/v1/forwards`. You cannot create resources directly — they are populated by querying upstream MCP servers.

**List all resources:**

```bash
GET /api/v1/resources
```

**Get a specific resource:**

```bash
GET /api/v1/resources/{name}
```

**Delete a resource:**

```bash
DELETE /api/v1/resources/{name}
```

### Prompts

Prompts are automatically discovered and registered when you add a forwarded MCP server via `POST /api/v1/forwards`. You cannot create prompts directly — they are populated by querying upstream MCP servers.

**List all prompts:**

```bash
GET /api/v1/prompts
```

**Get a specific prompt:**

```bash
GET /api/v1/prompts/{name}
```

**Delete a prompt:**

```bash
DELETE /api/v1/prompts/{name}
```

### Namespaces

**List all namespaces:**

```bash
GET /api/v1/namespaces
```

**Get a specific namespace:**

```bash
GET /api/v1/namespaces/{name}
```

**Create a namespace:**

```bash
POST /api/v1/namespaces
Content-Type: application/json

{
  "name": "finance",
  "description": "Financial tools and resources"
}
```

**Delete a namespace:**

```bash
DELETE /api/v1/namespaces/{name}
```

### Forwards (MCP Proxying)

**List all forwards:**

```bash
GET /api/v1/forwards
```

**Create a forward:**

```bash
POST /api/v1/forwards
Content-Type: application/json

{
  "name": "upstream-mcp",
  "address": "http://upstream-server:8080/mcp"
}
```

This registers the forward AND auto-discovers tools from the upstream MCP server.

**Delete a forward:**

```bash
DELETE /api/v1/forwards/{name}
```

This removes the forward and all tools discovered from it.

**Refresh a forward:**

```bash
POST /api/v1/forwards/{name}/refreshes
```

Re-queries the upstream server and updates the tool list.

## Feature Routes

Features can expose their own management API routes via the `handle_route` method. These routes are dispatched after core routes.

### Chat Feature

**List available LLMs:**

```bash
GET /api/v1/chat/llms
```

Returns:

```json
{
  "data": ["inference"],
  "error": null
}
```

**List models for an LLM:**

```bash
GET /api/v1/chat/{llm}/models
```

Example:

```bash
GET /api/v1/chat/inference/models
```

Returns:

```json
{
  "data": [
    {"name": "llama3.1:8b", "size": "4.7GB"},
    {"name": "llama3.2:3b", "size": "2.0GB"}
  ],
  "error": null
}
```

**Proxy chat completion:**

```bash
POST /api/v1/chat/completions
Content-Type: application/json

{
  "model": "llama3.1:8b",
  "messages": [
    {"role": "user", "content": "Hello!"}
  ]
}
```

This proxies the request to `WANAKU_INFERENCE_UPSTREAM/v1/chat/completions`.

### MCP Metadata Feature

When `WANAKU_AUTH_ISSUER` is set, the MCP metadata feature exposes RFC 9728 OAuth Protected Resource Metadata.

**OAuth Protected Resource Metadata:**

```bash
GET /.well-known/oauth-protected-resource/{namespace}/mcp
```

Returns OAuth server metadata for the specified namespace. MCP clients use this to discover the authorization server and token endpoints.

**Example response:**

```json
{
  "issuer": "http://localhost:8543/realms/wanaku",
  "authorization_endpoint": "http://localhost:8543/realms/wanaku/protocol/openid-connect/auth",
  "token_endpoint": "http://localhost:8543/realms/wanaku/protocol/openid-connect/token"
}
```

This endpoint is read-only metadata — actual authentication is handled by oauth2-proxy running as a sidecar. See `deploy/auth/README.md` for deployment details.

## HTTP Status Codes

- **200 OK** — Success (most responses)
- **204 No Content** — Success, no body (DELETE operations)
- **400 Bad Request** — Invalid JSON or missing required fields
- **401 Unauthorized** — Auth enabled and token missing/invalid (RFC 6750 WWW-Authenticate header included)
- **404 Not Found** — Resource not found
- **500 Internal Server Error** — Server error (check logs)

## Error Handling

Errors are returned in the standard envelope:

```json
{
  "data": null,
  "error": "Tool 'unknown-tool' not found"
}
```

The HTTP status code indicates the error class (400, 404, 500), and the `error` field provides details.

### Intercept Feature

**List interactions:**

```bash
GET /api/v1/interactions
```

Returns the recorded request/response interactions. These are used by the evaluator engine for conversation history context.

**Clear interactions:**

```bash
DELETE /api/v1/interactions
```

Clears all stored interactions from the in-memory store.

## CORS

The management API sets `Access-Control-Allow-Origin` via the `WANAKU_CORS_ORIGIN` environment variable (defaults to `*`). Set this to a specific origin in production:

```bash
export WANAKU_CORS_ORIGIN=https://app.example.com
```

The MCP endpoint (port 8081) has CORS enabled via the `cors` filter in the pipeline.

## Rate Limiting

None. The management API is unthrottled. In production, put it behind a reverse proxy with rate limiting (e.g., nginx `limit_req`).

## Authentication

**When auth is disabled** (default): The management API is unauthenticated. Anyone who can reach port 8080 can delete tools or manage forwards.

**When auth is enabled** (via oauth2-proxy): All `/api/v1/*` routes require a valid Bearer token:

```bash
curl http://localhost:4181/api/v1/tools \
  -H "Authorization: Bearer <token>"
```

oauth2-proxy validates tokens and proxies authenticated requests to Praxis on port 8080. Praxis itself does not perform any authentication.

**oauth2-proxy deployment:**

See `deploy/auth/README.md` for oauth2-proxy configuration. The typical setup uses two instances:
- **oauth2-proxy-mcp** (port 4180 → 8081) — MCP endpoint, requires `mcp-user` role
- **oauth2-proxy-mgmt** (port 4181 → 8080) — management API/UI, requires `admin` role

Both instances share a cookie secret for SSO.

**For production deployments without auth:**

1. Run Praxis standalone on ports 8081/8080 without oauth2-proxy
2. Bind to localhost only (`WANAKU_MGMT_LISTEN=127.0.0.1:8080`)
3. Use a reverse proxy with API key auth (nginx `auth_request`, Envoy `ext_authz`)

## Persistence

The registry is in-memory by default. Changes made via the API are lost on restart unless you enable file persistence:

```bash
export WANAKU_PERSIST_BACKEND=file
export WANAKU_PERSIST_PATH=/data/registry
```

See [Configuration](./configuration.md) for details.

## Example Workflows

### Discover Tools via Forward

```bash
# 1. Register a forward (auto-discovers tools)
curl -X POST http://localhost:8080/api/v1/forwards \
  -H "Content-Type: application/json" \
  -d '{"name": "echo-server", "address": "http://localhost:8180/mcp"}'

# 2. List discovered tools
curl http://localhost:8080/api/v1/tools

# 3. Verify a specific tool
curl http://localhost:8080/api/v1/tools/echo
```

### Forward to Upstream MCP Server

```bash
# 1. Register the forward (auto-discovers tools)
curl -X POST http://localhost:8080/api/v1/forwards \
  -H "Content-Type: application/json" \
  -d '{"name": "upstream", "address": "http://upstream:8080/mcp"}'

# 2. List discovered tools
curl http://localhost:8080/api/v1/tools

# 3. Refresh after upstream changes
curl -X POST http://localhost:8080/api/v1/forwards/upstream/refreshes
```

### Create a Namespace with Isolated Tools

```bash
# 1. Create namespace
curl -X POST http://localhost:8080/api/v1/namespaces \
  -H "Content-Type: application/json" \
  -d '{"name": "finance", "description": "Financial tools"}'

# 2. Register a forward (tools inherit the namespace from the forward configuration)
curl -X POST http://localhost:8080/api/v1/forwards \
  -H "Content-Type: application/json" \
  -d '{"name": "market-data", "address": "http://market-data-mcp:8080/mcp", "namespace": "finance"}'

# 3. Query finance namespace via MCP
curl -X POST http://localhost:8081/finance/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc": "2.0", "method": "tools/list", "id": 1}'
```

Tools discovered from the forward will be registered in the `finance` namespace. Namespace assignment happens through the forward configuration, not by creating tools directly.

## Related Docs

- [Architecture](./architecture.md) — how the management API fits into the server
- [Configuration](./configuration.md) — `WANAKU_MGMT_LISTEN` and persistence options
- [Features](./features.md) — how features expose custom routes
