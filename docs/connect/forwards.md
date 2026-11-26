---
title: Connect upstream MCP servers with forwards
description: Register an upstream MCP server as a Wanaku forward with the CLI or wanaku.yaml, and let Wanaku discover its tools, resources, and prompts.
---

# Connect upstream MCP servers with forwards

A forward connects Wanaku to one upstream MCP server. Agents call the tools through Wanaku, and Wanaku forwards each allowed call to the upstream server.

## Register a forward

Use the CLI to register a forward to an MCP server:

```bash
wanaku forwards add --service="http://localhost:8180/mcp" --name my-mcp-server --namespace default --no-auth
```

Wanaku connects to the upstream server. It discovers and registers all available tools. Each forward connects to one upstream MCP server. Wanaku automatically discovers the tools, resources, and prompts on that server.

List the discovered tools:

```bash
wanaku tools list --no-auth
```

To register more upstream servers, add one forward for each server. To register forwards at startup, put them in the `forwards` section of [`wanaku.yaml`](../reference/wanaku-yaml.md). To register forwards through the REST API, read [Create a Forward](../reference/management-api.md#create-a-forward).

When Wanaku runs in a container, `localhost` is the container itself. Read [Run Wanaku in a container](../operate/container.md#troubleshooting) for the addresses of services on the host.

## Next steps

- [Give each team its own MCP endpoint with namespaces](namespaces.md): put forwards in separate namespaces.
- [Keep API keys away from AI agents with credential brokerage](../govern/credential-brokerage.md): give a forward the token that the upstream server requires.
- [Govern AI agent tool calls](../govern/index.md): allow or deny the calls to the discovered tools.

## Connector profile configuration model

A connector profile identifies a reusable MCP streamable HTTP configuration by
name and positive version. Each connector instance identifies one profile
version, one namespace, and one upstream address.

The profile defines a `provider_key` to identify the source. The
`drift_mode` field accepts `allow` or `deny` and defaults to `deny`.
Startup validation checks this field. It does not make a drift decision.

The shared connector types validate instance values as follows.

1. Select the exact profile name and version.
2. Check the address against the profile's TLS, query, and origin constraints.
3. Use the profile default when the field has one.
4. For fields without a default, check each instance value against the field's enumeration.
5. Check that the instance supplies a binding reference for each required credential purpose.

An instance cannot override a profile default. The composed configuration
records the source of each value. The instance stores
binding IDs separately from configuration values. Binding validation checks the
owner, origin, namespace, purpose, and injection mechanism against brokerage
metadata. The broker resolves credentials separately.

Migration validation requires a higher version of the same profile and preserves
the instance name, namespace, address, and immutable configuration values. To
return to an earlier definition, use that definition with a new version number.

Wanaku validates the `connector_profiles` and `connector_instances` sections in
`wanaku.yaml` at startup. Use `generic-mcp` version `1` to select the built-in
profile. The name `generic-mcp` is reserved.

```yaml
connector_profiles:
  - identity:
      name: example-mcp
      version: 1
    provider_key: example
    transport: mcp_streamable_http
    address:
      require_tls: true
      allowed_origins:
        - https://example.org:443
connector_instances:
  - profile:
      name: example-mcp
      version: 1
    name: example
    namespace: default
    address: https://example.org/mcp
```

Startup validation does not contact the instance or publish its capabilities.
Use the existing `forwards` section to register an upstream for runtime use.
