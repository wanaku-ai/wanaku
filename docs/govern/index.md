---
title: "Govern AI agent tool calls: policies, posture and audit"
description: Control what AI agents can do through Wanaku with governance posture, action policies, evaluators, credentials, redaction, and audit.
---

# Govern AI agent tool calls: policies, posture and audit

These pages are for policy authors. They follow the path of a request through Wanaku: the rules that decide it, the credentials and data controls that apply to it, and the evidence that it leaves.

## Rules

- [Governance Posture](governance-posture.md): set the enforcement mode, the fail-safe defaults, and namespace resolution.
- [Action Policies](action-policies.md): write deterministic rules that allow or deny MCP actions.
- [Evaluators](evaluators.md): configure evaluators, their triggers, and their processors.
- [Evaluator engines](evaluator-engines.md): configure the LLM, TypeSafe System One, OPA, and passthrough engines.

## Credentials and data

- [Credential Brokerage](credential-brokerage.md): give forwards scoped, fail-closed upstream credentials.
- [Redaction boundary](redaction.md): learn where Wanaku removes credentials and PII.

## Evidence

- [Governance audit trail](audit-trail.md): read audit events, redaction rules, and retention.
- [Policy Simulation](policy-simulation.md): validate candidate policies and compare decisions before you activate them.

## Tutorials

- [Govern an MCP server](govern-an-mcp-server.md): put one MCP server behind a credential binding, action-policy rules, and an LLM evaluator.
- [Gate tool calls with OPA](gate-calls-with-opa.md): allow or deny tool calls with a Rego policy.

## Next steps

- [Getting Started with Wanaku](../get-started/quickstart.md): run a server to try these rules on.
- [Wanaku configuration file](../reference/wanaku-yaml.md): find where each governance setting goes in `wanaku.yaml`.
