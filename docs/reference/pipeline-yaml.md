---
title: Pipeline configuration file (pipeline.yaml)
description: "Reference for the Wanaku pipeline.yaml file: listeners, TLS, filter chains, filter options, and the filter order rules that the pipeline needs."
---

# Pipeline configuration file (pipeline.yaml)

The pipeline configuration defines listeners, filter chains, and filter-specific settings. This YAML file uses the native Praxis configuration format. This documentation calls the file `pipeline.yaml`. The server accepts any file name.

**Override:** Pass with `--pipeline-config`:

```bash
wanaku-server --pipeline-config /path/to/pipeline.yaml
```

A custom file replaces the complete embedded pipeline. Start from [`server/src/default.yaml`](https://github.com/wanaku-ai/wanaku/blob/main/server/src/default.yaml), which is the embedded default, and change only what you need.

**Format:**

```yaml
listeners:
  - name: mcp
    address: "127.0.0.1:8081"
    filter_chains: [mcp_router]

filter_chains:
  - name: mcp_router
    filters:
      - filter: cors
        allow_origins: ["*"]
      - filter: mcp
        on_invalid: continue
      - filter: wanaku_mcp_id
      - filter: wanaku_namespace
      - filter: wanaku_well_known
      - filter: wanaku_mcp_init
      - filter: wanaku_mcp_integrity
      - filter: wanaku_action_policy
      - filter: wanaku_evaluator
      - filter: wanaku_tool_list
      - filter: wanaku_tool_call
      - filter: wanaku_resource_list
      - filter: wanaku_resource_read
      - filter: wanaku_prompt_list
      - filter: wanaku_prompt_get
      - filter: static_response
        status: 200
        headers:
          - name: content-type
            value: application/json
        body: '{"jsonrpc":"2.0","error":{"code":-32601,"message":"method not supported"},"id":null}'
```

The `cors` filter requires `allow_origins`. The `static_response` filter requires `status`. Praxis rejects unknown keys in both filters, so the server does not start if a key is missing or misspelled.

## Praxis admin and metrics listeners

Wanaku builds Praxis without the `admin-api` feature.
The Praxis admin, health, and metrics listeners are disabled.
`admin.address` and `admin.metrics_address` do not start listeners.
The Wanaku management API uses `WANAKU_MGMT_LISTEN`.
Use the Wanaku management API `/healthz` route for health checks.

## Listener Configuration

**Change MCP port:**

```yaml
listeners:
  - name: mcp
    address: "0.0.0.0:8083"  # Bind to all interfaces, port 8083
    filter_chains: [mcp_router]
```

**Add TLS:**

```yaml
listeners:
  - name: mcp
    address: "0.0.0.0:8081"
    tls:
      certificates:
        - cert_path: /etc/wanaku/cert.pem
          key_path: /etc/wanaku/key.pem
    filter_chains: [mcp_router]
```

Wanaku installs the Praxis rustls crypto provider at startup. This provider uses the system OpenSSL library (`libcrypto`). The host must supply OpenSSL 3. If the provider does not install, Wanaku stops with a fatal error. For all listener TLS options, refer to the `praxis-proxy-tls` documentation.

## Filter Configuration

**CORS filter:**

```yaml
- filter: cors
  allow_origins: ["http://localhost:3000", "https://app.example.com"]
  allow_methods: ["GET", "POST", "OPTIONS"]
  allow_headers: ["Content-Type", "Authorization"]
```

**Note:** The `WANAKU_CORS_ORIGIN` env var overrides `allow_origins` in the embedded default pipeline config at startup. If you provide a custom pipeline config via `--pipeline-config`, `allow_origins` in that file is used as-is — the env var only applies to the embedded default.

**MCP filter (praxis-ai):**

```yaml
- filter: mcp
  on_invalid: continue  # REQUIRED for OPTIONS preflight
  max_body_bytes: 1048576  # 1MB limit
```

The `on_invalid: continue` setting allows OPTIONS requests (which have no body) to pass through without failing validation. Without it, CORS preflight fails.

**Custom filter:**

```yaml
- filter: wanaku_custom_feature
  some_option: value
  other_option: 42
```

Put the filter options at the same level as the `filter` key. Do not nest them under a `config` key. A filter that is in the chain is active, so there is no `enabled` key. The exact options depend on the filter.

## Filter Ordering

The order in `filters:` matters. The pipeline executes filters top-to-bottom.

**Critical rules:**

1. Put CORS first. Otherwise, error responses do not contain CORS headers.
2. **MCP must be before wanaku_namespace** — namespace filter reads `mcp.method` metadata
3. **wanaku_mcp_id must be before the governance filters** — it records the JSON-RPC request ID, so that synthetic error responses carry the correct `id`
4. **wanaku_namespace must be before tool/resource/prompt filters** — they all read `wanaku.namespace`
5. **static_response must be last** — catch-all for unhandled requests

If you reorder filters and requests start failing, check the logs. The filter that needed metadata will error with "missing metadata key".

## Next steps

- [Wanaku configuration file](wanaku-yaml.md): configure forwards, evaluators, and governance.
- [Architecture](../get-started/architecture.md): see how the filters process a request.
- [Ports and endpoints](ports-and-endpoints.md): find the default listener addresses.

## MCP content integrity

Content scanning is a defense-in-depth control. Keep authorization, tool input
validation, and credential controls enabled. A scan can miss harmful content, and
a rule can match safe content.

Set `rules` on the `wanaku_mcp_integrity` filter in the MCP chain. Put the filter
after `wanaku_namespace` and before `wanaku_action_policy` and `wanaku_evaluator`.
The embedded configuration has no rules and sends no content to scanners.

```yaml
- filter: wanaku_mcp_integrity
  rules:
    - id: service_token
      kind: tool_result
      check: { type: secret_pattern, pattern: '\bsvc_[A-Z0-9]{24}\b' }
      action: redact
    - id: argument_size
      kind: tool_arguments
      check: { type: size, max_bytes: 16384 }
      action: block
    - id: upstream_message
      kind: upstream_error
      pointer: /message
      check: { type: field }
      action: redact
```

The `kind` values are `tool_arguments`, `tool_result`, `resource_contents`,
`prompt_contents`, and `upstream_error`. A rule inspects the argument object,
the JSON-RPC result, or the JSON-RPC error object. An MCP result with
`isError: true` uses the local rules for both `tool_result` and `upstream_error`.
For external scanning, it has kind `upstream_error`. Error rules inspect the
response after credential redaction.

Set `pointer` to a JSON Pointer to select a field or an array element.
Omit `pointer` to select the full value. A rule with a pointer that does not exist
does not match.

| Check type | Match condition |
| --- | --- |
| `size` | The compact JSON encoding uses more than `max_bytes` bytes. |
| `type` | The selected value differs from `expected`. The values are `null`, `boolean`, `number`, `string`, `array`, and `object`. |
| `field` | The selected field or array element exists, including a null value. A nonempty pointer is required. |
| `secret_pattern` | The regular expression `pattern` matches a string value in the selected content. The check includes nested string values. |

Use distinct rule IDs to identify findings in the audit log. If more than one
rule matches, the filter uses the first applicable action in this order.

1. `block` rejects the request or withholds the result with error `-32003`.
2. `require_approval` rejects the request or withholds the result with error
   `-32004`. The filter does not queue, resume, or replay the action.
3. `redact` applies all matching redaction rules. A field check replaces the
   selected value with `[REDACTED]`. A secret-pattern check replaces each
   matching string with `[REDACTED]`. Use redaction on values that can accept a
   replacement string. The normal tool argument schema still applies.
4. `tag` passes the content and records the matching rule as an audit tag.
5. `allow` passes the content and records any findings. This is also the action
   when no rule matches.

Audit events include the content kind, effective action, and each finding's
rule ID, check, scanner, and proposed action. Findings exclude the scanned
content. Use `tag` or the namespace's audit mode to review false positives.

In enforce mode, the namespace's `on_failure` setting controls scanner failures.
With `deny`, a scanner failure blocks the content. With `allow`, the filter
still applies successful local findings. Audit mode records the findings and
failures and passes the original content. Disabled mode skips inspection in
that namespace. If a request uses a virtual namespace, the filter also applies
the posture of that namespace.

### Optional external scanner

To send content to a trusted external scanner, add an `external` section to the
filter. List each content kind that the scanner can receive.

```yaml
- filter: wanaku_mcp_integrity
  external:
    id: trusted_scanner
    url: https://scanner.example.com/inspect
    kinds: [tool_result]
    action: block
```

The filter sends an HTTPS POST with a JSON object containing `kind` and
`content`. The scanner must return a successful HTTP status and a JSON object
with one Boolean field, `matched`. The configured action applies when
`matched` is true. External results support `allow`, `tag`, `block`, and
`require_approval`. Use local rules for redaction.

The scanner has 500 milliseconds for the complete HTTP exchange. The request
limit is 1 MiB and the reply limit is 4 KiB. The filter does not follow
redirects or send client headers. The scanner receives content after local
redaction. The filter skips the external scan when local findings require
blocking or approval. Arguments added from forwarded headers use local rules
only. If the scanner exceeds a limit, returns an invalid reply, or fails the
HTTP exchange, the filter records the failure and applies the namespace's
failure posture.
