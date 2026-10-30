---
title: Secure a Wanaku deployment
description: A checklist to secure a Wanaku deployment, covering authentication, network exposure, secrets, containers, governance posture, and audit.
---

# Secure a Wanaku deployment

Wanaku enforces policy on agent traffic, but the proxy itself also needs protection. Use this checklist before you expose Wanaku outside a development machine. Each item links to the page that has the details.

## Authentication

- Put an authenticating proxy in front of the MCP and management listeners. Wanaku does not validate tokens itself. Read [Authentication](authentication.md).
- Use Keycloak or another OIDC provider as the issuer.
- Change the default admin passwords after setup.
- Regenerate the client secrets in the bundled realm, for example the `wanaku-service` client, before production use.
- Use strong, unique passwords for all service accounts.

## Network exposure

- Do not expose port 8080 outside a trusted network. The management API and the Admin UI have no built-in authentication. Read [Ports and endpoints](../reference/ports-and-endpoints.md#exposure).
- Do not expose Keycloak or the Wanaku listeners directly to the internet without an authenticating proxy.
- Use TLS for all external endpoints. You can add TLS to the listeners in `pipeline.yaml`, or end TLS at the proxy or the ingress. Read [Pipeline configuration file](../reference/pipeline-yaml.md).
- Set `WANAKU_CORS_ORIGIN` to the origin of your UI. The default value is `*`. Read [Environment variables](../reference/environment-variables.md).
- Use network policies to restrict access between services.
- Keep header forwarding off unless a downstream MCP server needs it. `WANAKU_FORWARD_HEADERS` is unset by default, and Wanaku then forwards no client headers. Read [Environment variables](../reference/environment-variables.md).
- Keep `WANAKU_AUDIT_INGESTION_ENABLED` off unless you send Camel semantic audit records. The ingestion endpoint has no authentication. Read [Ingest Camel semantic audit records](../connect/semantic-audit-ingestion.md).

## Secrets

- Do not commit secrets, passwords, or API keys to version control.
- Give upstream credentials to Wanaku through credential bindings. A binding holds a secret reference, such as `env:API_TOKEN`, and never an inline value. Read [Credential Brokerage](../govern/credential-brokerage.md).
- Store the values in Kubernetes Secrets, Sealed Secrets, or an external secret manager. Read [Deploy Wanaku on Kubernetes and OpenShift](kubernetes.md).
- Rotate secrets regularly.
- Use different secrets for development and production.

## Containers and plugins

- Use the latest stable version of the Wanaku images.
- Scan container images for vulnerabilities regularly.
- Run containers with minimal privileges.
- Use read-only file systems where possible. When persistence is on, Wanaku needs a writable persistence directory. Read [Persist registry and revision state](persistence.md).
- Install only trusted Admin UI plugins. Plugins run as same-origin JavaScript without a sandbox. Read [Build an Admin UI plugin](../extend/plugin-development-guide.md).

## Governance and audit

- Keep the default governance posture (`no_match: deny`), and allow actions explicitly. Read [Governance Posture](../govern/governance-posture.md).
- Test policy changes with policy simulation before you activate them. Read [Policy Simulation](../govern/policy-simulation.md).
- Keep audit payload capture off unless you need it, and review the redaction rules before you turn it on. Read [Governance audit trail](../govern/audit-trail.md) and [Redaction boundary](../govern/redaction.md).
- Monitor authentication failures and unusual access patterns at the authenticating proxy.
- Review the audit events regularly, and set up alerts for suspicious activity.

## Next steps

- [Report a vulnerability](https://github.com/wanaku-ai/wanaku/blob/main/SECURITY.md): read the supported versions and the reporting procedure.
- [Authentication](authentication.md): set up oauth2-proxy and Keycloak.
- [Governance Posture](../govern/governance-posture.md): choose the default result for unmatched actions.
