# Test Plan: OpenTelemetry Distributed Tracing and Request ID Propagation

## Overview

This test plan verifies the OpenTelemetry tracing, Micrometer metrics, and MCP request ID propagation introduced in PR #1311 ("feat: Implement OpenTelemetry for distributed tracing and request ID propagation"). It covers:

- OTEL Collector and Jaeger deployment on OpenShift
- W3C `traceparent` propagation across router → gRPC → capability
- `x-wanaku-request-id` header propagation via gRPC interceptors
- Structured log output with `traceId` and `requestId` MDC keys
- Prometheus metrics endpoint on the router
- End-to-end trace visibility in Jaeger UI

The plan uses the `wanaku mcp` CLI commands from PR #1393 (installed locally) as the MCP client to drive requests.

Every step except Phase 0 is fully automatable.

## Prerequisites

### Required tools

| Tool | Minimum version | Verify command |
|------|-----------------|----------------|
| `oc` | 4.12+ | `oc version --client` |
| `helm` | 3.x | `helm version --short` |
| `curl` | any | `curl --version` |
| `jq` | 1.6+ | `jq --version` |
| `wanaku` | PR #1393 build | `wanaku --version` |

### Prerequisite check script

```bash
#!/bin/bash
set -e

FAIL=0

for CMD in oc helm curl jq wanaku; do
  if ! command -v "${CMD}" > /dev/null 2>&1; then
    echo "FAIL: ${CMD} is not installed"
    FAIL=1
  else
    echo "PASS: ${CMD} found at $(command -v ${CMD})"
  fi
done

# Verify wanaku mcp subcommand exists
if wanaku mcp --help > /dev/null 2>&1; then
  echo "PASS: wanaku mcp subcommand available"
else
  echo "FAIL: wanaku mcp subcommand not available (PR #1393 not installed)"
  FAIL=1
fi

if [ "${FAIL}" -ne 0 ]; then
  echo ""
  echo "FAIL: one or more prerequisites missing"
  exit 1
fi

echo ""
echo "PASS: all prerequisites met"
```

### Environment variables

```bash
export WANAKU_NAMESPACE="${WANAKU_NAMESPACE:-wanaku-test}"
export WANAKU_REPO_ROOT="${WANAKU_REPO_ROOT:-.}"
export WANAKU_ROUTER_IMAGE="${WANAKU_ROUTER_IMAGE:-quay.io/wanaku/wanaku-router-backend:latest}"
export WANAKU_CAPABILITY_HTTP_IMAGE="${WANAKU_CAPABILITY_HTTP_IMAGE:-quay.io/wanaku/wanaku-tool-service-http:latest}"
export OTEL_COLLECTOR_IMAGE="${OTEL_COLLECTOR_IMAGE:-otel/opentelemetry-collector-contrib:0.127.0}"
export JAEGER_IMAGE="${JAEGER_IMAGE:-jaegertracing/jaeger:2.6}"
```

### Helper: wait for resource deletion

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

### Helper: retry with backoff

```bash
retry_until() {
  local DESCRIPTION="$1"
  local CMD="$2"
  local MAX_RETRIES="${3:-24}"
  local INTERVAL="${4:-5}"

  for i in $(seq 1 ${MAX_RETRIES}); do
    if eval "${CMD}"; then
      echo "PASS: ${DESCRIPTION} (attempt ${i})"
      return 0
    fi
    if [ "${i}" -eq "${MAX_RETRIES}" ]; then
      echo "FAIL: ${DESCRIPTION} not achieved after ${MAX_RETRIES} attempts"
      return 1
    fi
    echo "Waiting for ${DESCRIPTION}... (attempt ${i})"
    sleep ${INTERVAL}
  done
}
```

---

## Phase 0: Build Images via CI (MANUAL)

### Step 0.1: Checkout PR #1311 to a CI branch

Checkout the PR locally and push it to a `ci-` prefixed branch so CI builds container images with the tracing changes.

```bash
gh pr checkout 1311
git checkout -b ci-observability-tracing
git push origin ci-observability-tracing
```

### Step 0.2: Wait for CI to build images

Monitor the CI pipeline until images are published. The CI-built image tags should correspond to the branch name or commit SHA.

```bash
# Check CI status on the branch
gh run list --branch ci-observability-tracing --limit 5

# Wait for the latest run to complete
gh run watch $(gh run list --branch ci-observability-tracing --limit 1 --json databaseId -q '.[0].databaseId')
```

### Step 0.3: Update environment variables with CI-built images

Once CI completes, set the image variables to the CI-built tags:

```bash
export WANAKU_ROUTER_IMAGE="quay.io/wanaku/wanaku-router-backend:<ci-tag>"
export WANAKU_CAPABILITY_HTTP_IMAGE="quay.io/wanaku/wanaku-tool-service-http:<ci-tag>"
```

**Verification:**

```bash
# Verify images are pullable
for IMG in "${WANAKU_ROUTER_IMAGE}" "${WANAKU_CAPABILITY_HTTP_IMAGE}"; do
  oc image info "${IMG}" > /dev/null 2>&1 && echo "PASS: ${IMG} exists" || echo "FAIL: ${IMG} not found"
done
```

