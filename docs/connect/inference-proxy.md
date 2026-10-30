---
title: Inference proxy
description: Send OpenAI-compatible inference traffic through the Wanaku inference proxy on port 8083, and use it from the LLM Chat page of the Debugger plugin.
---

# Inference proxy

The inference proxy is a raw, transparent reverse proxy (port 8083) to an
OpenAI-compatible backend. It forwards requests as-is, including the caller's
own `Authorization` header — Wanaku does not inject or store a credential for
it. Point it at a backend with `WANAKU_INFERENCE_UPSTREAM`:

```bash
export WANAKU_INFERENCE_UPSTREAM=127.0.0.1:11434
```

The [Wanaku Debugger plugin](https://github.com/wanaku-ai/wanaku-barn/blob/main/docs/deployment-debugger.md)
provides the LLM Chat page. Install the plugin from the Admin UI Plugin Catalog.
Open Debugger from the Developer navigation section. Select the LLM Chat tab.
The page calls this port directly with a key you supply in the browser. See
[Management API](../reference/management-api.md) for the endpoint shape.

By default, the LLM Chat page keeps the API key in memory for the current
session only. The page does not save the key to local storage. To keep the key
across sessions, first enable **Remember LLM settings in this browser**. Then
enable **Also remember the inference API key**. The page writes the key to
browser local storage. A script or a browser extension on the page can then read the key. Enable this
option only on a trusted device.

Because the proxy forwards the `Origin` header unchanged, the backend's own
origin policy still applies. Ollama, for example, rejects browser-origin
requests by default — set `OLLAMA_ORIGINS` on the Ollama side to allow the
Admin UI's origin. Wanaku's own CORS filter on port 8083 controls only the
response back to the browser; it does not affect what the backend accepts.

Wanaku also rewrites the outgoing `Host` header to match
`WANAKU_INFERENCE_UPSTREAM`, instead of forwarding the browser's original
Host. Backends that route by hostname (for example, a TLS-terminating
ingress in front of a hosted LLM API) reject a mismatched Host. **Note:**
like `WANAKU_CORS_ORIGIN`, this override applies only to the embedded
default pipeline config. A custom `--pipeline-config` must define its own
`headers` filter with `request_set` on the `inference_proxy` chain to get
this behavior.

`WANAKU_INFERENCE_UPSTREAM` may include a path, for example
`https://openrouter.ai/api`. Wanaku prepends that path to every request it
forwards — a request to `/v1/chat/completions` on port 8083 reaches
`https://openrouter.ai/api/v1/chat/completions` upstream. The same
custom-`--pipeline-config` caveat applies: this requires a `path_rewrite`
filter with `add_prefix` on the `inference_proxy` chain.

## Next steps

- [Wanaku environment variables](../reference/environment-variables.md): see all variables that the inference proxy reads.
- [Pipeline configuration file](../reference/pipeline-yaml.md): define the `inference_proxy` chain in a custom pipeline.
- [Management API](../reference/management-api.md): find the endpoint shape.
