# Governed MCP Server Example

This example shows a complete `wanaku.yaml` for one upstream MCP server. It combines a forward, a credential binding, an LLM connection, an evaluator, action-policy rules, and governance.

## Files

| File | Purpose |
|------|---------|
| `wanaku.yaml` | Wanaku configuration with a forward, a bearer binding, an LLM connection, an LLM evaluator, action-policy rules, and governance. |

The processor is `actions/dist/safety_review_action.wasm`. Its source is in [`actions/safety-review`](../../actions/safety-review/src/lib.rs).

## Run the Example

Follow the [Govern an MCP server](../../docs/govern/govern-an-mcp-server.md) tutorial. It describes the prerequisites, what the configuration does, and how to run and adapt the example.
