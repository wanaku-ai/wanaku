# Common: OpenShift Login

Reusable steps for logging in to an OpenShift cluster using a service account token.

## Prerequisites

- `oc` CLI installed
- `OPENSHIFT_API_URL` environment variable set (e.g. `https://api.cluster.example.com:6443`)
- `OPENSHIFT_SA_TOKEN` environment variable set (service account token)

## Steps

### 1. Log in to OpenShift

```bash
oc login "${OPENSHIFT_API_URL}" --token="${OPENSHIFT_SA_TOKEN}" --insecure-skip-tls-verify=true
```

### 2. Verify login

```bash
oc whoami
# Expected: prints the service account name (e.g. system:serviceaccount:<namespace>:<sa-name>)

oc whoami --show-server
# Expected: prints the cluster API URL

oc version
# Expected: prints client and server version info
```
