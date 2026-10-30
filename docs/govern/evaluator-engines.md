---
title: Evaluator engines
description: "Configure the Wanaku evaluator engines: LLM connections and fields, TypeSafe System One, Open Policy Agent, and the passthrough engine."
---

# Evaluator engines

Set `engine.type` for each evaluator. The engine produces the string that the
WASM processor receives in `ctx.llmResult`. The field name remains unchanged
for guest ABI compatibility.

| Type | Behavior |
|---|---|
| `llm` | Calls the named LLM connection. It supports the fields in [LLM Fields](#llm-fields). |
| `passthrough` | Does not make a network call. It provides JSON with `method`, `tool_name`, `arguments`, `tools`, and `history`. |
| `typesafe-system-one` | Calls a TypeSafe System One Noul primitive and provides a normalized typed result. |
| `opa` | Queries an Open Policy Agent decision through the REST Data API and provides a normalized decision. See [Open Policy Agent](#open-policy-agent). |

Each engine type declares two execution capabilities. Basic audit uses the
external capability. Policy simulation uses the offline simulation capability.
An engine without network calls does not get offline simulation support
automatically.

| Type | External execution | Offline simulation |
|---|---|---|
| `llm` | Yes | No |
| `passthrough` | No | Yes |
| `typesafe-system-one` | Yes | No |
| `opa` | Yes | No |

Use this configuration for a processor that needs the normalized request context:

```yaml
evaluators:
  - name: "context-processor"
    trigger:
      method: "tools/call"
    engine:
      type: passthrough
    processor:
      path: "/wasm/context-processor.wasm"
```

Each evaluator must set `engine`. The previous top-level `llm:` field is not supported. A configuration that contains it stops startup with `configuration_invalid`.

## LLM Connections

| Field | Type | Purpose |
|-------|------|---------|
| `name` | string | Unique identifier referenced by an evaluator's `engine.connection` |
| `model` | string | Model name sent in the request body |
| `url` | string | Base URL of an OpenAI-compatible API, including the version path (for example `http://localhost:11434/v1` or `https://openrouter.ai/api/v1`). Wanaku appends only `/chat/completions`. |
| `api_key` | string | Optional bearer token for the LLM endpoint, or `env:VARIABLE_NAME`. When it is empty, Wanaku sends no `Authorization` header. |

- `model`, `url`, and `name` are required. `api_key` is optional. When `api_key` is empty, Wanaku sends no `Authorization` header.
- Each LLM request has a fixed timeout of 10 seconds. A timeout follows the governance `on_failure` behavior.

Set `llm_connections[].api_key` to `env:VARIABLE_NAME` to read the key from an environment variable. For example:

```yaml
llm_connections:
  - name: "remote-llm"
    model: "remote-model"
    url: "https://llm.example.com/v1"
    api_key: "env:LLM_API_KEY"
```

Wanaku resolves the reference when it loads the connections at startup. The variable name and value must not be empty. The variable must exist and contain a valid Unicode value. An invalid reference rejects the complete connection set. Error messages do not include the key value. Other API key strings remain literal values. Omit `api_key` or use an empty string for a connection that does not require authentication.

Wanaku resolves every `env:` reference in every connection at startup, also for a connection that no evaluator uses. One missing variable rejects the complete connection set and stops startup with `connection_configuration_invalid`. Remove connections that you do not use, or set their variables.

The server loads connections once at startup from `wanaku.yaml`. No API can create,
update, or read a connection's `api_key`. This design keeps credentials out of the
management API, including out of evaluator revision history. See
[Security Considerations](evaluators.md#security-considerations).

## LLM Fields

| Field | Type | Purpose |
|-------|------|---------|
| `operation` | string | `classify` (pick a label), `filter` (return structured data), or `augment` (enrich prompt) |
| `prompt` | string | System prompt for the LLM. The engine builds a user prompt with request context. |
| `connection` | string | Name of an entry in `llm_connections` (see above) |
| `result_schema` | object | Optional JSON Schema for validating LLM output. When set, the host validates the LLM result before passing it to the WASM guest. On mismatch, retries once with a correction prompt. |

**How the LLM sees context:** The engine builds a user prompt containing:

- Recent conversation history (last 10 interactions from the intercept filter)
- MCP method and namespace
- Tool name and arguments (for `tools/call`)
- Available tools (for `tools/list`)

Your system prompt tells the LLM what to do with that context.

**LLM failures:** These conditions are failures. They follow `on_failure`, and the processor does not run. The default `on_failure` value is `deny`.

| Condition | Audit reason code |
|-----------|-------------------|
| The LLM endpoint is not reachable | `evaluator_remote_unavailable` |
| The LLM endpoint does not answer in the request timeout | `evaluator_remote_timeout` |
| The LLM endpoint returns HTTP 401 or 403 | `evaluator_remote_auth_failed` |
| The LLM endpoint returns another error status | `evaluator_llm_failed` |
| The response is not JSON, has no `choices[0].message.content` string, or the content is blank | `evaluator_llm_failed` |
| The result does not match `result_schema` after the correction retry | `evaluator_schema_invalid` |
| The connection is not available at request time | `evaluator_connection_unavailable` |

Wanaku sends the schema correction prompt only when the LLM returns content that does not match `result_schema`. Transport, status, and response format failures do not cause a correction retry. If the correction request fails, Wanaku uses the reason code of that failure. Error messages and audit events do not contain the API key or the upstream response body.

The metrics store counts a call that returns missing or blank content as an empty result. Transport, status, and malformed JSON failures are call failures. They are not empty results.

## TypeSafe System One

TypeSafe System One connections are config-only. Set them in `wanaku.yaml`.
The management API does not expose the endpoint or API key.

```yaml
typesafe_system_one_connections:
  - name: "typesafe"
    model: "jev-latest"
    url: "https://api.typesafe.ai"
    api_key: "env:TYPESAFE_API_KEY"

evaluators:
  - name: "tool-safety"
    trigger:
      method: "tools/call"
    engine:
      type: typesafe-system-one
      connection: "typesafe"
      state: context                 # context (default) | arguments
      noul:
        id: "is_safe"
        instructions: "Is this MCP tool request safe to run?"
        criteria:
          "true": "The request is read-only and low impact."
          "false": "The request changes systems or has a high impact."
    processor:
      path: "actions/dist/safety_review_action.wasm"
```

Set `TYPESAFE_API_KEY` in the server environment before startup. Wanaku resolves `env:VARIABLE_NAME` once when it loads connections. The variable must exist and contain a non-empty Unicode value. An empty variable name or invalid value rejects the complete connection set. Error messages do not include the key value. API keys without the `env:` prefix remain literal values.

For TypeSafe System One connections, `api_key` is required. `model` defaults to `jev-latest`. `url` defaults to `https://api.typesafe.ai`. Wanaku appends `/v1/systemone` to the URL. Each request has a fixed timeout of 10 seconds. Wanaku always sends the `Authorization: Bearer` header, also when the key is empty.

The Noul primitive returns the probability that its answer is true. Wanaku
passes this stable JSON object to `ctx.llmResult`:

```json
{
  "engine": "typesafe-system-one",
  "primitive": { "id": "is_safe", "type": "noul" },
  "answer": { "noul": 0.95 },
  "probabilities": { "true": 0.95, "false": 0.05 }
}
```

`state: context` sends `method`, `tool_name`, `arguments`, and `tools`.
When the active conversation has usable history, it also sends `history` as a
chronological array of `{role, content}` messages. Wanaku omits empty and
invalid messages. If no usable messages exist, it omits `history`.

`state: arguments` sends the MCP arguments when no usable history exists. When
history exists, it sends an object with `arguments` and `history`. This avoids
an argument named `history` from replacing the conversation history. The
normalized result is independent of the TypeSafe HTTP response format.

The sample `safety_review_action.wasm` blocks a Noul result below `0.5`. It
allows a result at or above `0.5`.

## Open Policy Agent

The `opa` engine evaluates requests with Rego policies and policy data. It does not use an LLM. Operators deploy and manage OPA, the policies, and the policy data. Wanaku sends one query per matching request and enforces the result through the processor. This page covers the REST Data API scope. Embedded Rego that is compiled to WebAssembly is a separate engine.

Wanaku is tested with OPA 1.21 and Rego v1. The engine uses only the `POST /v1/data/<path>` Data API. That API is stable in all OPA 1.x releases.

### OPA Connections

OPA connections are config-only. Set them in `wanaku.yaml`. The management API does not expose the URL, the token, or the certificate path.

```yaml
opa_connections:
  - name: "local-policy"
    url: "http://127.0.0.1:8181"
    token: ""                          # Optional
    timeout_ms: 2000                   # Optional
    ca_cert: "/etc/wanaku/opa-ca.pem"  # Optional
```

| Field | Type | Purpose |
|-------|------|---------|
| `name` | string | Unique identifier. An evaluator refers to it in `engine.connection`. |
| `url` | string | OPA base URL. It must start with `http://` or `https://`. |
| `token` | string | Optional bearer token. Use it when OPA runs with `--authentication=token`. |
| `timeout_ms` | integer | Optional request timeout. The default is `2000`. The range is `1` to `30000`. |
| `ca_cert` | path | Optional PEM file. Wanaku trusts this CA certificate for `https` URLs in addition to the public roots. |

Wanaku rejects all OPA connections at startup if one entry is not valid. Then startup fails with `connection_configuration_invalid`. Use a local sidecar with `http://127.0.0.1` URLs. Use `https`, a token, and OPA authorization policies for a remote OPA server.

### Evaluator Configuration

```yaml
evaluators:
  - name: "tool-policy-gate"
    trigger:
      method: "tools/call"
      namespace: "finance"
    engine:
      type: opa
      connection: "local-policy"
      decision_path: "wanaku/tool_call/decision"
    processor:
      path: "actions/dist/opa_allow_action.wasm"
```

| Field | Type | Purpose |
|-------|------|---------|
| `connection` | string | Name of an entry in `opa_connections`. |
| `decision_path` | string | OPA data path of the decision. Use segments of ASCII letters, digits, and underscores. Separate the segments with `/`. Do not use a leading or trailing `/`. |

Wanaku validates the connection name and the decision path when it activates a configuration. A configuration with an unknown connection or a path that is not valid does not become active.

### Input Document

Wanaku sends `POST <url>/v1/data/<decision_path>` with this body:

```json
{
  "input": {
    "version": "wanaku.opa.input/v1",
    "method": "tools/call",
    "namespace": "finance",
    "tool_name": "transfer",
    "arguments": { "amount": 250.5, "approved": true, "tags": ["q3"] }
  }
}
```

| Field | Rule |
|-------|------|
| `version` | Always `wanaku.opa.input/v1`. Check this value in the policy. |
| `method` | The MCP method. It is always present. |
| `namespace` | The resolved Wanaku namespace. It is `default` when the request has no namespace. |
| `tool_name` | The MCP `params.name` value. It is `null` when the request has no name. |
| `arguments` | The MCP `params.arguments` object with its original JSON types. It is `{}` when the request has no arguments or the arguments are not an object. |

The input contains only these fields. Wanaku does not send HTTP headers, conversation history, the tool catalog, connection tokens, or credentials from credential bindings.

### Decision Contract

The policy must return one of these results:

- A boolean. `true` allows the request. `false` denies the request.
- An object with a boolean `allow` member. An optional `reason` member must be a string or `null`. Other members are permitted.

Wanaku passes this normalized result to the processor in `ctx.llmResult`:

```json
{
  "engine": "opa",
  "version": "wanaku.opa.result/v1",
  "decision_path": "wanaku/tool_call/decision",
  "allow": false,
  "reason": "amount_over_limit",
  "result": { "allow": false, "reason": "amount_over_limit" }
}
```

The `result` member contains the complete OPA result. The processor makes the final action. The sample `opa_allow_action.wasm` passes only when `allow` is `true`. Otherwise it blocks the request with the message `denied by policy: <reason>`.

### Failures

An explicit denial from the policy is a decision. It is not a failure. Wanaku records it with the reason code `evaluator_evaluated`.

These conditions are failures. They follow `on_failure`, and the processor does not run:

| Condition | Audit reason code |
|-----------|-------------------|
| OPA is not reachable | `evaluator_remote_unavailable` |
| OPA does not answer in `timeout_ms` | `evaluator_remote_timeout` |
| OPA returns HTTP 401 or 403 | `evaluator_remote_auth_failed` |
| OPA returns another error status, for example a policy evaluation error | `evaluator_policy_error` |
| The response has no `result` member, because the decision is undefined | `evaluator_decision_undefined` |
| The result is `null`, has an incorrect shape, is not JSON, or is larger than 1 MiB | `evaluator_decision_invalid` |
| The connection is not available at request time | `evaluator_connection_unavailable` |

The default `on_failure` value is `deny`.

### Operations

- OPA is an external engine. `audit` mode with `audit_level: basic` does not send queries to OPA. `audit_level: full` sends queries and does not apply the action. `disabled` mode does not send queries. See [Governance Posture](governance-posture.md).
- Wanaku queries OPA for each matching request. Policy and data changes in OPA, for example from OPA bundles, apply to the next request. You do not restart Wanaku and you do not create a new evaluator revision.
- Each decision audit event contains the `engine` attribute. For OPA, it also contains `decision_path`. The event also contains the outcome, the reason code, `duration_ms`, and the request correlation ID.
- The metrics store records the engine latency and the result for each evaluator.
- Set `RUST_LOG=wanaku_feature_evaluator=debug` to log the decision path, outcome, and latency of each OPA query.

For a runnable policy, Rego tests, and configuration, see [`examples/opa`](../../examples/opa/README.md).

## Next steps

- [Evaluators](evaluators.md): attach an engine to a trigger and a processor.
- [Gate tool calls with OPA](gate-calls-with-opa.md): run a complete OPA example.
- [Wanaku configuration file](../reference/wanaku-yaml.md): see where the connection sections go.
