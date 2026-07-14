# Test Plan: Performance Testing (Local)

## Overview

This test plan covers local performance testing of the Wanaku router using k6 load tests over SSE (Server-Sent Events). It exercises two workloads: MCP tool invocation and MCP resource read, at escalating virtual user (VU) levels (1, 10, 500, 1000, 2000, 30000).

The plan supports two modes:

1. **Single run** — build from the current branch, launch services, run k6, collect metrics.
2. **Baseline comparison** — run the same tests against a CI-built baseline (main branch) and the current branch, then generate a Markdown comparison report highlighting regressions and improvements.

Authentication is handled via Keycloak (Docker or Podman). The router and all capabilities run locally as Java processes.

Every step is fully automatable.

## Prerequisites

### Required tools

| Tool | Minimum version | Verify command |
|------|-----------------|----------------|
| `java` | 21+ | `java -version` |
| `mvn` | 3.9+ | `mvn -version` |
| `docker` or `podman` | 20+ / 4.0+ | `docker --version` or `podman --version` |
| `curl` | any | `curl --version` |
| `k6` | 0.50+ (with xk6-mcp) | `k6 version` |
| `python3` | 3.8+ | `python3 --version` |
| `wanaku` | build from source | `wanaku --version` |

### Container runtime detection

Follow [common/container-runtime.md](common/container-runtime.md). After completion,
`CONTAINER_RUNTIME` is set and exported.

### k6 with xk6-mcp extension

The k6 binary must be compiled with the `xk6-mcp` extension. A standard k6 install will not work — `import mcp from 'k6/x/mcp'` will fail.

### Environment variables

```bash
export WANAKU_REPO_ROOT="${WANAKU_REPO_ROOT:-.}"
# WANAKU_VERSION is derived from the build output in Phase 1.
# Override manually if needed: export WANAKU_VERSION="x.y.z"
export K6_BIN="${K6_BIN:-$HOME/bin/k6}"
export WANAKU_BIN="${WANAKU_BIN:-$HOME/bin/wanaku}"
export JAVA_OPTS="${JAVA_OPTS:--XX:+UseNUMA -Xmx4G -Xms4G}"
export KEYCLOAK_IMAGE="${KEYCLOAK_IMAGE:-quay.io/keycloak/keycloak:26.6.1}"
export KEYCLOAK_HOST="${KEYCLOAK_HOST:-localhost}"
export KEYCLOAK_ADMIN_USER="${KEYCLOAK_ADMIN_USER:-admin}"
export KEYCLOAK_ADMIN_PASS="${KEYCLOAK_ADMIN_PASS:-admin}"
export KEYCLOAK_REALM="${KEYCLOAK_REALM:-wanaku}"
export ROUTER_HOST="${ROUTER_HOST:-${KEYCLOAK_HOST}}"
export VU_LEVELS="${VU_LEVELS:-1 10 500 1000 2000 30000}"
export TEST_DURATION="${TEST_DURATION:-30s}"
export TEST_BASE_DIR="${TEST_BASE_DIR:-$HOME/perf-results}"
export GRPC_PORT_START="${GRPC_PORT_START:-9190}"

# For baseline comparison mode only:
export BASELINE_BRANCH="${BASELINE_BRANCH:-main}"
export CI_BASE="${CI_BASE:-http://integration-ci.usersys.redhat.com:8080/view/Wanaku/job/wanaku-automated-builds/job}"
export EVAL_DIR="${EVAL_DIR:-$HOME/perf-evaluation-$(date +%Y%m%d-%H%M%S)}"
```

### Variable persistence across phases

This plan is designed to run as a single session. Variables set in earlier phases (e.g. `WORK_DIR`, `ROUTER_JAR`, `TOOL_JAR`, `PROVIDER_JAR`, `ROUTER_PID`, `TOOL_PID`, `PROVIDER_PID`, `CLIENT_SECRET`, `KEYCLOAK_URL`, `ROUTER_URL`) must remain exported for subsequent phases. If running phases independently, re-derive these values before proceeding — each phase documents what it produces and what it consumes.

---

## Phase 0: Prerequisites

### Test 0.1: Verify required tools

