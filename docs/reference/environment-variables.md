---
title: Wanaku environment variables
description: Reference for the Wanaku server environment variables: listen addresses, persistence, Admin UI override, header forwarding, and feature limits.
---

# Wanaku environment variables

Environment variables have the highest priority of all configuration sources. The server reads them at startup.

## Core Environment Variables

These control core server behavior:

| Variable | Default | Purpose |
|---|---|---|
| `WANAKU_MGMT_LISTEN` | `0.0.0.0:8080` | Management API listen address (host:port) |
| `WANAKU_A2A_LISTEN` | `0.0.0.0:8084` | Registered A2A agents listen address in the embedded pipeline. |
| `WANAKU_A2A_PUBLIC_URL` | `http://127.0.0.1:8084/` | Public HTTP or HTTPS origin for registered agent proxy URLs and Agent Cards. |
| `WANAKU_INFERENCE_UPSTREAM` | `127.0.0.1:11434` | Upstream for the inference-proxy pipeline (port 8083), an OpenAI-compatible passthrough. Clients call this port directly and supply their own bearer token. |
| `WANAKU_PERSIST_BACKEND` | `file` | File-based registry persistence. Only the exact value `none` disables persistence. Any other value uses file persistence. |
| `WANAKU_PERSIST_PATH` | `$HOME/.wanaku/server` | Directory where Wanaku reads and writes `registry.json`. The default plugins path is `<WANAKU_PERSIST_PATH>/plugins`. |
| `WANAKU_UI_PATH` | _(unset = embedded)_ | Filesystem path to admin UI override (use for local dev). No effect in the headless build. |
| `WANAKU_CORS_ORIGIN` | `*` | Value for `Access-Control-Allow-Origin` on all HTTP responses (management API, MCP endpoint, inference proxy, and CORS preflight) |
| `WANAKU_AUTH_ISSUER` | _(unset = disabled)_ | OIDC issuer URL for RFC 9728 metadata endpoint |
| `WANAKU_AUTH_UPSTREAM_ISSUER` | `WANAKU_AUTH_ISSUER` | Internal OIDC issuer URL for token requests |
| `WANAKU_FORWARD_HEADERS` | _(unset = none)_ | Comma-separated list of HTTP header names to forward from incoming MCP requests to downstream tool invocations (e.g., `Authorization,DPoP`). Per-tool overrides via the `wanaku.forward_headers` label. |
| `WANAKU_FORWARD_HEALTHCHECK_INTERVAL` | `30` | Interval, in seconds, of the background loop that re-probes forwards currently marked unavailable and flips them back to available once they recover — no manual refresh required. Set to `0` to disable the loop. |

**Example:**

```bash
export WANAKU_MGMT_LISTEN=0.0.0.0:9091
export WANAKU_PERSIST_BACKEND=file
export WANAKU_PERSIST_PATH=/var/lib/wanaku/registry
wanaku-server
```

### Management API Listen Address

The `WANAKU_MGMT_LISTEN` variable controls where the management API binds. Format: `host:port`.

**Bind to all interfaces (default):**

```bash
export WANAKU_MGMT_LISTEN=0.0.0.0:8080
```

**Bind to localhost only:**

```bash
export WANAKU_MGMT_LISTEN=127.0.0.1:8080
```

Useful when running Wanaku behind a reverse proxy (nginx, Envoy) that handles external traffic.

**Bind to specific IP:**

```bash
export WANAKU_MGMT_LISTEN=10.0.1.42:8080
```

### Admin UI Override

The admin UI is embedded in the binary. Wanaku serves it at `http://localhost:8080/admin/`, and `/` redirects to `/admin/`. The headless build (`--no-default-features`) does not include the UI, and it ignores `WANAKU_UI_PATH`.

To serve UI files from a directory on disk instead of the embedded bundle:

```bash
export WANAKU_UI_PATH=/absolute/path/to/ui/dist
wanaku-server
```

The server serves files from the specified directory instead of the embedded bundle.

A relative path resolves against the working directory of the server process. For example, `./ui/admin/dist` works when you start the server from the repository root. In the container image, the working directory is `/etc/wanaku`. Use an absolute path in a deployment.

### Header Forwarding

By default, Wanaku does not forward any HTTP headers from incoming MCP requests to downstream tool invocations. To enable header forwarding (e.g., for gateway-mediated token exchange), configure an allowlist of header names.

**Global allowlist** — applies to all tool calls:

```bash
export WANAKU_FORWARD_HEADERS=Authorization,DPoP
```

**Per-tool override** — set the `wanaku.forward_headers` label on a `ToolEntry`:

```json
{
  "name": "github-api",
  "uri": "http://mcp-gateway:8080/mcp",
  "type": "mcp-forward",
  "labels": {
    "wanaku.forward_headers": "Authorization,X-Third-Party-Token"
  }
}
```

Both lists are merged at runtime — a header is forwarded if it appears in either the global allowlist or the per-tool label. Header names are case-insensitive.

