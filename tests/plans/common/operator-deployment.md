# Common: Operator Deployment via Helm

Reusable steps for installing the Wanaku operator on OpenShift using Helm.

## Prerequisites

- Namespace created (see [namespace-setup.md](namespace-setup.md))
- `WANAKU_NAMESPACE` environment variable set
- Helm 3.x installed
- The repository root is available at `WANAKU_REPO_ROOT`

## Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `WANAKU_NAMESPACE` | Target namespace | `wanaku-test` |
| `WANAKU_REPO_ROOT` | Path to the wanaku repository root | `/path/to/wanaku` |
| `WANAKU_OPERATOR_IMAGE` | Operator container image (optional override) | `quay.io/wanaku/wanaku-operator:latest` |

## Steps

### 1. Set the repository root

```bash
# Set this to the root of the wanaku git checkout
export WANAKU_REPO_ROOT="${WANAKU_REPO_ROOT:-.}"
```

### 2. Verify the Helm chart exists

```bash
CHART_DIR="${WANAKU_REPO_ROOT}/apps/wanaku-operator/deploy/helm/wanaku-operator"
if [ ! -f "${CHART_DIR}/Chart.yaml" ]; then
  echo "FAIL: Helm chart not found at ${CHART_DIR}"
  exit 1
fi
echo "PASS: Helm chart found"
```

### 3. Install the operator via Helm

```bash
helm install wanaku-operator \
  "${WANAKU_REPO_ROOT}/apps/wanaku-operator/deploy/helm/wanaku-operator" \
  --namespace "${WANAKU_NAMESPACE}" \
  --set operatorNamespace="${WANAKU_NAMESPACE}"
```

**Expected output:** Helm prints a success message containing `STATUS: deployed`.

**Verification:**

```bash
helm status wanaku-operator --namespace "${WANAKU_NAMESPACE}" -o json | jq -r '.info.status' | grep -q "deployed"
echo "helm-status=$?"
# Expected: helm-status=0
```

### 4. Wait for the operator deployment to become available

```bash
oc wait deployment/wanaku-operator \
  --for=condition=Available \
  --timeout=180s \
  --namespace "${WANAKU_NAMESPACE}"
```

**Expected output:** `deployment.apps/wanaku-operator condition met`

### 5. Verify the operator pod is running

```bash
OPERATOR_POD_STATUS=$(oc get pods -l app.kubernetes.io/name=wanaku-operator \
  -n "${WANAKU_NAMESPACE}" -o jsonpath='{.items[0].status.phase}')
if [ "${OPERATOR_POD_STATUS}" != "Running" ]; then
  echo "FAIL: operator pod status is '${OPERATOR_POD_STATUS}', expected 'Running'"
  exit 1
fi
echo "PASS: operator pod is Running"
```

### 6. Verify CRDs are installed

```bash
for CRD in wanakurouters.wanaku.ai wanakucapabilities.wanaku.ai wanakuservicecatalogs.wanaku.ai; do
  if ! oc get crd "${CRD}" > /dev/null 2>&1; then
    echo "FAIL: CRD ${CRD} not found"
    exit 1
  fi
  echo "PASS: CRD ${CRD} exists"
done
```

### 7. Verify RBAC resources were created

```bash
# Check ClusterRoles
for ROLE_SUFFIX in wanaku-capability-cluster-role wanaku-router-cluster-role wanaku-service-catalog-cluster-role josdk-crd-validating-cluster-role; do
  ROLE_NAME="${WANAKU_NAMESPACE}-${ROLE_SUFFIX}"
  if ! oc get clusterrole "${ROLE_NAME}" > /dev/null 2>&1; then
    echo "FAIL: ClusterRole ${ROLE_NAME} not found"
    exit 1
  fi
  echo "PASS: ClusterRole ${ROLE_NAME} exists"
done

# Check ServiceAccount
oc get serviceaccount wanaku-operator -n "${WANAKU_NAMESPACE}" > /dev/null 2>&1
echo "service-account-exists=$?"
# Expected: service-account-exists=0
```

### 8. Verify operator health endpoints

```bash
OPERATOR_POD=$(oc get pods -l app.kubernetes.io/name=wanaku-operator \
  -n "${WANAKU_NAMESPACE}" -o jsonpath='{.items[0].metadata.name}')

# Check liveness
oc exec "${OPERATOR_POD}" -n "${WANAKU_NAMESPACE}" -- \
  curl -sf http://localhost:8081/q/health/live > /dev/null 2>&1
echo "liveness=$?"
# Expected: liveness=0

# Check readiness
oc exec "${OPERATOR_POD}" -n "${WANAKU_NAMESPACE}" -- \
  curl -sf http://localhost:8081/q/health/ready > /dev/null 2>&1
echo "readiness=$?"
# Expected: readiness=0
```
