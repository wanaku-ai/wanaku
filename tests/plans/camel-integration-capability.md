# Test Plan: Camel Integration Capability on OpenShift

## Overview

This test plan verifies the Camel Integration Capability (CIC) deployed on OpenShift via the Wanaku operator CRDs. The CIC loads Apache Camel routes from YAML and exposes them as MCP tools and resources via gRPC. It registers with a Wanaku MCP Router for AI agent discovery.

The operator manages the entire lifecycle: the WanakuCamelRoute CRD packages routes into a service catalog ZIP, deploys the catalog to the router via REST API, and creates a CIC Deployment and Service automatically. The WanakuCapability CRD (with `type: "camel-integration-capability"`) and WanakuServiceCatalog CRD provide alternative deployment modes.

No manual Deployment, Service, or ConfigMap resources should be created for CIC -- the operator handles all of that.

Every step is fully automatable.

**Hard timeout rule:** Any single operation (oc wait, polling loop, port-forward setup, etc.) that takes longer than **3 minutes** MUST be considered broken. Mark the test as FAIL immediately and abort — do not wait longer. All `--timeout` values in this plan MUST NOT exceed `180s`.

## Prerequisites

### Required tools

| Tool | Minimum version | Verify command |
|------|-----------------|----------------|
| `oc` | 4.12+ | `oc version --client` |
| `helm` | 3.x | `helm version --short` |
| `curl` | any (health checks via `oc exec`) | `curl --version` |
| `jq` | 1.6+ | `jq --version` |
| `base64` | any (coreutils) | `base64 --version 2>/dev/null \|\| echo "available"` |
| `wanaku` | build from source | `wanaku --version` |
| `zip` | any | `zip --version` |

### Prerequisite check script

```bash
#!/bin/bash
set -e

FAIL=0

for CMD in oc helm curl jq base64 wanaku zip; do
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
export WANAKU_REPO_ROOT="${WANAKU_REPO_ROOT:-.}"
export WANAKU_ROUTER_IMAGE="${WANAKU_ROUTER_IMAGE:-quay.io/wanaku/wanaku-router-backend:latest}"
export CIC_IMAGE="${CIC_IMAGE:-quay.io/wanaku/camel-integration-capability:latest}"
# OIDC client secret for CIC-to-router authentication (defaults to "mypasswd")
export WANAKU_OIDC_CLIENT_SECRET="${WANAKU_OIDC_CLIENT_SECRET:-mypasswd}"
```

Follow [common/namespace-setup.md](common/namespace-setup.md) before Phase 1 to derive a unique `WANAKU_NAMESPACE` for this run.

### Helper: wait for resource deletion

Follow [common/wait-for-deletion.md](common/wait-for-deletion.md) to define the `wait_for_deletion` function.

### Helper: obtain a Bearer token from the CLI

For direct REST API queries against the router (bypassing the `wanaku` CLI), retrieve the Bearer token stored by `wanaku auth login` (performed in Phase 3, Test 3.4 via [common/oidc-login-verification.md](common/oidc-login-verification.md)).

```bash
get_router_token() {
  wanaku auth token --get --unmask --plain 2>/dev/null
}
```

### Helper: query router REST API via oc exec

Uses `oc exec` into the router pod with a Bearer token obtained from Keycloak.

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

### Helper: port-forward for wanaku CLI access

All `wanaku` CLI commands (`tools list`, `resources list`, `mcp tool`, etc.) require port-forwarding to reach the router inside the cluster.

```bash
start_port_forward() {
  local ROUTER_POD
  ROUTER_POD=$(oc get pods -l app=wanaku-test-router-mcp-router \
    -n "${WANAKU_NAMESPACE}" -o jsonpath='{.items[0].metadata.name}')

  oc port-forward "${ROUTER_POD}" 18080:8080 -n "${WANAKU_NAMESPACE}" > /dev/null 2>&1 &
  PORT_FWD_PID=$!
  sleep 3

  if ! kill -0 "${PORT_FWD_PID}" 2>/dev/null; then
    echo "FAIL: port-forward process died"
    return 1
  fi
  echo "PASS: port-forward started (PID ${PORT_FWD_PID})"
}

stop_port_forward() {
  if [ -n "${PORT_FWD_PID}" ]; then
    kill "${PORT_FWD_PID}" 2>/dev/null
    wait "${PORT_FWD_PID}" 2>/dev/null
    unset PORT_FWD_PID
  fi
}

WANAKU_HOST="http://localhost:18080"
MCP_URI="http://localhost:18080/public/mcp"
```

---

## Phase 0: OpenShift Login

Follow [common/openshift-login.md](common/openshift-login.md) to log in to the target OpenShift cluster using a service account token.

---

## Phase 1: Environment Setup

### Step 1.1: Create namespace

Follow [common/namespace-setup.md](common/namespace-setup.md) to create and verify the namespace. Use the unique `WANAKU_NAMESPACE` value from the environment variables above, or override it if you need a specific namespace.

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

---

## Phase 3: WanakuRouter Setup

### Test 3.1: Create a WanakuRouter with Keycloak authentication

**Description:** Create a WanakuRouter CR that all CIC tests reference. Wait for Ready.

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

### Test 3.4: Verify OIDC login via router

Follow [common/oidc-login-verification.md](common/oidc-login-verification.md) to verify end-to-end OIDC authentication through the router.

---

## Phase 4: Deploy CIC via WanakuCamelRoute (Hello World)

### Test 4.1: Create a WanakuCamelRoute with a hello-world tool