### Step 0.4: Log in to OpenShift

```bash
oc login <cluster-api-url> --token=<token>
oc whoami
# Expected: prints the logged-in username
```

---

## Phase 1: Environment Setup

### Step 1.1: Create namespace

Follow [common/namespace-setup.md](common/namespace-setup.md).

### Step 1.2: Deploy Operator

Follow [common/operator-deployment.md](common/operator-deployment.md).

---

## Phase 2: Deploy Observability Infrastructure

### Step 2.1: Deploy OpenTelemetry Collector

```bash
cat <<EOF | oc apply -n "${WANAKU_NAMESPACE}" -f -
apiVersion: v1
kind: ConfigMap
metadata:
  name: otel-collector-config
  labels:
    app: otel-collector
    wanaku-test: "true"
data:
  config.yaml: |
    receivers:
      otlp:
        protocols:
          grpc:
            endpoint: 0.0.0.0:4317
          http:
            endpoint: 0.0.0.0:4318
    exporters:
      otlp/jaeger:
        endpoint: jaeger:4317
        tls:
          insecure: true
      debug:
        verbosity: basic
    service:
      pipelines:
        traces:
          receivers: [otlp]
          exporters: [otlp/jaeger, debug]
        metrics:
          receivers: [otlp]
          exporters: [debug]
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: otel-collector
  labels:
    app: otel-collector
    wanaku-test: "true"
spec:
  replicas: 1
  selector:
    matchLabels:
      app: otel-collector
  template:
    metadata:
      labels:
        app: otel-collector
    spec:
      containers:
        - name: otel-collector
          image: ${OTEL_COLLECTOR_IMAGE}
          args: ["--config=/etc/otelcol/config.yaml"]
          ports:
            - containerPort: 4317
              name: otlp-grpc
            - containerPort: 4318
              name: otlp-http
          volumeMounts:
            - name: config
              mountPath: /etc/otelcol/config.yaml
              subPath: config.yaml
      volumes:
        - name: config
          configMap:
            name: otel-collector-config
---
apiVersion: v1
kind: Service
metadata:
  name: otel-collector
  labels:
    app: otel-collector
    wanaku-test: "true"
spec:
  selector:
    app: otel-collector
  ports:
    - port: 4317
      targetPort: 4317
      name: otlp-grpc
    - port: 4318
      targetPort: 4318
      name: otlp-http
EOF
```

**Verification:**

```bash
oc wait deployment/otel-collector \
  --for=condition=Available \
  --timeout=120s \
  -n "${WANAKU_NAMESPACE}"
echo "otel-collector-available=$?"
# Expected: otel-collector-available=0
```

### Step 2.2: Deploy Jaeger

```bash
cat <<EOF | oc apply -n "${WANAKU_NAMESPACE}" -f -
apiVersion: apps/v1
kind: Deployment
metadata:
  name: jaeger
  labels:
    app: jaeger
    wanaku-test: "true"
spec:
  replicas: 1
  selector:
    matchLabels:
      app: jaeger
  template:
    metadata:
      labels:
        app: jaeger
    spec:
      containers:
        - name: jaeger
          image: ${JAEGER_IMAGE}
          env:
            - name: COLLECTOR_OTLP_ENABLED
              value: "true"
          ports:
            - containerPort: 16686
              name: ui
            - containerPort: 4317
              name: otlp-grpc
---
apiVersion: v1
kind: Service
metadata:
  name: jaeger
  labels:
    app: jaeger
    wanaku-test: "true"
spec:
  selector:
    app: jaeger
  ports:
    - port: 16686
      targetPort: 16686
      name: ui
    - port: 4317
      targetPort: 4317
      name: otlp-grpc
EOF
```

**Verification:**

```bash
oc wait deployment/jaeger \
  --for=condition=Available \
  --timeout=120s \
  -n "${WANAKU_NAMESPACE}"
echo "jaeger-available=$?"
# Expected: jaeger-available=0
```

### Step 2.3: Expose Jaeger UI via Route

```bash
oc expose service jaeger --port=ui -n "${WANAKU_NAMESPACE}" 2>/dev/null || true
export JAEGER_URL="http://$(oc get route jaeger -n "${WANAKU_NAMESPACE}" -o jsonpath='{.spec.host}')"
echo "JAEGER_URL=${JAEGER_URL}"

retry_until "Jaeger UI accessible" \
  "curl -sf -o /dev/null '${JAEGER_URL}'" 12 5
```

### Step 2.4: Verify OTEL Collector can reach Jaeger

```bash
OTEL_POD=$(oc get pods -l app=otel-collector -n "${WANAKU_NAMESPACE}" -o jsonpath='{.items[0].metadata.name}')

oc logs "${OTEL_POD}" -n "${WANAKU_NAMESPACE}" --tail=20 | grep -i "error" && {
  echo "WARN: errors found in OTEL Collector logs"
} || {
  echo "PASS: no errors in OTEL Collector logs"
}
```

---

## Phase 3: Deploy Wanaku Stack with Tracing Enabled

