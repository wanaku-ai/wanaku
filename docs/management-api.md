# Management API

The management API lets operators inspect and change Wanaku at runtime. It listens on port 8080 by default. Set `WANAKU_MGMT_LISTEN` to use a different address.

The server uses Pingora's `ServeHttp` trait. Core routes run first. Feature routes run after core routes.

## Access and Response Format

Wanaku does not apply built-in authentication or rate limits to the management API. Restrict access to port 8080. In production, put the API behind an authenticated reverse proxy and apply a rate limit there.

Most JSON responses use this envelope:

```json
{
  "data": {
    "name": "example"
  },
  "error": null
}
```

For an error, `data` is `null` and `error` contains a message:

```json
{
  "data": null,
  "error": "Tool 'unknown-tool' not found"
}
```

The `data` value can be an object, an array, or `null`. This envelope keeps the API compatible with the classic Wanaku CLI.

## Service and Core Routes

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/health` | Get the server health. |
| `GET` | `/healthz` | Get the server health. |
| `GET` | `/openapi.json` | Get the OpenAPI document. |
| `GET` | `/api/v1/management/info` | Get the server name and version. |
| `GET` | `/api/v1/management/statistics` | Get registry counts. |
| `GET` | `/api/v1/tools` | List tools. |
| `GET` | `/api/v1/tools/{name}` | Get a tool. |
| `PUT` | `/api/v1/tools/{name}` | Replace a tool entry with the JSON request body. |
| `DELETE` | `/api/v1/tools/{name}` | Delete a tool. |
| `GET` | `/api/v1/resources` | List resources. |
| `GET` | `/api/v1/resources/{name}` | Get a resource. |
| `PUT` | `/api/v1/resources/{name}` | Replace a resource entry with the JSON request body. |
| `DELETE` | `/api/v1/resources/{name}` | Delete a resource. |
| `GET` | `/api/v1/prompts` | List prompts. |
| `GET` | `/api/v1/prompts/{name}` | Get a prompt. |
| `DELETE` | `/api/v1/prompts/{name}` | Delete a prompt. |
| `GET` | `/api/v1/namespaces` | List namespaces. |
| `GET` | `/api/v1/namespaces/{name}` | Get a namespace. |
| `POST` | `/api/v1/namespaces` | Create a namespace from the JSON request body. |
| `PUT` | `/api/v1/namespaces/{name}` | Replace a namespace entry with the JSON request body. |
| `DELETE` | `/api/v1/namespaces/{name}` | Delete a namespace. |
| `GET` | `/api/v1/forwards` | List forwards. |
| `GET` | `/api/v1/forwards/{name}` | Get a forward. |
| `POST` | `/api/v1/forwards` | Create a forward and run upstream discovery. |
| `DELETE` | `/api/v1/forwards/{name}` | Delete a forward and its discovered entries. |
| `POST` | `/api/v1/forwards/{name}/refreshes` | Run upstream discovery again. |

Wanaku discovers tools, resources, and prompts when you create or refresh a forward. The API does not have create routes for these entries. The `PUT` routes let you change existing tool and resource metadata.

### Create a Namespace

Send one request:

```bash
curl -X POST http://localhost:8080/api/v1/namespaces \
  -H 'Content-Type: application/json' \
  -d '{"name":"finance"}'
```

Namespace names can contain lowercase letters, numbers, and hyphens. They cannot start or end with a hyphen. The maximum length is 63 characters.

### Create a Forward

Include the namespace in the request:

```bash
curl -X POST http://localhost:8080/api/v1/forwards \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "upstream-mcp",
    "address": "http://upstream-server:8080/mcp",
    "namespace": "finance"
  }'
```

Wanaku stores the forward even if discovery fails. In that case, the response reports zero discovered entries and the forward records the error in `status_message`.

## Feature Routes

### Metrics

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/metrics` | Get filter, evaluator, LLM, WASM, and pipeline metrics. |