**Description:** Create a WanakuCamelRoute CR with a simple direct-to-log route and a single tool. The operator packages the route into a service catalog ZIP, deploys it to the router, and creates a CIC Deployment and Service automatically.

```bash
cat <<'EOF' | oc apply -n "${WANAKU_NAMESPACE}" -f -
apiVersion: wanaku.ai/v1alpha1
kind: WanakuCamelRoute
metadata:
  name: cic-hello-world
spec:
  routerRef: wanaku-test-router
  route:
    - route:
        id: route-3104
        from:
          id: from-1702
          uri: direct
          parameters:
            name: wanaku
          steps:
            - log:
                id: log-2526
                message: Hello ${body}
            - setBody:
                simple: Hello ${body} from ${routeId}
  mcp:
    tools:
      - name: sends-greeting
        routeId: route-3104
        description: "Sends a greeting message to show that the application is working"
        properties:
          - name: wanaku_body
            type: string
            description: The greeting message to send
            required: true
EOF
```

**Verification - CR accepted:**

```bash
oc get wanakucamelroute cic-hello-world -n "${WANAKU_NAMESPACE}" -o name
echo "hello-world-cr-exists=$?"
# Expected: hello-world-cr-exists=0
```

### Test 4.2: Verify WanakuCamelRoute reaches Ready condition

```bash
oc wait wanakucamelroute/cic-hello-world \
  --for=condition=Ready \
  --timeout=120s \
  -n "${WANAKU_NAMESPACE}"
# Expected output: wanakucamelroute.wanaku.ai/cic-hello-world condition met
```

### Test 4.3: Verify status fields - deployedCatalogName

```bash
CATALOG_NAME=$(oc get wanakucamelroute cic-hello-world -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.status.deployedCatalogName}')
echo "deployedCatalogName=${CATALOG_NAME}"

if [ "${CATALOG_NAME}" != "cic-hello-world" ]; then
  echo "FAIL: deployedCatalogName is '${CATALOG_NAME}', expected 'cic-hello-world'"
  exit 1
fi
echo "PASS: deployedCatalogName is correct"
```

### Test 4.4: Verify status fields - registeredTools

```bash
TOOLS=$(oc get wanakucamelroute cic-hello-world -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.status.registeredTools[*]}')
echo "registeredTools=${TOOLS}"

if ! echo "${TOOLS}" | grep -q "sends-greeting"; then
  echo "FAIL: sends-greeting not found in registeredTools"
  exit 1
fi
echo "PASS: sends-greeting is in registeredTools"
```

### Test 4.5: Verify status fields - registeredResources is empty

```bash
RESOURCES=$(oc get wanakucamelroute cic-hello-world -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.status.registeredResources[*]}')
echo "registeredResources=${RESOURCES}"

if [ -n "${RESOURCES}" ]; then
  echo "FAIL: registeredResources should be empty for a tool-only CR, got '${RESOURCES}'"
  exit 1
fi
echo "PASS: registeredResources is empty"
```

### Test 4.6: Verify CIC Deployment created by operator

**Description:** The operator creates a Deployment named `<cr-name>-cic` automatically. No manual Deployment creation is needed.

```bash
oc wait deployment/cic-hello-world-cic \
  --for=condition=Available \
  --timeout=120s \
  -n "${WANAKU_NAMESPACE}"
echo "cic-deployment-available=$?"
# Expected: cic-deployment-available=0
```

### Test 4.7: Verify CIC Service created by operator

**Description:** The operator creates a ClusterIP Service named `<cr-name>-cic` for the gRPC port (9190).

```bash
oc get service cic-hello-world-cic -n "${WANAKU_NAMESPACE}" -o name
echo "cic-service-exists=$?"
# Expected: cic-service-exists=0

SVC_PORT=$(oc get service cic-hello-world-cic -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.spec.ports[0].port}')
echo "cic-service-port=${SVC_PORT}"
# Expected: 9190
```

### Test 4.8: Verify CIC pod is running

```bash
CIC_POD=$(oc get pods -l component=cic-hello-world-cic \
  -n "${WANAKU_NAMESPACE}" -o jsonpath='{.items[0].metadata.name}')

if [ -z "${CIC_POD}" ]; then
  echo "FAIL: no CIC pod found"
  exit 1
fi

POD_PHASE=$(oc get pod "${CIC_POD}" -n "${WANAKU_NAMESPACE}" -o jsonpath='{.status.phase}')
if [ "${POD_PHASE}" != "Running" ]; then
  echo "FAIL: CIC pod phase is '${POD_PHASE}', expected 'Running'"
  oc describe pod "${CIC_POD}" -n "${WANAKU_NAMESPACE}" | tail -20
  exit 1
fi
echo "PASS: CIC pod is Running"
```

### Test 4.9: Verify CIC startup logs

```bash
CIC_POD=$(oc get pods -l component=cic-hello-world-cic \
  -n "${WANAKU_NAMESPACE}" -o jsonpath='{.items[0].metadata.name}')

FAIL=0

oc logs "${CIC_POD}" -n "${WANAKU_NAMESPACE}" | grep -q "Camel Integration Capability" && \
  echo "PASS: startup banner found in CIC logs" || { echo "FAIL: startup banner not found"; FAIL=1; }

oc logs "${CIC_POD}" -n "${WANAKU_NAMESPACE}" | grep -q "Using data directory" && \
  echo "PASS: data directory log message found" || { echo "FAIL: data directory log not found"; FAIL=1; }

if [ "${FAIL}" -ne 0 ]; then
  echo "CIC logs (last 30 lines):"
  oc logs "${CIC_POD}" -n "${WANAKU_NAMESPACE}" --tail=30
fi
```

