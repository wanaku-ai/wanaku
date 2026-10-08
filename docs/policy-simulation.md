# Policy Simulation

A policy update can block a working tool or permit an action that an operator intended to deny. Simulation gives reviewers decision evidence before activation. Use it to validate a candidate, test one MCP action, or compare a bounded action set with the active configuration.

Simulation does not activate or persist a candidate as policy. It does not forward MCP requests, resolve credentials, change registry entries, or change namespace bindings. A successful report does not authorize activation. The activation API validates the configuration again.

## Select a candidate

Each request accepts a `candidate` object. An empty object selects the active action policy and evaluator configuration. The two configurations have separate revision numbers. Revision `0` identifies the initial configuration when no stored active revision exists.

| Field | Purpose |
| --- | --- |
| `policy_revision` | Select a stored action-policy revision. |
| `evaluator_revision` | Select a stored evaluator revision. |
| `inline_policy` | Supply a transient action-policy object. |
| `inline_evaluators` | Supply a transient evaluator configuration. |
| `base_policy_revision` | Identify the base for an inline policy or policy patch. |
| `base_evaluator_revision` | Identify the base for inline evaluators or an evaluator patch. |
| `policy_patch` | Apply a JSON Merge Patch to the specified policy base. |
| `evaluator_patch` | Apply a JSON Merge Patch to the specified evaluator base. |
| `evidence_ids` | Identify evidence for replay comparison. |

Use one source for each configuration. Specify the corresponding base revision for an inline candidate or patch. A merge patch replaces arrays. A `null` patch member removes that member.

Use the action-policy schema from [Action Policies](action-policies.md). Use the evaluator schema from [Evaluator Engine](evaluator-engine.md). Do not include connection credentials in a candidate.

## Validate without activation

Validate the active configuration:

```bash
curl --fail-with-body -sS \
  -X POST http://localhost:8080/api/v1/policy-simulations/validate \
  -H 'Content-Type: application/json' \
  -d '{"candidate":{}}'
```

The standard management envelope contains a report with `schema_version: "1.0"` in `data`. Validation returns `200` for a well-formed request, including a candidate with validation errors. Single-action and replay requests return `422` when the candidate is invalid. The report includes `valid`, `stale`, `identity`, and `diagnostics`. Each diagnostic has a `severity`, a stable `reason_code`, and a configuration `path`. Errors make `valid` false. Warnings identify risks that do not make the configuration invalid.

Validation compiles deterministic policy selectors, predicates, evaluator schemas, and referenced WASM processors. It checks available namespace, capability, label, and credential-binding metadata. It does not resolve a binding or contact an upstream service. Validation reports safely discoverable diagnostics together. Some configuration errors prevent subsequent dependent checks.

## Simulate one action

The action contains a namespace and a JSON-RPC request. The namespace defaults to `default`. Supported methods are `tools/call`, `resources/read`, and `prompts/get`.

```bash
curl --fail-with-body -sS \
  -X POST http://localhost:8080/api/v1/policy-simulations/simulate \
  -H 'Content-Type: application/json' \
  -d '{
    "candidate": {},
    "action": {
      "namespace": "default",
      "request": {
        "jsonrpc": "2.0",
        "id": 1,
        "method": "tools/call",
        "params": {"name": "get_weather", "arguments": {"city": "Prague"}}
      }
    }
  }'
```

A simulation report includes policy and evaluator identity, checksums for candidates, normalized operation, safe target metadata, effective posture, stage decisions, matching identifiers, stable reason codes, final decision, baseline and failure behavior use, redaction metadata, and duration. The report does not contain the request body or proposed response body.

The simulator uses the production MCP parser and policy adapter. Registry metadata supplies the same labels and target state that the production policy adapter uses.

### Evaluator isolation

Simulation does not call external LLM providers. A matching LLM evaluator has an explicit skipped stage. When that evaluator can change the outcome, the report sets `conditional` to `true`. Treat a conditional result as incomplete evidence.

