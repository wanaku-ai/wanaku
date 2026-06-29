# Test Plan: Wanaku Operator Basic Functionality on OpenShift

## Overview

This test plan verifies the basic functionality of the Wanaku Kubernetes operator deployed on OpenShift. It covers operator installation, CRD lifecycle, reconciliation, Keycloak/OIDC integration, router and capability deployment, service catalog management, and cleanup.

Every step is fully automatable.

**Hard timeout rule:** Any single operation (oc wait, polling loop, port-forward setup, etc.) that takes longer than **3 minutes** MUST be considered broken. Mark the test as FAIL immediately and abort — do not wait longer. All `--timeout` values in this plan MUST NOT exceed `180s`.

## Prerequisites

### Required tools

| Tool | Minimum version | Verify command |
|------|-----------------|----------------|
| `oc` | 4.12+ | `oc version --client` |
| `helm` | 3.x | `helm version --short` |
| `curl` | any | `curl --version` |
| `jq` | 1.6+ | `jq --version` |
| `base64` | any (coreutils) | `base64 --version 2>/dev/null \|\| echo "available"` |

### Prerequisite check script

```bash
#!/bin/bash
set -e

FAIL=0

for CMD in oc helm curl jq base64; do
  if ! command -v "${CMD}" > /dev/null 2>&1; then
    echo "FAIL: ${CMD} is not installed"
    FAIL=1
  else
    echo "PASS: ${CMD} found at $(command -v ${CMD})"
  fi
done

# Verify oc client version
OC_VERSION=$(oc version --client -o json 2>/dev/null | jq -r '.clientVersion.gitVersion // empty' || echo "unknown")
echo "  oc client version: ${OC_VERSION}"

# Verify helm version
HELM_VERSION=$(helm version --short 2>/dev/null || echo "unknown")
echo "  helm version: ${HELM_VERSION}"

if [ "${FAIL}" -ne 0 ]; then
  echo ""
  echo "FAIL: one or more prerequisites missing"
  exit 1
fi

echo ""
echo "PASS: all prerequisites met"
```

### Environment variables

Set these before running the plan. All image tags default to `latest` but can be overridden to test specific versions.

```bash
export WANAKU_NAMESPACE="${WANAKU_NAMESPACE:-wanaku-test}"
export WANAKU_REPO_ROOT="${WANAKU_REPO_ROOT:-.}"
export WANAKU_ROUTER_IMAGE="${WANAKU_ROUTER_IMAGE:-quay.io/wanaku/wanaku-router-backend:latest}"
export WANAKU_CAPABILITY_HTTP_IMAGE="${WANAKU_CAPABILITY_HTTP_IMAGE:-quay.io/wanaku/wanaku-tool-service-http:latest}"
```

### Helper: wait for resource deletion

Follow [common/wait-for-deletion.md](common/wait-for-deletion.md) to define the `wait_for_deletion` function.

---

## Phase 0: OpenShift Login

Follow [common/openshift-login.md](common/openshift-login.md) to log in to the target OpenShift cluster using a service account token.

---

## Phase 1: Environment Setup

### Step 1.1: Create namespace

Follow [common/namespace-setup.md](common/namespace-setup.md) to create and verify the namespace.

### Step 1.2: Deploy Keycloak

Follow [common/keycloak-setup.md](common/keycloak-setup.md). After completion, verify these variables are set:

```bash
for VAR_NAME in KEYCLOAK_HOST KEYCLOAK_URL WANAKU_OIDC_SECRET; do
  eval "VAL=\${${VAR_NAME}}"
  if [ -z "${VAL}" ]; then
    echo "FAIL: ${VAR_NAME} is not set"
    exit 1
  fi
  echo "PASS: ${VAR_NAME} is set"
done
```

---

## Phase 2: Operator Installation

Follow [common/operator-deployment.md](common/operator-deployment.md) to install the operator via Helm and verify CRDs, RBAC, and health endpoints.

---

## Phase 3: WanakuRouter Lifecycle

### Test 3.1: Create a WanakuRouter with Keycloak authentication

**Description:** Create a `WanakuRouter` CR with `authServer` pointing to the deployed Keycloak instance. Verify the operator reconciles and creates the expected resources.

```bash
cat <<EOF | oc apply -n "${WANAKU_NAMESPACE}" -f -
apiVersion: "wanaku.ai/v1alpha1"
kind: WanakuRouter
metadata:
  name: wanaku-test-router
spec:
  auth:
    authServer: "http://keycloak:8080"
    authProxy: "auto"
  router:
    image: ${WANAKU_ROUTER_IMAGE}
    imagePullPolicy: Always
EOF
```

