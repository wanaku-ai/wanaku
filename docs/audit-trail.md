# Governance audit trail

Wanaku records governance decisions in a dedicated audit store. Audit events are separate from interaction records and metrics. The stable event schema lets operators correlate a request with the controls that evaluated it and the result of the evaluation.

The current schema version is `1.0`. Each event contains an event ID, a stream ID, a sequence number, an ISO 8601 UTC timestamp, a correlation ID, a decision, and a reason code. An event can also contain the namespace, operation, normalized target, actor, evaluator or filter, policy revision, response status, and duration. Administrative events use a separate category.

Wanaku records action-policy and evaluator decisions. Wanaku also records evaluator configuration updates, revision activations, namespace bindings, and namespace unbindings. Action-policy events retain all matched rule IDs and deny reason codes. The response to the caller still contains only the selected safe reason.

## Configure audit storage

Add an `audit` section to `wanaku.yaml`:

```yaml
audit:
  max_records: 10000
  capture_payloads: false
  payload_max_bytes: 16384
  sensitive_fields:
    - private_value
  sensitive_json_pointers:
    - /params/arguments/customer/token
```

`max_records` sets the retention limit. The default is `10000`. Wanaku removes the oldest event when the store reaches the limit.

`capture_payloads` controls request payload capture. The default is `false`. Wanaku stores normalized metadata without a request or response body when capture is disabled.

`payload_max_bytes` sets the maximum serialized payload size. The default is `16384`. Wanaku replaces a payload that exceeds this limit with `[REDACTED]`.

`sensitive_fields` adds case-insensitive JSON field names to the default redaction list. `sensitive_json_pointers` adds exact JSON Pointer paths.

You can use these environment variables to override the YAML values:

- `WANAKU_AUDIT_MAX_RECORDS`
- `WANAKU_AUDIT_CAPTURE_PAYLOADS`
- `WANAKU_AUDIT_PAYLOAD_MAX_BYTES`

Wanaku always removes authorization data, cookies, API keys, client secrets, passwords, tokens, and credential-shaped string values before it stores a captured payload. Wanaku also applies redaction to structured event attributes. Redaction occurs before file persistence.

## Enable persistence

Audit persistence uses the configured Wanaku file persistence directory. Wanaku writes `audit-events.json` in that directory. Wanaku loads the file at startup. Wanaku writes an atomic bounded snapshot after each event.

When persistence is disabled, Wanaku keeps the bounded audit history in memory only.

Audit is an observation mechanism. An audit storage failure does not change or block the governance decision. Wanaku logs a content-free storage error, increments the dropped-event count, and reports degraded audit health.

The metrics snapshot reports audit persistence failures in `audit.storage_failures`. This metric uses no high-cardinality event fields.

## Query events

Use the management API to list events, get one event, inspect the schema version, or inspect store health. The list endpoint accepts time, namespace, actor, operation, target, decision, reason-code, and correlation-ID filters. It also accepts offset and limit pagination parameters.

Wanaku does not provide cryptographic verification in schema version `1.0`. Issue #1970 tracks canonical encoding, hash chaining, signatures, checkpoints, and offline verification.