```bash
FAIL=0

for CMD in java mvn curl python3; do
  if ! command -v "${CMD}" > /dev/null 2>&1; then
    echo "FAIL: ${CMD} is not installed"
    FAIL=1
  else
    echo "PASS: ${CMD} found at $(command -v ${CMD})"
  fi
done

if [ ! -x "${K6_BIN}" ]; then
  echo "FAIL: k6 not found at ${K6_BIN}"
  FAIL=1
else
  echo "PASS: k6 found at ${K6_BIN}"
fi

if [ ! -x "${WANAKU_BIN}" ]; then
  if ! command -v wanaku > /dev/null 2>&1; then
    echo "FAIL: wanaku CLI not found at ${WANAKU_BIN} or on PATH"
    FAIL=1
  else
    echo "PASS: wanaku CLI found on PATH"
  fi
else
  echo "PASS: wanaku CLI found at ${WANAKU_BIN}"
fi

if [ "${FAIL}" -ne 0 ]; then
  echo ""
  echo "FAIL: one or more prerequisites missing"
  exit 1
fi

echo ""
echo "PASS: all prerequisites met"
```

### Test 0.2: Verify k6 has the xk6-mcp extension

```bash
K6_MODULES=$("${K6_BIN}" version 2>&1)
if echo "${K6_MODULES}" | grep -q "mcp"; then
  echo "PASS: k6 has xk6-mcp extension"
else
  echo "FAIL: k6 does not have xk6-mcp extension (rebuild with xk6)"
  exit 1
fi
```

### Test 0.3: Verify environment variables

```bash
for VAR_NAME in WANAKU_REPO_ROOT K6_BIN TEST_DURATION; do
  eval "VAL=\${${VAR_NAME}}"
  if [ -z "${VAL}" ]; then
    echo "FAIL: ${VAR_NAME} is not set"
    exit 1
  fi
  echo "PASS: ${VAR_NAME}=${VAL}"
done
```

### Test 0.4: Verify k6 test scripts exist

```bash
SCRIPT_DIR="${WANAKU_REPO_ROOT}/tests/load"

for SCRIPT in mcp-tools-invoke-sse.js mcp-resources-read-sse.js; do
  if [ ! -f "${SCRIPT_DIR}/${SCRIPT}" ]; then
    echo "FAIL: k6 script not found: ${SCRIPT_DIR}/${SCRIPT}"
    exit 1
  fi
  echo "PASS: ${SCRIPT_DIR}/${SCRIPT} exists"
done
```

---

## Phase 1: Build

### Test 1.1: Build distribution artifacts

```bash
cd "${WANAKU_REPO_ROOT}"
mvn package -Pdist -DskipTests -T1C -q
```

**Derive `WANAKU_VERSION` from build output:**

```bash
export WANAKU_VERSION="${WANAKU_VERSION:-$(cat ${WANAKU_REPO_ROOT}/core/core-util/target/classes/version.txt)}"
echo "WANAKU_VERSION=${WANAKU_VERSION}"
```

**Verification:**

```bash
ROUTER_DIST="${WANAKU_REPO_ROOT}/apps/wanaku-router-backend/target/distributions/wanaku-router-backend-${WANAKU_VERSION}.tar.gz"
TOOL_NOOP_DIST="${WANAKU_REPO_ROOT}/capabilities/tools/wanaku-tool-performance-noop/target/distributions/wanaku-tool-performance-noop-${WANAKU_VERSION}.tar.gz"
PROVIDER_STATIC_DIST="${WANAKU_REPO_ROOT}/capabilities/providers/wanaku-provider-performance-static-file/target/distributions/wanaku-provider-performance-static-file-${WANAKU_VERSION}.tar.gz"

for FILE in "${ROUTER_DIST}" "${TOOL_NOOP_DIST}" "${PROVIDER_STATIC_DIST}"; do
  if [ ! -f "${FILE}" ]; then
    echo "FAIL: distribution artifact not found: ${FILE}"
    exit 1
  fi
  echo "PASS: $(basename ${FILE}) exists"
done
```

### Test 1.2: Unpack the router distribution

