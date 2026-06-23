# Test Plan: WanakuCamelRoute CRD on OpenShift

## Overview

This test plan verifies the WanakuCamelRoute CRD feature on OpenShift. The WanakuCamelRoute lets users define Apache Camel routes and MCP tool/resource metadata inline in a Kubernetes CR. The operator packages the route into a service catalog ZIP and deploys it to the router via REST API.

Every step is fully automatable.

## Prerequisites

### Required tools

| Tool | Minimum version | Verify command |
|------|-----------------|----------------|
| `oc` | 4.12+ | `oc version --client` |
| `helm` | 3.x | `helm version --short` |
| `curl` | any | `curl --version` |
| `jq` | 1.6+ | `jq --version` |
| `base64` | any (coreutils) | `base64 --version 2>/dev/null \|\| echo "available"` |
| `wanaku` | 0.2.0+ | `wanaku --version` |

### Prerequisite check script

```bash
#!/bin/bash
set -e

FAIL=0

for CMD in oc helm curl jq base64 wanaku; do
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
# OIDC client secret for operator-to-router authentication (defaults to "mypasswd")
export WANAKU_OIDC_CLIENT_SECRET="${WANAKU_OIDC_CLIENT_SECRET:-mypasswd}"
```

### Helper: wait for resource deletion

Follow [common/wait-for-deletion.md](common/wait-for-deletion.md) to define the `wait_for_deletion` function.

### Helper: obtain a Bearer token from Keycloak

The router has OIDC auth enabled, so all API requests (even from inside the pod) require a Bearer token. This helper obtains a token from Keycloak using the `wanaku-service` client credentials. It uses `oc exec` into the Keycloak pod since the Keycloak route may not be accessible from outside the cluster.

```bash
get_router_token() {
  local KC_POD
  KC_POD=$(oc get pods -l app=keycloak \
    -n "${WANAKU_NAMESPACE}" -o jsonpath='{.items[0].metadata.name}')

  oc exec "${KC_POD}" -n "${WANAKU_NAMESPACE}" -- \
    curl -sf \
      -d "client_id=wanaku-service" \
      -d "client_secret=${WANAKU_OIDC_CLIENT_SECRET}" \
      -d "grant_type=client_credentials" \
      "http://localhost:8080/realms/wanaku/protocol/openid-connect/token" \
    | jq -r '.access_token'
}
```

### Helper: query router REST API via oc exec

Uses `oc exec` into the router pod with a Bearer token obtained from Keycloak. Health endpoints (`/q/health/*`) do not require auth and are queried without a token.

```bash
query_router_api() {
  local ENDPOINT="$1"
  local ROUTER_POD
  ROUTER_POD=$(oc get pods -l app=wanaku-test-router-mcp-router \
    -n "${WANAKU_NAMESPACE}" -o jsonpath='{.items[0].metadata.name}')

  local TOKEN
  TOKEN=$(get_router_token)
  if [ -z "${TOKEN}" ] || [ "${TOKEN}" = "null" ]; then
    echo "ERROR: failed to obtain Bearer token from Keycloak" >&2
    return 1
  fi

  oc exec "${ROUTER_POD}" -n "${WANAKU_NAMESPACE}" -- \
    curl -sf -H "Authorization: Bearer ${TOKEN}" "http://localhost:8080${ENDPOINT}"
}
```

### Helper: port-forward for MCP CLI access

MCP verification tests use the `wanaku mcp` CLI to connect to the router's public MCP endpoint. This requires port-forwarding from the local machine to the router pod.

```bash
start_mcp_port_forward() {
  local ROUTER_POD
  ROUTER_POD=$(oc get pods -l app=wanaku-test-router-mcp-router \
    -n "${WANAKU_NAMESPACE}" -o jsonpath='{.items[0].metadata.name}')

  oc port-forward "${ROUTER_POD}" 18080:8080 -n "${WANAKU_NAMESPACE}" > /dev/null 2>&1 &
  MCP_PORT_FWD_PID=$!
  sleep 3

  if ! kill -0 "${MCP_PORT_FWD_PID}" 2>/dev/null; then
    echo "FAIL: port-forward process died"
    return 1
  fi
  echo "PASS: port-forward started (PID ${MCP_PORT_FWD_PID})"
}

stop_mcp_port_forward() {
  if [ -n "${MCP_PORT_FWD_PID}" ]; then
    kill "${MCP_PORT_FWD_PID}" 2>/dev/null
    wait "${MCP_PORT_FWD_PID}" 2>/dev/null
    unset MCP_PORT_FWD_PID
  fi
}

MCP_URI="http://localhost:18080/public/mcp"
```

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