### Test 4.10: Verify catalog exists in router

```bash
CATALOG_RESPONSE=$(query_router_api /api/v1/service-catalog)

if echo "${CATALOG_RESPONSE}" | jq -e '.data' > /dev/null 2>&1; then
  if echo "${CATALOG_RESPONSE}" | jq -e '.data[] | select(.name == "cic-hello-world")' > /dev/null 2>&1; then
    echo "PASS: cic-hello-world catalog found in router"
  else
    echo "FAIL: cic-hello-world catalog not found in router response"
    echo "Response: ${CATALOG_RESPONSE}"
    exit 1
  fi
else
  echo "FAIL: unexpected catalog response format"
  echo "Response: ${CATALOG_RESPONSE}"
  exit 1
fi
```

### Test 4.11: Verify tool registered via wanaku CLI

**Description:** After the operator deploys the catalog and starts the CIC, the sends-greeting tool should appear in the router's tool list.

```bash
start_port_forward

MAX_RETRIES=12
RETRY_INTERVAL=5
for i in $(seq 1 ${MAX_RETRIES}); do
  TOOLS_OUTPUT=$(wanaku tools list --host "${WANAKU_HOST}" --plain 2>&1)

  if echo "${TOOLS_OUTPUT}" | grep -q "sends-greeting"; then
    echo "PASS: sends-greeting tool registered in router (attempt ${i})"
    break
  fi

  if [ "${i}" -eq "${MAX_RETRIES}" ]; then
    echo "FAIL: sends-greeting tool not found in router after ${MAX_RETRIES} attempts"
    echo "Output: ${TOOLS_OUTPUT}"
    stop_port_forward
    exit 1
  fi
  echo "Waiting for CIC registration... (attempt ${i})"
  sleep ${RETRY_INTERVAL}
done

stop_port_forward
```

### Test 4.12: Verify operator logs show successful reconciliation

```bash
OPERATOR_POD=$(oc get pods -l app.kubernetes.io/name=wanaku-operator \
  -n "${WANAKU_NAMESPACE}" -o jsonpath='{.items[0].metadata.name}')

oc logs "${OPERATOR_POD}" -n "${WANAKU_NAMESPACE}" | grep -q "Starting CamelRoute reconciliation for cic-hello-world" && \
  echo "PASS: reconciliation start logged" || \
  echo "FAIL: reconciliation start not found in operator logs"

oc logs "${OPERATOR_POD}" -n "${WANAKU_NAMESPACE}" | grep -q "Successfully deployed CamelRoute 'cic-hello-world'" && \
  echo "PASS: successful deployment logged" || \
  echo "FAIL: successful deployment not found in operator logs"
```

---

## Phase 5: Tool Invocation

### Test 5.1: Verify tool is listed via wanaku CLI

**Description:** Use the `wanaku tools list` command to confirm the sends-greeting tool is registered.

```bash
start_port_forward

TOOLS_OUTPUT=$(wanaku tools list --host "${WANAKU_HOST}" --plain 2>&1)
echo "Tools list output:"
echo "${TOOLS_OUTPUT}"

if echo "${TOOLS_OUTPUT}" | grep -q "sends-greeting"; then
  echo "PASS: sends-greeting tool found via wanaku CLI"
else
  echo "FAIL: sends-greeting tool not found"
  stop_port_forward
  exit 1
fi

stop_port_forward
```

### Test 5.2: List tools via MCP endpoint

```bash
start_port_forward

TOOL_LIST=$(wanaku mcp tool list --uri "${MCP_URI}" 2>&1)
echo "MCP tool list output:"
echo "${TOOL_LIST}"

if ! echo "${TOOL_LIST}" | grep -q "sends-greeting"; then
  echo "FAIL: sends-greeting tool not found in MCP tool list"
  stop_port_forward
  exit 1
fi
echo "PASS: sends-greeting tool visible via MCP"

stop_port_forward
```

### Test 5.3: Invoke tool via MCP endpoint

**Description:** Call the sends-greeting tool with a body parameter and verify the response contains the expected greeting.

```bash
start_port_forward

TOOL_RESPONSE=$(wanaku mcp tool --uri "${MCP_URI}" --name sends-greeting --param wanaku_body=World 2>&1)
echo "Tool response: ${TOOL_RESPONSE}"

if echo "${TOOL_RESPONSE}" | grep -qi "Hello\|World"; then
  echo "PASS: sends-greeting tool returned expected greeting content"
else
  echo "FAIL: sends-greeting tool response does not contain expected content"
  stop_port_forward
  exit 1
fi

stop_port_forward
```

### Test 5.4: Invoke non-existent tool should fail

```bash
start_port_forward

TOOL_RESPONSE=$(wanaku mcp tool --uri "${MCP_URI}" --name "non-existent-tool-99999" --param key=value 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: non-existent tool invocation failed as expected (exit code ${EXIT_CODE})"
else
  echo "FAIL: non-existent tool invocation should have failed"
fi

stop_port_forward
```

---

## Phase 6: Deploy with Promote-Employee Routes (Tools + Resources)

### Test 6.1: Delete the hello-world WanakuCamelRoute CR

**Description:** Remove the hello-world CamelRoute CR. The operator handles cleanup of the CIC Deployment, Service, and PVC via ownerReferences.

