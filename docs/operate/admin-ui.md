---
title: Use the Wanaku Admin UI
description: Open the Wanaku Admin UI, find each page in the side navigation, and learn which documentation page explains the feature that each page manages.
---

# Use the Wanaku Admin UI

The Admin UI is a web console for the Wanaku management API. It shows the same data as the CLI and the API, so you can inspect and change a running server from a browser.

## Open the Admin UI

Only the full distribution of the server contains the Admin UI. The headless build does not serve it. With the headless build, use the CLI or the [Management API](../reference/management-api.md).

1. Start the server.
2. Open `http://localhost:8080` in your browser. The server redirects `/` to `/admin/`.

Port 8080 has no built-in authentication. On a remote host, do not expose the port to the network. Use an SSH tunnel (`ssh -L 8080:localhost:8080 <host>`), or put an authenticating proxy in front of the port. Read [Authentication](authentication.md).

Some browser features are available only on HTTPS or `localhost` origins. Plugins can fail when you open the UI over plain HTTP on a remote address.

## Pages

The side navigation shows these pages. The last column links to the page that explains the feature.

| Page | What you do there | Read more |
|---|---|---|
| Home | See the dashboard: counts of tools, resources, prompts, and forwards, and the filter and evaluator metrics. | [Management API](../reference/management-api.md#metrics) |
| Tools, Resources, Prompts | List the registered capabilities, and enable or disable them. | [Management API](../reference/management-api.md#enable-and-disable-tools-resources-and-prompts) |
| Namespaces | Create, edit, and delete namespaces. Each namespace has its own MCP endpoint. | [Management API](../reference/management-api.md#create-a-namespace) |
| Forwards | Add upstream MCP servers, and inspect what they expose. | [Management API](../reference/management-api.md#create-a-forward) |
| A2A agents | Add, edit, and delete agents for the A2A proxy. | [A2A Proxy](../connect/a2a.md) |
| Evaluators | Add evaluators, and bind namespaces to conversations. | [Evaluators](../govern/evaluators.md) |
| CLI Downloads | Download the Wanaku CLI for your platform. | [Quickstart](../get-started/quickstart.md) |
| Credential Bindings | Inspect credential bindings. Operators create bindings outside the UI. | [Credential Brokerage](../govern/credential-brokerage.md) |
| Audit | Read governance audit events. | [Governance audit trail](../govern/audit-trail.md) |
| Action Policies | Read the active policy and its revision history. The page is read-only. | [Action Policies](../govern/action-policies.md) |
| Policy Simulation | Validate a candidate configuration, simulate one action, or replay a batch. | [Policy Simulation](../govern/policy-simulation.md) |
| Installed Plugins | See the loaded Admin UI plugins and configure their backend services. | [Build an Admin UI plugin](../extend/plugin-development-guide.md) |
| Plugin Catalog | Install plugins from a catalog. | [Plugin Catalog](../extend/plugin-catalog.md) |

Credential Bindings, Audit, Action Policies, Policy Simulation, Installed Plugins, and Plugin Catalog are in the **Admin** section. CLI Downloads is in the **Developer** section. Pages that plugins add appear in the **Extensions** menu.

## Next steps

- [Secure a Wanaku deployment](secure-a-deployment.md): protect the management port before you open the UI to other users.
- [Admin UI development](../contributing/admin-ui.md): change or extend the Admin UI code.
- [Plugin Catalog](../extend/plugin-catalog.md): add pages to the Admin UI with plugins.