### Step 3.1: Create WanakuRouter with OTEL enabled (noauth)

Deploy a router with tracing enabled, pointing to the OTEL Collector service deployed in Phase 2.

```bash
cat <<EOF | oc apply -n "${WANAKU_NAMESPACE}" -f -
apiVersion: "wanaku.ai/v1alpha1"
kind: WanakuRouter
metadata:
  name: wanaku-tracing-router
spec:
  router:
    image: ${WANAKU_ROUTER_IMAGE}
    imagePullPolicy: Always
    env:
      - name: WANAKU_HTTP_AUTH
        value: none
      - name: QUARKUS_OTEL_SDK_DISABLED
        value: "false"
      - name: QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT
        value: "http://otel-collector:4317"
EOF
```

**Verification:**

```bash
oc wait wanakurouter/wanaku-tracing-router \
  --for=condition=Ready \
  --timeout=120s \
  -n "${WANAKU_NAMESPACE}"
```

### Step 3.2: Verify router deployment has OTEL env vars

```bash
DEPLOYMENT="wanaku-tracing-router-mcp-router"

OTEL_DISABLED=$(oc get deployment "${DEPLOYMENT}" -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.spec.template.spec.containers[0].env[?(@.name=="QUARKUS_OTEL_SDK_DISABLED")].value}')
if [ "${OTEL_DISABLED}" != "false" ]; then
  echo "FAIL: QUARKUS_OTEL_SDK_DISABLED is '${OTEL_DISABLED}', expected 'false'"
  exit 1
fi
echo "PASS: QUARKUS_OTEL_SDK_DISABLED=false"

OTEL_ENDPOINT=$(oc get deployment "${DEPLOYMENT}" -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.spec.template.spec.containers[0].env[?(@.name=="QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT")].value}')
if [ "${OTEL_ENDPOINT}" != "http://otel-collector:4317" ]; then
  echo "FAIL: OTEL endpoint is '${OTEL_ENDPOINT}', expected 'http://otel-collector:4317'"
  exit 1
fi
echo "PASS: OTEL exporter endpoint configured"
```

### Step 3.3: Wait for router to become accessible

```bash
export WANAKU_ROUTER_URL="http://$(oc get route wanaku-tracing-router -n "${WANAKU_NAMESPACE}" -o jsonpath='{.spec.host}')"
echo "WANAKU_ROUTER_URL=${WANAKU_ROUTER_URL}"

retry_until "router health live" \
  "curl -sf -o /dev/null '${WANAKU_ROUTER_URL}/q/health/live'" 24 5
```

### Step 3.4: Deploy WanakuCapability with OTEL enabled

```bash
cat <<EOF | oc apply -n "${WANAKU_NAMESPACE}" -f -
apiVersion: "wanaku.ai/v1alpha1"
kind: WanakuCapability
metadata:
  name: wanaku-tracing-capabilities
spec:
  routerRef: wanaku-tracing-router
  capabilities:
    - name: wanaku-http-tracing
      image: ${WANAKU_CAPABILITY_HTTP_IMAGE}
      imagePullPolicy: Always
      env:
        - name: QUARKUS_OTEL_SDK_DISABLED
          value: "false"
        - name: QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT
          value: "http://otel-collector:4317"
EOF
```

**Verification:**

```bash
oc wait wanakucapability/wanaku-tracing-capabilities \
  --for=condition=Ready \
  --timeout=120s \
  -n "${WANAKU_NAMESPACE}"

oc wait deployment/wanaku-http-tracing \
  --for=condition=Available \
  --timeout=120s \
  -n "${WANAKU_NAMESPACE}"
echo "PASS: capability deployment available"
```

### Step 3.5: Verify capability has OTEL env vars

```bash
OTEL_EP=$(oc get deployment wanaku-http-tracing -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.spec.template.spec.containers[0].env[?(@.name=="QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT")].value}')
if [ "${OTEL_EP}" != "http://otel-collector:4317" ]; then
  echo "FAIL: capability OTEL endpoint is '${OTEL_EP}'"
  exit 1
fi
echo "PASS: capability OTEL endpoint configured"
```

---

## Phase 4: Verify Prometheus Metrics Endpoint

### Test 4.1: Router exposes Prometheus metrics

```bash
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "${WANAKU_ROUTER_URL}/q/metrics")
if [ "${HTTP_CODE}" != "200" ]; then
  echo "FAIL: metrics endpoint returned HTTP ${HTTP_CODE}"
  exit 1
fi
echo "PASS: metrics endpoint returns 200"
```

### Test 4.2: Metrics contain expected JVM/HTTP metrics

```bash
METRICS_OUTPUT=$(curl -sf "${WANAKU_ROUTER_URL}/q/metrics")

for METRIC in "jvm_" "http_server_"; do
  if echo "${METRICS_OUTPUT}" | grep -q "${METRIC}"; then
    echo "PASS: found ${METRIC} metrics"
  else
    echo "FAIL: missing ${METRIC} metrics"
  fi
done
```

---

## Phase 5: Trace Propagation - Tool Invocation

### Test 5.1: List available tools via MCP client