**Verification - CR accepted:**

```bash
oc get wanakurouter wanaku-test-router -n "${WANAKU_NAMESPACE}" -o name
echo "router-cr-exists=$?"
# Expected: router-cr-exists=0
```

**Verification - Wait for Ready condition:**

```bash
oc wait wanakurouter/wanaku-test-router \
  --for=condition=Ready \
  --timeout=120s \
  -n "${WANAKU_NAMESPACE}"
# Expected output: wanakurouter.wanaku.ai/wanaku-test-router condition met
```

**Verification - Status fields populated:**

```bash
ROUTER_HOST=$(oc get wanakurouter wanaku-test-router -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.status.host}')
SSE_ENDPOINT=$(oc get wanakurouter wanaku-test-router -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.status.sseEndpoint}')
STREAMABLE_ENDPOINT=$(oc get wanakurouter wanaku-test-router -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.status.streamableEndpoint}')

echo "host=${ROUTER_HOST}"
echo "sseEndpoint=${SSE_ENDPOINT}"
echo "streamableEndpoint=${STREAMABLE_ENDPOINT}"

[ -n "${ROUTER_HOST}" ] && echo "PASS: host is set" || { echo "FAIL: host is empty"; exit 1; }
[ -n "${SSE_ENDPOINT}" ] && echo "PASS: sseEndpoint is set" || { echo "FAIL: sseEndpoint is empty"; exit 1; }
[ -n "${STREAMABLE_ENDPOINT}" ] && echo "PASS: streamableEndpoint is set" || { echo "FAIL: streamableEndpoint is empty"; exit 1; }
```

### Test 3.2: Verify reconciled resources - Deployment

**Description:** The operator should create a deployment named `wanaku-test-router-mcp-router`.

```bash
oc wait deployment/wanaku-test-router-mcp-router \
  --for=condition=Available \
  --timeout=120s \
  -n "${WANAKU_NAMESPACE}"

# Verify container image
ROUTER_IMAGE=$(oc get deployment wanaku-test-router-mcp-router -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.spec.template.spec.containers[0].image}')
echo "router-image=${ROUTER_IMAGE}"
if [ "${ROUTER_IMAGE}" != "${WANAKU_ROUTER_IMAGE}" ]; then
  echo "FAIL: unexpected router image '${ROUTER_IMAGE}'"
  exit 1
fi
echo "PASS: router image matches"

# Verify imagePullPolicy
ROUTER_PULL_POLICY=$(oc get deployment wanaku-test-router-mcp-router -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.spec.template.spec.containers[0].imagePullPolicy}')
if [ "${ROUTER_PULL_POLICY}" != "Always" ]; then
  echo "FAIL: unexpected pull policy '${ROUTER_PULL_POLICY}', expected 'Always'"
  exit 1
fi
echo "PASS: imagePullPolicy is Always"
```

### Test 3.3: Verify reconciled resources - Internal Service

**Description:** The operator should create a ClusterIP service named `internal-wanaku-test-router`.

```bash
oc get service internal-wanaku-test-router -n "${WANAKU_NAMESPACE}" > /dev/null 2>&1
echo "internal-service-exists=$?"
# Expected: internal-service-exists=0

SVC_TYPE=$(oc get service internal-wanaku-test-router -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.spec.type}')
if [ "${SVC_TYPE}" != "ClusterIP" ]; then
  echo "FAIL: service type is '${SVC_TYPE}', expected 'ClusterIP'"
  exit 1
fi
echo "PASS: internal service is ClusterIP"

SVC_PORT=$(oc get service internal-wanaku-test-router -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.spec.ports[0].port}')
echo "internal-service-port=${SVC_PORT}"
# Expected: 8080
```

### Test 3.4: Verify reconciled resources - OpenShift Route

**Description:** On OpenShift, the operator creates a Route (not an Ingress) for external access.

```bash
oc get route wanaku-test-router -n "${WANAKU_NAMESPACE}" > /dev/null 2>&1
echo "route-exists=$?"
# Expected: route-exists=0

ROUTE_HOST=$(oc get route wanaku-test-router -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.spec.host}')
echo "route-host=${ROUTE_HOST}"

if [ -z "${ROUTE_HOST}" ]; then
  echo "FAIL: route host is empty"
  exit 1
fi
echo "PASS: route host is set"

# Verify the route targets the internal service
ROUTE_SVC=$(oc get route wanaku-test-router -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.spec.to.name}')
if [ "${ROUTE_SVC}" != "internal-wanaku-test-router" ]; then
  echo "FAIL: route targets '${ROUTE_SVC}', expected 'internal-wanaku-test-router'"
  exit 1
fi
echo "PASS: route targets internal service"
```

