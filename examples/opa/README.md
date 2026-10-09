# OPA Tool-Call Policy Example

This example shows Wanaku tool-call governance with Open Policy Agent (OPA). A Rego policy decides each `tools/call` request in the `finance` namespace. Wanaku sends the request to the backend only when the policy allows it. No LLM connection is necessary.

## Files

| File | Purpose |
|------|---------|
| `policy.rego` | Policy in package `wanaku.tool_call`. It has a boolean `allow` decision and a structured `decision`. |
| `data.json` | Policy data: amount limits and blocked tools for each namespace. |
| `policy_test.rego` | Rego unit tests. |
| `wanaku.yaml` | Wanaku configuration with an OPA connection and an OPA evaluator. |

The processor is `actions/dist/opa_allow_action.wasm`. Its source is in [`actions/opa-allow`](../../actions/opa-allow/src/lib.rs).

## Policy Rules

The policy denies a request when one of these conditions is true:

- The input version is not `wanaku.opa.input/v1`.
- The method is not `tools/call`.
- The tool is in `blocked_tools` for the namespace.
- The tool has an amount limit and `arguments.amount` is not a JSON number.
- The tool has an amount limit and `arguments.amount` is larger than the limit.

## Prerequisites

- OPA 1.x. This example is tested with OPA 1.21.
- `cargo-component` and the `wasm32-wasip1` target to build the processor.
- An MCP server for the `finance` namespace at `http://127.0.0.1:9090/mcp`. It must provide a `transfer` tool.

## Test the Policy

Run the Rego tests from the repository root:

```bash
opa test -v examples/opa
```

## Run the Example

1. Build the processor:

   ```bash
   mkdir -p actions/dist
   (cd actions/opa-allow && cargo component build --release)
   cp actions/opa-allow/target/wasm32-wasip1/release/opa_allow_action.wasm actions/dist/
   ```

2. Start OPA with the policy and the data:

   ```bash
   opa run --server --addr 127.0.0.1:8181 examples/opa/policy.rego examples/opa/data.json
   ```

3. Start Wanaku with the example configuration:

   ```bash
   cargo run -- --wanaku-config examples/opa/wanaku.yaml
   ```

4. Send a transfer that is below the limit. Wanaku sends it to the backend:

   ```bash
   curl -s http://127.0.0.1:8081/finance/mcp \
     -H 'content-type: application/json' -H 'accept: application/json, text/event-stream' \
     -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"transfer","arguments":{"amount":250}}}'
   ```

5. Send a transfer that is above the limit. Wanaku blocks it, and the backend does not receive it:

   ```bash
   curl -s http://127.0.0.1:8081/finance/mcp \
     -H 'content-type: application/json' -H 'accept: application/json, text/event-stream' \
     -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"transfer","arguments":{"amount":5000}}}'
   ```

   The response contains `blocked by evaluator tool-policy-gate: denied by policy: amount_over_limit`.

## Change the Policy at Runtime

Change the limit in OPA. Wanaku uses the new limit on the next request. You do not restart Wanaku.

```bash
curl -s -X PUT http://127.0.0.1:8181/v1/data/policy/amount_limits/finance/transfer -d '10000'
```

Send the request from step 5 again. The policy allows it now.

In production, use OPA bundles to distribute policies and data separately from Wanaku evaluator revisions.

## Use the Boolean Decision

To use the boolean decision, set `decision_path: "wanaku/tool_call/allow"`. The processor then receives `"reason": null` when the policy denies a request.