`allow_external_evaluators` defaults to `false`. This implementation rejects `true`. It does not offer nondeterministic external execution.

Only passthrough WASM processors execute during simulation. The component import allowlist rejects blocking WASI capabilities and unsupported imports with `evaluator_processor_capability_unsupported`. Passthrough WASM processors run with read-only registry capabilities. The host records proposed actions instead of applying them. Registry writes fail with a simulation reason. Suppressed writes and conversation history reads make enforce-mode results conditional because live host data can change guest behavior. The host does not resolve credentials or forward requests. Simulation does not write proposed decisions into production audit history.

When evaluator definitions exactly match the active runtime, simulation reuses its compiled processor artifacts. For historical or new definitions, simulation reads the processor files again. A change to a file can therefore change a historical simulation result. Preserve processor artifacts when you need reproducible revision comparisons.

## Replay and compare

Replay runs as an asynchronous management job. It captures the active configuration and compares each action with the selected candidate. Use an explicit action set or select redacted audit events by ID. The simulator does not collect conversation interaction bodies automatically.

Create `replay.json`:

```json
{
  "candidate": {},
  "actions": [
    {
      "namespace": "default",
      "request": {
        "jsonrpc": "2.0",
        "id": 1,
        "method": "prompts/get",
        "params": {"name": "summary", "arguments": {}}
      }
    }
  ]
}
```

Start the job:

```bash
curl --fail-with-body -sS \
  -X POST http://localhost:8080/api/v1/policy-simulations/replays \
  -H 'Content-Type: application/json' \
  --data-binary @replay.json > replay-start.json
replay_id=$(jq -er '.data.id' replay-start.json)
```

Get the report:

```bash
curl --fail-with-body -sS \
  "http://localhost:8080/api/v1/policy-simulations/replays/$replay_id" \
  > replay-result.json
```

The report contains job status, policy identity, staleness, aggregate counts, bounded per-action details, skipped audit IDs, a safe failure reason, and duration. Each detail contains active and candidate reports.

| Category | Meaning |
| --- | --- |
| `allow_to_allow` | Both configurations permit the action. |
| `allow_to_deny` | The candidate blocks a previously permitted action. |
| `deny_to_allow` | The candidate permits a previously blocked action. |
| `deny_to_deny` | Both configurations block the action. |
| `no_match_changed` | Both decisions allow, but only one uses the no-match baseline. |
| `error_or_indeterminate` | An error or conditional stage prevents a definite comparison. |

`access_expansion` highlights `deny_to_allow`. `compatibility_impact` highlights `allow_to_deny`. Review both categories before activation.

### Explicit audit input

Set `audit_event_ids` to the IDs of events that you select for replay:

```json
{
  "candidate": {},
  "audit_event_ids": ["selected-audit-event-id"]
}
```

Replay requires a usable MCP request payload from a decision event. The event must have passed audit redaction without field replacement or truncation. Events with redacted fields cannot supply complete decision evidence. Audit payload capture is disabled by default. Events without a usable payload produce explicit skipped-event evidence. Their absence does not prove that the candidate preserves access. See [Governance Audit Trail](audit-trail.md) for payload capture and redaction settings.

### Recommendation evidence

Set `evidence_ids` in the candidate. Set `evidence_id` on a synthetic action that supplies supporting evidence. Replay marks actions outside that evidence with `outside_evidence`. Check the category to determine whether their decisions changed.

The recommendation service from #1873 is not implemented by this feature. A caller can submit its proposed merge patch and evidence IDs for review. Simulation does not approve a recommendation or apply its patch. A finite action set does not prove that all other actions retain their previous decisions.

## Check a candidate in CI

Write the validation request to `candidate.json`. Include the base revisions for inline configurations or patches.

```bash
curl --fail-with-body -sS \
  -X POST http://localhost:8080/api/v1/policy-simulations/validate \
  -H 'Content-Type: application/json' \
  --data-binary @candidate.json > validation.json
jq -e '.data.valid == true and .data.stale == false' validation.json
```

