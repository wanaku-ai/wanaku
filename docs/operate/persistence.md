---
title: Persist registry and revision state
description: Configure Wanaku file persistence for the registry and for evaluator and action-policy revisions, and update pre-release revision files.
---

# Persist registry and revision state

Wanaku keeps the registry and the evaluator and action-policy revision histories in files, so that they survive a restart. `WANAKU_PERSIST_BACKEND` and `WANAKU_PERSIST_PATH` control all of these files.

## Registry Persistence

File persistence is enabled by default. Wanaku reads and writes `$HOME/.wanaku/server/registry.json`. Set a different directory when the default location is not suitable:

```bash
export WANAKU_PERSIST_BACKEND=file
export WANAKU_PERSIST_PATH=/data/registry
```

On startup, the server loads `registry.json` from `WANAKU_PERSIST_PATH`. Each registry change sends a full snapshot to one background writer. The writer keeps only the newest pending snapshot. On an orderly Pingora shutdown, Wanaku waits for pending persistence for up to 10 seconds.

**Format:**

```json
{
  "tools": [],
  "resources": [],
  "prompts": [],
  "namespaces": [],
  "forwards": []
}
```

**Limitation:** File persistence has one in-process writer. It does not coordinate separate Wanaku processes. Do not run multiple replicas against the same persistence directory. If the process stops because of SIGKILL, an out-of-memory error, or a panic, changes since the latest completed snapshot are lost. Use a shared external persistence implementation before you run multiple replicas.

To disable persistence:

```bash
export WANAKU_PERSIST_BACKEND=none
```

With `none`, the registry stays in memory. Wanaku loses all registry changes at restart.

## Embedded redb Registry Persistence

