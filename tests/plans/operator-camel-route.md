# Test Plan: WanakuCamelRoute CRD on OpenShift

## Overview

This test plan verifies the WanakuCamelRoute CRD feature on OpenShift. The WanakuCamelRoute lets users define Apache Camel routes and MCP tool/resource metadata inline in a Kubernetes CR. The operator packages the route into a service catalog ZIP and deploys it to the router via REST API.

Every step except the initial `oc login` is fully automatable.

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
```

### Helper: wait for resource deletion

Several tests verify that resources are cleaned up after CR deletion. This helper polls for deletion instead of using a fixed sleep.

```bash
wait_for_deletion() {
  local RESOURCE_TYPE="$1"
  local RESOURCE_NAME="$2"
  local NAMESPACE="$3"
  local TIMEOUT="${4:-60}"
  local INTERVAL=3
  local ELAPSED=0

  while oc get "${RESOURCE_TYPE}" "${RESOURCE_NAME}" -n "${NAMESPACE}" > /dev/null 2>&1; do
    if [ "${ELAPSED}" -ge "${TIMEOUT}" ]; then
      echo "FAIL: ${RESOURCE_TYPE}/${RESOURCE_NAME} still exists after ${TIMEOUT}s"
      return 1
    fi
    sleep ${INTERVAL}
    ELAPSED=$((ELAPSED + INTERVAL))
  done
  echo "PASS: ${RESOURCE_TYPE}/${RESOURCE_NAME} deleted (${ELAPSED}s)"
  return 0
}
```

### Helper: query router REST API via oc exec

The router has OIDC auth enabled, so external `curl` calls get a 302 redirect. All catalog verification steps use `oc exec` into the router pod to query the internal localhost endpoint.

```bash
query_router_api() {
  local ENDPOINT="$1"
  local ROUTER_POD
  ROUTER_POD=$(oc get pods -l app.kubernetes.io/name=wanaku-test-router-mcp-router \
    -n "${WANAKU_NAMESPACE}" -o jsonpath='{.items[0].metadata.name}')

  oc exec "${ROUTER_POD}" -n "${WANAKU_NAMESPACE}" -- \
    curl -sf "http://localhost:8080${ENDPOINT}"
}
```

---

## Phase 0: OpenShift Login (MANUAL)

This is the only manual step in the plan.

### Step 0.1: Log in to OpenShift

Log in to the target OpenShift cluster:

```bash
oc login <cluster-api-url> --username=<username> --password=<password>
# Or with a token:
# oc login <cluster-api-url> --token=<token>
```

### Step 0.2: Verify login

```bash
oc whoami
# Expected: prints the logged-in username

oc whoami --show-server
# Expected: prints the cluster API URL

oc version
# Expected: prints client and server version info
```

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

### Test 2.1: Verify WanakuCamelRoute CRD is registered

**Description:** Confirm the WanakuCamelRoute CRD was installed by the Helm chart.

```bash
oc get crd wanakucamelroutes.wanaku.ai
echo "camelroute-crd-exists=$?"
# Expected: camelroute-crd-exists=0
```

### Test 2.2: Verify WanakuCamelRoute RBAC

**Description:** Confirm the ClusterRole for WanakuCamelRoute was created.

```bash
ROLE_NAME="${WANAKU_NAMESPACE}-wanaku-camel-route-cluster-role"
oc get clusterrole "${ROLE_NAME}"
echo "camelroute-clusterrole-exists=$?"
# Expected: camelroute-clusterrole-exists=0
```

---

## Phase 3: WanakuRouter Setup

### Test 3.1: Create a WanakuRouter with Keycloak authentication

**Description:** Create a WanakuRouter CR that all CamelRoute tests reference. Wait for Ready.

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

**Verification - Wait for Ready condition:**

```bash
oc wait wanakurouter/wanaku-test-router \
  --for=condition=Ready \
  --timeout=120s \
  -n "${WANAKU_NAMESPACE}"
# Expected output: wanakurouter.wanaku.ai/wanaku-test-router condition met
```

### Test 3.2: Verify router deployment is available

```bash
oc wait deployment/wanaku-test-router-mcp-router \
  --for=condition=Available \
  --timeout=120s \
  -n "${WANAKU_NAMESPACE}"
echo "router-deployment-available=$?"
# Expected: router-deployment-available=0
```

### Test 3.3: Verify router pod is running and REST API is reachable

```bash
ROUTER_POD=$(oc get pods -l app.kubernetes.io/name=wanaku-test-router-mcp-router \
  -n "${WANAKU_NAMESPACE}" -o jsonpath='{.items[0].metadata.name}')