**SEP-2243 argument injection** — When a tool's input schema has properties annotated with `x-mcp-header` (SEP-2243), Wanaku automatically injects matching forwarded header values as tool arguments before forwarding. This ensures downstream MCP servers using `@McpParamHeader` receive the value correctly. This behavior is enabled by default and can be disabled per-tool:

```json
{
  "labels": {
    "wanaku.forward_headers": "Authorization",
    "wanaku.inject_header_args": "false"
  }
}
```

When disabled, headers are forwarded only as raw HTTP headers on the downstream connection — suitable for gateway scenarios where the gateway reads headers directly.

**Use case:** A gateway (Envoy ExtProc, IBM ContextForge) sits between Wanaku and a protected downstream API. The gateway performs token exchange (e.g., Keycloak STS) using the `Authorization` header from the original request. Wanaku forwards the header so the gateway has a `subject_token` to exchange.

**Security considerations:**

- Headers that would corrupt the downstream HTTP request (`host`, `content-type`, `content-length`, `transfer-encoding`, `connection`) and rmcp-reserved headers (`accept`, `mcp-session-id`, `last-event-id`) are always blocked, even if they appear in the allowlist.
- When `Authorization` forwarding is enabled, anyone with management API access can register a tool pointing to an arbitrary URL — the caller's bearer token would then be forwarded to that URL. Secure the management API (authentication, network isolation) before enabling credential forwarding in production.

## Feature-Specific Environment Variables

Features (mcp-metadata, evaluator, etc.) define their own environment variables.

### Evaluator Processor Runner

The evaluator runs production WASM processors on a bounded runner. These variables set the runner limits. The server reads them at startup. A missing, invalid, or zero value keeps the default.

| Variable | Default | Purpose |
|---|---|---|
| `WANAKU_EVALUATOR_PROCESSOR_CONCURRENCY` | Number of available CPUs | Maximum concurrent processor invocations. The runner rejects more invocations with `processor_overloaded`. |
| `WANAKU_EVALUATOR_PROCESSOR_TIMEOUT_MS` | `1000` | Wall-clock deadline for one invocation. |
| `WANAKU_EVALUATOR_PROCESSOR_MEMORY_BYTES` | `67108864` | Linear memory limit for one invocation. |
| `WANAKU_EVALUATOR_PROCESSOR_FUEL` | `10000000` | Fuel budget for one invocation. |

Runner failures follow the governance `on_failure` behavior. See [Processor Resource Limits](../govern/evaluators.md#processor-resource-limits).

### Intercept Feature

The intercept feature records request/response interactions for conversation tracking. Evaluators use this history to provide context to LLM operations.

| Variable | Default | Purpose |
|---|---|---|
| `WANAKU_INTERACTION_CAPACITY` | `1000` | Maximum number of interactions kept in the in-memory store |

The intercept feature captures the LLM request body and response body. The feature redacts sensitive data from both bodies before it stores them. The feature preserves the conversation content that intent analysis needs. See [Redaction boundary](../govern/redaction.md) for the redaction rules and limitations.

Configure the redaction rules in the intercept filter node of the pipeline configuration:

```yaml
- name: wanaku_intercept
  filter: wanaku_intercept
  capture_payloads: true
  payload_max_bytes: 4194304
  include_default_redaction_rules: true
  sensitive_fields:
    - private_value
  sensitive_json_pointers:
    - /messages/0/content
  credential_markers:
    - "credential="
  token_prefixes:
    - custom_
```

`capture_payloads` controls request and response body capture. The default is `true`. Intent analysis needs the conversation content. Set this value to `false` to keep only the envelope (path, status, model, timing). This setting disables intent analysis for the affected interactions.

`payload_max_bytes` sets the maximum serialized body size after redaction. The default is `4194304`. Wanaku replaces a body that exceeds this limit with a single redaction marker.

`include_default_redaction_rules` controls the built-in redaction rules. The default is `true`. Set this value to `false` only when you must replace or disable the built-in rules.

`sensitive_fields`, `sensitive_json_pointers`, `credential_markers`, and `token_prefixes` extend the built-in rules. These controls use the same behavior as the audit configuration. See [Governance audit trail](../govern/audit-trail.md).

You can use these environment variables to override the YAML values:

| Variable | Default | Purpose |
|---|---|---|
| `WANAKU_INTERCEPT_CAPTURE_PAYLOADS` | `true` | Capture request and response bodies |
| `WANAKU_INTERCEPT_MAX_BODY_BYTES` | `4194304` | Maximum buffered body size, in bytes |
| `WANAKU_INTERCEPT_PAYLOAD_MAX_BYTES` | `4194304` | Maximum retained body size after redaction, in bytes |

`WANAKU_INTERCEPT_CAPTURE_PAYLOADS` enables capture only for the values `1`, `true`, or `yes`. Any other value disables capture. When the variable is unset, the YAML value applies.

## Next steps

- [Configuration](configuration.md): see how the environment variables and the YAML files work together.
- [Persist registry and revision state](../operate/persistence.md): configure the persistence directory that `WANAKU_PERSIST_PATH` sets.
- [Authentication](../operate/authentication.md): set the `WANAKU_AUTH_ISSUER` variables for oauth2-proxy.