```bash
WORK_DIR=$(mktemp -d)
echo "Working directory: ${WORK_DIR}"

ROUTER_DIR="${WORK_DIR}/router"
mkdir -p "${ROUTER_DIR}"
tar xzf "${ROUTER_DIST}" -C "${ROUTER_DIR}"

ROUTER_JAR=$(find "${ROUTER_DIR}" -name 'quarkus-run.jar' -print -quit)
if [ -z "${ROUTER_JAR}" ]; then
  echo "FAIL: quarkus-run.jar not found in router distribution"
  exit 1
fi
echo "PASS: router JAR at ${ROUTER_JAR}"
```

### Test 1.3: Unpack the performance noop tool distribution

```bash
TOOL_DIR="${WORK_DIR}/tool-noop"
mkdir -p "${TOOL_DIR}"
tar xzf "${TOOL_NOOP_DIST}" -C "${TOOL_DIR}"

TOOL_JAR=$(find "${TOOL_DIR}" -name 'quarkus-run.jar' -print -quit)
if [ -z "${TOOL_JAR}" ]; then
  echo "FAIL: quarkus-run.jar not found in tool-noop distribution"
  exit 1
fi
echo "PASS: tool-noop JAR at ${TOOL_JAR}"
```

### Test 1.4: Unpack the static file provider distribution

```bash
PROVIDER_DIR="${WORK_DIR}/provider-static"
mkdir -p "${PROVIDER_DIR}"
tar xzf "${PROVIDER_STATIC_DIST}" -C "${PROVIDER_DIR}"

PROVIDER_JAR=$(find "${PROVIDER_DIR}" -name 'quarkus-run.jar' -print -quit)
if [ -z "${PROVIDER_JAR}" ]; then
  echo "FAIL: quarkus-run.jar not found in provider-static distribution"
  exit 1
fi
echo "PASS: provider-static JAR at ${PROVIDER_JAR}"
```

---

## Phase 2: Keycloak Setup

### Test 2.1: Start Keycloak container

If a keycloak container is already running, skip the start.

```bash
export KEYCLOAK_HOST="${KEYCLOAK_HOST:-localhost}"
export KEYCLOAK_URL="http://${KEYCLOAK_HOST}:8543"

if ${CONTAINER_RUNTIME} ps --filter name=keycloak --format '{{.Names}}' 2>/dev/null | grep -q '^keycloak$'; then
  echo "PASS: Keycloak container already running"
else
  ${CONTAINER_RUNTIME} run -d --name keycloak --rm -p 0.0.0.0:8543:8080 \
    -e KC_BOOTSTRAP_ADMIN_USERNAME="${KEYCLOAK_ADMIN_USER}" \
    -e KC_BOOTSTRAP_ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASS}" \
    -v keycloak-dev:/opt/keycloak/data \
    "${KEYCLOAK_IMAGE}" start-dev

  if [ $? -ne 0 ]; then
    echo "FAIL: could not start Keycloak container"
    exit 1
  fi
  echo "PASS: Keycloak container started"
fi
```

### Test 2.2: Wait for Keycloak to respond

```bash
MAX_RETRIES=90
RETRY_INTERVAL=2
for i in $(seq 1 ${MAX_RETRIES}); do
  HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "${KEYCLOAK_URL}" 2>/dev/null || echo "000")
  if [ "${HTTP_CODE}" = "200" ]; then
    echo "PASS: Keycloak is responding (attempt ${i})"
    break
  fi
  if [ "${i}" -eq "${MAX_RETRIES}" ]; then
    echo "FAIL: Keycloak not responding after ${MAX_RETRIES} attempts"
    exit 1
  fi
  sleep ${RETRY_INTERVAL}
done
```

---

## Phase 3: Start Router and Obtain Credentials

### Test 3.1: Start the router

```bash
export ROUTER_HOST="${ROUTER_HOST:-${KEYCLOAK_HOST}}"
export ROUTER_URL="http://${ROUTER_HOST}:8080"

java ${JAVA_OPTS} -Dquarkus.profile=perf \
  -Dquarkus.http.host=0.0.0.0 \
  -Dauth.server="${KEYCLOAK_URL}" \
  -jar "${ROUTER_JAR}" &
ROUTER_PID=$!
echo "Router started with PID ${ROUTER_PID}"
```

**Verification:**

