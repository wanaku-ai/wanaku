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
- You're sending valid JSON-RPC (must have `"jsonrpc": "2.0"`, `"method"`, and `"id"`)
- The URL path matches a configured namespace (`/mcp` -> `"default"`, `/{namespace}/mcp` -> `"{namespace}"`)

Enable trace logs to see what the filters are doing:

```bash
RUST_LOG=wanaku_filters=trace wanaku-server
```

## Tools don't appear in `/mcp` but show up via `wanaku tools list`

Check the namespace. Tools registered with `namespace: "finance"` only appear in `/finance/mcp`, not `/mcp`.

```bash
# List tools in the default namespace
wanaku tools list --no-auth

# Query the finance namespace via MCP
curl -X POST http://localhost:8081/finance/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc": "2.0", "method": "tools/list", "id": 1}'
```

## Tool calls time out

MCP forward calls have a connection timeout. If your upstream MCP server takes too long, verify it's reachable and responding:

```bash
curl -s http://your-upstream-mcp:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc": "2.0", "method": "tools/list", "id": 1}'
```

## LLM-based features (chat) don't work

Verify:
- The LLM endpoint is reachable (`curl http://localhost:11434/v1/models`)
- Environment variables are set correctly (`WANAKU_INFERENCE_UPSTREAM`)
- The feature is configured via the management API or `wanaku.yaml`

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

By default, the registry lives in RAM and is lost on restart. Enable file persistence to survive restarts:

```bash
export WANAKU_PERSIST_BACKEND=file
export WANAKU_PERSIST_PATH=/data/registry
wanaku-server
```

On startup, the server loads `registry.json` from `WANAKU_PERSIST_PATH`. On shutdown (SIGTERM, SIGINT), it writes back.

> **Note:** If the server crashes (SIGKILL, OOM), the registry is lost. For production, consider implementing a custom persistence backend.

## Debugging

Enable trace logs to see all filter decisions, metadata reads/writes, and registry operations:

```bash
RUST_LOG=trace wanaku-server
```

For filter-specific logs only:

```bash
RUST_LOG=wanaku_filters=trace wanaku-server
```