Wanaku can keep the registry in an embedded [redb](https://www.redb.org) database instead of `registry.json`. redb runs inside the Wanaku process. It needs no external database or storage service.

```bash
export WANAKU_PERSIST_BACKEND=redb
export WANAKU_PERSIST_PATH=/data/registry
```

On startup, Wanaku opens `wanaku.redb` in `WANAKU_PERSIST_PATH`. If the file does not exist, Wanaku creates it. The default backend stays `file`.

The redb backend applies to the registry only: agents, tools, resources, prompts, forwards, namespaces, and credential bindings. Audit events, evaluator revisions, action-policy revisions, WASM tool handlers, and plugin configuration stay in their JSON files in the same directory.

### Storage layout

Each registry record kind has its own table. Each row holds one record. The row key is the record name, scoped by namespace where the registry scopes it. The row value is the JSON form of the record. The database also stores a schema version.

### Writes and durability

- Each registry change sends a snapshot to the same background writer as the `file` backend. The writer keeps only the newest pending snapshot.
- The writer runs on a dedicated thread, not on an async runtime worker.
- The writer applies each snapshot in one redb write transaction. The transaction writes only the rows that changed and removes the rows that are gone. It does not replace the whole registry.
- The transaction commits with immediate durability. A commit returns after the data is on disk.
- If a commit fails, the database keeps the previous committed state. The registry persistence status reports the error.
- A management API call returns before the background writer commits its change. A process stop before the commit loses that change.

### Recovery and failures

- After an unclean shutdown, redb repairs the database when Wanaku opens it. The database contains the last committed transaction.
- If Wanaku cannot open `wanaku.redb`, startup stops. Causes include a corrupt file, a file with a newer schema version, and a file that another process holds open.
- If a stored record is not valid, startup stops. A record is not valid when it is not valid JSON, when an agent fails validation, or when the namespace graph is not valid. Wanaku does not write to the database in this case, so the stored records stay unchanged. Fix or move the database file, then start the server again.

### Limits

- redb locks the database file. Only one Wanaku process can use a database file at a time.
- redb does not replicate data to other hosts. Do not put the file on a shared file system for multiple replicas.
- Wanaku does not import `registry.json` into `wanaku.redb`. When you change the backend, the registry starts empty, and Wanaku keeps `registry.json` unchanged.

## WASM tool handler persistence

When file persistence is enabled, the server reads and writes `tool-handlers.json` in `WANAKU_PERSIST_PATH`. This file contains handler definitions, component paths, capability grants, and execution limits. It does not contain compiled native code or component bytes.

Deploy each referenced component file before you restart the server. Startup recompiles and validates the components. An invalid or missing component stops startup. An explicit `tool_handlers` configuration replaces the restored handler configuration. An absent key retains the restored configuration. An empty list removes all handlers.

Runtime activation validates and compiles the candidate before publication. A failed activation retains the active configuration. See [WASM tool handlers](../extend/wasm-tool-handlers.md) for the host API and lifecycle procedures.

## Evaluator Revision Persistence

Evaluator revision history uses the same persistence configuration as the registry. When `WANAKU_PERSIST_BACKEND=file`, the server reads and writes `evaluator-revisions.json` in `WANAKU_PERSIST_PATH`. When persistence is disabled, revision history stays in memory and is lost on restart.

The server writes the file after every revision change, not only at shutdown. A revision created or activated through the management API therefore survives an abrupt stop.

On startup the server loads the persisted history. The `wanaku.yaml` configuration is the source of truth for the live runtime. If the startup configuration matches the persisted active revision, the server re-validates and recompiles that revision and installs it, without recording a new revision. If the startup configuration differs, the server activates it as a new revision.

When the server re-applies an existing active revision, it re-validates the evaluator names, triggers, and LLM connection references and recompiles the WASM processors and result schemas. If the revision no longer validates or compiles on the current host, the server marks the evaluator runtime invalid and logs the error. Startup stops by default. The top-level `evaluator_startup_failure: deny` option permits startup with an invalid runtime. Governed actions are then blocked in enforce mode. The server does not append a revision in this case.

The server keeps a bounded history of the most recent 50 revisions. Older revisions are dropped. Rejected revisions are also persisted, so a failed activation stays visible in the history.

If the startup `wanaku.yaml` evaluator configuration is identical to the persisted active revision, the server keeps that revision active and creates no new revision. A restart with an unchanged configuration does not grow the history.

**Format:**

```json
{
  "revisions": [],
  "active_id": null,
  "next_id": 1
}
```

**Limitation:** File persistence supports one writer. Use a shared external persistence implementation before you run multiple replicas.

## Update an earlier pre-release configuration

This section applies to the 0.3.0 pre-release builds. Evaluator configuration changed between these builds. The 0.3.0 release removes `on_error` without an automatic migration. The field is rejected in YAML, API requests, and all persisted evaluator revisions, including inactive revisions.

Before you start the updated build:

1. Stop the server.
2. Back up the configuration and `evaluator-revisions.json` in `WANAKU_PERSIST_PATH`. The default directory is `$HOME/.wanaku/server`.
3. Remove `on_error` from each evaluator in the startup YAML and each definition in the persisted revision history.
4. Set `governance.default.on_failure` or a namespace override in the startup YAML. Use `allow` for the former `continue` behavior. Use `deny` for the former `block` behavior. The setting applies to shared namespace governance, not to one evaluator. Evaluators in the same namespace must use the same failure behavior.
5. Start the server with the updated configuration.

To discard pre-release evaluator history instead, move the backed-up `evaluator-revisions.json` out of the persistence directory while the server is stopped. Restore the required evaluator definitions in the startup YAML before restart. This resets evaluator revision history. Keep `registry.json` and other persistence files in place.

The server stops at startup if legacy fields remain in the revision file. `evaluator_startup_failure: deny` does not migrate this file or restore evaluator readiness.

## Action Policy Revision Persistence

Action-policy revision history uses the same persistence settings. Wanaku writes `action-policy-revisions.json` in `WANAKU_PERSIST_PATH`. The action-policy stream is independent from the evaluator stream.

Wanaku writes the file after each revision change. Wanaku validates and installs the persisted active policy during startup. See [Action Policies](../govern/action-policies.md) for the policy schema and lifecycle.

## Next steps

- [Wanaku environment variables](../reference/environment-variables.md): find the defaults of the persistence variables.
- [Deploy Wanaku on Kubernetes and OpenShift](kubernetes.md): mount a persistent volume for the persistence directory.
- [FAQ and Troubleshooting](troubleshooting.md): find out why registry data is lost on restart.
