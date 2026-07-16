# Service Account Setup for Test Plans

Running operator test plans with your personal (admin) credentials is dangerous. This guide creates a dedicated service account with only the permissions the test plans require.

The service account lives in a shared `wanaku-test-infra` namespace, separate from the per-run namespace where each test plan executes. This keeps the test infrastructure stable while allowing multiple plans to reuse the same cluster without sharing a test namespace.

No permission change is needed for that split: the service account already has cluster-scoped access to create and delete per-run namespaces/projects, plus the namespaced permissions the plans use inside those namespaces.

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

The `create-service-account.sh` script prints a token valid for 1 year. For CI systems, store it as a secret (e.g., GitHub Actions `OPENSHIFT_SA_TOKEN`). Reuse the same service-account namespace across runs unless you intentionally want to recreate the infrastructure.

To rotate the token:

```bash
oc create token wanaku-test-runner -n wanaku-test-infra --duration=8760h
```

## Teardown

To remove the service account and all RBAC resources:

```bash
./tests/plans/setup/teardown-service-account.sh
```
