---
title: Give each team its own MCP endpoint with namespaces
description: Create Wanaku namespaces, register forwards in them, and give each team, tenant, or environment its own MCP endpoint with an isolated tool list.
---

# Give each team its own MCP endpoint with namespaces

Namespaces isolate tools and resources. Each namespace has its own MCP endpoint, so each team, tenant, or environment sees only its own tools.

## Naming rules

Each namespace has a **name**. Wanaku uses this name as the namespace segment in the URL path.

- Use only lowercase letters, numbers, and hyphens.
- Start and end the name with a letter or number.
- Use 1 to 63 characters.
- Examples: `finance`, `my-team`, `staging-42`

## Create a namespace and add a forward

Create a `"finance"` namespace:

```bash
wanaku namespaces create finance --no-auth
```

Register a forward in the `finance` namespace:

```bash
wanaku forwards add --service="http://market-data-mcp:8080/mcp" --name market-data-server --namespace finance --no-auth
```

Send a query to the MCP endpoint for the `finance` namespace:

```bash
curl -X POST http://localhost:8081/finance/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc": "2.0", "method": "tools/list", "id": 1}'
```

The response contains only the tools from the `market-data-server` forward. It does not contain tools from the default namespace.

## Endpoint paths

Concrete MCP endpoints use `/{namespace}/mcp`. Virtual MCP endpoints use `/virtual-ns/{namespace}/mcp`. Paths such as `/finance` and `/mcp` do not use a complete pattern. Wanaku rejects these paths.

## Virtual namespaces

A virtual namespace combines tools, resources, prompts, and A2A agents from concrete namespaces. It does not own copies of these entries. Changes to a member inventory appear in the virtual view. When a forward is removed, its entries disappear from all virtual views.

Create the concrete namespaces first. Then create the virtual namespace:

```bash
curl -X POST http://localhost:8080/api/v1/namespaces \
  -H 'Content-Type: application/json' \
  -d '{"name":"integration","members":["kafka","flink"]}'
```

The admin UI also supports virtual namespaces. Select the virtual namespace option in the namespace modal. Select concrete members from the member list.

Connect MCP clients to `http://localhost:8081/virtual-ns/integration/mcp`. The virtual view uses qualified names. For example, `kafka/health-check` and `flink/health-check` remain separate tools. Use the qualified name in `tools/call` and `prompts/get`:

```bash
curl -X POST http://localhost:8081/virtual-ns/integration/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"kafka/health-check","arguments":{}}}'
```

Resource names also use `namespace/name`. Resource URIs use `wanaku://{concrete-namespace}/{original-uri}`. For example, `file:///report.csv` in `kafka` becomes `wanaku://kafka/file:///report.csv`. Resource templates keep their template expressions after this prefix. Use the listed URI in `resources/read`. Wanaku removes the prefix before it sends the request upstream.

Virtual namespace rules:

- Each member must be an existing concrete namespace. Virtual members are forbidden.
- Duplicate members are forbidden.
- An empty `members` list defines an empty virtual namespace. An absent or null `members` field defines a concrete namespace. `targets` is an alias for `members`.
- Namespace names remain unique in this installation.
- Register forwards, tools, resources, prompts, and A2A agents in concrete namespaces only.
- Remove member references before deleting a concrete namespace.
- A concrete namespace with entries or member references cannot become virtual.

These rules also apply to configuration loading and snapshot restoration. Use `PUT /api/v1/namespaces/{name}` to replace a virtual namespace definition.

Wanaku resolves each action to its concrete owner before dispatch. Credential restrictions use the concrete namespace and owning forward ID. Action policies and evaluators check both the virtual namespace and the concrete namespace. A denial in either scope blocks the action. Each scope uses its configured governance posture. Audit records retain both namespace identities.

In the virtual action-policy scope, target names use `namespace/name` and resource URIs use the `wanaku://` prefix. In the concrete scope, targets use their original names and URIs. Evaluator triggers use each evaluated namespace. Evaluator processors receive the original upstream name in `tool_name`.

For A2A discovery, send `GET /virtual-ns/integration/a2a` to the A2A listener on port 8084. The response lists member agents with qualified names and virtual proxy URLs. Use `/virtual-ns/integration/a2a/kafka/worker` to select agent `worker` in `kafka`. Its Agent Card is at `/virtual-ns/integration/a2a/kafka/worker/.well-known/agent-card.json`. See [A2A traffic](a2a.md) for supported protocol operations.

## Next steps

- [Governance posture](../govern/governance-posture.md#global-posture-and-namespace-overrides): set a different governance posture for each namespace.
- [Connect upstream MCP servers with forwards](forwards.md): register the upstream servers of each namespace.
- [Management API](../reference/management-api.md#create-a-namespace): create and update namespaces through the REST API.
