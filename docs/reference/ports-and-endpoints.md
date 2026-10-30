---
title: Wanaku ports and endpoints
description: Default ports, listen addresses, and endpoint paths of the Wanaku management API, Admin UI, MCP, inference proxy, and A2A listeners.
---

# Wanaku ports and endpoints

Wanaku serves each kind of traffic on its own port. All listeners bind to all interfaces by default.

## Listeners

| Listener | Default address | Set with | Paths |
|---|---|---|---|
| Management API and Admin UI | `0.0.0.0:8080` | `WANAKU_MGMT_LISTEN` | `/api/v1/...`, `/healthz`, `/admin/` (`/` redirects to `/admin/`) |
| MCP | `0.0.0.0:8081` | the `mcp` listener in `pipeline.yaml` | `/{namespace}/mcp`, for example `/default/mcp` and `/public/mcp` |
| Inference proxy | `0.0.0.0:8083` | the `inference` listener in `pipeline.yaml` | OpenAI-compatible paths, for example `/v1/chat/completions` |
| A2A | `0.0.0.0:8084` | `WANAKU_A2A_LISTEN` | Registered A2A agent proxy URLs |

The headless build (`--no-default-features`) does not serve the Admin UI.

The MCP listener also serves OAuth protected-resource metadata at `/.well-known/oauth-protected-resource/{namespace}/mcp`. See [Authentication](../operate/authentication.md).

## Exposure

The management API has no built-in authentication. Do not expose port 8080 outside a trusted network. Put an authenticating proxy, such as oauth2-proxy, in front of the MCP and management listeners. The bundled Compose setup uses port 4180 for the management proxy and port 4181 for the MCP proxy.

## Next steps

- [Wanaku environment variables](environment-variables.md): change the management API and A2A addresses.
- [Pipeline configuration file (pipeline.yaml)](pipeline-yaml.md): change the MCP and inference listeners, or add TLS.
- [Management API](management-api.md): find the routes under `/api/v1/`.
