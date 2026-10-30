---
title: Govern agent actions
description: Control what AI agents can do through Wanaku with governance posture, action policies, evaluators, credentials, redaction, and audit.
---

# Govern agent actions

These pages are for policy authors. They follow the path of a request through Wanaku: the rules that decide it, the credentials and data controls that apply to it, and the evidence that it leaves.

## Rules

- [Governance Posture](governance-posture.md): set the enforcement mode, the fail-safe defaults, and namespace resolution.
- [Action Policies](action-policies.md): write deterministic rules that allow or deny MCP actions.
- [Evaluator Engine](evaluators.md): configure evaluators and write JavaScript or Rust action scripts.

## Credentials and data

- [Credential Brokerage](credential-brokerage.md): give forwards scoped, fail-closed upstream credentials.
- [Redaction boundary](redaction.md): learn where Wanaku removes credentials and PII.

## Evidence

- [Governance audit trail](audit-trail.md): read audit events, redaction rules, and retention.
- [Policy Simulation](policy-simulation.md): validate candidate policies and compare decisions before you activate them.

## Next steps

- [Getting Started with Wanaku](../get-started/quickstart.md): run a server to try these rules on.
- [Configuration](../reference/configuration.md): find where each governance setting goes in `wanaku.yaml`.
