---
title: Connect agents and backends
description: Connect agents and backends to Wanaku with governed agent-to-agent traffic, the inference proxy, and semantic routing with Barn and WSR.
---

# Connect agents and backends

These pages are for integration developers who connect agents and backends through Wanaku.

## Pages in this section

- [Connect upstream MCP servers with forwards](forwards.md): register upstream MCP servers and discover their tools.
- [Give each team its own MCP endpoint with namespaces](namespaces.md): isolate tools for each team, tenant, or environment.
- [A2A Proxy](a2a.md): govern agent-to-agent (A2A) traffic.
- [Inference proxy](inference-proxy.md): send OpenAI-compatible inference traffic through Wanaku.
- [Semantic routing with Barn and WSR](semantic-routing.md): author routes in Barn, deploy the Wanaku Semantic Router (WSR), and check governed MCP calls.
- [Ingest Camel semantic audit records](semantic-audit-ingestion.md): send semantic decisions from Camel runtimes to the audit store.

## Next steps

- [Govern agent actions](../govern/index.md): apply policy to the traffic that you connect.
- [Management API](../reference/management-api.md): register forwards and namespaces through the REST API.
