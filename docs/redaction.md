# Redaction boundary

Wanaku sits between agents and backend systems. It sees credentials, headers, tool
arguments, and conversation content on every request. This document explains where
Wanaku removes sensitive data before that data leaves the proxy, and where the
boundary ends. Read it before you enable payload capture or share Wanaku logs.

Wanaku uses one redaction engine for every sink. The engine removes sensitive JSON
field names, credential-shaped values, and configured patterns. See
[Governance audit trail](./audit-trail.md) for the full rule reference.

## Where redaction applies

Wanaku applies redaction at three sinks.

- **Tool-call forwarding logs.** The MCP tool-call filter logs forwarded header
  names only. The filter does not log header values. The filter logs tool argument
  key names only. The filter does not log argument values. A failed forwarded call
  returns a redacted detail to the client. The filter also logs the redacted detail.
- **Retained interactions.** The intercept feature captures the LLM request body and
  response body. The feature redacts both bodies before it stores them. The feature
  redacts the bodies before the management API returns them.
- **Governance audit events.** The audit store redacts event fields, structured
  attributes, and captured payloads before retention and before file persistence.

## What Wanaku redacts

The default rules redact these JSON field names. The match is case-insensitive.

- `authorization`
- `proxy-authorization`
- `cookie`
- `set-cookie`
- `api_key`
- `apikey`
- `client_secret`
- `password`
- `token`

The default rules also redact credential-shaped string values. The rules match a
`client_secret=` substring. The rules match `sk-`, `ghp_`, `github_pat_`, and
`xoxb-` prefixes. The rules match a 20-character `AKIA` value. The rules match an
`eyJ` value that contains two periods. Wanaku replaces a matched value with
`[REDACTED]`.

The rules also match the `bearer ` and `basic ` auth-scheme markers at a
non-alphanumeric boundary. Wanaku applies these two markers to tool-call error
details and to audit events. Wanaku does not apply these two markers to
conversation bodies. The words "basic" and "bearer" are common in natural
language. A conversation body match would remove the conversation context that
intent analysis needs.

## What Wanaku preserves

The intercept feature keeps the conversation context that intent analysis needs.
The feature preserves the `role` and `content` fields in the `messages` array. The
feature preserves the completion ID and the model name as envelope metadata.

The feature still redacts a credential inside a `content` value when the value
matches a sensitive field name, a configured JSON Pointer path, a configured
marker or prefix, or a built-in credential token format (for example, `sk-` or a
JWT). Add a `sensitive_json_pointer` to redact a credential in a known content
location that the default rules do not detect.

## Configure redaction

You configure the intercept sink and the audit sink separately.

- Configure the intercept sink in the pipeline filter node. See the intercept
  section in [Configuration](./configuration.md).
- Configure the audit sink in `wanaku.yaml`. See
  [Governance audit trail](./audit-trail.md).

Both sinks accept the same redaction controls: `include_default_redaction_rules`,
`sensitive_fields`, `sensitive_json_pointers`, `credential_markers`, and
`token_prefixes`. Add a `sensitive_json_pointer` for a field that the default rules
do not detect.

## Limitations

The redaction boundary has limits. Review these limits before you enable payload
capture or share logs.

- **Custom-shaped secrets.** The default rules detect common credential formats. A
  secret with a custom format can pass the default rules. Add a `credential_marker`,
  a `token_prefix`, or a `sensitive_json_pointer` for a custom format.
- **Upstream error text.** Wanaku redacts a forwarded tool-call error before the
  error reaches the client or the log. Wanaku removes each forwarded header value.
  Wanaku also removes each token segment of a forwarded value. This step covers an
  error that echoes only the token part of an authorization header. This step also
  covers a short token. Wanaku then applies the default shape rules as a backstop.
  This redaction is best-effort. An upstream error that echoes only a fragment of a
  forwarded value can pass the rules. A custom-shaped secret that originates in the
  upstream system can also pass the rules.
- **Configuration key spelling.** Wanaku ignores an unknown redaction key. A
  mistyped key (for example, `sensitive_field` instead of `sensitive_fields`) is
  dropped without an error. The default value applies. Verify the key spelling after
  you configure a redaction rule.
- **Non-JSON bodies.** Field-name redaction and JSON Pointer redaction need JSON
  object structure. Wanaku captures a non-JSON body (for example, a streaming
  `text/event-stream` response) as a single string. Wanaku applies only the
  value-shape rules to that string. The field-name and pointer rules do not apply.
- **Payload size bound.** The intercept sink and the audit sink each apply a payload
  size bound. Wanaku replaces a serialized payload that exceeds the bound with a
  single redaction marker. A large body is dropped, not truncated.
- **Capture stays on for intent analysis.** The intercept feature captures payloads
  by default. Intent analysis needs the conversation content. Set
  `capture_payloads: false` to keep only the envelope. This setting disables intent
  analysis for the affected interactions.
- **Scope.** Redaction applies to the three sinks in this document. Redaction does
  not change the request or response that Wanaku forwards to the backend or returns
  to the agent.

## Related Docs

- [Configuration](./configuration.md) — intercept redaction settings and environment variables
- [Governance audit trail](./audit-trail.md) — audit redaction rule reference
- [Evaluator engine](./evaluator-engine.md) — how intent analysis uses conversation history
