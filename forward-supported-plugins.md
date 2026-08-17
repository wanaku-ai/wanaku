# Forward-Supported Plugins

Design notes for linking forwarded MCP server identity to UI plugins.

## Context

Wanaku now captures `McpServerInfo` (server name, version, capabilities, extensions) during forward discovery via `peer_info()`. The admin UI shows this in the forwards table. This document describes the next step: letting plugins discover which forwards they support and interact with them.

## Problem

The existing plugin system is UI-only — plugins add pages and navigation, and can proxy HTTP calls to backend services. But plugins have no awareness of forwarded MCP servers. A Camel plugin can't discover that a "camel-prod" forward exists, what type of server it is, or call its tools.

## Research Findings: MCP Server Identity

Investigation of the three MCP server stacks in the ecosystem:

| Field | Camel MCP (4.22.0) | Quarkiverse MCP (1.13.1) | Wanaku Praxis (Rust) |
|---|---|---|---|
| `serverInfo.name` | `camelContext.getName()` (auto-generated, e.g. `"camel-1"`) | `quarkus.application.name` or `"N/A"` | `"wanaku-praxis"` |
| `serverInfo.version` | `camelContext.getVersion()` (e.g. `"4.22.0"`) | `quarkus.application.version` or `"N/A"` | `"0.3.0"` |
| `experimental`/`extensions` | none | none | none |

**Key finding:** Camel MCP servers default to auto-generated names — not useful for type identification. The config option `camel.server.mcp-server-name` exists but the Wanaku Camel integration doesn't set it. No server in the ecosystem uses `experimental` or `extensions` fields.

## Proposed Design

### Plugin Manifest Extension

Add optional `mcpServers` field to `plugin.json`:

```json
{
  "id": "camel-plugin",
  "name": "Apache Camel",
  "mcpServers": {
    "matchNames": ["apache-camel*"],
    "matchTools": ["camel_*"],
    "matchLabels": {"type": "camel"}
  }
}
```

Three matching strategies (any match activates):
- `matchNames` — glob patterns against `serverInfo.serverName`
- `matchTools` — glob patterns against discovered tool names for that forward
- `matchLabels` — key-value pairs that must all match forward labels

### Server-Side Matching Endpoint

`GET /api/v1/plugins/{id}/forwards` — returns forwards matching this plugin's `mcpServers` declaration.

### PluginHost API Extension

```typescript
export interface ForwardsAPI {
  list(): Promise<ForwardEntry[]>;
  listMatching(): Promise<ForwardEntry[]>;
}

export interface McpAPI {
  callTool(forwardName: string, toolName: string, args: object): Promise<unknown>;
}
```

Plugins call `host.forwards.listMatching()` at activation to find their forwards, and `host.mcp.callTool()` to interact with them.

### Communication Channels

1. **MCP tool calls via PluginHost** — proxied through management API to `mcp_client::call_tool()`. A Camel server exposes tools like `camel_get_routes` — the plugin calls them directly.
2. **HTTP proxy (already exists)** — if the MCP server also exposes REST endpoints, the plugin's `host.http` proxy reaches them via service mapping in `wanaku.yaml`.

### End-to-End Flow

```
1. Admin registers forward with labels: {"type": "camel"}
2. Server captures McpServerInfo {serverName: "apache-camel", ...}
3. Plugin "camel-plugin" activates, calls host.forwards.listMatching()
4. Server matches: matchNames ✓ OR matchTools "camel_*" ✓ OR matchLabels ✓
5. Plugin gets [{name: "camel-prod", serverInfo: {...}, labels: {...}}]
6. Plugin registers "Camel: camel-prod" nav entry and page
7. Page calls host.mcp.callTool("camel-prod", "camel_get_routes", {})
8. Plugin renders route topology
```

## Open Questions

1. **Camel server naming convention** — need upstream change in wanaku-barn to set `camel.server.mcp-server-name` to a well-known value (e.g., `"apache-camel"`).

2. **Live discovery notifications** — should plugins be notified when a new matching forward appears? Polling is simpler to start.

3. **MCP session persistence** — ephemeral connections are fine initially; optimize with a connection pool if latency becomes an issue.

4. **Namespace scoping** — should plugins be restricted to forwards in specific namespaces? The existing `permissions` field in plugin manifests is reserved but not enforced.

5. **Camel-specific tools** — does the Camel MCP server expose introspection tools (get routes, get context, etc.) today? If not, that's upstream work.

## Key Files

| File | Role |
|---|---|
| `features/plugins/src/manifest.rs` | Add `mcpServers` field to `PluginManifest` |
| `features/plugins/src/lib.rs` | Server-side matching logic |
| `ui/admin/src/plugins/types.ts` | Add `ForwardsAPI` and `McpAPI` to `PluginHost` |
| `ui/admin/src/plugins/plugin-host.ts` | Implement the new APIs |
| `apis/src/registry.rs` | `ForwardEntry` already has `server_info` and `labels` |
| `apis/src/mcp_client.rs` | `call_tool()` already exists for MCP proxying |