```bash
MAX_RETRIES=60
RETRY_INTERVAL=2
for i in $(seq 1 ${MAX_RETRIES}); do
  HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "${ROUTER_URL}" 2>/dev/null || echo "000")
  if [ "${HTTP_CODE}" = "200" ] || [ "${HTTP_CODE}" = "401" ] || [ "${HTTP_CODE}" = "404" ]; then
    echo "PASS: router is responding (attempt ${i}, HTTP ${HTTP_CODE})"
    break
  fi
  if [ "${i}" -eq "${MAX_RETRIES}" ]; then
    echo "FAIL: router not responding after ${MAX_RETRIES} attempts"
    kill "${ROUTER_PID}" 2>/dev/null || true
    exit 1
  fi
  sleep ${RETRY_INTERVAL}
done
```

### Test 3.2: Obtain OIDC client secret

```bash
CRED_OUTPUT=$("${WANAKU_BIN}" admin credentials show \
  --admin-username "${KEYCLOAK_ADMIN_USER}" --admin-password "${KEYCLOAK_ADMIN_PASS}" \
  --client-id wanaku-service --show-secret 2>&1)

CRED_CLEAN=$(echo "${CRED_OUTPUT}" | sed 's/\x1b\[[0-9;]*m//g')
CLIENT_SECRET=$(echo "${CRED_CLEAN}" | sed -n 's/.*Client Secret: \([^ ]*\).*/\1/p')

if [ -z "${CLIENT_SECRET}" ]; then
  echo "FAIL: could not parse client secret from wanaku output"
  echo "${CRED_OUTPUT}"
  exit 1
fi
echo "PASS: client secret obtained (length: ${#CLIENT_SECRET})"
```

---

## Phase 4: Start Capabilities

### Test 4.1: Start the performance noop tool

```bash
GRPC_PORT=${GRPC_PORT_START}

java ${JAVA_OPTS} -Dquarkus.profile=perf \
  -Dquarkus.grpc.server.port="${GRPC_PORT}" \
  -Dwanaku.service.registration.uri="${ROUTER_URL}" \
  -Dquarkus.oidc-client.auth-server-url="${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}" \
  -Dquarkus.oidc-client.client-id=wanaku-service \
  -Dquarkus.oidc-client.credentials.secret="${CLIENT_SECRET}" \
  -jar "${TOOL_JAR}" &
TOOL_PID=$!
echo "Tool noop started with PID ${TOOL_PID} on gRPC port ${GRPC_PORT}"
```

### Test 4.2: Start the static file provider

```bash
GRPC_PORT=$((GRPC_PORT_START + 1))

java ${JAVA_OPTS} -Dquarkus.profile=perf \
  -Dquarkus.grpc.server.port="${GRPC_PORT}" \
  -Dwanaku.service.registration.uri="${ROUTER_URL}" \
  -Dquarkus.oidc-client.auth-server-url="${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}" \
  -Dquarkus.oidc-client.client-id=wanaku-service \
  -Dquarkus.oidc-client.credentials.secret="${CLIENT_SECRET}" \
  -jar "${PROVIDER_JAR}" &
PROVIDER_PID=$!
echo "Provider static-file started with PID ${PROVIDER_PID} on gRPC port ${GRPC_PORT}"
```

### Test 4.3: Wait for capabilities to register

Capabilities register with the router asynchronously after startup.

```bash
MAX_RETRIES=30
RETRY_INTERVAL=2
for i in $(seq 1 ${MAX_RETRIES}); do
  TOOL_COUNT=$(curl -s "${ROUTER_URL}/api/v1/tools" 2>/dev/null | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d.get('data',{}).get('tools',[])))" 2>/dev/null || echo "0")
  if [ "${TOOL_COUNT}" -gt 0 ]; then
    echo "PASS: ${TOOL_COUNT} tool(s) registered (attempt ${i})"
    break
  fi
  if [ "${i}" -eq "${MAX_RETRIES}" ]; then
    echo "FAIL: no tools registered after ${MAX_RETRIES} attempts"
    exit 1
  fi
  sleep ${RETRY_INTERVAL}
done
```

### Test 4.4: Verify the performancenoop tool is listed