### Interactions

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/interactions` | List recorded interactions. |
| `DELETE` | `/api/v1/interactions` | Clear recorded interactions. |

The evaluator uses these interactions as conversation history.

### Evaluators

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/evaluators` | List evaluator definitions. |
| `PUT` | `/api/v1/evaluators` | Replace evaluator definitions. The body uses the `evaluators` configuration schema. |
| `GET` | `/api/v1/evaluators/llm-connections` | List configured LLM connection names only, never the model, URL, or credential. Connections are config-only. Set them in `wanaku.yaml`, not through this API. |
| `GET` | `/api/v1/evaluators/namespaces` | List namespace-to-conversation bindings. |
| `PUT` | `/api/v1/evaluators/namespaces/{namespace}` | Bind a namespace to a conversation. |
| `DELETE` | `/api/v1/evaluators/namespaces/{namespace}` | Remove a namespace binding. |

Use this body to bind a namespace:

```json
{
  "conversation_id": "conversation-123"
}
```

See [Evaluator Engine](evaluator-engine.md) for the evaluator configuration schema.

### Action Policies

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/action-policies` | Get the effective policy. |
| `PUT` | `/api/v1/action-policies` | Validate and activate a policy. |
| `GET` | `/api/v1/action-policies/revisions` | List policy revision metadata. |
| `GET` | `/api/v1/action-policies/revisions/active` | Get the active policy revision. |
| `GET` | `/api/v1/action-policies/revisions/{id}` | Get one policy revision. |
| `POST` | `/api/v1/action-policies/revisions/{id}/activate` | Activate a prior policy as a new revision. |

The update and activation requests accept an optional `expected_revision`. Wanaku returns `409 Conflict` if the value does not equal the active revision. See [Action Policies](action-policies.md) for request examples and validation behavior.

### Inference Proxy (not part of this API)

Chat completions do not go through the management API. Wanaku exposes a
separate, raw reverse-proxy listener on port 8083 that forwards requests
as-is to whatever OpenAI-compatible backend `WANAKU_INFERENCE_UPSTREAM`
points at — including the caller's own `Authorization` header. The Admin
UI's LLM Chat page calls this port directly with a key you supply in the
browser:

```bash
curl -X POST http://localhost:8083/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your-key>" \
  -d '{
    "model": "llama3.1:8b",
    "messages": [{"role": "user", "content": "Hello!"}]
  }'
```

See [Configuration](configuration.md#inference-proxy) for how to set the
upstream.

### OAuth Metadata

When you set `WANAKU_AUTH_ISSUER`, the MCP metadata feature exposes OAuth Protected Resource Metadata:

```text
GET /.well-known/oauth-protected-resource/{namespace}/mcp
```

MCP clients use this document to find the authorization server. An external authentication proxy enforces access to the MCP endpoint. See [Authentication](auth.md).

### Web UI Plugins

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/plugins` | List discovered UI plugin manifests. |
| `GET` | `/plugins/{pluginId}/{path}` | Get a static plugin file. |
| Any HTTP method | `/api/plugins/{pluginId}/{serviceId}/{path}` | Send a request through a configured plugin service mapping. |

The plugin proxy accepts only plugin and service IDs that Wanaku loaded from configuration. It forwards the request method, body, query, and selected headers to the configured service. See [Plugin Development Guide](plugin-development-guide.md).

## Persistence

Wanaku enables file persistence by default. It writes registry snapshots to `$HOME/.wanaku/server` when the process shuts down in an orderly manner.

Set `WANAKU_PERSIST_BACKEND=none` to disable persistence. Set `WANAKU_PERSIST_PATH` to use a different directory.

File persistence supports one writer. Do not run multiple replicas against the same persistence directory.

## Status Codes

The API uses these status codes:

- `200 OK`: The request succeeded. Core delete routes return a JSON confirmation in the standard envelope.
- `400 Bad Request`: The request body is missing or invalid.
- `404 Not Found`: The route or requested entry does not exist.
- `500 Internal Server Error`: The server could not complete the request.

Plugin file and proxy routes can return other status codes from file handling or the upstream service.

## CORS

The management API sets `Access-Control-Allow-Origin` from `WANAKU_CORS_ORIGIN`. The default value is `*`.

Set a specific origin in production:

```bash
export WANAKU_CORS_ORIGIN=https://app.example.com
```

The MCP endpoint (port 8081) and the inference proxy (port 8083) each use their own pipeline CORS filter, set from the same `WANAKU_CORS_ORIGIN` value.

## Related Docs

- [Architecture](architecture.md) — See how the management API fits into the server.
- [Configuration](configuration.md) — Configure the listen address, persistence, and CORS.
- [Features](features.md) — Add management routes through the feature system.
- [Authentication](auth.md) — Put the management API behind oauth2-proxy.
