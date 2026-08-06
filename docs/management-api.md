# Management API

The management API runs on port 9090 (configurable via `WANAKU_MGMT_LISTEN`) and provides REST endpoints for managing tools, resources, prompts, namespaces, forwards, and services.

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
    {"name": "echo", "type": "echo-tool", "uri": "echo-tool://echo", "namespace": "default"}
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

**Create a tool:**

```bash
POST /api/v1/tools
Content-Type: application/json

{
  "name": "echo",
  "type": "echo-tool",
  "uri": "echo-tool://echo",
  "description": "Echoes a message",
  "namespace": "default",
  "input_schema": {
    "type": "object",
    "properties": {
      "message": {"type": "string"}
    },
    "required": ["message"]
  }
}
```

Fields:
- `name` (required) — unique tool identifier
- `type` (required) — service type or `"mcp-forward"`
- `uri` (required) — tool-specific URI
- `description` (optional) — human-readable description
- `namespace` (optional, defaults to `"default"`)
- `input_schema` (optional) — JSON Schema for tool arguments

**Delete a tool:**

```bash
DELETE /api/v1/tools/{name}
```

Returns 204 on success, 404 if not found.

### Resources

**List all resources:**

```bash
GET /api/v1/resources
```

**Get a specific resource:**

```bash
GET /api/v1/resources/{name}
```

**Create a resource:**

```bash
POST /api/v1/resources
Content-Type: application/json

{
  "name": "readme",
  "type": "file",
  "uri": "file:///README.md",
  "description": "Project README",
  "namespace": "default",
  "mime_type": "text/markdown"
}
```

Fields:
- `name` (required)
- `type` (required)
- `uri` (required)
- `description` (optional)
- `namespace` (optional, defaults to `"default"`)
- `mime_type` (optional)

**Delete a resource:**

```bash
DELETE /api/v1/resources/{name}
```

### Prompts

**List all prompts:**

```bash
GET /api/v1/prompts
```

**Get a specific prompt:**

```bash
GET /api/v1/prompts/{name}
```

**Create a prompt:**

```bash
POST /api/v1/prompts
Content-Type: application/json

{
  "name": "code-review",
  "description": "Review code for issues",
  "namespace": "default",
  "messages": [
    {
      "role": "user",
      "content": {
        "type": "text",
        "text": "Review this code: {{code}}"
      }
    }
  ],
  "arguments": [
    {
      "name": "code",
      "description": "Code to review",
      "required": true
    }
  ]
}
```

Fields:
- `name` (required)
- `description` (optional)
- `namespace` (optional, defaults to `"default"`)
- `messages` (required) — array of message objects (role + content)
- `arguments` (optional) — array of argument schemas

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

### Services

**List all services:**

```bash
GET /api/v1/services
```

**Create a service:**

```bash
POST /api/v1/services
Content-Type: application/json

{
  "name": "echo-tool",
  "address": "localhost:9191",
  "service_type": "tool-invoker"
}
```

Fields:
- `name` (required) — must match tool `type` field
- `address` (required) — `host:port` of gRPC server
- `service_type` (required) — `"tool-invoker"`, `"resource-provider"`, or `"multi-capability"`

**Delete a service:**

```bash
DELETE /api/v1/services/{name}
```

## Feature Routes

Features can expose their own management API routes via the `handle_route` method. These routes are dispatched after core routes.

### Safety Feature

**Get safety config:**

```bash
GET /api/v1/safety
```

Returns:

```json
{
  "data": {
    "llm_url": "http://localhost:11434/v1",
    "model": "llama3.1:8b",
    "timeout": 30
  },
  "error": null
}
```

**Update safety config:**

```bash
PUT /api/v1/safety
Content-Type: application/json

{
  "llm_url": "http://ollama:11434/v1",
  "model": "llama3.2:3b",
  "timeout": 60
}
```

**Disable safety checks:**

```bash
DELETE /api/v1/safety
```

### Chat Feature

**List available LLMs:**