MAX_RETRIES=24
RETRY_INTERVAL=5
for i in $(seq 1 ${MAX_RETRIES}); do
  HTTP_CODE=$(oc exec "${ROUTER_POD}" -n "${WANAKU_NAMESPACE}" -- \
    curl -sf -o /dev/null -w "%{http_code}" http://localhost:8080/q/health/ready 2>/dev/null || echo "000")
  if [ "${HTTP_CODE}" = "200" ]; then
    echo "PASS: router REST API is reachable (attempt ${i})"
    break
  fi
  if [ "${i}" -eq "${MAX_RETRIES}" ]; then
    echo "FAIL: router REST API not reachable after ${MAX_RETRIES} attempts (last HTTP ${HTTP_CODE})"
    exit 1
  fi
  echo "Waiting for router REST API... (attempt ${i}, HTTP ${HTTP_CODE})"
  sleep ${RETRY_INTERVAL}
done
```

---

## Phase 4: WanakuCamelRoute Happy Path - Tool

### Test 4.1: Create a WanakuCamelRoute with a tool

**Description:** Create a CamelRoute CR with a simple direct-to-log route and a single tool. Verify the operator reconciles, sets status, and deploys the catalog.

```bash
cat <<'EOF' | oc apply -n "${WANAKU_NAMESPACE}" -f -
apiVersion: wanaku.ai/v1alpha1
kind: WanakuCamelRoute
metadata:
  name: test-greeting-tool
spec:
  routerRef: wanaku-test-router
  route:
    - route:
        id: greeting-route
        from:
          uri: direct:greeting
          steps:
            - setBody:
                simple: "Hello from WanakuCamelRoute! Input: ${body}"
            - log:
                message: "Greeting tool invoked"
  mcp:
    tools:
      - name: test-greeting
        routeId: greeting-route
        description: "A test greeting tool"
        properties:
          - name: message
            type: string
            description: "The greeting message"
            required: true
EOF
```

**Verification - CR accepted:**

```bash
oc get wanakucamelroute test-greeting-tool -n "${WANAKU_NAMESPACE}" -o name
echo "greeting-tool-cr-exists=$?"
# Expected: greeting-tool-cr-exists=0
```

### Test 4.2: Verify CamelRoute reaches Ready condition

```bash
oc wait wanakucamelroute/test-greeting-tool \
  --for=condition=Ready \
  --timeout=120s \
  -n "${WANAKU_NAMESPACE}"
# Expected output: wanakucamelroute.wanaku.ai/test-greeting-tool condition met
```

### Test 4.3: Verify status fields - deployedCatalogName

```bash
CATALOG_NAME=$(oc get wanakucamelroute test-greeting-tool -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.status.deployedCatalogName}')
echo "deployedCatalogName=${CATALOG_NAME}"

if [ "${CATALOG_NAME}" != "test-greeting-tool" ]; then
  echo "FAIL: deployedCatalogName is '${CATALOG_NAME}', expected 'test-greeting-tool'"
  exit 1
fi
echo "PASS: deployedCatalogName is correct"
```

### Test 4.4: Verify status fields - registeredTools

```bash
TOOLS=$(oc get wanakucamelroute test-greeting-tool -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.status.registeredTools[*]}')
echo "registeredTools=${TOOLS}"

if ! echo "${TOOLS}" | grep -q "test-greeting"; then
  echo "FAIL: test-greeting not found in registeredTools"
  exit 1
fi
echo "PASS: test-greeting is in registeredTools"
```

### Test 4.5: Verify status fields - registeredResources is empty

```bash
RESOURCES=$(oc get wanakucamelroute test-greeting-tool -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.status.registeredResources[*]}')
echo "registeredResources=${RESOURCES}"

if [ -n "${RESOURCES}" ]; then
  echo "FAIL: registeredResources should be empty for a tool-only CR, got '${RESOURCES}'"
  exit 1
fi
echo "PASS: registeredResources is empty"
```

### Test 4.6: Verify catalog exists in router

```bash
ROUTER_POD=$(oc get pods -l app.kubernetes.io/name=wanaku-test-router-mcp-router \
  -n "${WANAKU_NAMESPACE}" -o jsonpath='{.items[0].metadata.name}')

CATALOG_RESPONSE=$(oc exec "${ROUTER_POD}" -n "${WANAKU_NAMESPACE}" -- \
  curl -sf http://localhost:8080/api/v1/service-catalog)

if echo "${CATALOG_RESPONSE}" | jq -e '.data' > /dev/null 2>&1; then
  if echo "${CATALOG_RESPONSE}" | jq -e '.data[] | select(.name == "test-greeting-tool")' > /dev/null 2>&1; then
    echo "PASS: test-greeting-tool catalog found in router"
  else
    echo "FAIL: test-greeting-tool catalog not found in router response"
    echo "Response: ${CATALOG_RESPONSE}"
    exit 1
  fi
else
  echo "FAIL: unexpected catalog response format"
  echo "Response: ${CATALOG_RESPONSE}"
  exit 1
fi
```

### Test 4.7: Verify operator logs show successful reconciliation

```bash
OPERATOR_POD=$(oc get pods -l app.kubernetes.io/name=wanaku-operator \
  -n "${WANAKU_NAMESPACE}" -o jsonpath='{.items[0].metadata.name}')

oc logs "${OPERATOR_POD}" -n "${WANAKU_NAMESPACE}" | grep -q "Starting CamelRoute reconciliation for test-greeting-tool" && \
  echo "PASS: reconciliation start logged" || \
  echo "FAIL: reconciliation start not found in operator logs"

oc logs "${OPERATOR_POD}" -n "${WANAKU_NAMESPACE}" | grep -q "Successfully deployed CamelRoute 'test-greeting-tool'" && \
  echo "PASS: successful deployment logged" || \
  echo "FAIL: successful deployment not found in operator logs"
```

---

## Phase 5: WanakuCamelRoute Happy Path - Resource

### Test 5.1: Create a WanakuCamelRoute with a resource

**Description:** Create a CamelRoute CR with a resource (not a tool). Verify status and catalog deployment.

```bash
cat <<'EOF' | oc apply -n "${WANAKU_NAMESPACE}" -f -
apiVersion: wanaku.ai/v1alpha1
kind: WanakuCamelRoute
metadata:
  name: test-info-resource
spec:
  routerRef: wanaku-test-router
  route:
    - route:
        id: info-route
        from:
          uri: direct:info
          steps:
            - setBody:
                constant: '{"version": "1.0", "status": "healthy"}'
            - log:
                message: "Info resource accessed"
  mcp:
    resources:
      - name: test-info
        routeId: info-route
        description: "A test info resource"
        uri: "wanaku://test/info"
        mimeType: "application/json"
EOF
```

### Test 5.2: Verify resource CR reaches Ready condition

```bash
oc wait wanakucamelroute/test-info-resource \
  --for=condition=Ready \
  --timeout=120s \
  -n "${WANAKU_NAMESPACE}"
# Expected output: wanakucamelroute.wanaku.ai/test-info-resource condition met
```

### Test 5.3: Verify status fields - registeredResources

```bash
RESOURCES=$(oc get wanakucamelroute test-info-resource -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.status.registeredResources[*]}')
echo "registeredResources=${RESOURCES}"

if ! echo "${RESOURCES}" | grep -q "test-info"; then
  echo "FAIL: test-info not found in registeredResources"
  exit 1
fi
echo "PASS: test-info is in registeredResources"
```

### Test 5.4: Verify status fields - registeredTools is empty

```bash
TOOLS=$(oc get wanakucamelroute test-info-resource -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.status.registeredTools[*]}')
echo "registeredTools=${TOOLS}"

if [ -n "${TOOLS}" ]; then
  echo "FAIL: registeredTools should be empty for a resource-only CR, got '${TOOLS}'"
  exit 1
fi
echo "PASS: registeredTools is empty"
```

### Test 5.5: Verify resource catalog exists in router

```bash
ROUTER_POD=$(oc get pods -l app.kubernetes.io/name=wanaku-test-router-mcp-router \
  -n "${WANAKU_NAMESPACE}" -o jsonpath='{.items[0].metadata.name}')

CATALOG_RESPONSE=$(oc exec "${ROUTER_POD}" -n "${WANAKU_NAMESPACE}" -- \
  curl -sf http://localhost:8080/api/v1/service-catalog)

if echo "${CATALOG_RESPONSE}" | jq -e '.data[] | select(.name == "test-info-resource")' > /dev/null 2>&1; then
  echo "PASS: test-info-resource catalog found in router"
else
  echo "FAIL: test-info-resource catalog not found in router response"
  exit 1
fi
```

---

## Phase 6: WanakuCamelRoute Happy Path - Combined (Tools + Resources + Properties)

### Test 6.1: Create a WanakuCamelRoute with tools, resources, and properties

**Description:** Create a CR with both tools and resources and a properties map. Verify all status fields.

```bash
cat <<'EOF' | oc apply -n "${WANAKU_NAMESPACE}" -f -
apiVersion: wanaku.ai/v1alpha1
kind: WanakuCamelRoute
metadata:
  name: test-combined-cr
spec:
  routerRef: wanaku-test-router
  route:
    - route:
        id: combined-tool-route
        from:
          uri: direct:combined-tool
          steps:
            - setBody:
                simple: "Combined tool result: ${body}"
            - log:
                message: "Combined tool invoked"
    - route:
        id: combined-resource-route
        from:
          uri: direct:combined-resource
          steps:
            - setBody:
                constant: '{"combined": true}'
            - log:
                message: "Combined resource accessed"
  properties:
    app.greeting: "Hello from combined"
    app.version: "1.0.0"
  mcp:
    tools:
      - name: combined-tool
        routeId: combined-tool-route
        description: "A tool in a combined CR"
        properties:
          - name: input
            type: string
            description: "The input value"
            required: true
    resources:
      - name: combined-resource
        routeId: combined-resource-route
        description: "A resource in a combined CR"
        uri: "wanaku://test/combined"
        mimeType: "application/json"
EOF
```

### Test 6.2: Verify combined CR reaches Ready condition

```bash
oc wait wanakucamelroute/test-combined-cr \
  --for=condition=Ready \
  --timeout=120s \
  -n "${WANAKU_NAMESPACE}"
# Expected output: wanakucamelroute.wanaku.ai/test-combined-cr condition met
```

### Test 6.3: Verify combined status fields

```bash
CATALOG_NAME=$(oc get wanakucamelroute test-combined-cr -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.status.deployedCatalogName}')
TOOLS=$(oc get wanakucamelroute test-combined-cr -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.status.registeredTools[*]}')
RESOURCES=$(oc get wanakucamelroute test-combined-cr -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.status.registeredResources[*]}')

echo "deployedCatalogName=${CATALOG_NAME}"
echo "registeredTools=${TOOLS}"
echo "registeredResources=${RESOURCES}"

[ "${CATALOG_NAME}" = "test-combined-cr" ] && echo "PASS: deployedCatalogName is correct" || { echo "FAIL: unexpected deployedCatalogName '${CATALOG_NAME}'"; exit 1; }

echo "${TOOLS}" | grep -q "combined-tool" && echo "PASS: combined-tool in registeredTools" || { echo "FAIL: combined-tool not in registeredTools"; exit 1; }

echo "${RESOURCES}" | grep -q "combined-resource" && echo "PASS: combined-resource in registeredResources" || { echo "FAIL: combined-resource not in registeredResources"; exit 1; }
```

### Test 6.4: Verify combined catalog exists in router

```bash
ROUTER_POD=$(oc get pods -l app.kubernetes.io/name=wanaku-test-router-mcp-router \
  -n "${WANAKU_NAMESPACE}" -o jsonpath='{.items[0].metadata.name}')

CATALOG_RESPONSE=$(oc exec "${ROUTER_POD}" -n "${WANAKU_NAMESPACE}" -- \
  curl -sf http://localhost:8080/api/v1/service-catalog)

if echo "${CATALOG_RESPONSE}" | jq -e '.data[] | select(.name == "test-combined-cr")' > /dev/null 2>&1; then
  echo "PASS: test-combined-cr catalog found in router"
else
  echo "FAIL: test-combined-cr catalog not found in router response"
  exit 1
fi
```

---

## Phase 7: WanakuCamelRoute Update Reconciliation

### Test 7.1: Patch an existing CamelRoute and verify re-reconciliation

**Description:** Patch the greeting tool CR to change the tool description and add a second tool. Verify the operator re-reconciles and updates the catalog.

```bash
oc patch wanakucamelroute test-greeting-tool -n "${WANAKU_NAMESPACE}" \
  --type=merge \
  -p '{
    "spec": {
      "mcp": {
        "tools": [
          {
            "name": "test-greeting",
            "routeId": "greeting-route",
            "description": "An updated test greeting tool",
            "properties": [
              {"name": "message", "type": "string", "description": "The greeting message", "required": true}
            ]
          },
          {
            "name": "test-farewell",
            "routeId": "greeting-route",
            "description": "A farewell tool added via patch",
            "properties": [
              {"name": "name", "type": "string", "description": "The person to farewell", "required": true}
            ]
          }
        ]
      }
    }
  }'
```

**Verification - Wait for reconciliation to complete:**

```bash
# Give the operator a moment to process the update
sleep 10

# Verify the CR is still Ready
READY_STATUS=$(oc get wanakucamelroute test-greeting-tool -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}')
if [ "${READY_STATUS}" != "True" ]; then
  echo "FAIL: CR is not Ready after patch (status: ${READY_STATUS})"
  exit 1
fi
echo "PASS: CR is still Ready after patch"
```

### Test 7.2: Verify updated registeredTools

```bash
TOOLS=$(oc get wanakucamelroute test-greeting-tool -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.status.registeredTools[*]}')
echo "registeredTools=${TOOLS}"

echo "${TOOLS}" | grep -q "test-greeting" && echo "PASS: test-greeting still in registeredTools" || { echo "FAIL: test-greeting missing after patch"; exit 1; }
echo "${TOOLS}" | grep -q "test-farewell" && echo "PASS: test-farewell added to registeredTools" || { echo "FAIL: test-farewell not in registeredTools after patch"; exit 1; }
```

### Test 7.3: Verify operator logs show re-reconciliation

```bash
OPERATOR_POD=$(oc get pods -l app.kubernetes.io/name=wanaku-operator \
  -n "${WANAKU_NAMESPACE}" -o jsonpath='{.items[0].metadata.name}')

# Count reconciliation entries for the patched CR — should be more than 1
RECON_COUNT=$(oc logs "${OPERATOR_POD}" -n "${WANAKU_NAMESPACE}" \
  | grep -c "Starting CamelRoute reconciliation for test-greeting-tool" || echo "0")
echo "reconciliation-count=${RECON_COUNT}"

if [ "${RECON_COUNT}" -ge 2 ]; then
  echo "PASS: multiple reconciliations detected for test-greeting-tool"
else
  echo "FAIL: expected at least 2 reconciliations, got ${RECON_COUNT}"
fi
```

---

## Phase 8: WanakuCamelRoute Negative Tests

### Test 8.1: Missing routerRef

**Description:** A CamelRoute without `routerRef` should not become Ready.

```bash
cat <<'EOF' | oc apply -n "${WANAKU_NAMESPACE}" -f -
apiVersion: wanaku.ai/v1alpha1
kind: WanakuCamelRoute
metadata:
  name: bad-no-router-ref
spec:
  route:
    - route:
        id: bad-route
        from:
          uri: direct:bad
          steps:
            - log:
                message: "Should not deploy"
  mcp:
    tools:
      - name: bad-tool
        routeId: bad-route
        description: "A tool that should fail"
EOF

# Poll until the CR has a status or timeout
END=$((SECONDS + 30))
while [ $SECONDS -lt $END ]; do
  STATUS=$(oc get wanakucamelroute bad-no-router-ref -n "${WANAKU_NAMESPACE}" \
    -o jsonpath='{.status}' 2>/dev/null || echo "")
  [ -n "${STATUS}" ] && break
  sleep 3
done

READY_STATUS=$(oc get wanakucamelroute bad-no-router-ref -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}' 2>/dev/null || echo "")
if [ "${READY_STATUS}" = "True" ]; then
  echo "FAIL: CamelRoute without routerRef should not become Ready"
  exit 1
fi
echo "PASS: CamelRoute without routerRef is not Ready"

# Verify operator logged the error
OPERATOR_POD=$(oc get pods -l app.kubernetes.io/name=wanaku-operator \
  -n "${WANAKU_NAMESPACE}" -o jsonpath='{.items[0].metadata.name}')
oc logs "${OPERATOR_POD}" -n "${WANAKU_NAMESPACE}" --tail=30 | grep -q "routerRef must be specified" && \
  echo "PASS: operator logged routerRef validation error" || \
  echo "INFO: routerRef validation error not in recent logs"

# Cleanup
oc delete wanakucamelroute bad-no-router-ref -n "${WANAKU_NAMESPACE}" --ignore-not-found=true
```

### Test 8.2: Missing route

**Description:** A CamelRoute without `route` should not become Ready.

```bash
cat <<'EOF' | oc apply -n "${WANAKU_NAMESPACE}" -f -
apiVersion: wanaku.ai/v1alpha1
kind: WanakuCamelRoute
metadata:
  name: bad-no-route
spec:
  routerRef: wanaku-test-router
  mcp:
    tools:
      - name: bad-tool
        routeId: missing-route
        description: "A tool with no route"
EOF

END=$((SECONDS + 30))
while [ $SECONDS -lt $END ]; do
  STATUS=$(oc get wanakucamelroute bad-no-route -n "${WANAKU_NAMESPACE}" \
    -o jsonpath='{.status}' 2>/dev/null || echo "")
  [ -n "${STATUS}" ] && break
  sleep 3
done

READY_STATUS=$(oc get wanakucamelroute bad-no-route -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}' 2>/dev/null || echo "")
if [ "${READY_STATUS}" = "True" ]; then
  echo "FAIL: CamelRoute without route should not become Ready"
  exit 1
fi
echo "PASS: CamelRoute without route is not Ready"

OPERATOR_POD=$(oc get pods -l app.kubernetes.io/name=wanaku-operator \
  -n "${WANAKU_NAMESPACE}" -o jsonpath='{.items[0].metadata.name}')
oc logs "${OPERATOR_POD}" -n "${WANAKU_NAMESPACE}" --tail=30 | grep -q "route must be specified" && \
  echo "PASS: operator logged route validation error" || \
  echo "INFO: route validation error not in recent logs"

oc delete wanakucamelroute bad-no-route -n "${WANAKU_NAMESPACE}" --ignore-not-found=true
```

### Test 8.3: Missing mcp

**Description:** A CamelRoute without `mcp` should not become Ready.

```bash
cat <<'EOF' | oc apply -n "${WANAKU_NAMESPACE}" -f -
apiVersion: wanaku.ai/v1alpha1
kind: WanakuCamelRoute
metadata:
  name: bad-no-mcp
spec:
  routerRef: wanaku-test-router
  route:
    - route:
        id: orphan-route
        from:
          uri: direct:orphan
          steps:
            - log:
                message: "No MCP"
EOF

END=$((SECONDS + 30))
while [ $SECONDS -lt $END ]; do
  STATUS=$(oc get wanakucamelroute bad-no-mcp -n "${WANAKU_NAMESPACE}" \
    -o jsonpath='{.status}' 2>/dev/null || echo "")
  [ -n "${STATUS}" ] && break
  sleep 3
done

READY_STATUS=$(oc get wanakucamelroute bad-no-mcp -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}' 2>/dev/null || echo "")
if [ "${READY_STATUS}" = "True" ]; then
  echo "FAIL: CamelRoute without mcp should not become Ready"
  exit 1
fi
echo "PASS: CamelRoute without mcp is not Ready"

OPERATOR_POD=$(oc get pods -l app.kubernetes.io/name=wanaku-operator \
  -n "${WANAKU_NAMESPACE}" -o jsonpath='{.items[0].metadata.name}')
oc logs "${OPERATOR_POD}" -n "${WANAKU_NAMESPACE}" --tail=30 | grep -q "mcp must define at least one tool or resource" && \
  echo "PASS: operator logged mcp validation error" || \
  echo "INFO: mcp validation error not in recent logs"

oc delete wanakucamelroute bad-no-mcp -n "${WANAKU_NAMESPACE}" --ignore-not-found=true
```

### Test 8.4: Empty mcp (tools and resources both empty)

**Description:** A CamelRoute with empty tools and resources lists should not become Ready.

```bash
cat <<'EOF' | oc apply -n "${WANAKU_NAMESPACE}" -f -
apiVersion: wanaku.ai/v1alpha1
kind: WanakuCamelRoute
metadata:
  name: bad-empty-mcp
spec:
  routerRef: wanaku-test-router
  route:
    - route:
        id: empty-mcp-route
        from:
          uri: direct:empty
          steps:
            - log:
                message: "Empty MCP"
  mcp:
    tools: []
    resources: []
EOF

END=$((SECONDS + 30))
while [ $SECONDS -lt $END ]; do
  STATUS=$(oc get wanakucamelroute bad-empty-mcp -n "${WANAKU_NAMESPACE}" \
    -o jsonpath='{.status}' 2>/dev/null || echo "")
  [ -n "${STATUS}" ] && break
  sleep 3
done

READY_STATUS=$(oc get wanakucamelroute bad-empty-mcp -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}' 2>/dev/null || echo "")
if [ "${READY_STATUS}" = "True" ]; then
  echo "FAIL: CamelRoute with empty mcp should not become Ready"
  exit 1
fi
echo "PASS: CamelRoute with empty mcp is not Ready"

OPERATOR_POD=$(oc get pods -l app.kubernetes.io/name=wanaku-operator \
  -n "${WANAKU_NAMESPACE}" -o jsonpath='{.items[0].metadata.name}')
oc logs "${OPERATOR_POD}" -n "${WANAKU_NAMESPACE}" --tail=30 | grep -q "mcp must define at least one tool or resource" && \
  echo "PASS: operator logged empty mcp validation error" || \
  echo "INFO: empty mcp validation error not in recent logs"

oc delete wanakucamelroute bad-empty-mcp -n "${WANAKU_NAMESPACE}" --ignore-not-found=true
```

### Test 8.5: Non-existent routerRef

**Description:** A CamelRoute referencing a WanakuRouter that does not exist should not become Ready.

```bash
cat <<'EOF' | oc apply -n "${WANAKU_NAMESPACE}" -f -
apiVersion: wanaku.ai/v1alpha1
kind: WanakuCamelRoute
metadata:
  name: bad-missing-router
spec:
  routerRef: non-existent-router
  route:
    - route:
        id: missing-router-route
        from:
          uri: direct:missing
          steps:
            - log:
                message: "Router does not exist"
  mcp:
    tools:
      - name: missing-router-tool
        routeId: missing-router-route
        description: "A tool referencing a non-existent router"
EOF

END=$((SECONDS + 30))
while [ $SECONDS -lt $END ]; do
  STATUS=$(oc get wanakucamelroute bad-missing-router -n "${WANAKU_NAMESPACE}" \
    -o jsonpath='{.status}' 2>/dev/null || echo "")
  [ -n "${STATUS}" ] && break
  sleep 3
done

READY_STATUS=$(oc get wanakucamelroute bad-missing-router -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}' 2>/dev/null || echo "")
if [ "${READY_STATUS}" = "True" ]; then
  echo "FAIL: CamelRoute with non-existent routerRef should not become Ready"
  exit 1
fi
echo "PASS: CamelRoute with non-existent routerRef is not Ready"

OPERATOR_POD=$(oc get pods -l app.kubernetes.io/name=wanaku-operator \
  -n "${WANAKU_NAMESPACE}" -o jsonpath='{.items[0].metadata.name}')
oc logs "${OPERATOR_POD}" -n "${WANAKU_NAMESPACE}" --tail=30 | grep -q "non-existent-router" && \
  echo "PASS: operator logged missing router error" || \
  echo "INFO: missing router error not in recent logs"

oc delete wanakucamelroute bad-missing-router -n "${WANAKU_NAMESPACE}" --ignore-not-found=true
```

---

## Phase 9: WanakuCamelRoute Deletion and Cleanup Verification

### Test 9.1: Delete the greeting tool CR and verify catalog removal

```bash
oc delete wanakucamelroute test-greeting-tool -n "${WANAKU_NAMESPACE}"
wait_for_deletion wanakucamelroute test-greeting-tool "${WANAKU_NAMESPACE}" 60

# Verify catalog was removed from router
ROUTER_POD=$(oc get pods -l app.kubernetes.io/name=wanaku-test-router-mcp-router \
  -n "${WANAKU_NAMESPACE}" -o jsonpath='{.items[0].metadata.name}')

sleep 5
CATALOG_RESPONSE=$(oc exec "${ROUTER_POD}" -n "${WANAKU_NAMESPACE}" -- \
  curl -sf http://localhost:8080/api/v1/service-catalog)

if echo "${CATALOG_RESPONSE}" | jq -e '.data[] | select(.name == "test-greeting-tool")' > /dev/null 2>&1; then
  echo "FAIL: test-greeting-tool catalog still exists in router after CR deletion"
  exit 1
fi
echo "PASS: test-greeting-tool catalog removed from router"

# Verify other catalogs are unaffected
echo "${CATALOG_RESPONSE}" | jq -e '.data[] | select(.name == "test-info-resource")' > /dev/null 2>&1 && \
  echo "PASS: test-info-resource catalog is unaffected" || \
  echo "FAIL: test-info-resource catalog was unexpectedly removed"

echo "${CATALOG_RESPONSE}" | jq -e '.data[] | select(.name == "test-combined-cr")' > /dev/null 2>&1 && \
  echo "PASS: test-combined-cr catalog is unaffected" || \
  echo "FAIL: test-combined-cr catalog was unexpectedly removed"
```

### Test 9.2: Delete the info resource CR and verify catalog removal

```bash
oc delete wanakucamelroute test-info-resource -n "${WANAKU_NAMESPACE}"
wait_for_deletion wanakucamelroute test-info-resource "${WANAKU_NAMESPACE}" 60

sleep 5
CATALOG_RESPONSE=$(oc exec "${ROUTER_POD}" -n "${WANAKU_NAMESPACE}" -- \
  curl -sf http://localhost:8080/api/v1/service-catalog)

if echo "${CATALOG_RESPONSE}" | jq -e '.data[] | select(.name == "test-info-resource")' > /dev/null 2>&1; then
  echo "FAIL: test-info-resource catalog still exists in router after CR deletion"
  exit 1
fi
echo "PASS: test-info-resource catalog removed from router"

echo "${CATALOG_RESPONSE}" | jq -e '.data[] | select(.name == "test-combined-cr")' > /dev/null 2>&1 && \
  echo "PASS: test-combined-cr catalog is unaffected" || \
  echo "FAIL: test-combined-cr catalog was unexpectedly removed"
```

### Test 9.3: Delete the combined CR and verify catalog removal

```bash
oc delete wanakucamelroute test-combined-cr -n "${WANAKU_NAMESPACE}"
wait_for_deletion wanakucamelroute test-combined-cr "${WANAKU_NAMESPACE}" 60

sleep 5
CATALOG_RESPONSE=$(oc exec "${ROUTER_POD}" -n "${WANAKU_NAMESPACE}" -- \
  curl -sf http://localhost:8080/api/v1/service-catalog)

if echo "${CATALOG_RESPONSE}" | jq -e '.data[] | select(.name == "test-combined-cr")' > /dev/null 2>&1; then
  echo "FAIL: test-combined-cr catalog still exists in router after CR deletion"
  exit 1
fi
echo "PASS: test-combined-cr catalog removed from router"
```

### Test 9.4: Verify operator logs show cleanup messages

```bash
OPERATOR_POD=$(oc get pods -l app.kubernetes.io/name=wanaku-operator \
  -n "${WANAKU_NAMESPACE}" -o jsonpath='{.items[0].metadata.name}')

for CR_NAME in test-greeting-tool test-info-resource test-combined-cr; do
  oc logs "${OPERATOR_POD}" -n "${WANAKU_NAMESPACE}" | grep -q "Cleaning up CamelRoute for ${CR_NAME}" && \
    echo "PASS: cleanup logged for ${CR_NAME}" || \
    echo "FAIL: cleanup not logged for ${CR_NAME}"
done
```

---

## Phase 10: Post-Deletion Verification

### Test 10.1: Verify operator pod is still running with no unexpected restarts

```bash
OPERATOR_POD=$(oc get pods -l app.kubernetes.io/name=wanaku-operator \
  -n "${WANAKU_NAMESPACE}" -o jsonpath='{.items[0].metadata.name}')

POD_STATUS=$(oc get pod "${OPERATOR_POD}" -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.status.phase}')
RESTART_COUNT=$(oc get pod "${OPERATOR_POD}" -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.status.containerStatuses[0].restartCount}')

echo "operator-pod-status=${POD_STATUS}"
echo "operator-restart-count=${RESTART_COUNT}"

if [ "${POD_STATUS}" != "Running" ]; then
  echo "FAIL: operator pod is not Running (status: ${POD_STATUS})"
  exit 1
fi
echo "PASS: operator pod is Running"

if [ "${RESTART_COUNT}" -gt 0 ]; then
  echo "WARN: operator restarted ${RESTART_COUNT} time(s) — investigate"
else
  echo "PASS: operator has zero restarts"
fi
```

### Test 10.2: Verify no WanakuCamelRoute resources remain

```bash
REMAINING=$(oc get wanakucamelroute -n "${WANAKU_NAMESPACE}" --no-headers 2>/dev/null | wc -l | tr -d ' ')
echo "remaining-camelroutes=${REMAINING}"

if [ "${REMAINING}" != "0" ]; then
  echo "FAIL: ${REMAINING} WanakuCamelRoute resource(s) still exist"
  oc get wanakucamelroute -n "${WANAKU_NAMESPACE}"
  exit 1
fi
echo "PASS: no WanakuCamelRoute resources remain"
```

### Test 10.3: Verify operator logs have no unexpected errors

**Description:** Filter operator logs for errors, excluding expected validation errors from Phase 8 negative tests.

```bash
OPERATOR_POD=$(oc get pods -l app.kubernetes.io/name=wanaku-operator \
  -n "${WANAKU_NAMESPACE}" -o jsonpath='{.items[0].metadata.name}')

UNEXPECTED_ERRORS=$(oc logs "${OPERATOR_POD}" -n "${WANAKU_NAMESPACE}" \
  | grep -i "error\|exception" \
  | grep -iv "routerRef must be specified" \
  | grep -iv "route must be specified" \
  | grep -iv "mcp must define at least one tool or resource" \
  | grep -iv "not found in namespace" \
  | grep -iv "non-existent-router" \
  | grep -iv "Failed to remove service catalog" \
  | wc -l | tr -d ' ')

echo "unexpected-error-count=${UNEXPECTED_ERRORS}"

if [ "${UNEXPECTED_ERRORS}" -gt 0 ]; then
  echo "WARN: ${UNEXPECTED_ERRORS} unexpected error(s) in operator logs — review below:"
  oc logs "${OPERATOR_POD}" -n "${WANAKU_NAMESPACE}" \
    | grep -i "error\|exception" \
    | grep -iv "routerRef must be specified" \
    | grep -iv "route must be specified" \
    | grep -iv "mcp must define at least one tool or resource" \
    | grep -iv "not found in namespace" \
    | grep -iv "non-existent-router" \
    | grep -iv "Failed to remove service catalog" \
    | tail -10
else
  echo "PASS: no unexpected errors in operator logs"
fi
```

---

## Phase 11: Cleanup

### Step 11.1: Delete remaining WanakuCamelRoute resources

Delete any leftover CamelRoute CRs before the common cleanup (which does not handle this resource type).

```bash
oc delete wanakucamelroute --all -n "${WANAKU_NAMESPACE}" --ignore-not-found=true
```

### Step 11.2: Delete the WanakuRouter CR

```bash
oc delete wanakurouter wanaku-test-router -n "${WANAKU_NAMESPACE}" --ignore-not-found=true

wait_for_deletion deployment wanaku-test-router-mcp-router "${WANAKU_NAMESPACE}" 60
wait_for_deletion service internal-wanaku-test-router "${WANAKU_NAMESPACE}" 30
wait_for_deletion route wanaku-test-router "${WANAKU_NAMESPACE}" 30
```

### Step 11.3: Full teardown

Follow [common/cleanup.md](common/cleanup.md) for full teardown.

---

## Test Summary Matrix

| Phase | Test ID | Test Name | Priority |
|-------|---------|-----------|----------|
| 0 | 0.1-0.2 | OpenShift login (MANUAL) | Critical |
| 1 | 1.1-1.2 | Environment setup | Critical |
| 2 | 2.1-2.2 | Operator installation + CamelRoute CRD/RBAC | Critical |
| 3 | 3.1-3.3 | WanakuRouter setup and readiness | Critical |
| 4 | 4.1-4.7 | CamelRoute happy path - tool | Critical |
| 5 | 5.1-5.5 | CamelRoute happy path - resource | Critical |
| 6 | 6.1-6.4 | CamelRoute happy path - combined (tools + resources + properties) | High |
| 7 | 7.1-7.3 | CamelRoute update reconciliation | Medium |
| 8 | 8.1-8.5 | CamelRoute negative tests (validation errors) | High |
| 9 | 9.1-9.4 | CamelRoute deletion and catalog removal | Critical |
| 10 | 10.1-10.3 | Post-deletion verification | Medium |
| 11 | 11.1-11.3 | Cleanup | Critical |
