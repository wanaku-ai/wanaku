# Governance Posture

Governance posture defines how Wanaku handles policy decisions, unmatched actions, and evaluation failures. The model keeps these controls separate. This separation makes the effective behavior explicit for each namespace.

> [!IMPORTANT]
> The current implementation provides the shared posture types, default values, namespace resolution, and configuration validation. It does not load this model from `wanaku.yaml` or apply it in the action-policy and evaluator filters yet. Do not use the example configuration in production until runtime integration is complete.

## Posture settings

Each posture has four independent settings.

| Setting | Values | Default | Purpose |
| --- | --- | --- | --- |
| `mode` | `enforce`, `audit`, `disabled` | `enforce` | Controls whether governance decisions affect traffic. |
| `no_match` | `allow`, `deny` | `deny` | Controls the result when no policy or evaluator matches an action. |
| `on_failure` | `allow`, `deny` | `deny` | Controls the result when governance cannot produce a decision. |
| `audit_level` | `basic`, `full` | `basic` | Controls whether audit mode calls an LLM. |

The defaults are fail-safe. Wanaku denies an unmatched action or a failed governance decision unless the operator explicitly selects `allow`.

### Enforcement modes

`enforce` applies governance decisions to traffic.

`audit` observes traffic without enforcement. The `basic` audit level does not call an LLM. The `full` audit level permits LLM calls so that Wanaku can produce complete would-be decisions. Full audit is an explicit opt-in because it adds LLM cost and latency.

`disabled` intentionally bypasses governance for the applicable scope. A disabled posture must include a non-empty `disabled_reason`. Validation rejects a disabled posture without this reason.

### No-match behavior

`allow` permits an action when no rule or evaluator matches it.

`deny` rejects an action when no rule or evaluator matches it. This value is the default.

The decision model keeps an explicit allow separate from an allow that comes from the no-match behavior. Audit events and metrics can use this distinction after runtime integration is complete.

### Failure behavior

`allow` permits an action when governance cannot produce a decision.

`deny` rejects an action when governance cannot produce a decision. This value is the default.

The failure behavior is for unavailable or invalid policy state and evaluation failures. Runtime integration will apply it to LLM, schema, processor, WASM, registry, and internal failures.

## Global posture and namespace overrides

The model contains one global posture and a map of namespace overrides. An override changes only the fields that it specifies. All other fields inherit the global value.

Namespace resolution uses the namespace name as the map key. It does not use declaration order. One request resolves one posture value for its namespace.

The following example shows the configuration shape that the shared types accept:

```yaml
governance:
  default:
    mode: enforce
    no_match: deny
    on_failure: deny
    audit_level: basic

  namespaces:
    sandbox:
      mode: audit
      no_match: allow
      audit_level: full

    maintenance:
      mode: disabled
      disabled_reason: Scheduled maintenance window
```

The effective `sandbox` posture uses `on_failure: deny` from the global posture. The effective `maintenance` posture inherits the global no-match, failure, and audit settings.

Unknown fields cause deserialization to fail. An empty namespace name causes validation to fail.

## Current implementation boundary

The shared model is in `types/src/governance.rs`. It provides:

- Serializable posture enums and structures.
- Fail-safe default values.
- Global-to-namespace resolution.
- Validation for namespace names and disabled-scope reasons.
- OpenAPI schema derivation when the `openapi` feature is enabled.

The following work remains for complete runtime support:

- Load and validate `governance` from `wanaku.yaml`.
- Capture the effective posture in an immutable request snapshot.
- Apply the posture in the action-policy and evaluator filters.
- Suppress evaluator side effects in audit mode.
- Add readiness status, audit events, and bounded metrics.

See [issue #1872](https://github.com/wanaku-ai/wanaku/issues/1872) for the complete acceptance criteria.

