# Action Policies

Action policies give Wanaku a deterministic authorization layer for MCP actions. They complement the evaluator. Use action policies for rules that depend on known names, labels, URIs, and structured request values. Use evaluators for contextual or semantic decisions.

## Configure a policy

Add one `action_policy` object to `wanaku.yaml`:

```yaml
action_policy:
  rules:
    - id: deny-production-delete
      description: Prevent destructive production calls
      effect: deny
      selectors:
        namespace: production
        operation: tools/call
        target_type: tool
        target_name:
          matcher: glob
          value: "delete-*"
        labels:
          risk: high
      predicates:
        - operator: equals
          pointer: /arguments/force
          value: true
      reason_code: destructive_action_denied
      message: This action is not permitted.
      metadata:
        owner: platform-security

    - id: allow-public-reports
      effect: allow
      selectors:
        operation: resources/read
        target_type: resource
        uri:
          matcher: prefix
          value: "https://reports.example/public/"
```

Wanaku validates and compiles the complete policy before activation. Wanaku rejects the revision if a rule is invalid. An invalid revision does not replace the last active revision.

Each rule uses these fields:

| Field | Required | Value |
| --- | --- | --- |
| `id` | Yes | A stable identifier. Start with a letter or digit. Then use letters, digits, `_`, `-`, `.`, `/`, or `:`. |
| `description` | No | Text for operators. |
| `effect` | Yes | `allow` or `deny`. |
| `selectors` | Yes | One or more action selectors. |
| `predicates` | No | A list of typed JSON Pointer predicates. |
| `reason_code` | No | A stable identifier for a denied action. |
| `message` | No | A safe message that Wanaku can return to the caller. |
| `metadata` | No | Operator metadata. Metadata keys use the identifier syntax. Values are JSON values. |

Unknown fields cause validation to fail. Rule IDs must be unique in one policy.

## Select actions

A rule can use these selectors:

| Selector | Value |
| --- | --- |
| `namespace` | The Wanaku namespace. |
| `operation` | `tools/call`, `resources/read`, or `prompts/get`. |
| `target_type` | `tool`, `resource`, or `prompt`. |
| `target_name` | An `exact` or `glob` match expression. |
| `labels` | Required registry label key-value pairs. |
| `uri` | An `exact` or `prefix` match expression for a resource URI. |

All selectors and all predicates in one rule use AND semantics. The rule matches only when every configured condition matches.

For `labels`, the configured labels must be a subset of the registry entry labels. Extra labels on the registry entry do not prevent a match. Prompt entries do not currently supply registry labels to the policy adapter.

### Match target names

Use `exact` to compare the complete target name:

```yaml
target_name: { matcher: exact, value: delete-account }
```

Use `glob` for target names. `*` matches zero or more characters. `?` matches one character. A backslash escapes the next character. For example, `report-\*` matches the literal name `report-*`. A trailing backslash is invalid.

URI selectors do not support glob matching. They support `exact` and literal `prefix` matching only.

### Match resource URIs

Wanaku compares the exact URI string from `resources/read`. Wanaku does not decode, resolve, or normalize the URI. Case, escaping, separators, and trailing slashes remain significant.

```yaml
uri:
  matcher: prefix
  value: "s3://company-private/"
```

A URI selector applies only to a resource read. Tool registry transport URIs are not policy resource URIs.

## Match structured request values

Each predicate reads the MCP `params` object with an RFC 6901 JSON Pointer. JSON types remain distinct. The string `"1"` does not equal the number `1`.

| Operator | Operand | Match condition |
| --- | --- | --- |
| `exists` | `value: true` or `false` | The pointer is present or absent. |
| `equals` | `value` | The present value equals the operand. |
| `not_equals` | `value` | The present value does not equal the operand. |
| `one_of` | Non-empty `values` | The present value equals one list value. |
| `not_one_of` | Non-empty `values` | The present value equals no list value. |

Example:

```yaml
predicates:
  - operator: exists
    pointer: /arguments/approved
    value: true
  - operator: one_of
    pointer: /arguments/environment
    values: [production, staging]
```

Missing is different from JSON `null`:

| Input at the pointer | `exists: true` | `exists: false` | `equals: null` | `not_equals: null` | `one_of: [null]` | `not_one_of: [null]` |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Missing | No | Yes | No | No | No | No |
| `null` | Yes | No | Yes | No | Yes | No |
| `false` | Yes | No | No | Yes | No | Yes |
| `0` | Yes | No | No | Yes | No | Yes |
| `""` | Yes | No | No | Yes | No | Yes |