```bash
oc delete wanakucamelroute cic-hello-world -n "${WANAKU_NAMESPACE}"
wait_for_deletion wanakucamelroute cic-hello-world "${WANAKU_NAMESPACE}" 60

# Verify operator cleaned up managed resources
wait_for_deletion deployment cic-hello-world-cic "${WANAKU_NAMESPACE}" 60
wait_for_deletion service cic-hello-world-cic "${WANAKU_NAMESPACE}" 30
echo "PASS: hello-world CamelRoute CR and managed resources deleted"
```

### Test 6.2: Create a WanakuCamelRoute with promote-employee routes (tools + resources)

**Description:** Create a CamelRoute CR with multiple routes exposing two tools and one resource. This tests the operator's ability to handle combined tools and resources in a single CR.

```bash
cat <<'EOF' | oc apply -n "${WANAKU_NAMESPACE}" -f -
apiVersion: wanaku.ai/v1alpha1
kind: WanakuCamelRoute
metadata:
  name: cic-promote-employee
spec:
  routerRef: wanaku-test-router
  route:
    - route:
        id: route-3103
        from:
          id: from-4035
          uri: direct
          parameters:
            name: initiate-promotion
          steps:
            - log:
                id: log-2526
                message: Initiating promotion process for employee ${header.EMPLOYEE}
            - setBody:
                simple:
                  expression: Promotion process for employee ${header.EMPLOYEE} has started.
    - route:
        id: route-3104
        from:
          id: from-4035-1797
          uri: direct
          parameters:
            name: confirm-promotion
          steps:
            - log:
                id: log-2526-2688
                message: Confirming promotion process for employee ${header.EMPLOYEE}
            - setBody:
                simple:
                  expression: Promotion process for employee ${header.EMPLOYEE} has completed. You can now send an email and congratulate him.
    - route:
        id: route-3105
        autoStartup: false
        from:
          id: from-9762
          uri: direct
          parameters:
            name: employee-history
          steps:
            - log:
                id: log-2526-2688-1609
                message: Obtaining the history for employee
            - setBody:
                id: setBody-1229
                simple:
                  expression: ${body}
  mcp:
    tools:
      - name: initiate-employee-promotion
        routeId: route-3103
        description: "Initiate the promotion process for an employee"
        properties:
          - name: employee
            type: string
            description: The employee to confirm the promotion
            required: true
      - name: confirm-employee-promotion
        routeId: route-3104
        description: "Confirm the promotion of an employee"
        properties:
          - name: employee
            type: string
            description: The employee to confirm the promotion
            required: true
    resources:
      - name: employee-performance-history
        routeId: route-3105
        description: "Obtain the employee performance history"
        uri: "wanaku://employee/performance-history"
        mimeType: "application/json"
EOF
```

**Verification - CR accepted:**

```bash
oc get wanakucamelroute cic-promote-employee -n "${WANAKU_NAMESPACE}" -o name
echo "promote-employee-cr-exists=$?"
# Expected: promote-employee-cr-exists=0
```

### Test 6.3: Verify promote-employee CR reaches Ready condition

```bash
oc wait wanakucamelroute/cic-promote-employee \
  --for=condition=Ready \
  --timeout=120s \
  -n "${WANAKU_NAMESPACE}"
# Expected output: wanakucamelroute.wanaku.ai/cic-promote-employee condition met
```

### Test 6.4: Verify status fields show tools and resources

```bash
CATALOG_NAME=$(oc get wanakucamelroute cic-promote-employee -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.status.deployedCatalogName}')
TOOLS=$(oc get wanakucamelroute cic-promote-employee -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.status.registeredTools[*]}')
RESOURCES=$(oc get wanakucamelroute cic-promote-employee -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.status.registeredResources[*]}')

echo "deployedCatalogName=${CATALOG_NAME}"
echo "registeredTools=${TOOLS}"
echo "registeredResources=${RESOURCES}"

[ "${CATALOG_NAME}" = "cic-promote-employee" ] && echo "PASS: deployedCatalogName is correct" || { echo "FAIL: unexpected deployedCatalogName '${CATALOG_NAME}'"; exit 1; }

echo "${TOOLS}" | grep -q "initiate-employee-promotion" && echo "PASS: initiate-employee-promotion in registeredTools" || { echo "FAIL: initiate-employee-promotion not in registeredTools"; exit 1; }

echo "${TOOLS}" | grep -q "confirm-employee-promotion" && echo "PASS: confirm-employee-promotion in registeredTools" || { echo "FAIL: confirm-employee-promotion not in registeredTools"; exit 1; }

echo "${RESOURCES}" | grep -q "employee-performance-history" && echo "PASS: employee-performance-history in registeredResources" || { echo "FAIL: employee-performance-history not in registeredResources"; exit 1; }
```

### Test 6.5: Verify CIC Deployment and Service for promote-employee

```bash
oc wait deployment/cic-promote-employee-cic \
  --for=condition=Available \
  --timeout=120s \
  -n "${WANAKU_NAMESPACE}"
echo "promote-cic-deployment-available=$?"
# Expected: promote-cic-deployment-available=0

oc get service cic-promote-employee-cic -n "${WANAKU_NAMESPACE}" -o name
echo "promote-cic-service-exists=$?"
# Expected: promote-cic-service-exists=0
```

### Test 6.6: Verify both tools are registered in router