### Test 3.5: Verify reconciled resources - PVC

**Description:** The operator creates a PersistentVolumeClaim named `router-volume-claim`.

```bash
oc get pvc router-volume-claim -n "${WANAKU_NAMESPACE}" > /dev/null 2>&1
echo "pvc-exists=$?"
# Expected: pvc-exists=0

PVC_STATUS=$(oc get pvc router-volume-claim -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.status.phase}')
echo "pvc-status=${PVC_STATUS}"
# Expected: Bound
```

### Test 3.6: Verify reconciled resources - Environment variables

**Description:** The router deployment should have AUTH_SERVER, AUTH_PROXY, and AUTH_REALM set.

```bash
DEPLOYMENT="wanaku-test-router-mcp-router"

AUTH_SERVER_VAL=$(oc get deployment "${DEPLOYMENT}" -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.spec.template.spec.containers[0].env[?(@.name=="AUTH_SERVER")].value}')
echo "AUTH_SERVER=${AUTH_SERVER_VAL}"
if [ "${AUTH_SERVER_VAL}" != "http://keycloak:8080" ]; then
  echo "FAIL: AUTH_SERVER is '${AUTH_SERVER_VAL}'"
  exit 1
fi
echo "PASS: AUTH_SERVER is correct"

AUTH_PROXY_VAL=$(oc get deployment "${DEPLOYMENT}" -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.spec.template.spec.containers[0].env[?(@.name=="AUTH_PROXY")].value}')
echo "AUTH_PROXY=${AUTH_PROXY_VAL}"
# When authProxy=auto, this should be http://<route-host>
if [ -z "${AUTH_PROXY_VAL}" ]; then
  echo "FAIL: AUTH_PROXY is empty"
  exit 1
fi
echo "PASS: AUTH_PROXY is set"

AUTH_REALM_VAL=$(oc get deployment "${DEPLOYMENT}" -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.spec.template.spec.containers[0].env[?(@.name=="AUTH_REALM")].value}')
echo "AUTH_REALM=${AUTH_REALM_VAL}"
if [ "${AUTH_REALM_VAL}" != "wanaku" ]; then
  echo "FAIL: AUTH_REALM is '${AUTH_REALM_VAL}', expected 'wanaku'"
  exit 1
fi
echo "PASS: AUTH_REALM is correct"
```

### Test 3.7: Verify owner references

**Description:** All operator-managed resources should have an ownerReference pointing to the WanakuRouter CR.

```bash
ROUTER_UID=$(oc get wanakurouter wanaku-test-router -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.metadata.uid}')

for RESOURCE in "deployment/wanaku-test-router-mcp-router" "service/internal-wanaku-test-router" "route.route.openshift.io/wanaku-test-router" "pvc/router-volume-claim"; do
  OWNER_UID=$(oc get "${RESOURCE}" -n "${WANAKU_NAMESPACE}" \
    -o jsonpath='{.metadata.ownerReferences[0].uid}' 2>/dev/null || echo "")
  if [ "${OWNER_UID}" != "${ROUTER_UID}" ]; then
    echo "FAIL: ${RESOURCE} ownerReference UID mismatch (got '${OWNER_UID}', expected '${ROUTER_UID}')"
  else
    echo "PASS: ${RESOURCE} ownerReference is correct"
  fi
done
```

### Test 3.8: Verify router health endpoint is accessible

**Description:** The router backend should expose health probes.

```bash
export WANAKU_ROUTER_URL="http://$(oc get route wanaku-test-router -n "${WANAKU_NAMESPACE}" -o jsonpath='{.spec.host}')"

MAX_RETRIES=24
RETRY_INTERVAL=5
for i in $(seq 1 ${MAX_RETRIES}); do
  HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "${WANAKU_ROUTER_URL}/q/health/live" 2>/dev/null || echo "000")
  if [ "${HTTP_CODE}" = "200" ]; then
    echo "PASS: router health endpoint is live (attempt ${i})"
    break
  fi
  if [ "${i}" -eq "${MAX_RETRIES}" ]; then
    echo "FAIL: router health not reachable after ${MAX_RETRIES} attempts (last HTTP ${HTTP_CODE})"
    exit 1
  fi
  echo "Waiting for router... (attempt ${i}, HTTP ${HTTP_CODE})"
  sleep ${RETRY_INTERVAL}
done
```

### Test 3.9: Verify OIDC login via router

Follow [common/oidc-login-verification.md](common/oidc-login-verification.md) to verify end-to-end OIDC authentication through the router.

---

## Phase 4: WanakuCapability Lifecycle