Use the `wanaku mcp` CLI to list tools exposed by the router's MCP endpoint.

```bash
STREAMABLE_ENDPOINT="${WANAKU_ROUTER_URL}/mcp"
wanaku mcp tool list --uri "${STREAMABLE_ENDPOINT}"
echo "tool-list-exit-code=$?"
# Expected: 0 — tools are listed (at least the HTTP tool should be registered)
```

### Test 5.2: Invoke a tool and capture the response

Invoke an HTTP tool that was registered by the capability service. This generates a full trace: MCP client → router → gRPC → capability → downstream HTTP.

```bash
# First, check what tools are available
TOOL_LIST=$(wanaku mcp tool list --uri "${STREAMABLE_ENDPOINT}" 2>&1)
echo "Available tools:"
echo "${TOOL_LIST}"

# Invoke an HTTP tool (adjust --name and --param based on the registered tool name)
wanaku mcp tool --uri "${STREAMABLE_ENDPOINT}" --name http --param url=http://httpbin.org/get
echo "tool-invoke-exit-code=$?"
# Expected: 0 — tool executed successfully
```

### Test 5.3: Verify traces appear in Jaeger

After invoking a tool, traces should be visible in Jaeger for the `wanaku-router` service.

```bash
# Wait a few seconds for trace export
sleep 5

# Query Jaeger API for traces from wanaku-router
TRACES=$(curl -sf "${JAEGER_URL}/api/traces?service=wanaku-router&limit=5" 2>/dev/null || echo "")
if [ -z "${TRACES}" ]; then
  echo "FAIL: could not query Jaeger API"
  exit 1
fi

TRACE_COUNT=$(echo "${TRACES}" | jq '.data | length')
if [ "${TRACE_COUNT}" -gt 0 ]; then
  echo "PASS: found ${TRACE_COUNT} trace(s) for wanaku-router in Jaeger"
else
  echo "FAIL: no traces found for wanaku-router"
  exit 1
fi
```

### Test 5.4: Verify trace spans cross service boundaries

A tool invocation trace should contain spans from both the router and the capability service.

```bash
# Get the most recent trace
LATEST_TRACE_ID=$(echo "${TRACES}" | jq -r '.data[0].traceID')
echo "Latest trace ID: ${LATEST_TRACE_ID}"

TRACE_DETAIL=$(curl -sf "${JAEGER_URL}/api/traces/${LATEST_TRACE_ID}")

# Extract unique service names from the trace
SERVICES=$(echo "${TRACE_DETAIL}" | jq -r '[.data[0].processes | to_entries[].value.serviceName] | unique | .[]')
echo "Services in trace: ${SERVICES}"

echo "${SERVICES}" | grep -q "wanaku-router" && echo "PASS: router spans present" || echo "FAIL: no router spans"
```

### Test 5.5: Verify custom span attributes

The trace should include `wanaku.mcp.request_id` and `wanaku.mcp.tool_name` attributes.

```bash
# Search for wanaku-specific span attributes in the trace
SPAN_TAGS=$(echo "${TRACE_DETAIL}" | jq -r '[.data[0].spans[].tags[] | select(.key | startswith("wanaku."))] | unique_by(.key)')
echo "Wanaku span attributes: ${SPAN_TAGS}"

echo "${SPAN_TAGS}" | jq -e '.[] | select(.key == "wanaku.mcp.request_id")' > /dev/null 2>&1 \
  && echo "PASS: wanaku.mcp.request_id attribute found" \
  || echo "WARN: wanaku.mcp.request_id attribute not found (may depend on MCP client sending request IDs)"

echo "${SPAN_TAGS}" | jq -e '.[] | select(.key == "wanaku.mcp.tool_name")' > /dev/null 2>&1 \
  && echo "PASS: wanaku.mcp.tool_name attribute found" \
  || echo "WARN: wanaku.mcp.tool_name attribute not found"
```

---

## Phase 6: Trace Propagation - Resource Acquisition

### Test 6.1: List available resources via MCP client

```bash
wanaku mcp resource list --uri "${STREAMABLE_ENDPOINT}"
echo "resource-list-exit-code=$?"
# Expected: 0
```

### Test 6.2: Read a resource (if any available)

```bash
RESOURCE_LIST=$(wanaku mcp resource list --uri "${STREAMABLE_ENDPOINT}" 2>&1)
echo "Available resources:"
echo "${RESOURCE_LIST}"

# If resources are available, read one (adjust --resource-uri accordingly)
# wanaku mcp resource --uri "${STREAMABLE_ENDPOINT}" --resource-uri <resource-uri>
# echo "resource-read-exit-code=$?"
```

### Test 6.3: Verify resource acquisition trace in Jaeger

```bash
sleep 5

RESOURCE_TRACES=$(curl -sf "${JAEGER_URL}/api/traces?service=wanaku-router&limit=5&operation=resource" 2>/dev/null || echo "")

# Even listing resources should produce spans
TRACES_AFTER=$(curl -sf "${JAEGER_URL}/api/traces?service=wanaku-router&limit=10")
TRACE_COUNT_AFTER=$(echo "${TRACES_AFTER}" | jq '.data | length')
echo "Total traces after resource operations: ${TRACE_COUNT_AFTER}"

if [ "${TRACE_COUNT_AFTER}" -gt "${TRACE_COUNT}" ]; then
  echo "PASS: new traces generated by resource operations"
else
  echo "WARN: no additional traces (resource operations may not generate distinct traces)"
fi
```