A missing value never matches a comparison operator. Use `exists: false` to match an omitted value.

## Decision behavior

Wanaku evaluates all rules. The declaration order does not change the result.

1. If any matching rule has `effect: deny`, Wanaku denies the action.
2. If no deny matches and an allow matches, Wanaku records an explicit allow and continues the pipeline.
3. If no rule matches, Wanaku continues the pipeline. The baseline behavior belongs to [#1872](https://github.com/wanaku-ai/wanaku/issues/1872).

A static allow does not skip the evaluator or another downstream filter. A static deny returns a JSON-RPC error before the evaluator and upstream action handler run.

The caller receives one deterministic safe denial reason. Wanaku selects the matching deny rule with the lexicographically lowest rule ID. A configured `reason_code` and `message` take precedence. Otherwise, Wanaku uses `action_policy_denied` and `The requested action is not allowed.` The decision model retains all matching rule IDs and configured reason codes for authorized internal consumers.

An unconfigured policy continues governed requests. If no active last-known-good policy exists, an invalid configured policy rejects governed requests with `action_policy_invalid`. If an active policy exists, an invalid update or startup policy does not replace it. Wanaku continues to enforce the active last-known-good policy. These are temporary failure semantics. [#1872](https://github.com/wanaku-ai/wanaku/issues/1872) owns the final baseline and failure modes.

Wanaku governs `tools/call`, `resources/read`, and `prompts/get`. Wanaku does not filter `tools/list`, `resources/list`, or `prompts/list`. Discovery does not grant authorization.

## Pipeline position

Place `wanaku_action_policy` after MCP metadata, namespace resolution, and `wanaku_mcp_init`. Place it before `wanaku_evaluator`:

```yaml
- filter: mcp
- filter: wanaku_namespace
- filter: wanaku_mcp_init
- filter: wanaku_action_policy
- filter: wanaku_evaluator
```

The default pipeline already uses this order.

## Manage policy revisions

The management API uses a dedicated policy resource:

| Method | Path | Result |
| --- | --- | --- |
| `GET` | `/api/v1/action-policies` | Get the effective policy and active revision metadata. |
| `PUT` | `/api/v1/action-policies` | Validate and activate a policy. |
| `GET` | `/api/v1/action-policies/revisions` | List revision metadata, newest first. |
| `GET` | `/api/v1/action-policies/revisions/active` | Get the active revision and policy. |
| `GET` | `/api/v1/action-policies/revisions/{id}` | Get one revision and policy. |
| `POST` | `/api/v1/action-policies/revisions/{id}/activate` | Validate the selected policy and create a new active revision. |

Activate a policy:

```bash
curl -X PUT http://localhost:8080/api/v1/action-policies \
  -H 'Content-Type: application/json' \
  -d '{
    "policy": {
      "rules": [{
        "id": "deny-delete",
        "effect": "deny",
        "selectors": {"operation": "tools/call"}
      }]
    },
    "expected_revision": 4
  }'
```

`expected_revision` is optional. If it does not equal the active revision, Wanaku returns HTTP `409 Conflict`. A validation failure returns HTTP `422 Unprocessable Entity`. Wanaku records the invalid revision as rejected. It does not replace the active revision.

Rollback creates a new revision. It does not change historical data:

```bash
curl -X POST http://localhost:8080/api/v1/action-policies/revisions/2/activate \
  -H 'Content-Type: application/json' \
  -d '{"expected_revision": 4}'
```

Wanaku stores policy revisions in `action-policy-revisions.json` under `WANAKU_PERSIST_PATH` when file persistence is enabled. It writes the file after each revision change. The history contains at most 50 revisions. The policy revision stream is independent from `evaluator-revisions.json`.

At startup, Wanaku immediately validates and installs the persisted active policy. If `wanaku.yaml` contains a different valid policy, Wanaku activates a new startup revision. If the policy is unchanged, Wanaku does not create a duplicate revision. An invalid startup policy does not replace a persisted last-known-good policy.

## Deferred work

Governance audit events and external decision evidence belong to [#1869](https://github.com/wanaku-ai/wanaku/issues/1869). Action-policy decisions already retain rule IDs and reason codes for that integration. Wanaku does not copy structured request values into management responses.

Agent-to-agent action adapters belong to [#383](https://github.com/wanaku-ai/wanaku/issues/383). The engine and action context are transport-neutral so that work can reuse the current rule semantics.