```bash
TOOLS_OUTPUT=$(curl -s "${ROUTER_URL}/api/v1/tools" 2>/dev/null)
if echo "${TOOLS_OUTPUT}" | grep -q "performancenoop"; then
  echo "PASS: performancenoop tool is registered"
else
  echo "FAIL: performancenoop tool not found in tool list"
  echo "${TOOLS_OUTPUT}"
  exit 1
fi
```

### Test 4.5: Verify the in-memory-file.txt resource is listed

```bash
RESOURCES_OUTPUT=$(curl -s "${ROUTER_URL}/api/v1/resources" 2>/dev/null)
if echo "${RESOURCES_OUTPUT}" | grep -q "in-memory-file.txt"; then
  echo "PASS: in-memory-file.txt resource is registered"
else
  echo "FAIL: in-memory-file.txt resource not found in resource list"
  echo "${RESOURCES_OUTPUT}"
  exit 1
fi
```

---

## Phase 5: Run k6 Tool Invocation Tests

Run the tool invocation test at each VU level. The router and capabilities are restarted between VU levels to ensure clean state.

**Note:** For the first VU level, services are already running from Phase 3–4. For subsequent VU levels, services must be stopped and restarted (see Phase 7 restart procedure).

### Test 5.1: Run tool invocation at each VU level

For each VU level in `${VU_LEVELS}`:

```bash
TOOL_RESULTS_DIR="${TEST_BASE_DIR}/tools-invoke-sse"
mkdir -p "${TOOL_RESULTS_DIR}"

for VUS in ${VU_LEVELS}; do
  echo "--- Running k6 tool invocation: ${VUS} VUs ---"

  "${K6_BIN}" run \
    --no-usage-report \
    --tag "name=tools-invoke-sse" \
    --out "csv=${TOOL_RESULTS_DIR}/test-results-vus-${VUS}.csv" \
    --summary-export "${TOOL_RESULTS_DIR}/test-summary-vus-${VUS}.json" \
    --vus "${VUS}" \
    --duration "${TEST_DURATION}" \
    --console-output "${TOOL_RESULTS_DIR}/test-output-vus-${VUS}.log" \
    --no-color \
    "${WANAKU_REPO_ROOT}/tests/load/mcp-tools-invoke-sse.js"

  K6_EXIT=$?
  if [ "${K6_EXIT}" -ne 0 ]; then
    echo "WARNING: k6 exited with status ${K6_EXIT} at ${VUS} VUs"
  fi

  if [ ! -f "${TOOL_RESULTS_DIR}/test-summary-vus-${VUS}.json" ]; then
    echo "FAIL: summary file not created for ${VUS} VUs"
    exit 1
  fi
  echo "PASS: tool invocation completed at ${VUS} VUs"
done
```

### Test 5.2: Verify tool invocation results exist

```bash
for VUS in ${VU_LEVELS}; do
  SUMMARY="${TOOL_RESULTS_DIR}/test-summary-vus-${VUS}.json"
  if [ ! -f "${SUMMARY}" ]; then
    echo "FAIL: missing summary: ${SUMMARY}"
    exit 1
  fi

  if ! python3 -c "import json; json.load(open('${SUMMARY}'))" 2>/dev/null; then
    echo "FAIL: invalid JSON in ${SUMMARY}"
    exit 1
  fi
  echo "PASS: ${SUMMARY} is valid JSON"
done
```

---

## Phase 6: Run k6 Resource Read Tests

### Test 6.1: Run resource read at each VU level

```bash
RESOURCE_RESULTS_DIR="${TEST_BASE_DIR}/resources-read-sse"
mkdir -p "${RESOURCE_RESULTS_DIR}"

for VUS in ${VU_LEVELS}; do
  echo "--- Running k6 resource read: ${VUS} VUs ---"

  "${K6_BIN}" run \
    --no-usage-report \
    --tag "name=resources-read-sse" \
    --out "csv=${RESOURCE_RESULTS_DIR}/test-results-vus-${VUS}.csv" \
    --summary-export "${RESOURCE_RESULTS_DIR}/test-summary-vus-${VUS}.json" \
    --vus "${VUS}" \
    --duration "${TEST_DURATION}" \
    --console-output "${RESOURCE_RESULTS_DIR}/test-output-vus-${VUS}.log" \
    --no-color \
    "${WANAKU_REPO_ROOT}/tests/load/mcp-resources-read-sse.js"

  K6_EXIT=$?
  if [ "${K6_EXIT}" -ne 0 ]; then
    echo "WARNING: k6 exited with status ${K6_EXIT} at ${VUS} VUs"
  fi

  if [ ! -f "${RESOURCE_RESULTS_DIR}/test-summary-vus-${VUS}.json" ]; then
    echo "FAIL: summary file not created for ${VUS} VUs"
    exit 1
  fi
  echo "PASS: resource read completed at ${VUS} VUs"
done
```