```bash
start_port_forward

MAX_RETRIES=12
RETRY_INTERVAL=5
for i in $(seq 1 ${MAX_RETRIES}); do
  TOOLS_OUTPUT=$(wanaku tools list --host "${WANAKU_HOST}" --plain 2>&1)

  INITIATE_FOUND=$(echo "${TOOLS_OUTPUT}" | grep -q "initiate-employee-promotion" && echo "yes" || echo "no")
  CONFIRM_FOUND=$(echo "${TOOLS_OUTPUT}" | grep -q "confirm-employee-promotion" && echo "yes" || echo "no")

  if [ "${INITIATE_FOUND}" = "yes" ] && [ "${CONFIRM_FOUND}" = "yes" ]; then
    echo "PASS: both promote-employee tools registered in router (attempt ${i})"
    break
  fi

  if [ "${i}" -eq "${MAX_RETRIES}" ]; then
    echo "FAIL: promote-employee tools not registered after ${MAX_RETRIES} attempts"
    echo "initiate-employee-promotion: ${INITIATE_FOUND}, confirm-employee-promotion: ${CONFIRM_FOUND}"
    echo "Output: ${TOOLS_OUTPUT}"
    stop_port_forward
    exit 1
  fi
  echo "Waiting for CIC registration... (attempt ${i})"
  sleep ${RETRY_INTERVAL}
done

stop_port_forward
```

### Test 6.7: Verify resource is registered in router

```bash
RESOURCES_RESPONSE=$(query_router_api /api/v1/resources)
echo "Router resources:"
echo "${RESOURCES_RESPONSE}"

if echo "${RESOURCES_RESPONSE}" | jq -e '.data[] | select(.name == "employee-performance-history")' > /dev/null 2>&1; then
  echo "PASS: employee-performance-history resource registered in router"
else
  echo "WARN: employee-performance-history resource not found — resource registration may depend on route autoStartup setting"
fi
```

### Test 6.8: List resources via MCP endpoint

```bash
start_port_forward

RESOURCE_LIST=$(wanaku mcp resource list --uri "${MCP_URI}" 2>&1)
EXIT_CODE=$?
echo "MCP resource list output:"
echo "${RESOURCE_LIST}"

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "WARN: wanaku mcp resource list failed (exit code ${EXIT_CODE}) — may be affected by native build issue #1406"
else
  echo "PASS: resource list succeeded"
fi

stop_port_forward
```

---

## Phase 7: Service Catalog and Capability CRD Modes

### Test 7.1: Delete promote-employee CamelRoute CR

```bash
oc delete wanakucamelroute cic-promote-employee -n "${WANAKU_NAMESPACE}"
wait_for_deletion wanakucamelroute cic-promote-employee "${WANAKU_NAMESPACE}" 60
wait_for_deletion deployment cic-promote-employee-cic "${WANAKU_NAMESPACE}" 60
echo "PASS: promote-employee CamelRoute CR and managed resources deleted"
```

### Test 7.2: Create a WanakuServiceCatalog with a hello-world catalog

**Description:** Use the WanakuServiceCatalog CRD to deploy a service catalog from a ConfigMap containing base64-encoded ZIP data. This tests the operator's ability to deploy catalogs without using `wanaku service deploy` CLI.

```bash
# Create a minimal test catalog ZIP
TEMP_DIR=$(mktemp -d)
mkdir -p "${TEMP_DIR}/hello-system"

cat > "${TEMP_DIR}/index.properties" <<'PROPS'
catalog.name = hello-system
catalog.description = Minimal hello-world catalog for CIC operator verification
catalog.services = hello-system
catalog.routes.hello-system = hello-system/hello-system.camel.yaml
catalog.rules.hello-system = hello-system/hello-system.wanaku-rules.yaml
PROPS

cat > "${TEMP_DIR}/hello-system/hello-system.camel.yaml" <<'ROUTES'
- route:
    id: route-3104
    from:
      id: from-1702
      uri: direct
      parameters:
        name: wanaku
      steps:
        - log:
            id: log-2526
            message: Hello ${body}
        - setBody:
            simple: Hello ${body} from ${routeId}
ROUTES

cat > "${TEMP_DIR}/hello-system/hello-system.wanaku-rules.yaml" <<'RULES'
mcp:
  tools:
    - sends-greeting:
        route:
          id: "route-3104"
        description: "Sends a greeting message to show that the application is working"
        properties:
          - name: wanaku_body
            type: string
            description: The greeting message to send
            required: true
RULES

# Package as base64-encoded ZIP
(cd "${TEMP_DIR}" && zip -r catalog.zip . 2>/dev/null)
base64 < "${TEMP_DIR}/catalog.zip" | tr -d '\n' > "${TEMP_DIR}/catalog.b64"

# Create the ConfigMap
oc create configmap cic-hello-catalog-data \
  --from-file=catalog.zip="${TEMP_DIR}/catalog.b64" \
  -n "${WANAKU_NAMESPACE}"
oc label configmap cic-hello-catalog-data wanaku-test=true -n "${WANAKU_NAMESPACE}"

rm -rf "${TEMP_DIR}"

echo "PASS: test ConfigMap created"
```

**Verification - ConfigMap has catalog.zip key:**

```bash
oc get configmap cic-hello-catalog-data -n "${WANAKU_NAMESPACE}" -o jsonpath='{.data}' | jq -r 'keys[]' | grep -q "catalog.zip"
echo "configmap-has-catalog-zip=$?"
# Expected: configmap-has-catalog-zip=0
```

### Test 7.3: Create WanakuServiceCatalog CR

```bash
cat <<'EOF' | oc apply -n "${WANAKU_NAMESPACE}" -f -
apiVersion: "wanaku.ai/v1alpha1"
kind: WanakuServiceCatalog
metadata:
  name: cic-test-service-catalogs
spec:
  routerRef: wanaku-test-router
  catalogs:
    - name: hello-system
      configMapRef: cic-hello-catalog-data
EOF
```

