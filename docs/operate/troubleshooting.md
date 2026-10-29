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
wanaku tools list --host http://localhost:4180 --token $TOKEN

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

On startup, the server loads `registry.json` from `WANAKU_PERSIST_PATH`. After each registry change, a background writer saves a full snapshot. On an orderly shutdown (SIGTERM, SIGINT), the server waits up to 10 seconds for a pending snapshot.

> **Note:** If the process stops because of SIGKILL or an out-of-memory error, changes since the latest completed snapshot are lost. File persistence supports one writer. See [Registry Persistence](../reference/configuration.md#registry-persistence).

To disable persistence, set `WANAKU_PERSIST_BACKEND=none`.

## Server does not start

The server prints `fatal: <reason>` and stops. These are common reasons:

| Message or log | Cause | Action |
|---|---|---|
| `invalid governance configuration` | The `governance` section has an unknown key or a value that is not valid. | Correct the section. See [Governance Posture](../govern/governance-posture.md). |
| `evaluator startup failed: configuration_invalid` | An evaluator definition is not valid. For example, it uses the removed `llm:` key instead of `engine:`, names an unknown connection, or has a `processor.path` file that does not exist or does not compile. A relative path resolves against the working directory. | Read the `startup evaluator configuration rejected` or `failed to parse evaluators config` log line for the details. Correct the evaluator. In a source tree, build the example actions into `actions/dist/`. See [Evaluator Engine](../govern/evaluators.md#yaml-configuration). |
| `evaluator startup failed: connection_configuration_invalid` | A connection entry is not valid, or an `env:` reference names a variable that is not set. Wanaku resolves every connection, also connections that no evaluator uses. | Set the variable, or remove the connection. |
| `unknown keys in wanaku configuration: <keys>` | `wanaku.yaml` has a key that the server does not read. The message gives the path of each key, for example `evaluators.0.trigger.namepsace`. | Correct or remove each key. See the [list of top-level keys](../reference/configuration.md#wanaku-config-file-wanakuyaml). |
| `invalid wanaku configuration YAML` | The `wanaku.yaml` file is not valid YAML. | Correct the YAML syntax. |
| `wanaku config not found, using defaults` (warning, the server starts) | The `--wanaku-config` file does not exist. The default is `wanaku.yaml` in the working directory. | Correct the path. The server does not stop for a missing file, also when you give the path explicitly. |

Any one invalid evaluator rejects the complete evaluator configuration. To start the server with an invalid evaluator runtime that blocks governed requests, set `evaluator_startup_failure: deny`. See [Evaluator governance startup](../reference/configuration.md#evaluator-governance-startup).

## A configuration key has no effect

The server rejects unknown keys in `wanaku.yaml`. A misspelled key stops startup and does not have this symptom. The server does not reject unknown environment variable names. If a setting has no effect, check the spelling of its environment variable. See [Verify Environment Variables](../reference/configuration.md#verify-environment-variables).

## Tool calls fail with `-32003`

The action-policy filter denied the request. With the default `no_match: deny` and no action policy, the filter denies every `tools/call`, `resources/read`, and `prompts/get`. The action-policy filter and the evaluator filter apply `no_match` independently. A governed request needs an action-policy rule that allows it and an evaluator that matches it, or a `no_match: allow` override for the namespace. See [Governance Posture](../govern/governance-posture.md).

## Debugging

Enable trace logs to see all filter decisions, metadata reads/writes, and registry operations:

```bash
RUST_LOG=trace wanaku-server
```

For filter-specific logs only:

```bash
RUST_LOG=wanaku_filters=trace wanaku-server
```