### Test 6.2: Verify resource read results exist

```bash
for VUS in ${VU_LEVELS}; do
  SUMMARY="${RESOURCE_RESULTS_DIR}/test-summary-vus-${VUS}.json"
  if [ ! -f "${SUMMARY}" ]; then
    echo "FAIL: missing summary: ${SUMMARY}"
    exit 1
  fi

  if ! python3 -c "import json; json.load(open('${SUMMARY}'))" 2>/dev/null; then
    echo "FAIL: invalid JSON in ${SUMMARY}"
    exit 1
  fi
  echo "PASS: ${SUMMARY} is valid JSON"
done
```

---

## Phase 7: Metric Validation

**Required variables from prior phases:** `TEST_BASE_DIR` (env var, Phase 0), `TOOL_RESULTS_DIR` (set in Phase 5). If running this phase independently, derive the path:

```bash
TOOL_RESULTS_DIR="${TEST_BASE_DIR}/tools-invoke-sse"
```

### Test 7.1: Verify no excessive errors at low VU levels

At 1 VU, the error count should be zero (the system is not under stress).

```bash
TOOL_RESULTS_DIR="${TOOL_RESULTS_DIR:-${TEST_BASE_DIR}/tools-invoke-sse}"
SUMMARY="${TOOL_RESULTS_DIR}/test-summary-vus-1.json"
ERROR_COUNT=$(python3 -c "
import json
d = json.load(open('${SUMMARY}'))
m = d.get('metrics', {})
errs = m.get('mcp_request_errors', {})
vals = errs.get('values', errs)
print(int(vals.get('count', 0)))
" 2>/dev/null || echo "-1")

if [ "${ERROR_COUNT}" -eq 0 ]; then
  echo "PASS: zero MCP errors at 1 VU"
elif [ "${ERROR_COUNT}" -gt 0 ]; then
  echo "FAIL: ${ERROR_COUNT} MCP errors at 1 VU (expected 0)"
  exit 1
else
  echo "WARNING: could not parse error count from summary"
fi
```

### Test 7.2: Verify iterations completed at 1 VU

```bash
ITER_COUNT=$(python3 -c "
import json
d = json.load(open('${SUMMARY}'))
m = d.get('metrics', {})
iters = m.get('iterations', {})
vals = iters.get('values', iters)
print(int(vals.get('count', 0)))
" 2>/dev/null || echo "0")

if [ "${ITER_COUNT}" -gt 0 ]; then
  echo "PASS: ${ITER_COUNT} iterations completed at 1 VU"
else
  echo "FAIL: no iterations completed at 1 VU"
  exit 1
fi
```

---

## Phase 8: Baseline Comparison (Optional)

This phase downloads baseline artifacts from CI, runs the same tests against them, and generates a comparison report. Skip this phase for single-run performance testing.

### Test 8.1: Download baseline router from CI

```bash
mkdir -p "${EVAL_DIR}"
BASELINE_ROUTER_DIST="${EVAL_DIR}/baseline-router.tar.gz"
CI_ROUTER="${CI_BASE}/${BASELINE_BRANCH}/lastBuild/artifact/wanaku/apps/wanaku-router-backend/target/distributions/wanaku-router-backend-${WANAKU_VERSION}.tar.gz"

curl -fsSL -o "${BASELINE_ROUTER_DIST}" "${CI_ROUTER}"
if [ ! -f "${BASELINE_ROUTER_DIST}" ]; then
  echo "FAIL: could not download baseline router from CI"
  exit 1
fi
echo "PASS: baseline router downloaded"
```