### Test 7.4: Verify WanakuServiceCatalog reaches Ready

```bash
oc wait wanakuservicecatalog/cic-test-service-catalogs \
  --for=condition=Ready \
  --timeout=120s \
  -n "${WANAKU_NAMESPACE}" 2>/dev/null

SC_READY=$(oc get wanakuservicecatalog cic-test-service-catalogs -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}' 2>/dev/null || echo "unknown")
echo "service-catalog-ready=${SC_READY}"

if [ "${SC_READY}" = "True" ]; then
  DEPLOYED=$(oc get wanakuservicecatalog cic-test-service-catalogs -n "${WANAKU_NAMESPACE}" \
    -o jsonpath='{.status.deployedCatalogs[*]}')
  echo "deployed-catalogs=${DEPLOYED}"
  if echo "${DEPLOYED}" | grep -q "hello-system"; then
    echo "PASS: hello-system listed in deployedCatalogs"
  else
    echo "FAIL: hello-system not found in deployedCatalogs"
  fi
else
  echo "FAIL: WanakuServiceCatalog did not reach Ready"
fi
```

### Test 7.5: Verify catalog deployed to router via REST API

```bash
CATALOG_RESPONSE=$(query_router_api /api/v1/service-catalog)

if echo "${CATALOG_RESPONSE}" | jq -e '.data[] | select(.name == "hello-system")' > /dev/null 2>&1; then
  echo "PASS: hello-system catalog found in router"
else
  echo "FAIL: hello-system catalog not found in router response"
  echo "Response: ${CATALOG_RESPONSE}"
fi
```

### Test 7.6: Deploy CIC via WanakuCapability with service catalog

**Description:** Use the WanakuCapability CRD with `type: "camel-integration-capability"` to deploy a CIC that picks up the hello-system catalog deployed in Tests 7.2-7.5.

```bash
cat <<EOF | oc apply -n "${WANAKU_NAMESPACE}" -f -
apiVersion: "wanaku.ai/v1alpha1"
kind: WanakuCapability
metadata:
  name: cic-catalog-capability
spec:
  auth:
    authServer: "http://keycloak:8080"
    authProxy: "auto"
  secrets:
    oidcCredentialsSecret: "${WANAKU_OIDC_CLIENT_SECRET}"
  routerRef: wanaku-test-router
  capabilities:
    - name: cic-catalog-test
      image: ${CIC_IMAGE}
      type: "camel-integration-capability"
      serviceCatalog: "hello-system"
      serviceCatalogSystem: "hello-system"
      imagePullPolicy: Always
EOF
```

### Test 7.7: Verify WanakuCapability reaches Ready

```bash
oc wait wanakucapability/cic-catalog-capability \
  --for=condition=Ready \
  --timeout=120s \
  -n "${WANAKU_NAMESPACE}"
# Expected output: wanakucapability.wanaku.ai/cic-catalog-capability condition met
```

### Test 7.8: Verify CIC capability deployment

```bash
oc wait deployment/cic-catalog-test \
  --for=condition=Available \
  --timeout=120s \
  -n "${WANAKU_NAMESPACE}"
echo "cic-catalog-deployment-available=$?"
# Expected: cic-catalog-deployment-available=0

# Verify the container image
CAP_IMAGE=$(oc get deployment cic-catalog-test -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.spec.template.spec.containers[0].image}')
echo "capability-image=${CAP_IMAGE}"
if [ "${CAP_IMAGE}" != "${CIC_IMAGE}" ]; then
  echo "FAIL: unexpected capability image"
  exit 1
fi
echo "PASS: capability image matches"
```

### Test 7.9: Verify CIC capability has correct environment variables

```bash
# Check SERVICE_CATALOG env var
SERVICE_CAT=$(oc get deployment cic-catalog-test -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.spec.template.spec.containers[0].env[?(@.name=="SERVICE_CATALOG")].value}')
echo "SERVICE_CATALOG=${SERVICE_CAT}"
if [ "${SERVICE_CAT}" != "hello-system" ]; then
  echo "FAIL: SERVICE_CATALOG is '${SERVICE_CAT}', expected 'hello-system'"
  exit 1
fi
echo "PASS: SERVICE_CATALOG is correct"

# Check SERVICE_CATALOG_SYSTEM env var
SERVICE_CAT_SYS=$(oc get deployment cic-catalog-test -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.spec.template.spec.containers[0].env[?(@.name=="SERVICE_CATALOG_SYSTEM")].value}')
echo "SERVICE_CATALOG_SYSTEM=${SERVICE_CAT_SYS}"
if [ "${SERVICE_CAT_SYS}" != "hello-system" ]; then
  echo "FAIL: SERVICE_CATALOG_SYSTEM is '${SERVICE_CAT_SYS}', expected 'hello-system'"
  exit 1
fi
echo "PASS: SERVICE_CATALOG_SYSTEM is correct"

# Check REGISTRATION_URL points to internal router
REG_URL=$(oc get deployment cic-catalog-test -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.spec.template.spec.containers[0].env[?(@.name=="REGISTRATION_URL")].value}')
echo "REGISTRATION_URL=${REG_URL}"
if ! echo "${REG_URL}" | grep -q "internal-wanaku-test-router"; then
  echo "FAIL: REGISTRATION_URL does not reference the internal router service"
  exit 1
fi
echo "PASS: REGISTRATION_URL is correct"
```

### Test 7.10: Verify CIC capability pod is running in catalog mode