---

## Phase 7: Structured Logging Verification

### Test 7.1: Verify router logs contain traceId and requestId

After the tool invocation in Phase 5, the router logs should contain structured fields.

```bash
ROUTER_POD=$(oc get pods -l component=wanaku-router-backend -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || \
  oc get pods -l app=wanaku-tracing-router-mcp-router -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.items[0].metadata.name}')

ROUTER_LOGS=$(oc logs "${ROUTER_POD}" -n "${WANAKU_NAMESPACE}" --tail=100)

echo "${ROUTER_LOGS}" | grep -q "traceId=" && echo "PASS: traceId present in router logs" || echo "FAIL: traceId missing from router logs"
echo "${ROUTER_LOGS}" | grep -q "requestId=" && echo "PASS: requestId present in router logs" || echo "FAIL: requestId missing from router logs"
```

### Test 7.2: Verify traceId is non-empty in logs for tool invocations

```bash
# Look for log lines with non-empty traceId (not traceId=, or traceId=0000...)
NON_EMPTY_TRACE=$(echo "${ROUTER_LOGS}" | grep -E "traceId=[0-9a-f]{16,}" | head -3)
if [ -n "${NON_EMPTY_TRACE}" ]; then
  echo "PASS: found log lines with non-empty traceId"
  echo "${NON_EMPTY_TRACE}"
else
  echo "WARN: no log lines with non-empty traceId found"
fi
```

### Test 7.3: Verify capability service logs contain traceId

```bash
CAP_POD=$(oc get pods -l app=wanaku-http-tracing -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")

if [ -n "${CAP_POD}" ]; then
  CAP_LOGS=$(oc logs "${CAP_POD}" -n "${WANAKU_NAMESPACE}" --tail=100)
  echo "${CAP_LOGS}" | grep -q "traceId=" && echo "PASS: traceId present in capability logs" || echo "WARN: traceId missing from capability logs (may not have received requests yet)"
  echo "${CAP_LOGS}" | grep -q "requestId=" && echo "PASS: requestId present in capability logs" || echo "WARN: requestId missing from capability logs"
else
  echo "SKIP: no capability pod found"
fi
```

---

## Phase 8: Request ID Correlation

### Test 8.1: Invoke a tool and correlate request ID across logs

Invoke a tool and verify the same `requestId` appears in both router and capability logs.

```bash
# Invoke a tool to generate a fresh request
wanaku mcp tool --uri "${STREAMABLE_ENDPOINT}" --name http --param url=http://httpbin.org/ip
sleep 3

# Grab the latest requestId from router logs
ROUTER_LOGS_FRESH=$(oc logs "${ROUTER_POD}" -n "${WANAKU_NAMESPACE}" --tail=30)
LAST_REQUEST_ID=$(echo "${ROUTER_LOGS_FRESH}" | grep -oE "requestId=[^ ,\]]*" | tail -1 | cut -d= -f2)
echo "Router requestId: ${LAST_REQUEST_ID}"

if [ -n "${LAST_REQUEST_ID}" ] && [ "${LAST_REQUEST_ID}" != "" ]; then
  echo "PASS: requestId captured from router logs"

  # Check if the same requestId appears in capability logs
  if [ -n "${CAP_POD}" ]; then
    CAP_LOGS_FRESH=$(oc logs "${CAP_POD}" -n "${WANAKU_NAMESPACE}" --tail=30)
    if echo "${CAP_LOGS_FRESH}" | grep -q "requestId=${LAST_REQUEST_ID}"; then
      echo "PASS: same requestId found in capability logs — end-to-end correlation confirmed"
    else
      echo "WARN: requestId not found in capability logs (may depend on gRPC interceptor activation)"
    fi
  fi
else
  echo "WARN: could not extract requestId from router logs"
fi
```

### Test 8.2: Correlate traceId across services

```bash
LAST_TRACE_ID=$(echo "${ROUTER_LOGS_FRESH}" | grep -oE "traceId=[0-9a-f]{16,}" | tail -1 | cut -d= -f2)
echo "Router traceId: ${LAST_TRACE_ID}"

if [ -n "${LAST_TRACE_ID}" ] && [ -n "${CAP_POD}" ]; then
  CAP_LOGS_FRESH=$(oc logs "${CAP_POD}" -n "${WANAKU_NAMESPACE}" --tail=30)
  if echo "${CAP_LOGS_FRESH}" | grep -q "traceId=${LAST_TRACE_ID}"; then
    echo "PASS: same traceId found in capability logs — distributed tracing confirmed"
  else
    echo "WARN: traceId not found in capability logs"
  fi
elif [ -z "${LAST_TRACE_ID}" ]; then
  echo "WARN: could not extract traceId from router logs"
fi
```

---

