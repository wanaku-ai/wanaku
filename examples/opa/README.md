# OPA Tool-Call Policy Example

This example shows Wanaku tool-call governance with Open Policy Agent (OPA). A Rego policy decides each `tools/call` request in the `finance` namespace.

## Files

| File | Purpose |
|------|---------|
| `policy.rego` | Policy in package `wanaku.tool_call`. It has a boolean `allow` decision and a structured `decision`. |
| `data.json` | Policy data: amount limits and blocked tools for each namespace. |
| `policy_test.rego` | Rego unit tests. |
| `wanaku.yaml` | Wanaku configuration with an OPA connection and an OPA evaluator. |

The processor is `actions/dist/opa_allow_action.wasm`. Its source is in [`actions/opa-allow`](../../actions/opa-allow/src/lib.rs).

## Run the Example

Follow the [Gate tool calls with OPA](../../docs/govern/gate-calls-with-opa.md) tutorial. It describes the policy rules, the prerequisites, the tests, and how to run the example and change the policy at runtime.
