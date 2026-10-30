---
layout: home
title: Wanaku documentation
description: Wanaku is a governed action proxy for AI agents. Start here to install it, connect agents and backends, govern their actions, and extend it.
hero:
  name: Wanaku
  text: A governed action proxy for AI agents
  tagline: Policy, credentials, and audit for every tool call, agent message, and inference request.
  actions:
    - theme: brand
      text: Quickstart
      link: ./get-started/quickstart
    - theme: alt
      text: Browse the docs
      link: ./get-started/
features:
  - title: Get started
    details: Install Wanaku, run your first governed MCP proxy, and learn the architecture.
    link: ./get-started/
  - title: Deploy and operate
    details: Run Wanaku on Kubernetes, persist state, add authentication, and secure the deployment.
    link: ./operate/
  - title: Connect agents and backends
    details: Proxy A2A traffic, inference requests, and semantic routers through Wanaku.
    link: ./connect/
  - title: Govern agent actions
    details: Control agent actions with posture, action policies, evaluators, credentials, and audit.
    link: ./govern/
  - title: Extend Wanaku
    details: Write evaluator actions, feature crates, and Admin UI plugins.
    link: ./extend/
  - title: Reference
    details: Look up environment variables, configuration files, ports, and the management API.
    link: ./reference/
---

# Wanaku documentation

Wanaku is a governed action proxy for AI agents. It sits between agents and the systems they act on, and it intercepts tool calls, agent-to-agent messages, and inference traffic. Agents never touch backend systems directly: policy, credentials, data controls, and audit happen in the proxy.

## Choose your path

| You want to | Start with | Then read |
|---|---|---|
| Try Wanaku for the first time | [Quickstart](get-started/quickstart.md) | [Architecture](get-started/architecture.md) |
| Move from the Java-based Wanaku 0.2 | [Migrate from Classic Wanaku](get-started/migration-from-classic.md) | [Deploy on Kubernetes and OpenShift](operate/kubernetes.md) |
| Deploy and run Wanaku | [Deploy and operate Wanaku](operate/index.md) | [Secure a Wanaku deployment](operate/secure-a-deployment.md) |
| Connect agents and backends | [Connect agents and backends](connect/index.md) | [A2A proxy](connect/a2a.md), [Inference proxy](connect/inference-proxy.md) |
| Decide what agents can do | [Govern agent actions](govern/index.md) | [Govern an MCP server](govern/govern-an-mcp-server.md) |
| Add your own logic | [Extend Wanaku](extend/index.md) | [Feature crates](extend/feature-crates.md), [Admin UI plugins](extend/plugin-development-guide.md) |
| Look up a setting or a route | [Wanaku reference](reference/index.md) | [Environment variables](reference/environment-variables.md) |
| Contribute code or docs | [Contributing to Wanaku](contributing/index.md) | [Testing](contributing/testing.md) |

## Is Wanaku for you?

Wanaku is a good fit when:

- You need policy, identity, and audit in the proxy, not in the agent or the LLM.
- You want agents to call tools without access to the backends.
- You want a different tool catalog for each team, tenant, or environment.
- You want evaluators, safety gates, and custom filters in the action path.

Wanaku is not a good fit yet when:

- You need clustering. Wanaku runs on a single node.
- You need a Prometheus exporter or distributed tracing. Wanaku keeps its metrics in memory.

Read [Architecture](get-started/architecture.md#whats-not-here-yet) for the other limits of this release.

## Which version am I reading?

These pages describe the Rust-based Wanaku server. Its early-access builds are development builds of the unreleased Wanaku 0.3.0. Releases up to v0.2.0 contain the Java-based Wanaku, which is deprecated. If you use the Java-based Wanaku, read [Migrate from Classic Wanaku](get-started/migration-from-classic.md).

## Get help

- [FAQ and troubleshooting](operate/troubleshooting.md): find the cause of common problems.
- [GitHub Issues](https://github.com/wanaku-ai/wanaku/issues): report a bug or request a feature.
- [GitHub Discussions](https://github.com/wanaku-ai/wanaku/discussions): ask a question.