```bash
GET /api/v1/chat/llms
```

Returns:

```json
{
  "data": ["ollama"],
  "error": null
}
```

**List models for an LLM:**

```bash
GET /api/v1/chat/{llm}/models
```

Example:

```bash
GET /api/v1/chat/ollama/models
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

This proxies the request to `WANAKU_OLLAMA_UPSTREAM/v1/chat/completions`.

## HTTP Status Codes

- **200 OK** — Success (most responses)
- **204 No Content** — Success, no body (DELETE operations)
- **400 Bad Request** — Invalid JSON or missing required fields
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

## CORS

The management API does NOT set CORS headers by default. If you're calling it from a browser, you'll get CORS errors.

Workarounds:
1. Run the API behind a reverse proxy (nginx, Envoy) that adds CORS headers
2. Add a CORS filter to the management API pipeline (not implemented yet)
3. Use a browser extension to disable CORS (dev only)

The MCP endpoint (port 8081) has CORS enabled via the `cors` filter in the pipeline.

## Rate Limiting

None. The management API is unthrottled. In production, put it behind a reverse proxy with rate limiting (e.g., nginx `limit_req`).

## Authentication

None. The management API is unauthenticated. Anyone who can reach port 9090 can create/delete tools.

For production:
1. Bind to localhost only (`WANAKU_MGMT_LISTEN=127.0.0.1:9090`)
2. Use a reverse proxy with API key auth (nginx `auth_request`, Envoy `ext_authz`)
3. Or add a custom auth filter to the management API pipeline

## Persistence

The registry is in-memory by default. Changes made via the API are lost on restart unless you enable file persistence:

```bash
export WANAKU_PERSIST_BACKEND=file
export WANAKU_PERSIST_PATH=/data/registry
```

See [Configuration](./configuration.md) for details.

## Example Workflows

### Register a Tool and Service

```bash
# 1. Register the service
curl -X POST http://localhost:9090/api/v1/services \
  -H "Content-Type: application/json" \
  -d '{"name": "echo-tool", "address": "localhost:9191", "service_type": "tool-invoker"}'

# 2. Register the tool
curl -X POST http://localhost:9090/api/v1/tools \
  -H "Content-Type: application/json" \
  -d '{
    "name": "echo",
    "type": "echo-tool",
    "uri": "echo-tool://echo",
    "description": "Echoes a message",
    "input_schema": {
      "type": "object",
      "properties": {"message": {"type": "string"}},
      "required": ["message"]
    }
  }'

# 3. Verify
curl http://localhost:9090/api/v1/tools
```

### Forward to Upstream MCP Server

```bash
# 1. Register the forward (auto-discovers tools)
curl -X POST http://localhost:9090/api/v1/forwards \
  -H "Content-Type: application/json" \
  -d '{"name": "upstream", "address": "http://upstream:8080/mcp"}'

# 2. List discovered tools
curl http://localhost:9090/api/v1/tools

# 3. Refresh after upstream changes
curl -X POST http://localhost:9090/api/v1/forwards/upstream/refreshes
```

### Create a Namespace with Isolated Tools

```bash
# 1. Create namespace
curl -X POST http://localhost:9090/api/v1/namespaces \
  -H "Content-Type: application/json" \
  -d '{"name": "finance", "description": "Financial tools"}'

# 2. Create tool in namespace
curl -X POST http://localhost:9090/api/v1/tools \
  -H "Content-Type: application/json" \
  -d '{
    "name": "get-stock-price",
    "type": "market-data",
    "uri": "market://stocks",
    "namespace": "finance"
  }'

# 3. Query finance namespace via MCP
curl -X POST http://localhost:8081/finance/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc": "2.0", "method": "tools/list", "id": 1}'
```

## Related Docs

- [Architecture](./architecture.md) — how the management API fits into the server
- [Configuration](./configuration.md) — `WANAKU_MGMT_LISTEN` and persistence options
- [Features](./features.md) — how features expose custom routes
