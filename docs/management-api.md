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
| `GET` | `/api/v1/audit/events` | List audit events with pagination and filters. |
| `GET` | `/api/v1/audit/events/{id}` | Get one audit event. |
| `GET` | `/api/v1/audit/schema` | Get the audit schema version. |
| `GET` | `/api/v1/audit/health` | Get audit-store health and the dropped-event count. |
| `GET` | `/api/v1/management/info` | Get the server name and version. |
| `GET` | `/api/v1/management/statistics` | Get registry counts. |
| `GET` | `/api/v1/tools` | List tools. |
| `GET` | `/api/v1/tools/{name}` | Get a tool. |
| `DELETE` | `/api/v1/tools/{name}` | Delete a tool. |
| `PUT` | `/api/v1/tools/{name}/enable` | Enable a tool. |
| `PUT` | `/api/v1/tools/{name}/disable` | Disable a tool. |
| `GET` | `/api/v1/resources` | List resources. |
| `GET` | `/api/v1/resources/{name}` | Get a resource. |
| `DELETE` | `/api/v1/resources/{name}` | Delete a resource. |
| `PUT` | `/api/v1/resources/{name}/enable` | Enable a resource. |
| `PUT` | `/api/v1/resources/{name}/disable` | Disable a resource. |
| `GET` | `/api/v1/prompts` | List prompts. |
| `GET` | `/api/v1/prompts/{name}` | Get a prompt. |
| `DELETE` | `/api/v1/prompts/{name}` | Delete a prompt. |
| `PUT` | `/api/v1/prompts/{name}/enable` | Enable a prompt. |
| `PUT` | `/api/v1/prompts/{name}/disable` | Disable a prompt. |
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
| `GET` | `/api/v1/agents` | List A2A agents. Use the optional `namespace` query parameter to select one namespace. |
| `GET` | `/api/v1/agents/{namespace}/{name}` | Get a registered A2A agent and its proxy endpoint. |
| `POST` | `/api/v1/agents` | Register an A2A agent. |
| `PUT` | `/api/v1/agents/{namespace}/{name}` | Replace an A2A agent's editable configuration. |
| `DELETE` | `/api/v1/agents/{namespace}/{name}` | Delete an A2A agent and remove its proxy route. |
| `GET` | `/api/v1/bindings` | List credential bindings. |
| `GET` | `/api/v1/bindings/{id}` | Get a credential binding. |

Wanaku discovers tools, resources, and prompts when you create or refresh a forward. The API does not have create routes for these entries. Tools and resources are read-only. You cannot edit them through the API.

### Enable and Disable Tools, Resources, and Prompts

Each tool, resource, and prompt has an `enabled` field. The default value is `true`.

A disabled entry stays in the management API. `GET /api/v1/tools` and the other list routes return it with `"enabled": false`. MCP clients cannot see or use a disabled entry. `tools/list`, `resources/list`, and `prompts/list` do not include it. `tools/call`, `resources/read`, and `prompts/get` reject it.

Wanaku keeps the state when it discovers the entries of a forward again. A forward refresh or a reconnect does not enable a disabled entry.

Do not delete a tool, resource, or prompt. The next discovery adds it again. Disable the entry instead:

```bash
curl -X PUT http://localhost:8080/api/v1/tools/get_weather/disable
```

To enable the entry again:

```bash
curl -X PUT http://localhost:8080/api/v1/tools/get_weather/enable
```

The response contains the updated entry. If the entry does not exist, the server returns `404`.

The Admin UI shows an Enable/Disable toggle for each tool, resource, and prompt. The Admin UI does not show a Delete button for these entries.

### Register an A2A agent

Register an upstream agent with its JSON-RPC address:

```bash
curl -X POST http://localhost:8080/api/v1/agents \
  -H 'Content-Type: application/json' \
  -d '{"name":"assistant","namespace":"default","description":"Team assistant","address":"http://localhost:9000/"}'
```

The name and namespace identify the agent. Both values must use valid identifiers. Wanaku registers a missing namespace when you create an agent. The address must be an HTTP or HTTPS URL. URL credentials and fragments are not permitted. Set `cardAddress` when the Agent Card uses a different discovery URL.

Agent responses contain `name`, `namespace`, `description`, `address`, optional `cardAddress`, and `proxyUrl`. The proxy URL comes from the configured public A2A origin. It is not an editable agent field.

Creating an existing agent returns `409`. Getting, updating, or deleting an unknown agent returns `404`. Invalid configuration returns `400`. An invalid update does not change the existing entry. An update does not change the namespace and name in the route.