To reject validation warnings as well:

```bash
jq -e '.data.valid == true and .data.stale == false and
  ([.data.diagnostics[] | select(.severity == "warning")] | length == 0)' \
  validation.json
```

Poll the replay route until the job reaches a terminal status. Set a CI timeout. Check the complete report before activation. For example, reject access expansion and incomplete comparisons:

```bash
jq -e '.data.status == "completed" and .data.stale == false and
  ((.data.counts.deny_to_allow // 0) == 0) and
  ((.data.counts.error_or_indeterminate // 0) == 0) and
  (.data.skipped_audit_ids | length == 0)' replay-result.json
```

Review compatibility changes separately. This command does not reject `allow_to_deny`.

## Staleness, cancellation, and retention

A report identifies the action-policy and evaluator revisions that it tested. A change to the active revision makes the report stale. Check `stale` when you retrieve a replay report. Run a new comparison after an active revision change.

A job has one of these statuses: `running`, `completed`, `cancelled`, or `failed`. Cancellation stops replay between actions. It does not interrupt an action that has already started.

Request cancellation:

```bash
curl --fail-with-body -sS -X POST \
  "http://localhost:8080/api/v1/policy-simulations/replays/$replay_id/cancel"
```

Deleting a running job also requests cancellation. Delete a retained job:

```bash
curl --fail-with-body -sS -X DELETE \
  "http://localhost:8080/api/v1/policy-simulations/replays/$replay_id"
```

Results use bounded in-memory storage. A server restart removes them. The server checks retention expiry when a caller starts or retrieves a job. Expiry removes retained results. A completed job starts a new retention interval. Expired or deleted IDs return `404`.

## Resource limits

Use the top-level `simulation` section in `wanaku.yaml`:

```yaml
simulation:
  max_candidate_bytes: 262144
  max_request_bytes: 1048576
  max_actions: 1000
  max_concurrent_jobs: 2
  max_stored_jobs: 50
  max_result_bytes: 4194304
  max_duration_ms: 30000
  retention_seconds: 3600
  max_registry_entries: 10000
  max_rules: 1000
  max_evaluators: 32
  wasm_fuel: 10000000
  wasm_memory_bytes: 67108864
  wasm_timeout_ms: 1000
```

These values are the defaults. Request size includes the complete JSON body. Candidate size measures the candidate JSON. `max_actions` bounds the replay action set. `max_concurrent_jobs` provides one shared admission limit for validation, single-action simulation, replay preparation, and running replay jobs. The server returns `429` when all permits are in use. `max_stored_jobs` bounds retained jobs. `max_result_bytes` bounds the report. `max_duration_ms` bounds request preparation and execution, and replay duration. The request returns `408` with `simulation_duration_limit` when its time budget expires. `retention_seconds` bounds result retention. `max_registry_entries` bounds registry snapshots. `max_rules` and `max_evaluators` bound candidate definitions.

`wasm_fuel`, `wasm_memory_bytes`, and `wasm_timeout_ms` bound each simulated processor execution. Simulation does not enable external evaluator calls. Wanaku validates the limits at startup. Invalid limits stop startup. Preparation and execution run on blocking workers outside the proxy request path. Request, candidate, registry snapshot, and synchronous report size failures return `413`. Limit failures use stable reason codes. They do not change production enforcement.

## Administrative audit

Simulation records administrative activity with safe policy identity and operation metadata. These events describe simulation activity. They do not represent production enforcement decisions. They do not contain candidate bodies, action bodies, secret values, or resolved credentials. Wanaku redacts report metadata, including diagnostic paths, matching identifiers, and policy identity, before it returns a report.

## Related documentation

- [Management API](management-api.md#policy-simulation)
- [Configuration](configuration.md)
- [Action Policies](action-policies.md)
- [Evaluator Engine](evaluator-engine.md)
- [Governance Posture](governance-posture.md)
