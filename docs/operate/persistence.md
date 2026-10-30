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

Wanaku 0.3.0 is unreleased. Evaluator configuration can change between pre-release builds. This change removes `on_error` without an automatic migration. The field is rejected in YAML, API requests, and all persisted evaluator revisions, including inactive revisions.

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