### Test 4.1: Create a WanakuCapability (HTTP tool service)

**Description:** Deploy a basic HTTP capability that references the router created in Phase 3.

```bash
cat <<EOF | oc apply -n "${WANAKU_NAMESPACE}" -f -
apiVersion: "wanaku.ai/v1alpha1"
kind: WanakuCapability
metadata:
  name: wanaku-test-capabilities
spec:
  auth:
    authServer: "http://keycloak:8080"
    authProxy: "auto"
  secrets:
    oidcCredentialsSecret: "${WANAKU_OIDC_SECRET}"
  routerRef: wanaku-test-router
  capabilities:
    - name: wanaku-http-test
      image: ${WANAKU_CAPABILITY_HTTP_IMAGE}
      imagePullPolicy: Always
EOF
```

### Test 4.2: Wait for capability to be Ready

```bash
oc wait wanakucapability/wanaku-test-capabilities \
  --for=condition=Ready \
  --timeout=120s \
  -n "${WANAKU_NAMESPACE}"
# Expected output: wanakucapability.wanaku.ai/wanaku-test-capabilities condition met
```

### Test 4.3: Verify capability deployment

```bash
oc wait deployment/wanaku-http-test \
  --for=condition=Available \
  --timeout=120s \
  -n "${WANAKU_NAMESPACE}"

# Verify the container image
CAP_IMAGE=$(oc get deployment wanaku-http-test -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.spec.template.spec.containers[0].image}')
echo "capability-image=${CAP_IMAGE}"
if [ "${CAP_IMAGE}" != "${WANAKU_CAPABILITY_HTTP_IMAGE}" ]; then
  echo "FAIL: unexpected capability image"
  exit 1
fi
echo "PASS: capability image matches"
```

### Test 4.4: Verify capability internal service

```bash
oc get service wanaku-http-test -n "${WANAKU_NAMESPACE}" > /dev/null 2>&1
echo "capability-service-exists=$?"
# Expected: capability-service-exists=0
```

### Test 4.5: Verify capability PVC

```bash
PVC_NAME="wanaku-http-test-volume-claim"
oc get pvc "${PVC_NAME}" -n "${WANAKU_NAMESPACE}" > /dev/null 2>&1
echo "capability-pvc-exists=$?"
# Expected: capability-pvc-exists=0
```

### Test 4.6: Verify capability environment variables

```bash
# Check WANAKU_SERVICE_REGISTRATION_URI (should point to internal router service)
REG_URI=$(oc get deployment wanaku-http-test -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.spec.template.spec.containers[0].env[?(@.name=="WANAKU_SERVICE_REGISTRATION_URI")].value}')
echo "registration-uri=${REG_URI}"
if ! echo "${REG_URI}" | grep -q "internal-wanaku-test-router"; then
  echo "FAIL: registration URI does not reference the internal router service"
  exit 1
fi
echo "PASS: registration URI is correct"

# Check OIDC credentials
OIDC_SECRET_VAL=$(oc get deployment wanaku-http-test -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.spec.template.spec.containers[0].env[?(@.name=="QUARKUS_OIDC_CLIENT_CREDENTIALS_SECRET")].value}')
if [ -z "${OIDC_SECRET_VAL}" ]; then
  echo "FAIL: OIDC secret not set on capability"
  exit 1
fi
echo "PASS: OIDC secret is set on capability"
```

---

## Phase 5: WanakuCapability Negative Tests

### Test 5.1: Create capability with missing routerRef

**Description:** A capability without `routerRef` should fail reconciliation.

```bash
cat <<EOF | oc apply -n "${WANAKU_NAMESPACE}" -f -
apiVersion: "wanaku.ai/v1alpha1"
kind: WanakuCapability
metadata:
  name: wanaku-bad-capability-no-ref
spec:
  capabilities:
    - name: bad-capability
      image: ${WANAKU_CAPABILITY_HTTP_IMAGE}
EOF

# Poll until the CR has a status (reconciler attempted) or timeout
END=$((SECONDS + 30))
while [ $SECONDS -lt $END ]; do
  STATUS=$(oc get wanakucapability wanaku-bad-capability-no-ref -n "${WANAKU_NAMESPACE}" \
    -o jsonpath='{.status}' 2>/dev/null || echo "")
  [ -n "${STATUS}" ] && break
  sleep 3
done

# The CR should exist but should NOT have a Ready=True condition
READY_STATUS=$(oc get wanakucapability wanaku-bad-capability-no-ref -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}' 2>/dev/null || echo "")
if [ "${READY_STATUS}" = "True" ]; then
  echo "FAIL: capability without routerRef should not become Ready"
  exit 1
fi
echo "PASS: capability without routerRef is not Ready"

# Cleanup
oc delete wanakucapability wanaku-bad-capability-no-ref -n "${WANAKU_NAMESPACE}" --ignore-not-found=true
```