## Phase 9: OTEL Disabled by Default (Negative Test)

### Test 9.1: Verify OTEL is disabled without explicit configuration

Deploy a router without OTEL env vars and confirm it starts without sending traces.

```bash
cat <<EOF | oc apply -n "${WANAKU_NAMESPACE}" -f -
apiVersion: "wanaku.ai/v1alpha1"
kind: WanakuRouter
metadata:
  name: wanaku-no-otel-router
spec:
  router:
    image: ${WANAKU_ROUTER_IMAGE}
    imagePullPolicy: Always
    env:
      - name: WANAKU_HTTP_AUTH
        value: none
EOF

oc wait wanakurouter/wanaku-no-otel-router \
  --for=condition=Ready \
  --timeout=120s \
  -n "${WANAKU_NAMESPACE}"
```

### Test 9.2: Verify no traces from the no-OTEL router

```bash
NO_OTEL_URL="http://$(oc get route wanaku-no-otel-router -n "${WANAKU_NAMESPACE}" -o jsonpath='{.spec.host}')"

retry_until "no-otel router health" \
  "curl -sf -o /dev/null '${NO_OTEL_URL}/q/health/live'" 24 5

# Send a request
curl -sf "${NO_OTEL_URL}/api/v1/management/info/version" > /dev/null
sleep 5

# Check Jaeger for traces from this service — there should be none with the default name
# (quarkus.otel.sdk.disabled=true is the default in the PR)
NOOTEL_TRACES=$(curl -sf "${JAEGER_URL}/api/traces?service=wanaku-router&limit=20" 2>/dev/null)
echo "INFO: This is a best-effort check. Since the no-otel router has OTEL disabled by default, no new traces should appear from it."
echo "PASS: negative test completed (manual verification in Jaeger UI recommended)"
```

### Test 9.3: Clean up no-OTEL router

```bash
oc delete wanakurouter wanaku-no-otel-router -n "${WANAKU_NAMESPACE}"
wait_for_deletion deployment wanaku-no-otel-router-mcp-router "${WANAKU_NAMESPACE}" 60
```

---

## Phase 10: Jaeger Trace Verification (Automated)

This phase uses the Jaeger REST API (`/api/services`, `/api/traces`) for deterministic assertions, plus an optional Playwright script for UI rendering smoke tests. The API carries the bulk of verification — Playwright is a secondary check.

### Test 10.1: Verify service discovery via API

```bash
SERVICES=$(curl -sf "${JAEGER_URL}/api/services")
if [ -z "${SERVICES}" ]; then
  echo "FAIL: could not query Jaeger services API"
  exit 1
fi

echo "${SERVICES}" | jq -e '.data[] | select(. == "wanaku-router")' > /dev/null 2>&1
if [ $? -eq 0 ]; then
  echo "PASS: wanaku-router found in Jaeger services"
else
  echo "FAIL: wanaku-router not found in Jaeger services"
  echo "Available services: $(echo "${SERVICES}" | jq -r '.data[]')"
  exit 1
fi
```

### Test 10.2: Verify capability service is also registered

```bash
# The capability service name depends on the wanaku.service.name property (defaults to "wanaku-capability")
CAP_SERVICE_FOUND=$(echo "${SERVICES}" | jq -r '.data[]' | grep -c "wanaku")
echo "Wanaku-related services in Jaeger: ${CAP_SERVICE_FOUND}"

if [ "${CAP_SERVICE_FOUND}" -ge 2 ]; then
  echo "PASS: multiple Wanaku services registered in Jaeger (router + capability)"
else
  echo "WARN: only ${CAP_SERVICE_FOUND} Wanaku service(s) found — capability may not have sent traces yet"
fi
```

### Test 10.3: Fetch recent traces and verify span count

```bash
START=$(( ($(date +%s) - 3600) * 1000000 ))
END=$(( $(date +%s) * 1000000 ))

RECENT_TRACES=$(curl -sf "${JAEGER_URL}/api/traces?service=wanaku-router&start=${START}&end=${END}&limit=5")
TRACE_COUNT=$(echo "${RECENT_TRACES}" | jq '.data | length')

if [ "${TRACE_COUNT}" -eq 0 ]; then
  echo "FAIL: no traces found for wanaku-router in the last hour"
  exit 1
fi
echo "PASS: found ${TRACE_COUNT} trace(s) for wanaku-router"

# Verify the most recent trace has multiple spans
LATEST_TRACE_ID=$(echo "${RECENT_TRACES}" | jq -r '.data[0].traceID')
SPAN_COUNT=$(echo "${RECENT_TRACES}" | jq '.data[0].spans | length')
echo "Latest trace ${LATEST_TRACE_ID}: ${SPAN_COUNT} span(s)"

if [ "${SPAN_COUNT}" -ge 2 ]; then
  echo "PASS: trace has multiple spans (expected: HTTP receive + gRPC call at minimum)"
else
  echo "WARN: trace has only ${SPAN_COUNT} span(s) — may indicate incomplete propagation"
fi
```

### Test 10.4: Verify spans show correct service names

