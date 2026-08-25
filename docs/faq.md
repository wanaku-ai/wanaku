# FAQ and Troubleshooting

Common issues and solutions when running Wanaku.

## "Address already in use" on startup

Something else is using port 8081 or 8080. Check with:

```bash
lsof -ti :8081
lsof -ti :8080
```

Kill the process, or change the management listen address via `WANAKU_MGMT_LISTEN`. The MCP listener address is defined in the pipeline config — use a custom config file with `--pipeline-config` to override it.

## Empty JSON-RPC response from MCP endpoint

Check that:
- The MCP filter has `on_invalid: continue` in the pipeline config
- You send valid JSON-RPC with `"jsonrpc": "2.0"`, `"method"`, and `"id"`.
- The URL path matches `/{namespace}/mcp`. Use `/default/mcp` for the default namespace.

Enable trace logs to see what the filters are doing:

```bash
RUST_LOG=wanaku_filters=trace wanaku-server
```

## Tools do not appear in `/default/mcp` but appear in `wanaku tools list`

Check the namespace. Tools registered with `namespace: "finance"` appear only in `/finance/mcp`. They do not appear in `/default/mcp`.

```bash
# List tools in the default namespace
wanaku tools list --no-auth

# Query the finance namespace via MCP
curl -X POST http://localhost:8081/finance/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc": "2.0", "method": "tools/list", "id": 1}'
```

## Tool calls time out

MCP forward calls have a connection timeout. If the upstream MCP server takes too long, verify that it is reachable and responds:

```bash
curl -s http://your-upstream-mcp:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc": "2.0", "method": "tools/list", "id": 1}'
```

## LLM-based features (evaluators, inference proxy) do not work

Verify:
- The LLM endpoint is reachable (`curl http://localhost:11434/v1/models`)
- `WANAKU_INFERENCE_UPSTREAM` is set correctly for the inference proxy (port 8083)
- Evaluator LLM connections are defined under `llm_connections` in `wanaku.yaml`

## Server crashes with "thread 'main' panicked"

Wanaku denies `unsafe_code`, `unwrap_used`, `expect_used`, and `panic` at the crate level. A panic means a logic bug. File an issue at [github.com/wanaku-ai/wanaku/issues](https://github.com/wanaku-ai/wanaku/issues) with the stack trace and steps to reproduce.

## CLI returns authentication errors

When running against a Wanaku server without authentication enabled, use the `--no-auth` flag:

```bash
wanaku tools list --no-auth
```

When authentication is enabled, authenticate first or pass a token:

```bash
# Pass a token directly
wanaku tools list --host http://localhost:4181 --token $TOKEN

# Or use the CLI's built-in auth
wanaku auth login --api-token <your-token>
wanaku tools list
```

## Registry data lost on restart

File persistence is enabled by default. Wanaku uses `$HOME/.wanaku/server/registry.json`. Set a different path when the default location is not suitable:

```bash
export WANAKU_PERSIST_BACKEND=file
export WANAKU_PERSIST_PATH=/data/registry
wanaku-server
```

On startup, the server loads `registry.json` from `WANAKU_PERSIST_PATH`. On shutdown (SIGTERM, SIGINT), it writes back.

> **Note:** Wanaku writes the snapshot during an orderly shutdown. If the process stops because of SIGKILL or an out-of-memory error, changes since the last snapshot are lost. File persistence supports one writer.

To disable persistence, set `WANAKU_PERSIST_BACKEND=none`.

## Debugging

Enable trace logs to see all filter decisions, metadata reads/writes, and registry operations:

```bash
RUST_LOG=trace wanaku-server
```

For filter-specific logs only:

```bash
RUST_LOG=wanaku_filters=trace wanaku-server
```