### Test 5.2: Create capability referencing a non-existent router

```bash
cat <<EOF | oc apply -n "${WANAKU_NAMESPACE}" -f -
apiVersion: "wanaku.ai/v1alpha1"
kind: WanakuCapability
metadata:
  name: wanaku-bad-capability-bad-ref
spec:
  routerRef: non-existent-router
  capabilities:
    - name: bad-capability-2
      image: ${WANAKU_CAPABILITY_HTTP_IMAGE}
EOF

# Poll until the CR has a status or timeout
END=$((SECONDS + 30))
while [ $SECONDS -lt $END ]; do
  STATUS=$(oc get wanakucapability wanaku-bad-capability-bad-ref -n "${WANAKU_NAMESPACE}" \
    -o jsonpath='{.status}' 2>/dev/null || echo "")
  [ -n "${STATUS}" ] && break
  sleep 3
done

READY_STATUS=$(oc get wanakucapability wanaku-bad-capability-bad-ref -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}' 2>/dev/null || echo "")
if [ "${READY_STATUS}" = "True" ]; then
  echo "FAIL: capability with non-existent routerRef should not become Ready"
  exit 1
fi
echo "PASS: capability with non-existent routerRef is not Ready"

# Check operator logs for the error
OPERATOR_POD=$(oc get pods -l app.kubernetes.io/name=wanaku-operator \
  -n "${WANAKU_NAMESPACE}" -o jsonpath='{.items[0].metadata.name}')
oc logs "${OPERATOR_POD}" -n "${WANAKU_NAMESPACE}" --tail=20 | grep -i "non-existent-router" && \
  echo "PASS: operator logged the missing router error" || \
  echo "INFO: missing router error not in recent logs"

# Cleanup
oc delete wanakucapability wanaku-bad-capability-bad-ref -n "${WANAKU_NAMESPACE}" --ignore-not-found=true
```

---

## Phase 6: WanakuServiceCatalog Lifecycle

### Test 6.1: Create a test ConfigMap with catalog data

**Description:** Create a ConfigMap containing a Base64-encoded ZIP (simulated minimal catalog data).

```bash
# Create a minimal test catalog ZIP
TEMP_DIR=$(mktemp -d)
mkdir -p "${TEMP_DIR}/test-system"
echo "# Test Camel route" > "${TEMP_DIR}/test-system/test-system.camel.yaml"
echo "# Test Wanaku rules" > "${TEMP_DIR}/test-system/test-system.wanaku-rules.yaml"
cat > "${TEMP_DIR}/index.properties" <<'PROPS'
catalog.name = test-system
catalog.description = Minimal test catalog for operator verification
catalog.services = test-system
catalog.routes.test-system = test-system/test-system.camel.yaml
catalog.rules.test-system = test-system/test-system.wanaku-rules.yaml
PROPS

# Package it as a Base64-encoded ZIP (write to file first to avoid streaming/EXT descriptors)
(cd "${TEMP_DIR}" && zip -r catalog.zip . 2>/dev/null)
base64 < "${TEMP_DIR}/catalog.zip" | tr -d '\n' > "${TEMP_DIR}/catalog.b64"

# Create the ConfigMap
oc create configmap test-catalog-data \
  --from-file=catalog.zip="${TEMP_DIR}/catalog.b64" \
  -n "${WANAKU_NAMESPACE}"
oc label configmap test-catalog-data wanaku-test=true -n "${WANAKU_NAMESPACE}"

# Cleanup temp files
rm -rf "${TEMP_DIR}"

echo "PASS: test ConfigMap created"
```

**Verification:**

```bash
oc get configmap test-catalog-data -n "${WANAKU_NAMESPACE}" -o jsonpath='{.data}' | jq -r 'keys[]' | grep -q "catalog.zip"
echo "configmap-has-catalog-zip=$?"
# Expected: configmap-has-catalog-zip=0
```

### Test 6.2: Create a WanakuServiceCatalog

```bash
cat <<'EOF' | oc apply -n "${WANAKU_NAMESPACE}" -f -
apiVersion: "wanaku.ai/v1alpha1"
kind: WanakuServiceCatalog
metadata:
  name: wanaku-test-service-catalogs
spec:
  routerRef: wanaku-test-router
  catalogs:
    - name: test-catalog
      configMapRef: test-catalog-data
EOF
```

### Test 6.3: Verify service catalog reconciliation

