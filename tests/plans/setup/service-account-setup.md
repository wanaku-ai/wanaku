# Service Account Setup for Test Plans

Running operator test plans with your personal (admin) credentials is dangerous. This guide creates a dedicated service account with only the permissions the test plans require.

The service account lives in a dedicated `wanaku-test-infra` namespace, separate from the `wanaku-test` namespace where tests run. This ensures the service account survives test cleanup and namespace deletion across test executions.

## Prerequisites

- `oc` CLI logged in as a cluster admin (one-time setup)

## Quick Start

From the repository root:

```bash
# Create the service account and RBAC
./tests/plans/setup/create-service-account.sh

# Save the token for CI or local use
export OPENSHIFT_SA_TOKEN=$(oc create token wanaku-test-runner -n wanaku-test-infra --duration=8760h)
```

Then log in as the service account instead of your admin user:

```bash
oc login <cluster-api-url> --token=${OPENSHIFT_SA_TOKEN}
oc whoami
# Expected: system:serviceaccount:wanaku-test-infra:wanaku-test-runner
```

## What the Service Account Can Do

| Allowed | Not Allowed |
|---|---|
| Create/delete test namespaces | Manage nodes or machines |
| Install/uninstall the operator Helm chart | Manage PVs or StorageClasses |
| CRUD on deployments, services, routes, PVCs | Manage SecurityContextConstraints |
| CRUD on Wanaku custom resources (CRDs) | Manage OLM operators |
| Read pod logs, exec into pods | Impersonate other users |
| Manage ClusterRoles/Bindings (for Helm) | Access cluster infrastructure |

## Token Management

The `create-service-account.sh` script prints a token valid for 1 year. For CI systems, store it as a secret (e.g., GitHub Actions `OPENSHIFT_SA_TOKEN`).

To rotate the token:

```bash
oc create token wanaku-test-runner -n wanaku-test-infra --duration=8760h
```

## Teardown

To remove the service account and all RBAC resources:

```bash
./tests/plans/setup/teardown-service-account.sh
```