Agent changes use the configured registry persistence backend. They affect subsequent proxy requests without a restart. See [A2A Proxy](./a2a.md) for the admin UI, listener configuration, policy, and supported protocol operations.


### Create a Namespace

Use the Wanaku CLI:

```bash
wanaku namespaces create finance --no-auth
```

The CLI sends this request to the management API:

```bash
curl -X POST http://localhost:8080/api/v1/namespaces \
  -H 'Content-Type: application/json' \
  -d '{"name":"finance"}'
```

Namespace names can contain lowercase letters, numbers, and hyphens. They cannot start or end with a hyphen. The maximum length is 63 characters.

### Create a Forward

Use the Wanaku CLI:

```bash
wanaku forwards add --service="http://upstream-server:8080/mcp" --name upstream-mcp --namespace finance --no-auth
```

The CLI sends this request to the management API. Include the namespace in the request:

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

### Inspect a Credential Binding

The binding routes are read-only. They return only non-secret binding metadata — `secretRefs` lists opaque references such as `env:API_TOKEN`, never a resolved value.

```bash
curl http://localhost:8080/api/v1/bindings/b1
```

There is no create, update, or delete route for bindings in this release. Bindings are part of the registry snapshot. See [Credential Brokerage](credential-brokerage.md) for the binding schema and the brokerage flow.

## Feature Routes

### Metrics

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/metrics` | Get filter, evaluator, LLM, WASM, and pipeline metrics. |

### Evaluators

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/evaluators` | List evaluator definitions. |
| `GET` | `/api/v1/evaluators/status` | Get runtime readiness, revision, safe reason code, and effective posture. The optional `namespace` query parameter defaults to `default`. |
| `PUT` | `/api/v1/evaluators` | Replace evaluator definitions. The body uses the `evaluators` configuration schema. |
| `GET` | `/api/v1/evaluators/llm-connections` | List configured LLM connection names only, never the model, URL, or credential. Connections are config-only. Set them in `wanaku.yaml`, not through this API. |
| `GET` | `/api/v1/evaluators/namespaces` | List namespace-to-conversation bindings. |
| `PUT` | `/api/v1/evaluators/namespaces/{namespace}` | Bind a namespace to a conversation. |
| `DELETE` | `/api/v1/evaluators/namespaces/{namespace}` | Remove a namespace binding. |
| `GET` | `/api/v1/evaluators/revisions` | List evaluator revision metadata. |
| `GET` | `/api/v1/evaluators/revisions/active` | Get the active evaluator revision. |
| `GET` | `/api/v1/evaluators/revisions/{id}` | Get one evaluator revision. |
| `POST` | `/api/v1/evaluators/revisions/{id}/activate` | Activate a prior evaluator configuration as a new revision. |

Use this body to bind a namespace:

```json
{
  "conversation_id": "conversation-123"
}
```

See [Evaluator Engine](evaluator-engine.md) for the evaluator configuration schema.

The OpenAPI document contains all evaluator request and response schemas. Run `yarn run generate-api` in `ui/admin` after you change an evaluator route or schema.

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

The OpenAPI document contains all action-policy request and response schemas. Run `yarn run generate-api` in `ui/admin` after you change an action-policy route or schema.

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

Open **Plugin Catalog** in the admin UI to browse available plugins. Select **Info** to view the metadata and required services. Select **Install** to download and install the archive. Set the service URLs in the configuration dialog when the plugin requires backend services. Restart the server after installation to load the plugin. The UI shows **Not specified** or **None** when the source has no value for a field.

`GET /api/v1/plugins/catalog` returns available plugins in the standard `data` response field. Each entry contains `id`, `name`, `version`, `description`, `publisher`, `license`, `dependencies`, `requires`, `metadata`, and `url`. The `url` field identifies the installation archive. The `requires.services` field lists the backend services that the plugin needs.

The default source is the embedded `features/plugins/plugin-catalog.json` file. The server uses the plugin description and publisher from each catalog entry. It does not read GitHub release notes or plugin archives during catalog requests.

`WANAKU_PLUGIN_CATALOG_PATH` selects a local JSON file. `WANAKU_PLUGIN_CATALOG_URL` selects a remote JSON file. A local path takes precedence. A custom source replaces the complete default catalog. See [Plugin Catalog](plugin-catalog.md) for the source format and publication instructions.

The endpoint returns HTTP 502 if the selected source is unavailable or invalid. It does not use a fallback source. A request has a 30-second limit. A remote download has a 15-second limit. Catalog JSON has a 1 MiB size limit.


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
- [Credential Brokerage](credential-brokerage.md) — Scoped, fail-closed upstream authentication for forwards.