**Description:** The service catalog reconciler reads the ConfigMap and sends the data to the router's REST API. If the router is running and reachable, the catalog should be deployed.

```bash
# Wait for the service catalog to become Ready
# Note: this may fail if the router's REST API is not yet fully available,
# which is an expected behavior worth verifying separately
oc wait wanakuservicecatalog/wanaku-test-service-catalogs \
  --for=condition=Ready \
  --timeout=120s \
  -n "${WANAKU_NAMESPACE}" 2>/dev/null

SC_READY=$(oc get wanakuservicecatalog wanaku-test-service-catalogs -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}' 2>/dev/null || echo "unknown")
echo "service-catalog-ready=${SC_READY}"

# If ready, check deployed catalogs list
if [ "${SC_READY}" = "True" ]; then
  DEPLOYED=$(oc get wanakuservicecatalog wanaku-test-service-catalogs -n "${WANAKU_NAMESPACE}" \
    -o jsonpath='{.status.deployedCatalogs[*]}')
  echo "deployed-catalogs=${DEPLOYED}"
  if echo "${DEPLOYED}" | grep -q "test-catalog"; then
    echo "PASS: test-catalog listed in deployedCatalogs"
  else
    echo "FAIL: test-catalog not found in deployedCatalogs"
  fi
fi
```

### Test 6.4: Service catalog negative test - missing ConfigMap

```bash
cat <<'EOF' | oc apply -n "${WANAKU_NAMESPACE}" -f -
apiVersion: "wanaku.ai/v1alpha1"
kind: WanakuServiceCatalog
metadata:
  name: wanaku-bad-service-catalog
spec:
  routerRef: wanaku-test-router
  catalogs:
    - name: missing-catalog
      configMapRef: this-configmap-does-not-exist
EOF

# Poll until the CR has a status or timeout
END=$((SECONDS + 30))
while [ $SECONDS -lt $END ]; do
  STATUS=$(oc get wanakuservicecatalog wanaku-bad-service-catalog -n "${WANAKU_NAMESPACE}" \
    -o jsonpath='{.status}' 2>/dev/null || echo "")
  [ -n "${STATUS}" ] && break
  sleep 3
done

BAD_SC_READY=$(oc get wanakuservicecatalog wanaku-bad-service-catalog -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}' 2>/dev/null || echo "")
if [ "${BAD_SC_READY}" = "True" ]; then
  echo "FAIL: service catalog with missing ConfigMap should not become Ready"
  exit 1
fi
echo "PASS: service catalog with missing ConfigMap is not Ready"

# Cleanup
oc delete wanakuservicecatalog wanaku-bad-service-catalog -n "${WANAKU_NAMESPACE}" --ignore-not-found=true
```

---

## Phase 7: Smoke Test - Full Flow

### Test 7.1: Verify the MCP SSE endpoint is accessible

```bash
ROUTER_HOST=$(oc get route wanaku-test-router -n "${WANAKU_NAMESPACE}" -o jsonpath='{.spec.host}')
MCP_SSE_URL="http://${ROUTER_HOST}/mcp/sse"

HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
  -H "Accept: text/event-stream" \
  --max-time 5 \
  "${MCP_SSE_URL}" 2>/dev/null || echo "000")

# SSE endpoint should return 200 (streaming) or a valid HTTP response
# A timeout (000) while connected is normal for SSE since it streams indefinitely
echo "mcp-sse-http-code=${HTTP_CODE}"
if [ "${HTTP_CODE}" = "000" ] || [ "${HTTP_CODE}" = "200" ] || [ "${HTTP_CODE}" = "401" ]; then
  echo "PASS: MCP SSE endpoint is reachable (HTTP ${HTTP_CODE})"
  # 401 is expected when auth is enabled and no token is provided
else
  echo "FAIL: unexpected response from MCP SSE endpoint (HTTP ${HTTP_CODE})"
fi
```

### Test 7.2: Verify the MCP streamable endpoint is accessible

```bash
MCP_STREAMABLE_URL="http://${ROUTER_HOST}/mcp/"

HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{}' \
  --max-time 5 \
  "${MCP_STREAMABLE_URL}" 2>/dev/null || echo "000")

echo "mcp-streamable-http-code=${HTTP_CODE}"
# Expect 401 (auth required) or 400 (bad request, but endpoint exists) or 200/405
if [ "${HTTP_CODE}" = "401" ] || [ "${HTTP_CODE}" = "400" ] || [ "${HTTP_CODE}" = "200" ] || [ "${HTTP_CODE}" = "405" ]; then
  echo "PASS: MCP streamable endpoint is reachable (HTTP ${HTTP_CODE})"
else
  echo "FAIL: unexpected response from MCP streamable endpoint (HTTP ${HTTP_CODE})"
fi
```