### Test 8.2: Download baseline tool-noop from CI

```bash
BASELINE_TOOL_DIST="${EVAL_DIR}/baseline-tool-noop.tar.gz"
CI_TOOL="${CI_BASE}/${BASELINE_BRANCH}/lastBuild/artifact/wanaku/capabilities/tools/wanaku-tool-performance-noop/target/distributions/wanaku-tool-performance-noop-${WANAKU_VERSION}.tar.gz"

curl -fsSL -o "${BASELINE_TOOL_DIST}" "${CI_TOOL}"
if [ ! -f "${BASELINE_TOOL_DIST}" ]; then
  echo "FAIL: could not download baseline tool-noop from CI"
  exit 1
fi
echo "PASS: baseline tool-noop downloaded"
```

### Test 8.3: Download baseline static-file provider from CI

```bash
BASELINE_PROVIDER_DIST="${EVAL_DIR}/baseline-provider-static.tar.gz"
CI_PROVIDER="${CI_BASE}/${BASELINE_BRANCH}/lastBuild/artifact/wanaku/capabilities/providers/wanaku-provider-performance-static-file/target/distributions/wanaku-provider-performance-static-file-${WANAKU_VERSION}.tar.gz"

curl -fsSL -o "${BASELINE_PROVIDER_DIST}" "${CI_PROVIDER}"
if [ ! -f "${BASELINE_PROVIDER_DIST}" ]; then
  echo "FAIL: could not download baseline provider-static from CI"
  exit 1
fi
echo "PASS: baseline provider-static downloaded"
```

### Test 8.4: Run baseline tests

Stop all services from the patched run (Phase 3–4), then re-run using baseline artifacts. Use `run-perf-test.sh` with the `--test-name baseline` label:

```bash
kill "${ROUTER_PID}" 2>/dev/null || true
kill "${TOOL_PID}" 2>/dev/null || true
kill "${PROVIDER_PID}" 2>/dev/null || true
sleep 2

"${WANAKU_REPO_ROOT}/tests/load/run-perf-test.sh" \
  --router-from "${BASELINE_ROUTER_DIST}" \
  --capability-from "${BASELINE_TOOL_DIST}" \
  --suite tools-sse \
  --test-name baseline \
  --test-base-dir "${EVAL_DIR}"

EXIT_CODE=$?
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: baseline tool tests failed (exit ${EXIT_CODE})"
  exit 1
fi
echo "PASS: baseline tool tests completed"
```

### Test 8.5: Run patched tests with labeling

```bash
"${WANAKU_REPO_ROOT}/tests/load/run-perf-test.sh" \
  --router-from "${ROUTER_DIST}" \
  --capability-from "${TOOL_NOOP_DIST}" \
  --suite tools-sse \
  --test-name patched \
  --test-base-dir "${EVAL_DIR}"

EXIT_CODE=$?
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: patched tool tests failed (exit ${EXIT_CODE})"
  exit 1
fi
echo "PASS: patched tool tests completed"
```

### Test 8.6: Generate comparison report

```bash
python3 "${WANAKU_REPO_ROOT}/tests/load/generate-perf-report.py" \
  --eval-dir "${EVAL_DIR}" \
  --test-scope tools

if [ ! -f "${EVAL_DIR}/perf-report.md" ]; then
  echo "FAIL: comparison report not generated"
  exit 1
fi
echo "PASS: comparison report generated at ${EVAL_DIR}/perf-report.md"
```

### Test 8.7: Check for regressions in report

A red circle indicator in the report means a >10% regression was detected.

```bash
if grep -q ":red_circle:" "${EVAL_DIR}/perf-report.md"; then
  echo "WARNING: regressions detected in performance report"
  grep ":red_circle:" "${EVAL_DIR}/perf-report.md"
else
  echo "PASS: no regressions detected"
fi
```

---

## Phase 9: Cleanup

### Test 9.1: Stop Java processes