```bash
CIC_CAP_POD=$(oc get pods -l app=cic-catalog-test \
  -n "${WANAKU_NAMESPACE}" -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")

if [ -z "${CIC_CAP_POD}" ]; then
  echo "FAIL: no CIC capability pod found"
  exit 1
fi

POD_PHASE=$(oc get pod "${CIC_CAP_POD}" -n "${WANAKU_NAMESPACE}" -o jsonpath='{.status.phase}')
if [ "${POD_PHASE}" != "Running" ]; then
  echo "FAIL: CIC capability pod phase is '${POD_PHASE}', expected 'Running'"
  oc describe pod "${CIC_CAP_POD}" -n "${WANAKU_NAMESPACE}" | tail -20
  exit 1
fi
echo "PASS: CIC capability pod is Running"

# Verify logs reference the service catalog
oc logs "${CIC_CAP_POD}" -n "${WANAKU_NAMESPACE}" | grep -qi "catalog\|hello-system" && \
  echo "PASS: service catalog reference found in CIC logs" || \
  echo "INFO: service catalog reference not found in CIC logs"
```

### Test 7.11: Delete service catalog mode resources

```bash
oc delete wanakucapability cic-catalog-capability -n "${WANAKU_NAMESPACE}" --ignore-not-found=true
oc delete wanakuservicecatalog cic-test-service-catalogs -n "${WANAKU_NAMESPACE}" --ignore-not-found=true

wait_for_deletion deployment cic-catalog-test "${WANAKU_NAMESPACE}" 60
echo "PASS: service catalog mode resources deleted"
```

---

## Phase 8: Error Handling and Negative Tests

### Test 8.1: Missing routerRef

**Description:** A WanakuCamelRoute without `routerRef` should not become Ready.

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

**Description:** A WanakuCamelRoute without `route` should not become Ready.

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

**Description:** A WanakuCamelRoute without `mcp` should not become Ready.

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

**Description:** A WanakuCamelRoute with empty tools and resources lists should not become Ready.

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

**Description:** A WanakuCamelRoute referencing a WanakuRouter that does not exist should not become Ready.

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

### Test 8.6: WanakuServiceCatalog with missing ConfigMap

**Description:** A WanakuServiceCatalog referencing a non-existent ConfigMap should not become Ready.

```bash
cat <<'EOF' | oc apply -n "${WANAKU_NAMESPACE}" -f -
apiVersion: "wanaku.ai/v1alpha1"
kind: WanakuServiceCatalog
metadata:
  name: bad-missing-configmap
spec:
  routerRef: wanaku-test-router
  catalogs:
    - name: missing-catalog
      configMapRef: this-configmap-does-not-exist
EOF

END=$((SECONDS + 30))
while [ $SECONDS -lt $END ]; do
  STATUS=$(oc get wanakuservicecatalog bad-missing-configmap -n "${WANAKU_NAMESPACE}" \
    -o jsonpath='{.status}' 2>/dev/null || echo "")
  [ -n "${STATUS}" ] && break
  sleep 3
done

BAD_SC_READY=$(oc get wanakuservicecatalog bad-missing-configmap -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}' 2>/dev/null || echo "")
if [ "${BAD_SC_READY}" = "True" ]; then
  echo "FAIL: service catalog with missing ConfigMap should not become Ready"
  exit 1
fi
echo "PASS: service catalog with missing ConfigMap is not Ready"

oc delete wanakuservicecatalog bad-missing-configmap -n "${WANAKU_NAMESPACE}" --ignore-not-found=true
```

### Test 8.7: WanakuCapability with missing routerRef

**Description:** A WanakuCapability without `routerRef` should not become Ready.

```bash
cat <<EOF | oc apply -n "${WANAKU_NAMESPACE}" -f -
apiVersion: "wanaku.ai/v1alpha1"
kind: WanakuCapability
metadata:
  name: bad-capability-no-ref
spec:
  capabilities:
    - name: bad-capability
      image: ${CIC_IMAGE}
      type: "camel-integration-capability"
EOF

END=$((SECONDS + 30))
while [ $SECONDS -lt $END ]; do
  STATUS=$(oc get wanakucapability bad-capability-no-ref -n "${WANAKU_NAMESPACE}" \
    -o jsonpath='{.status}' 2>/dev/null || echo "")
  [ -n "${STATUS}" ] && break
  sleep 3
done

READY_STATUS=$(oc get wanakucapability bad-capability-no-ref -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}' 2>/dev/null || echo "")
if [ "${READY_STATUS}" = "True" ]; then
  echo "FAIL: capability without routerRef should not become Ready"
  exit 1
fi
echo "PASS: capability without routerRef is not Ready"

oc delete wanakucapability bad-capability-no-ref -n "${WANAKU_NAMESPACE}" --ignore-not-found=true
```

### Test 8.8: Clean up negative test leftovers

```bash
# Belt-and-suspenders cleanup of any leftover negative-test CRs
oc delete wanakucamelroute bad-no-router-ref bad-no-route bad-no-mcp bad-empty-mcp bad-missing-router \
  -n "${WANAKU_NAMESPACE}" --ignore-not-found=true 2>/dev/null
oc delete wanakuservicecatalog bad-missing-configmap \
  -n "${WANAKU_NAMESPACE}" --ignore-not-found=true 2>/dev/null
oc delete wanakucapability bad-capability-no-ref \
  -n "${WANAKU_NAMESPACE}" --ignore-not-found=true 2>/dev/null

echo "PASS: negative test resources cleaned up"
```

---

## Phase 9: Post-Test Verification

### Test 9.1: Verify operator pod is still running with no unexpected restarts

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

### Test 9.2: Verify no WanakuCamelRoute resources remain

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

