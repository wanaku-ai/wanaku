# Governance audit trail

Wanaku records governance decisions in a dedicated audit store. Audit events are separate from interaction records and metrics. The stable event schema lets operators correlate a request with the controls that evaluated it and the result of the evaluation.

The current schema version is `1.0`. Each event contains an event ID, a stream ID, a sequence number, an ISO 8601 UTC timestamp, a correlation ID, a decision, and a reason code. An event can also contain the namespace, operation, normalized target, actor, evaluator or filter, policy revision, response status, and duration. Administrative events use a separate category.

Wanaku records action-policy and evaluator decisions. Wanaku also records evaluator configuration updates, revision activations, namespace bindings, and namespace unbindings. Action-policy events retain all matched rule IDs and deny reason codes. The response to the caller still contains only the selected safe reason.

Wanaku uses the HTTP `x-request-id` header as the request, correlation, and stream ID when the header is present. For MCP requests without this header, Wanaku uses the `x-request-id` tool argument or the JSON-RPC request ID. The tool argument also supplies the conversation ID. Administrative events use the HTTP `x-request-id` header. Actor and workload fields remain empty until a trusted identity source supplies these values.

## Configure audit storage

Add an `audit` section to `wanaku.yaml`:

```yaml
audit:
  max_records: 10000
  capture_payloads: false
  payload_max_bytes: 16384
  include_default_redaction_rules: true
  sensitive_fields:
    - private_value
  sensitive_json_pointers:
    - /params/arguments/customer/token
  credential_markers:
    - "credential="
  token_prefixes:
    - custom_
```

`max_records` sets the retention limit. The default is `10000`. Wanaku removes the oldest event when the store reaches the limit.

`capture_payloads` controls request payload capture. The default is `false`. Wanaku stores normalized metadata without a request or response body when capture is disabled.

`payload_max_bytes` sets the maximum serialized payload size. The default is `16384`. Wanaku replaces a payload that exceeds this limit with `[REDACTED]`.

`include_default_redaction_rules` controls the built-in redaction rules. The default is `true`. Set this value to `false` only when you must replace or disable the built-in rules.

`sensitive_fields` adds case-insensitive JSON field names. The built-in list contains `authorization`, `cookie`, `api_key`, `apikey`, `client_secret`, `password`, and `token`.

`sensitive_json_pointers` sets exact JSON Pointer paths that Wanaku redacts. The default list is empty.

`credential_markers` adds case-insensitive text markers. Wanaku matches each custom marker as a substring. The built-in rules match `bearer ` and `basic ` only at a non-alphanumeric boundary. The built-in rules match `client_secret=` as a substring.

`token_prefixes` adds case-insensitive token prefixes. Wanaku uses prefix matching for each custom value. The built-in rules match `sk-`, `ghp_`, `github_pat_`, and `xoxb-` prefixes. The built-in rules match a 20-character `AKIA` value. The built-in rules match an `eyJ` value that contains two periods.

Wanaku ignores empty custom rule entries. Custom lists extend the built-in rules when `include_default_redaction_rules` is `true`. Custom lists replace the built-in rules when this value is `false`. Empty custom lists disable field, marker, and prefix redaction only when this value is `false`. Review all replacement rules before you enable audit payload capture.

You can use these environment variables to override the YAML values:

- `WANAKU_AUDIT_MAX_RECORDS`
- `WANAKU_AUDIT_CAPTURE_PAYLOADS`
- `WANAKU_AUDIT_PAYLOAD_MAX_BYTES`

With the default settings, Wanaku removes authorization data, cookies, API keys, client secrets, passwords, tokens, and credential-shaped string values before it stores an event. Wanaku applies the configured redaction rules to event fields, structured attributes, and captured payloads. Redaction occurs before retention and file persistence.

## Enable persistence

Audit persistence uses the configured Wanaku file persistence directory. Wanaku writes `audit-events.json` in that directory. Wanaku loads the file at startup.

Wanaku sends bounded snapshots to one background persistence worker. The worker keeps only the newest pending snapshot when events arrive faster than storage can write them. The worker writes each selected snapshot atomically. Wanaku flushes the final pending snapshot when the last audit-store handle closes.

When persistence is disabled, Wanaku keeps the bounded audit history in memory only.

Audit is an observation mechanism. An audit storage failure does not change or block the governance decision. Wanaku logs the storage error on the server. It returns only a fixed, content-free error through the health API. It increments the dropped-event count and reports degraded audit health.

The metrics snapshot reports audit persistence failures in `audit.storage_failures`. This metric uses no high-cardinality event fields.

## Query events

Use the management API to list events, get one event, inspect the schema version, or inspect store health. The list endpoint accepts time, namespace, actor, operation, target, decision, reason-code, and correlation-ID filters. It also accepts offset and limit pagination parameters.

Wanaku does not provide cryptographic verification in schema version `1.0`. Issue #1970 tracks canonical encoding, hash chaining, signatures, checkpoints, and offline verification.