```bash
for PID_VAR in ROUTER_PID TOOL_PID PROVIDER_PID; do
  eval "PID=\${${PID_VAR}:-}"
  if [ -n "${PID}" ] && kill -0 "${PID}" 2>/dev/null; then
    kill "${PID}" 2>/dev/null || true
    echo "PASS: stopped ${PID_VAR} (PID ${PID})"
  fi
done

sleep 2

for PID_VAR in ROUTER_PID TOOL_PID PROVIDER_PID; do
  eval "PID=\${${PID_VAR}:-}"
  if [ -n "${PID}" ] && kill -0 "${PID}" 2>/dev/null; then
    kill -9 "${PID}" 2>/dev/null || true
    echo "PASS: force-killed ${PID_VAR} (PID ${PID})"
  fi
done
```

### Test 9.2: Stop Keycloak container

```bash
${CONTAINER_RUNTIME} stop keycloak 2>/dev/null || true
echo "PASS: Keycloak stopped (or was not running)"
```

### Test 9.3: Remove temporary directory

```bash
if [ -n "${WORK_DIR}" ] && [ -d "${WORK_DIR}" ]; then
  rm -rf "${WORK_DIR}"
  echo "PASS: removed ${WORK_DIR}"
fi
```

---

## Test Summary

| ID | Test | Priority | Phase |
|----|------|----------|-------|
| 0.1 | Verify required tools | P0 | Prerequisites |
| 0.2 | Verify k6 has xk6-mcp extension | P0 | Prerequisites |
| 0.3 | Verify environment variables | P0 | Prerequisites |
| 0.4 | Verify k6 test scripts exist | P0 | Prerequisites |
| 1.1 | Build distribution artifacts | P0 | Build |
| 1.2 | Unpack router distribution | P0 | Build |
| 1.3 | Unpack tool-noop distribution | P0 | Build |
| 1.4 | Unpack provider-static distribution | P0 | Build |
| 2.1 | Start Keycloak container | P0 | Keycloak |
| 2.2 | Wait for Keycloak to respond | P0 | Keycloak |
| 3.1 | Start the router | P0 | Router |
| 3.2 | Obtain OIDC client secret | P0 | Router |
| 4.1 | Start the performance noop tool | P0 | Capabilities |
| 4.2 | Start the static file provider | P0 | Capabilities |
| 4.3 | Wait for capabilities to register | P0 | Capabilities |
| 4.4 | Verify performancenoop tool listed | P1 | Capabilities |
| 4.5 | Verify in-memory-file.txt resource listed | P1 | Capabilities |
| 5.1 | Run tool invocation at each VU level | P0 | Tools Test |
| 5.2 | Verify tool invocation results exist | P1 | Tools Test |
| 6.1 | Run resource read at each VU level | P0 | Resources Test |
| 6.2 | Verify resource read results exist | P1 | Resources Test |
| 7.1 | Verify no errors at 1 VU | P1 | Validation |
| 7.2 | Verify iterations completed at 1 VU | P1 | Validation |
| 8.1 | Download baseline router from CI | P2 | Comparison |
| 8.2 | Download baseline tool-noop from CI | P2 | Comparison |
| 8.3 | Download baseline provider-static from CI | P2 | Comparison |
| 8.4 | Run baseline tests | P2 | Comparison |
| 8.5 | Run patched tests with labeling | P2 | Comparison |
| 8.6 | Generate comparison report | P2 | Comparison |
| 8.7 | Check for regressions in report | P2 | Comparison |
| 9.1 | Stop Java processes | P0 | Cleanup |
| 9.2 | Stop Keycloak container | P0 | Cleanup |
| 9.3 | Remove temporary directory | P0 | Cleanup |

## Key Metrics Tracked

| Metric | Type | Description |
|--------|------|-------------|
| `mcp_request_duration` (avg, med, p90, p95, max) | Latency | MCP request roundtrip time |
| `mcp_request_count` (rate, count) | Throughput | Total MCP requests processed |
| `mcp_request_errors` (rate, count) | Errors | Failed MCP requests |
| `iterations` (rate, count) | Throughput | k6 iteration completions |
| `iteration_duration` (avg, p95) | Latency | Full k6 iteration time |
| `data_sent` / `data_received` (rate) | Network | Network throughput |

## Report Indicators

- Green circle: >5% improvement over baseline
- Red circle: >10% regression over baseline
- Latency metrics: lower is better
- Throughput metrics: higher is better