### Test 7.3: Verify namespace MCP SSE endpoint requires authentication and is reachable

**Description:** The namespace-scoped MCP endpoints (`/ns-{N}/mcp/sse`) use a dynamic OIDC tenant resolver (`NamespaceTenantConfigResolver`). Verify that unauthenticated requests are rejected (HTTP 401) and authenticated requests are accepted. This covers the fix for [#1430](https://github.com/wanaku-ai/wanaku/issues/1430).

```bash
NS_MCP_SSE_URL="http://${ROUTER_HOST}/ns-0/mcp/sse"

# Step 1: Unauthenticated request should return 401
UNAUTH_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
  -H "Accept: text/event-stream" \
  --max-time 5 \
  "${NS_MCP_SSE_URL}" 2>/dev/null || echo "000")

echo "ns-0-mcp-sse-unauth=${UNAUTH_CODE}"
if [ "${UNAUTH_CODE}" = "401" ]; then
  echo "PASS: namespace MCP SSE returns 401 without token"
else
  echo "FAIL: expected 401, got HTTP ${UNAUTH_CODE}"
fi

# Step 2: Authenticated request should be accepted
KC_POD=$(oc get pods -l app=keycloak \
  -n "${WANAKU_NAMESPACE}" -o jsonpath='{.items[0].metadata.name}')

NS_TOKEN=$(oc exec "${KC_POD}" -n "${WANAKU_NAMESPACE}" -- \
  curl -sf \
    -d "client_id=wanaku-service" \
    -d "client_secret=${WANAKU_OIDC_CLIENT_SECRET}" \
    -d "grant_type=client_credentials" \
    "http://localhost:8080/realms/wanaku/protocol/openid-connect/token" \
  | jq -r '.access_token')

if [ -z "${NS_TOKEN}" ] || [ "${NS_TOKEN}" = "null" ]; then
  echo "FAIL: could not obtain token for namespace MCP test"
else
  AUTH_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "Accept: text/event-stream" \
    -H "Authorization: Bearer ${NS_TOKEN}" \
    --max-time 5 \
    "${NS_MCP_SSE_URL}" 2>/dev/null || echo "000")

  echo "ns-0-mcp-sse-auth=${AUTH_CODE}"
  # 200 = streaming, 000 = SSE timeout (normal for streaming endpoints)
  if [ "${AUTH_CODE}" = "200" ] || [ "${AUTH_CODE}" = "000" ]; then
    echo "PASS: namespace MCP SSE accepts authenticated request (HTTP ${AUTH_CODE})"
  else
    echo "FAIL: unexpected response from namespace MCP SSE with token (HTTP ${AUTH_CODE})"
  fi
fi
```

### Test 7.4: Verify router and capability pods are running

```bash
echo "--- All pods in ${WANAKU_NAMESPACE} ---"
oc get pods -n "${WANAKU_NAMESPACE}" -o wide

# Check all non-completed pods are Running
NOT_RUNNING=$(oc get pods -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{range .items[?(@.status.phase!="Running")]}{.metadata.name}={.status.phase}{"\n"}{end}' \
  | grep -v "^$" | wc -l | tr -d ' ')

if [ "${NOT_RUNNING}" != "0" ]; then
  echo "WARN: ${NOT_RUNNING} pod(s) not in Running state"
  oc get pods -n "${WANAKU_NAMESPACE}" --field-selector=status.phase!=Running
else
  echo "PASS: all pods are Running"
fi
```

### Test 7.5: Verify operator logs contain reconciliation records

```bash
OPERATOR_POD=$(oc get pods -l app.kubernetes.io/name=wanaku-operator \
  -n "${WANAKU_NAMESPACE}" -o jsonpath='{.items[0].metadata.name}')

echo "--- Operator reconciliation log entries ---"
oc logs "${OPERATOR_POD}" -n "${WANAKU_NAMESPACE}" | grep -i "reconciliation" | tail -10

# Verify we see reconciliation entries for both router and capability
ROUTER_RECON=$(oc logs "${OPERATOR_POD}" -n "${WANAKU_NAMESPACE}" | grep -c "Starting router reconciliation" || echo "0")
CAP_RECON=$(oc logs "${OPERATOR_POD}" -n "${WANAKU_NAMESPACE}" | grep -c "Starting capability reconciliation" || echo "0")

echo "router-reconciliation-count=${ROUTER_RECON}"
echo "capability-reconciliation-count=${CAP_RECON}"

if [ "${ROUTER_RECON}" -gt 0 ]; then
  echo "PASS: router reconciliation logged"
else
  echo "FAIL: no router reconciliation found in logs"
fi

if [ "${CAP_RECON}" -gt 0 ]; then
  echo "PASS: capability reconciliation logged"
else
  echo "FAIL: no capability reconciliation found in logs"
fi
```

---

## Phase 8: Resource Update Reconciliation

### Test 8.1: Update the router image and verify reconciliation

**Description:** Change the router CR spec and verify the operator updates the deployment.

```bash
# Patch the router to use a specific tag
oc patch wanakurouter wanaku-test-router -n "${WANAKU_NAMESPACE}" \
  --type=merge \
  -p "{\"spec\":{\"router\":{\"image\":\"${WANAKU_ROUTER_IMAGE}\"}}}"

# Wait for the deployment rollout to complete
oc rollout status deployment/wanaku-test-router-mcp-router \
  -n "${WANAKU_NAMESPACE}" \
  --timeout=120s

UPDATED_IMAGE=$(oc get deployment wanaku-test-router-mcp-router -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.spec.template.spec.containers[0].image}')
echo "updated-image=${UPDATED_IMAGE}"
if [ "${UPDATED_IMAGE}" = "${WANAKU_ROUTER_IMAGE}" ]; then
  echo "PASS: deployment image updated by operator"
else
  echo "FAIL: deployment image not updated (got '${UPDATED_IMAGE}')"
fi
```

### Test 8.2: Add an environment variable to the router

```bash
oc patch wanakurouter wanaku-test-router -n "${WANAKU_NAMESPACE}" \
  --type=merge \
  -p '{"spec":{"router":{"env":[{"name":"TEST_VAR","value":"test_value"}]}}}'

# Wait for the deployment rollout to complete
oc rollout status deployment/wanaku-test-router-mcp-router \
  -n "${WANAKU_NAMESPACE}" \
  --timeout=120s

TEST_VAR_VAL=$(oc get deployment wanaku-test-router-mcp-router -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.spec.template.spec.containers[0].env[?(@.name=="TEST_VAR")].value}')
if [ "${TEST_VAR_VAL}" = "test_value" ]; then
  echo "PASS: TEST_VAR environment variable added"
else
  echo "FAIL: TEST_VAR not found or wrong value (got '${TEST_VAR_VAL}')"
fi
```

---

## Phase 9: Deletion and Ownership Cascade

### Test 9.1: Delete the service catalog CR and verify cleanup

```bash
oc delete wanakuservicecatalog wanaku-test-service-catalogs -n "${WANAKU_NAMESPACE}" --ignore-not-found=true
echo "service-catalog-deleted=$?"
# Expected: service-catalog-deleted=0
```

### Test 9.2: Delete the capability CR and verify managed resources are removed

```bash
oc delete wanakucapability wanaku-test-capabilities -n "${WANAKU_NAMESPACE}"

# Wait for Kubernetes garbage collection via polling
wait_for_deletion deployment wanaku-http-test "${WANAKU_NAMESPACE}" 60
wait_for_deletion service wanaku-http-test "${WANAKU_NAMESPACE}" 30
```

### Test 9.3: Delete the router CR and verify managed resources are removed

```bash
oc delete wanakurouter wanaku-test-router -n "${WANAKU_NAMESPACE}"

# Wait for Kubernetes garbage collection via polling
wait_for_deletion deployment wanaku-test-router-mcp-router "${WANAKU_NAMESPACE}" 60
wait_for_deletion service internal-wanaku-test-router "${WANAKU_NAMESPACE}" 30
wait_for_deletion route wanaku-test-router "${WANAKU_NAMESPACE}" 30
```

---

## Phase 10: Cleanup

Follow [common/cleanup.md](common/cleanup.md) for full teardown.

---

## Test Summary Matrix

| Phase | Test ID | Test Name | Priority |
|-------|---------|-----------|----------|
| 0 | — | OpenShift login | Critical |
| 1 | 1.1-1.2 | Environment setup | Critical |
| 2 | — | Operator installation and health | Critical |
| 3 | 3.1-3.9 | WanakuRouter lifecycle, reconciliation, and OIDC login | Critical |
| 4 | 4.1-4.6 | WanakuCapability lifecycle | Critical |
| 5 | 5.1-5.2 | WanakuCapability negative tests | High |
| 6 | 6.1-6.4 | WanakuServiceCatalog lifecycle | High |
| 7 | 7.1-7.5 | Smoke test (full flow, incl. namespace OIDC) | Critical |
| 8 | 8.1-8.2 | Update reconciliation | Medium |
| 9 | 9.1-9.3 | Deletion and ownership cascade | Critical |
| 10 | — | Cleanup | Critical |