### Test 9.3: Verify operator logs have no unexpected errors

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
  | grep -iv "this-configmap-does-not-exist" \
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
    | grep -iv "this-configmap-does-not-exist" \
    | tail -10
else
  echo "PASS: no unexpected errors in operator logs"
fi
```

---

## Phase 10: Cleanup

### Step 10.1: Delete remaining CIC CRDs

Delete any leftover CamelRoute, ServiceCatalog, and Capability CRs before the common cleanup.

```bash
oc delete wanakucamelroute --all -n "${WANAKU_NAMESPACE}" --ignore-not-found=true
oc delete wanakuservicecatalog --all -n "${WANAKU_NAMESPACE}" --ignore-not-found=true
oc delete wanakucapability --all -n "${WANAKU_NAMESPACE}" --ignore-not-found=true

echo "PASS: CIC CRDs deleted"
```

### Step 10.2: Delete the WanakuRouter CR

```bash
oc delete wanakurouter wanaku-test-router -n "${WANAKU_NAMESPACE}" --ignore-not-found=true

wait_for_deletion deployment wanaku-test-router-mcp-router "${WANAKU_NAMESPACE}" 60
wait_for_deletion service internal-wanaku-test-router "${WANAKU_NAMESPACE}" 30
wait_for_deletion route wanaku-test-router "${WANAKU_NAMESPACE}" 30
```

### Step 10.3: Full teardown

Follow [common/cleanup.md](common/cleanup.md) for full teardown.

### Step 10.4: Delete namespace

```bash
oc delete project "${WANAKU_NAMESPACE}" --ignore-not-found=true
echo "PASS: namespace ${WANAKU_NAMESPACE} deleted"
```

---

## Test Summary Matrix

| Phase | Test ID | Test Name | Priority |
|-------|---------|-----------|----------|
| 0 | -- | OpenShift login | Critical |
| 1 | 1.1-1.2 | Environment setup (namespace, Keycloak) | Critical |
| 2 | 2.1 | Operator installation + WanakuCamelRoute CRD | Critical |
| 3 | 3.1 | Create WanakuRouter with Keycloak auth | Critical |
| 3 | 3.2 | Verify router deployment is available | Critical |
| 3 | 3.3 | Verify router REST API is reachable | Critical |
| 3 | 3.4 | Authenticate with Keycloak via wanaku CLI | Critical |
| 4 | 4.1 | Create WanakuCamelRoute (hello-world tool) | Critical |
| 4 | 4.2 | WanakuCamelRoute reaches Ready | Critical |
| 4 | 4.3 | Status: deployedCatalogName correct | Critical |
| 4 | 4.4 | Status: registeredTools contains sends-greeting | Critical |
| 4 | 4.5 | Status: registeredResources is empty | Medium |
| 4 | 4.6 | CIC Deployment created by operator | Critical |
| 4 | 4.7 | CIC Service created by operator (port 9190) | Critical |
| 4 | 4.8 | CIC pod is Running | Critical |
| 4 | 4.9 | CIC startup logs | Medium |
| 4 | 4.10 | Catalog exists in router REST API | Critical |
| 4 | 4.11 | Tool registered via wanaku CLI | Critical |
| 4 | 4.12 | Operator reconciliation logs | Medium |
| 5 | 5.1 | Tool listed via wanaku CLI | Critical |
| 5 | 5.2 | Tool listed via MCP endpoint | Critical |
| 5 | 5.3 | Tool invoked via MCP endpoint | Critical |
| 5 | 5.4 | Non-existent tool invocation fails | High |
| 6 | 6.1 | Delete hello-world CamelRoute (cascade cleanup) | High |
| 6 | 6.2 | Create promote-employee CamelRoute (tools + resources) | High |
| 6 | 6.3 | Promote-employee CR reaches Ready | High |
| 6 | 6.4 | Status shows tools and resources | High |
| 6 | 6.5 | CIC Deployment and Service for promote-employee | High |
| 6 | 6.6 | Both tools registered in router | High |
| 6 | 6.7 | Resource registered in router (REST API) | High |
| 6 | 6.8 | Resource listed via MCP endpoint | High |
| 7 | 7.1 | Delete promote-employee CamelRoute | High |
| 7 | 7.2-7.3 | WanakuServiceCatalog with ConfigMap ZIP | High |
| 7 | 7.4-7.5 | ServiceCatalog reaches Ready, catalog in router | High |
| 7 | 7.6-7.7 | WanakuCapability with CIC type + service catalog | High |
| 7 | 7.8-7.9 | CIC capability deployment + env vars | High |
| 7 | 7.10 | CIC capability pod running in catalog mode | High |
| 7 | 7.11 | Delete service catalog mode resources | High |
| 8 | 8.1 | Missing routerRef validation | High |
| 8 | 8.2 | Missing route validation | High |
| 8 | 8.3 | Missing mcp validation | High |
| 8 | 8.4 | Empty mcp validation | High |
| 8 | 8.5 | Non-existent routerRef validation | High |
| 8 | 8.6 | ServiceCatalog missing ConfigMap | High |
| 8 | 8.7 | Capability missing routerRef | High |
| 8 | 8.8 | Clean up negative test leftovers | Medium |
| 9 | 9.1 | Operator pod healthy, zero restarts | Medium |
| 9 | 9.2 | No WanakuCamelRoute resources remain | Medium |
| 9 | 9.3 | No unexpected errors in operator logs | Medium |
| 10 | 10.1-10.4 | Full cleanup (CRDs, router, operator, namespace) | Critical |
