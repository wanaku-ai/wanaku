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

A custom file replaces the complete embedded pipeline. Start from [`server/src/default.yaml`](../../server/src/default.yaml), which is the embedded default, and change only what you need.

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