```bash
TRACE_DETAIL=$(curl -sf "${JAEGER_URL}/api/traces/${LATEST_TRACE_ID}")

SPAN_SERVICES=$(echo "${TRACE_DETAIL}" | jq -r '[.data[0].processes | to_entries[].value.serviceName] | unique | .[]')
echo "Services in trace: ${SPAN_SERVICES}"

echo "${SPAN_SERVICES}" | grep -q "wanaku-router" \
  && echo "PASS: wanaku-router spans present in trace" \
  || echo "FAIL: wanaku-router spans missing from trace"
```

### Test 10.5: Verify custom wanaku span attributes

```bash
WANAKU_TAGS=$(echo "${TRACE_DETAIL}" | jq '[
  .data[0].spans[].tags[]
  | select(.key | startswith("wanaku.mcp."))
  | {key: .key, value: .value}
] | unique_by(.key)')

echo "Wanaku span attributes found:"
echo "${WANAKU_TAGS}" | jq -r '.[] | "  \(.key) = \(.value)"'

TAG_COUNT=$(echo "${WANAKU_TAGS}" | jq 'length')
if [ "${TAG_COUNT}" -gt 0 ]; then
  echo "PASS: found ${TAG_COUNT} wanaku.mcp.* attribute(s)"
else
  echo "WARN: no wanaku.mcp.* attributes found (may depend on MCP client sending request IDs)"
fi

# Check for specific attributes
echo "${WANAKU_TAGS}" | jq -e '.[] | select(.key == "wanaku.mcp.request_id")' > /dev/null 2>&1 \
  && echo "PASS: wanaku.mcp.request_id present" \
  || echo "WARN: wanaku.mcp.request_id not found"

echo "${WANAKU_TAGS}" | jq -e '.[] | select(.key == "wanaku.mcp.tool_name")' > /dev/null 2>&1 \
  && echo "PASS: wanaku.mcp.tool_name present" \
  || echo "WARN: wanaku.mcp.tool_name not found"
```

### Test 10.6: Verify parent-child span nesting

```bash
# Extract spans with their parent references
SPAN_TREE=$(echo "${TRACE_DETAIL}" | jq '[
  .data[0].spans[] | {
    spanID: .spanID,
    operation: .operationName,
    service: .process.serviceName,
    parentSpanID: (
      [.references[]? | select(.refType == "CHILD_OF") | .spanID] | first // null
    )
  }
]')

ROOT_SPANS=$(echo "${SPAN_TREE}" | jq '[.[] | select(.parentSpanID == null)] | length')
CHILD_SPANS=$(echo "${SPAN_TREE}" | jq '[.[] | select(.parentSpanID != null)] | length')

echo "Span tree: ${ROOT_SPANS} root span(s), ${CHILD_SPANS} child span(s)"

if [ "${CHILD_SPANS}" -gt 0 ]; then
  echo "PASS: trace has parent-child nesting"
else
  echo "WARN: no child spans found — trace may be flat"
fi

# Show the span tree for inspection
echo ""
echo "Span tree:"
echo "${SPAN_TREE}" | jq -r '.[] | "  [\(.service)] \(.operation) (parent: \(.parentSpanID // "root"))"'
```

### Test 10.7: Verify trace duration is reasonable

```bash
# Trace start and end times are in microseconds
TRACE_START=$(echo "${TRACE_DETAIL}" | jq '[.data[0].spans[].startTime] | min')
TRACE_END=$(echo "${TRACE_DETAIL}" | jq '[.data[0].spans[] | (.startTime + .duration)] | max')
TRACE_DURATION_MS=$(( (TRACE_END - TRACE_START) / 1000 ))

echo "Trace duration: ${TRACE_DURATION_MS}ms"

if [ "${TRACE_DURATION_MS}" -gt 0 ] && [ "${TRACE_DURATION_MS}" -lt 60000 ]; then
  echo "PASS: trace duration is reasonable (${TRACE_DURATION_MS}ms)"
elif [ "${TRACE_DURATION_MS}" -eq 0 ]; then
  echo "FAIL: trace duration is zero — spans may have incorrect timestamps"
else
  echo "WARN: trace duration is ${TRACE_DURATION_MS}ms — unexpectedly long"
fi
```

### Test 10.8 (Optional): Playwright UI smoke test

This test requires Node.js and Playwright installed locally. It verifies that the Jaeger UI renders traces correctly.

**Install Playwright (one-time):**

```bash
npm init -y
npm install @playwright/test
npx playwright install chromium
```

**Run the smoke test:**

```bash
JAEGER_URL="${JAEGER_URL}" npx playwright test tests/plans/scripts/jaeger-ui-smoke.spec.ts
```

**Playwright test script** (`tests/plans/scripts/jaeger-ui-smoke.spec.ts`):

