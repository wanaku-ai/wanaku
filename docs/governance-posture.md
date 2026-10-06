# Governance Posture

Governance posture defines how Wanaku handles policy decisions, unmatched actions, and evaluation failures. The model keeps these controls separate. This separation makes the effective behavior explicit for each namespace.

> [!IMPORTANT]
> Wanaku applies the same posture to action policies and evaluators. Each filter applies the no-match baseline independently. An action-policy allow does not bypass evaluator governance.

The implicit evaluator baseline applies to `tools/call`, `resources/read`, and `prompts/get`. Discovery and protocol methods continue when no evaluator matches them. An explicit evaluator can still govern a discovery method such as `tools/list`. A missing or invalid evaluator runtime does not apply the implicit baseline to discovery methods.

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

The decision model keeps an explicit allow separate from an allow that comes from the no-match behavior. Evaluator audit events and metrics record this distinction.

### Failure behavior

`allow` permits an action when governance cannot produce a decision.

`deny` rejects an action when governance cannot produce a decision. This value is the default.

The failure behavior applies to unavailable or invalid action-policy state and missing runtime state. It also applies to evaluator engine, schema, processor, WASM, registry, and internal failures.

## Global posture and namespace overrides

The model contains one global posture and a map of namespace overrides. An override changes only the fields that it specifies. All other fields inherit the global value.

Namespace resolution uses the namespace name as the map key. It does not use declaration order. One request resolves one posture value for its namespace.

Add the `governance` section to `wanaku.yaml`:

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

Wanaku validates this configuration during startup. Wanaku stops startup if the configuration is invalid. If the section is absent, Wanaku uses the fail-safe defaults.

## Action-policy behavior

The action-policy filter applies the effective namespace posture as follows:

| Condition | `enforce` | `audit` | `disabled` |
| --- | --- | --- | --- |
| Explicit allow | Allow | Record and allow | Skip evaluation |
| Explicit deny | Deny | Record and allow | Skip evaluation |
| No matching rule | Apply `no_match` | Record and allow | Skip evaluation |
| Invalid or unavailable policy | Apply `on_failure` | Record and allow | Skip evaluation |

Basic and full audit levels have the same result for deterministic action policies. Action-policy evaluation does not call an LLM and does not have evaluator side effects.

## Evaluator behavior

Wanaku resolves the posture before it calls an evaluation engine or creates WASM host state. One request uses one evaluator configuration revision.

| Mode | Engine execution | Traffic and registry effects |
| --- | --- | --- |
| `enforce` | Run the matching evaluator | Apply its action. Apply `on_failure` if evaluation fails. |
| `audit`, `basic` | Skip LLM, TypeSafe System One, and OPA calls. Local passthrough processors can run. | Continue without traffic changes or registry writes. |
| `audit`, `full` | Run the matching evaluator, including external calls | Continue without traffic changes or registry writes. |
| `disabled` | Skip evaluation | Continue. Record the disabled state and reason. |

Basic audit records skipped external evaluation as `not_evaluated`. It does not report an invented would-be decision. Full audit is an explicit opt-in for external cost and latency.

In audit mode, the `copy-tool-to-namespace` host function returns `false` without a registry write. Registry reads return the existing data. Wanaku does not simulate writes or subsequent reads of those writes. Processor response actions are recorded but do not change traffic or metadata. WASM execution has a fuel limit of 10 million units per invocation. Fuel exhaustion follows the failure policy.

The evaluator `on_error` field is removed. Configuration with this field is rejected. Set `governance.default.on_failure` or a namespace override instead. The default is `deny`.

Wanaku 0.3.0 is unreleased. Earlier pre-release evaluator configurations and persisted revisions are not migrated automatically. See [Update an earlier pre-release configuration](configuration.md#update-an-earlier-pre-release-configuration) before you update a pre-release build.

## Evaluator readiness

Use `GET /api/v1/evaluators/status?namespace=default` to read the effective posture and runtime status. The response includes a safe reason code and the active runtime revision when available.

| State | Meaning |
| --- | --- |
| `ready` | The configured evaluators are available. |
| `degraded` | An evaluator reported an operational failure. |
| `invalid` | The configured runtime could not be loaded or accessed. |
| `unconfigured` | No evaluator is configured for the namespace. |
| `disabled` | The effective posture disables evaluation. |

A successful evaluation clears the failure state for that evaluator. A new active revision starts with its own runtime status. Wildcard evaluators share their runtime status across namespaces.

Rejected management updates do not replace the working configuration. Invalid startup configuration stops the server by default. To start with an invalid evaluator runtime, set the top-level `evaluator_startup_failure: deny` option. In this mode, invalid runtime state blocks governed requests in enforce mode. Audit mode continues to observe traffic. Disabled mode skips evaluation.

## Evaluator observability

Evaluator audit events include the enforcement mode, effective action, would-be action when available, evaluation status, failure reason, and configuration revision. Events identify suppressed host writes. Raw engine errors are not used as public reason codes.

`GET /api/v1/metrics` includes `evaluator_governance` counters. These counters use fixed mode and outcome values. They record enforcement, audit, no-match, fail-open, fail-closed, skipped evaluation, and disabled outcomes. Namespace names, evaluator names, and request identifiers are not dimensions of these counters.

For audit mode, no-match and failure counter outcomes describe the configured would-be result. The effective request action remains allow.
