---
title: Wanaku configuration file (wanaku.yaml)
description: "Reference for the Wanaku wanaku.yaml file: top-level sections, key names, unknown-key behavior, evaluator configuration, and connections."
---

# Wanaku configuration file (wanaku.yaml)

The Wanaku configuration bootstraps core registry entries and feature settings at startup. This file is optional. If you omit it, Wanaku can still restore the registry from the default file snapshot. If no snapshot exists, the registry starts empty.

**Location:** Pass with `--wanaku-config`:

```bash
wanaku-server --pipeline-config /path/to/pipeline.yaml --wanaku-config /path/to/wanaku.yaml
```

The default value of `--wanaku-config` is `wanaku.yaml`. The server resolves this relative path against its working directory. In the container image, the working directory is `/etc/wanaku`.

When the server reads the file, it logs `loaded wanaku config` with the absolute path of the file. If the file does not exist, the server logs the warning `wanaku config not found, using defaults` and starts with the fail-safe defaults. This also applies when you give the path explicitly. Check the startup log if your configuration seems to have no effect. A file that the server cannot read, or that is not valid YAML, stops startup.

For a complete annotated file that combines forwards, credential bindings, connections, evaluators, action policies, and governance, see [`examples/governed-mcp`](https://github.com/wanaku-ai/wanaku/blob/main/examples/governed-mcp/README.md).

**Format:**

```yaml
forwards:
  - name: "upstream-mcp"
    address: "http://upstream:8080/mcp"
plugins:
  - id: "customer-management"
    services:
      customer-api:
        target: "http://customer-service:8080"
```

Wanaku loads these top-level sections from `wanaku.yaml`:

- `forwards` — core forward bootstrap configuration
- `bindings` — credential bindings that forwards reference for brokered authentication. See [Credential Brokerage](../govern/credential-brokerage.md#configuring-credentials-in-wanakuyaml)
- `llm_connections` — named LLM connections (model/url/api_key) for evaluators; config-only, never exposed via the management API
- `typesafe_system_one_connections` — named TypeSafe System One connections for evaluators; config-only, never exposed via the management API
- `opa_connections` — named Open Policy Agent connections (url/token/timeout_ms/ca_cert) for evaluators; config-only, never exposed via the management API. See [Open Policy Agent](../govern/evaluator-engines.md#open-policy-agent)
- `evaluators` — evaluator feature configuration
- `evaluator_startup_failure` — `abort` (default) or `deny`. Use a string value. Another value, for example a boolean, marks the evaluator runtime invalid with `startup_policy_invalid`. See [Evaluator governance startup](../govern/evaluators.md#evaluator-governance-startup)
- `action_policy` — declarative action-policy rules. See [Action Policies](../govern/action-policies.md)
- `simulation` — policy validation, simulation, and replay limits. See [Policy Simulation](../govern/policy-simulation.md#resource-limits)
- `governance` — global governance posture and namespace overrides
- `audit` — audit retention, payload capture, and redaction. See [Governance audit trail](../govern/audit-trail.md)
- `plugins` — plugin service mappings owned by the plugins feature

There is no top-level `namespaces` key. A forward sets its namespace with its own `namespace` field. Namespace governance overrides go under `governance.namespaces`.

The server rejects unknown keys at all levels of `wanaku.yaml`. An unknown key stops startup. This rule applies to the top-level keys and to the keys in every section. For example, a misspelled `evaluator:` top-level key or a misspelled `trigger.namepsace` key stops startup. The server prints one error that names each unknown key with its path:

```text
fatal: unknown keys in wanaku configuration: evaluator, forwards.0.credentials_bindings
```

The server checks the keys before it loads the sections. The feature that owns a section validates the values in that section.

The [Governance Posture](../govern/governance-posture.md) model defines fail-safe defaults and namespace overrides. Wanaku validates the section during startup. Invalid governance configuration stops startup.

Wanaku discovers tools, resources, and prompts from the configured forwards.

**Key names:**

All keys in `wanaku.yaml` use `snake_case`, for example `llm_connections`, `api_key`, `audit_level`, and `credential_bindings`.

The `forwards` and `bindings` entries also accept `camelCase` keys, for example `credentialBindings`, `forwardId`, `secretRefs`, `allowedPurposes`, `governedItems`, and `maxTtlSeconds`. These keys are aliases for compatibility with the management API, which uses `camelCase` field names in its JSON bodies. The two spellings have the same effect. Use one spelling in each entry.

The other sections accept only `snake_case` keys. For example, the server does not read `apiKey` in an `llm_connections` entry.

The `evaluators` section and the connection sections (`llm_connections`, `typesafe_system_one_connections`, and `opa_connections`) have their own reference pages. See [Evaluators](../govern/evaluators.md) for the evaluator definitions. See [Evaluator engines](../govern/evaluator-engines.md) for the connection keys and the `env:` references.

## Next steps

- [Governance Posture](../govern/governance-posture.md): configure the `governance` section.
- [Evaluators](../govern/evaluators.md): find the complete evaluator configuration.
- [Pipeline configuration file](pipeline-yaml.md): configure listeners and filters.