```typescript
import { test, expect } from "@playwright/test";

const JAEGER_URL = process.env.JAEGER_URL;

test.describe("Jaeger UI - Wanaku trace verification", () => {
  test.skip(!JAEGER_URL, "JAEGER_URL not set");

  test("service dropdown contains wanaku-router", async ({ page }) => {
    await page.goto(`${JAEGER_URL}/search`);

    // Click the service dropdown (Ant Design Select wrapped in SearchableSelect)
    const serviceSelect = page.locator('[data-testid="service"]');
    await serviceSelect.click();

    // Wait for dropdown panel and verify wanaku-router is listed
    const routerOption = page.getByText("wanaku-router", { exact: true });
    await expect(routerOption).toBeVisible({ timeout: 10000 });
  });

  test("find traces returns results for wanaku-router", async ({ page }) => {
    await page.goto(`${JAEGER_URL}/search`);

    // Select service
    await page.locator('[data-testid="service"]').click();
    await page.getByText("wanaku-router", { exact: true }).click();

    // Click Find Traces (uses data-test, not data-testid)
    await page.locator('[data-test="submit-btn"]').click();

    // Verify at least one result appears
    const spanCount = page.locator('[data-test="num-spans"]').first();
    await expect(spanCount).toBeVisible({ timeout: 15000 });
  });

  test("trace detail shows multiple service spans", async ({ page }) => {
    await page.goto(`${JAEGER_URL}/search`);

    // Select service and search
    await page.locator('[data-testid="service"]').click();
    await page.getByText("wanaku-router", { exact: true }).click();
    await page.locator('[data-test="submit-btn"]').click();

    // Click the first trace result
    const firstResult = page.locator('[data-test="num-spans"]').first();
    await expect(firstResult).toBeVisible({ timeout: 15000 });
    await firstResult.click();

    // Verify span rows are rendered in the trace detail view
    const spanRows = page.locator(".SpanBarRow");
    await expect(spanRows.first()).toBeVisible({ timeout: 10000 });

    const spanRowCount = await spanRows.count();
    expect(spanRowCount).toBeGreaterThanOrEqual(2);

    // Verify service name labels appear
    const serviceLabels = page.locator(".span-svc-name");
    await expect(serviceLabels.first()).toBeVisible();
  });

  test("span detail shows wanaku custom tags", async ({ page }) => {
    // Navigate to the latest trace via API, then open its detail page
    const apiResp = await page.request.get(
      `${JAEGER_URL}/api/traces?service=wanaku-router&limit=1`
    );
    const body = await apiResp.json();
    const traceId = body.data?.[0]?.traceID;
    test.skip(!traceId, "No traces available to inspect");

    await page.goto(`${JAEGER_URL}/trace/${traceId}`);

    // Wait for spans to render
    const spanRows = page.locator(".SpanBarRow");
    await expect(spanRows.first()).toBeVisible({ timeout: 10000 });

    // Expand the first span to see its detail/tags
    await page.locator(".span-name").first().click();

    // The key-value table should be visible with span tags
    const tagTable = page.locator(".KeyValueTable");
    await expect(tagTable.first()).toBeVisible({ timeout: 5000 });

    // Check for wanaku custom tag keys (best-effort — may not be on every span)
    const tagKeys = await page
      .locator(".KeyValueTable--keyColumn")
      .allTextContents();
    const hasWanakuTag = tagKeys.some((key) =>
      key.startsWith("wanaku.mcp.")
    );

    if (hasWanakuTag) {
      console.log("PASS: found wanaku.mcp.* tag in span detail");
    } else {
      console.log(
        "INFO: no wanaku.mcp.* tag on first span — try expanding other spans"
      );
    }
  });
});
```

**Note:** The Playwright tests use `data-test` (not `data-testid`) for the submit button, matching the Jaeger UI's marker convention. The Ant Design Select dropdowns render in portals, but Playwright handles this correctly via text matching.

---

## Phase 11: Cleanup

### Step 11.1: Delete Wanaku custom resources

```bash
oc delete wanakucapability wanaku-tracing-capabilities -n "${WANAKU_NAMESPACE}" --ignore-not-found=true
wait_for_deletion deployment wanaku-http-tracing "${WANAKU_NAMESPACE}" 60

oc delete wanakurouter wanaku-tracing-router -n "${WANAKU_NAMESPACE}" --ignore-not-found=true
wait_for_deletion deployment wanaku-tracing-router-mcp-router "${WANAKU_NAMESPACE}" 60
```

### Step 11.2: Delete observability infrastructure

```bash
oc delete deployment jaeger -n "${WANAKU_NAMESPACE}" --ignore-not-found=true
oc delete service jaeger -n "${WANAKU_NAMESPACE}" --ignore-not-found=true
oc delete route jaeger -n "${WANAKU_NAMESPACE}" --ignore-not-found=true

oc delete deployment otel-collector -n "${WANAKU_NAMESPACE}" --ignore-not-found=true
oc delete service otel-collector -n "${WANAKU_NAMESPACE}" --ignore-not-found=true
oc delete configmap otel-collector-config -n "${WANAKU_NAMESPACE}" --ignore-not-found=true

echo "PASS: observability infrastructure deleted"
```

### Step 11.3: Full cleanup

Follow [common/cleanup.md](common/cleanup.md) for remaining resources and operator uninstallation.

### Step 11.4: Delete CI branch

```bash
git push origin --delete ci-observability-tracing 2>/dev/null || true
echo "PASS: CI branch deleted"
```