**Verification - Wait for pod to schedule and start (fail fast if stuck):**

```bash
# Wait for the pod to exist
sleep 5
ROUTER_POD=$(oc get pods -l app=wanaku-test-router-mcp-router \
  -n "${WANAKU_NAMESPACE}" -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")

if [ -z "${ROUTER_POD}" ]; then
  echo "FAIL: no router pod found after 5s — operator may not have created the deployment"
  exit 1
fi

# Fail fast if pod is stuck in Pending/ContainerCreating for more than 60s
oc wait pod "${ROUTER_POD}" --for=condition=ContainersReady --timeout=60s -n "${WANAKU_NAMESPACE}" 2>/dev/null || {
  POD_PHASE=$(oc get pod "${ROUTER_POD}" -n "${WANAKU_NAMESPACE}" -o jsonpath='{.status.phase}')
  CONTAINER_STATE=$(oc get pod "${ROUTER_POD}" -n "${WANAKU_NAMESPACE}" \
    -o jsonpath='{.status.containerStatuses[0].state}' 2>/dev/null || echo "unknown")
  echo "FAIL: router pod not ready after 60s (phase=${POD_PHASE}, state=${CONTAINER_STATE})"
  echo "This usually indicates a cluster-level problem (image pull, PVC, scheduling)."
  oc describe pod "${ROUTER_POD}" -n "${WANAKU_NAMESPACE}" | tail -20
  exit 1
}
echo "PASS: router pod containers are ready"
```

**Wait for Ready condition on the CR:**

```bash
oc wait wanakurouter/wanaku-test-router \
  --for=condition=Ready \
  --timeout=60s \
  -n "${WANAKU_NAMESPACE}"
# Expected output: wanakurouter.wanaku.ai/wanaku-test-router condition met
```

### Test 3.2: Verify router deployment is available

```bash
oc wait deployment/wanaku-test-router-mcp-router \
  --for=condition=Available \
  --timeout=60s \
  -n "${WANAKU_NAMESPACE}"
echo "router-deployment-available=$?"
# Expected: router-deployment-available=0
```

### Test 3.3: Verify router REST API is reachable

```bash
ROUTER_POD=$(oc get pods -l app=wanaku-test-router-mcp-router \
  -n "${WANAKU_NAMESPACE}" -o jsonpath='{.items[0].metadata.name}')

MAX_RETRIES=12
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
CATALOG_RESPONSE=$(query_router_api /api/v1/service-catalog)

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

### Test 4.8: Verify operator used OIDC authentication for catalog deployment

**Description:** Since the router has auth enabled (`authServer: "http://keycloak:8080"`), the operator must obtain an OIDC token before deploying catalogs. Verify by checking that the operator did NOT get a 302 redirect (which would indicate missing auth), and that the deployment succeeded. With `DEBUG` logging enabled, the token request is also logged.

```bash
OPERATOR_POD=$(oc get pods -l app.kubernetes.io/name=wanaku-operator \
  -n "${WANAKU_NAMESPACE}" -o jsonpath='{.items[0].metadata.name}')

# The operator should NOT have logged a 302 redirect error
if oc logs "${OPERATOR_POD}" -n "${WANAKU_NAMESPACE}" | grep -q "HTTP 302"; then
  echo "FAIL: operator received HTTP 302 — OIDC authentication may not be working"
  exit 1
fi
echo "PASS: no HTTP 302 errors in operator logs"

# With DEBUG logging, the token request is visible
if oc logs "${OPERATOR_POD}" -n "${WANAKU_NAMESPACE}" | grep -q "Requesting OIDC token"; then
  echo "PASS: OIDC token request logged (DEBUG level)"
else
  echo "INFO: OIDC token request not in logs (enable DEBUG logging to see it)"
fi
```

### Test 4.9: Verify tool is listed and callable via MCP client

**Description:** Use the `wanaku mcp` CLI to verify the deployed tool is listed and returns a meaningful response when invoked.

```bash
start_mcp_port_forward

# Verify tool is listed
TOOL_LIST=$(wanaku mcp tool list --uri "${MCP_URI}" 2>&1)
echo "MCP tool list output:"
echo "${TOOL_LIST}"

