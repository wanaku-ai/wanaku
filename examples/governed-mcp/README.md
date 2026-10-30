# Governed MCP Server Example

This example shows a complete `wanaku.yaml` for one upstream MCP server. It combines the sections that a governed deployment uses: a forward, a credential binding, an LLM connection, an evaluator, action-policy rules, and governance. Each section in [`wanaku.yaml`](wanaku.yaml) has comments that explain how it depends on the other sections.

## Files

| File | Purpose |
|------|---------|
| `wanaku.yaml` | Wanaku configuration with a forward, a bearer binding, an LLM connection, an LLM evaluator, action-policy rules, and governance. |

The processor is `actions/dist/safety_review_action.wasm`. Its source is in [`actions/safety-review`](../../actions/safety-review/src/lib.rs).

## What the Configuration Does

Agents connect to `http://localhost:8081/tickets/mcp`. For a `tools/call` request in the `tickets` namespace:

1. The action-policy filter denies tools whose names start with `delete-`. It allows all other tool calls with an explicit allow.
2. The evaluator filter runs `tickets-safety-gate`. The LLM classifies the call, and the processor blocks `red` calls.
3. Wanaku injects the bearer token from `TICKETS_TOKEN` and forwards the call to the upstream server.

The governance posture keeps the fail-safe defaults: `no_match: deny` and `on_failure: deny`. The action-policy filter and the evaluator filter apply `no_match` independently. A governed request must pass both filters:

| Request in `tickets` | Action policy | Evaluator | Result |
|----------------------|---------------|-----------|--------|
| `tools/call` for `list-tickets` | Explicit allow | `tickets-safety-gate` runs | Allowed if the processor passes it |
| `tools/call` for `delete-ticket` | Explicit deny | Does not run | Denied with `-32003` |
| `resources/read` or `prompts/get` | No match, denied | Does not run | Denied with `-32003` |

If the LLM is not reachable, the evaluator fails. With `on_failure: deny`, Wanaku denies the call.

A namespace override with `no_match: allow` removes the baseline in both filters. It also opens `resources/read` and `prompts/get` in that namespace, because no rule or evaluator governs them. Prefer explicit rules and evaluators for each method that agents use.

The `sandbox` namespace uses `mode: audit`. Wanaku records the governance decisions there and lets every request continue.

## Prerequisites

- An MCP server at `http://127.0.0.1:3000/mcp` that accepts a bearer token.
- An OpenAI-compatible LLM endpoint. The example uses Ollama at `http://localhost:11434/v1` with the `llama3.2` model.
- `cargo-component` and the `wasm32-wasip1` target to build the processor.

## Run the Example

1. Build the processor:

   ```bash
   mkdir -p actions/dist
   (cd actions/safety-review && cargo component build --release)
   cp actions/safety-review/target/wasm32-wasip1/release/safety_review_action.wasm actions/dist/
   ```

2. Set the upstream token in the server environment:

   ```bash
   export TICKETS_TOKEN=<token>
   ```

3. Start Wanaku from the repository root with the example configuration:

   ```bash
   cargo run -- --wanaku-config examples/governed-mcp/wanaku.yaml
   ```

4. List the tools:

   ```bash
   curl -s -X POST http://localhost:8081/tickets/mcp \
     -H "Content-Type: application/json" \
     -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'
   ```

## Adapt the Example

- Change the forward `address`, the binding `origin`, and the namespace for your server. The origin keeps only the scheme, host, and port.
- Replace the evaluator with an OPA evaluator or a passthrough evaluator. See [Evaluator Engine](../../docs/govern/evaluators.md) and [`examples/opa`](../opa/README.md).
- Remove the `llm_connections` entry if no evaluator uses it. Wanaku resolves every connection at startup.

## Related Docs

- [Configuration](../../docs/reference/wanaku-yaml.md)
- [Governance Posture](../../docs/govern/governance-posture.md)
- [Action Policies](../../docs/govern/action-policies.md)
- [Credential Brokerage](../../docs/govern/credential-brokerage.md)
