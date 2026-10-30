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

All MCP endpoints use the `/{namespace}/mcp` pattern. Paths such as `/finance` and `/mcp` do not use this complete pattern. Wanaku rejects these paths.

## Next steps

- [Governance posture](../govern/governance-posture.md#global-posture-and-namespace-overrides): set a different governance posture for each namespace.
- [Connect upstream MCP servers with forwards](forwards.md): register the upstream servers of each namespace.
- [Management API](../reference/management-api.md#create-a-namespace): create and update namespaces through the REST API.