if ! echo "${TOOL_LIST}" | grep -q "test-greeting"; then
  echo "FAIL: test-greeting tool not found in MCP tool list"
  stop_mcp_port_forward
  exit 1
fi
echo "PASS: test-greeting tool visible via MCP"

# Invoke the tool and verify a meaningful response
TOOL_RESPONSE=$(wanaku mcp tool --uri "${MCP_URI}" --name test-greeting --param message=hello 2>&1)
echo "MCP tool invocation response:"
echo "${TOOL_RESPONSE}"

if echo "${TOOL_RESPONSE}" | grep -qi "hello"; then
  echo "PASS: test-greeting tool returned a meaningful response"
else
  echo "FAIL: test-greeting tool response does not contain expected content"
  stop_mcp_port_forward
  exit 1
fi

stop_mcp_port_forward
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
CATALOG_RESPONSE=$(query_router_api /api/v1/service-catalog)

if echo "${CATALOG_RESPONSE}" | jq -e '.data[] | select(.name == "test-info-resource")' > /dev/null 2>&1; then
  echo "PASS: test-info-resource catalog found in router"
else
  echo "FAIL: test-info-resource catalog not found in router response"
  exit 1
fi
```

### Test 5.6: Verify resource is registered and tool still visible

**Description:** Verify the deployed resource is registered with the router (via REST API) and that previously deployed tools remain visible via MCP.

**Note:** `wanaku mcp resource list` may fail in native image builds due to missing GraalVM reflection config for `McpResource` (see [#1406](https://github.com/wanaku-ai/wanaku/issues/1406)). Resource registration is verified via the REST API instead.

```bash
# Verify resource is registered via REST API
RESOURCES_RESPONSE=$(query_router_api /api/v1/resources)
echo "Router resources:"
echo "${RESOURCES_RESPONSE}"

if echo "${RESOURCES_RESPONSE}" | jq -e '.data[] | select(.name == "test-info")' > /dev/null 2>&1; then
  echo "PASS: test-info resource registered in router"
else
  echo "FAIL: test-info resource not found in router"
  exit 1
fi

# Verify the previously deployed tool is still visible via MCP
start_mcp_port_forward

TOOL_LIST=$(wanaku mcp tool list --uri "${MCP_URI}" 2>&1)
if echo "${TOOL_LIST}" | grep -q "test-greeting"; then
  echo "PASS: test-greeting tool still visible via MCP"
else
  echo "FAIL: test-greeting tool disappeared from MCP tool list"
  stop_mcp_port_forward
  exit 1
fi

stop_mcp_port_forward
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
CATALOG_RESPONSE=$(query_router_api /api/v1/service-catalog)

if echo "${CATALOG_RESPONSE}" | jq -e '.data[] | select(.name == "test-combined-cr")' > /dev/null 2>&1; then
  echo "PASS: test-combined-cr catalog found in router"
else
  echo "FAIL: test-combined-cr catalog not found in router response"
  exit 1
fi
```

### Test 6.5: Verify all tools are callable and resources are registered

**Description:** Verify all deployed tools are visible and callable via MCP, and all resources are registered in the router.

**Note:** Resource listing via MCP CLI may fail in native builds ([#1406](https://github.com/wanaku-ai/wanaku/issues/1406)). Resources are verified via REST API.

```bash
start_mcp_port_forward

TOOL_LIST=$(wanaku mcp tool list --uri "${MCP_URI}" 2>&1)
echo "MCP tool list:"
echo "${TOOL_LIST}"

FAIL=0

# Verify all tools are listed via MCP
echo "${TOOL_LIST}" | grep -q "test-greeting" && \
  echo "PASS: test-greeting listed" || { echo "FAIL: test-greeting not found"; FAIL=1; }
echo "${TOOL_LIST}" | grep -q "combined-tool" && \
  echo "PASS: combined-tool listed" || { echo "FAIL: combined-tool not found"; FAIL=1; }

# Invoke combined-tool and verify response
COMBINED_TOOL_RESPONSE=$(wanaku mcp tool --uri "${MCP_URI}" --name combined-tool --param input=test 2>&1)
echo "combined-tool response: ${COMBINED_TOOL_RESPONSE}"
echo "${COMBINED_TOOL_RESPONSE}" | grep -qi "test\|combined\|result" && \
  echo "PASS: combined-tool returned meaningful response" || { echo "FAIL: combined-tool response unexpected"; FAIL=1; }

stop_mcp_port_forward

# Verify resources via REST API
RESOURCES_RESPONSE=$(query_router_api /api/v1/resources)
echo "Router resources:"
echo "${RESOURCES_RESPONSE}"

echo "${RESOURCES_RESPONSE}" | jq -e '.data[] | select(.name == "test-info")' > /dev/null 2>&1 && \
  echo "PASS: test-info resource registered" || { echo "FAIL: test-info not found"; FAIL=1; }
echo "${RESOURCES_RESPONSE}" | jq -e '.data[] | select(.name == "combined-resource")' > /dev/null 2>&1 && \
  echo "PASS: combined-resource registered" || { echo "FAIL: combined-resource not found"; FAIL=1; }

if [ "${FAIL}" -ne 0 ]; then
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

sleep 5
CATALOG_RESPONSE=$(query_router_api /api/v1/service-catalog)

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
CATALOG_RESPONSE=$(query_router_api /api/v1/service-catalog)

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
CATALOG_RESPONSE=$(query_router_api /api/v1/service-catalog)

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

### Test 9.5: Verify tools and resources are gone after deletion

**Description:** After all CamelRoute CRs are deleted, verify tools are removed from MCP and resources from the REST API.

```bash
start_mcp_port_forward

TOOL_LIST=$(wanaku mcp tool list --uri "${MCP_URI}" 2>&1)
echo "MCP tool list after deletion:"
echo "${TOOL_LIST}"

FAIL=0

# Verify tools are no longer listed via MCP
echo "${TOOL_LIST}" | grep -q "test-greeting" && \
  { echo "FAIL: test-greeting still in MCP tool list"; FAIL=1; } || \
  echo "PASS: test-greeting removed from MCP"

echo "${TOOL_LIST}" | grep -q "combined-tool" && \
  { echo "FAIL: combined-tool still in MCP tool list"; FAIL=1; } || \
  echo "PASS: combined-tool removed from MCP"

# Verify tool list is empty (skip invoke — CLI hangs when no tools exist)
if echo "${TOOL_LIST}" | grep -qi "No tools found"; then
  echo "PASS: tool list confirms no tools remain"
fi

stop_mcp_port_forward

# Verify resources are gone via REST API
RESOURCES_RESPONSE=$(query_router_api /api/v1/resources)
echo "Router resources after deletion:"
echo "${RESOURCES_RESPONSE}"

echo "${RESOURCES_RESPONSE}" | jq -e '.data[] | select(.name == "test-info")' > /dev/null 2>&1 && \
  { echo "FAIL: test-info still in router"; FAIL=1; } || \
  echo "PASS: test-info removed from router"

echo "${RESOURCES_RESPONSE}" | jq -e '.data[] | select(.name == "combined-resource")' > /dev/null 2>&1 && \
  { echo "FAIL: combined-resource still in router"; FAIL=1; } || \
  echo "PASS: combined-resource removed from router"

if [ "${FAIL}" -ne 0 ]; then
  exit 1
fi
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
| 0 | — | OpenShift login | Critical |
| 1 | 1.1-1.2 | Environment setup | Critical |
| 2 | 2.1-2.2 | Operator installation + CamelRoute CRD/RBAC | Critical |
| 3 | 3.1-3.3 | WanakuRouter setup and readiness | Critical |
| 4 | 4.1-4.8 | CamelRoute happy path - tool (REST API verification) | Critical |
| 4 | 4.9 | CamelRoute tool visible via MCP client | Critical |
| 5 | 5.1-5.5 | CamelRoute happy path - resource (REST API verification) | Critical |
| 5 | 5.6 | CamelRoute resource registered + tool still visible | Critical |
| 6 | 6.1-6.4 | CamelRoute happy path - combined (REST API verification) | High |
| 6 | 6.5 | Combined tools callable + resources registered | High |
| 7 | 7.1-7.3 | CamelRoute update reconciliation | Medium |
| 8 | 8.1-8.5 | CamelRoute negative tests (validation errors) | High |
| 9 | 9.1-9.4 | CamelRoute deletion and catalog removal (REST API) | Critical |
| 9 | 9.5 | Tools gone from MCP + resources gone from REST API | Critical |
| 10 | 10.1-10.3 | Post-deletion verification | Medium |
| 11 | 11.1-11.3 | Cleanup | Critical |
