# Test Plan: Capability Archetype Full Lifecycle

## Overview

This test plan verifies the full lifecycle of a capability generated from a Wanaku archetype: scaffold, build, start, register with the router, and invoke via MCP. It extends the existing build-only archetype test (`tests/archetype-tests/test-archetypes.sh`) to cover runtime behavior.

The plan covers four archetype variants:

- **Quarkus tool service** (default `--type quarkus`)
- **Camel tool service** (`--type camel`)
- **Quarkus resource provider** (default `--type quarkus`)
- **Camel resource provider** (`--type camel`)

Every step except "Phase 0: Prerequisites" is fully automatable.

## Prerequisites

### Required tools

| Tool | Minimum version | Verify command |
|------|-----------------|----------------|
| `java` | 21+ | `java -version` |
| `mvn` | 3.9+ | `mvn -version` |
| `wanaku` | 0.2.0+ | `wanaku --version` |
| `jq` | 1.6+ | `jq --version` |

### CLI invocation

When using the CLI from a local build (not installed), use `java -jar` directly:

```bash
CLI_JAR="apps/wanaku-cli/target/quarkus-app/quarkus-run.jar"
java -jar ${CLI_JAR} capabilities create tool --name ...
```

Do **not** assign the full command to a single variable (e.g., `WANAKU_CLI="java -jar path/to/jar"`) -- zsh treats it as a single token. Use `CLI_JAR` for the path and call `java -jar ${CLI_JAR}` explicitly.

### Environment variables

```bash
export WANAKU_REPO_ROOT="${WANAKU_REPO_ROOT:-.}"
export WANAKU_ROUTER_URL="${WANAKU_ROUTER_URL:-http://localhost:8080}"
export WANAKU_MCP_SSE_URI="${WANAKU_MCP_SSE_URI:-${WANAKU_ROUTER_URL}/public/mcp/sse}"
export TOOL_HTTP_PORT="${TOOL_HTTP_PORT:-9010}"
export CAMEL_TOOL_HTTP_PORT="${CAMEL_TOOL_HTTP_PORT:-9011}"
```

### Helper: poll for capability registration

```bash
wait_for_capability() {
  local SERVICE_NAME="$1"
  local HOST="${2:-${WANAKU_ROUTER_URL}}"
  local TIMEOUT="${3:-120}"
  local INTERVAL=5
  local ELAPSED=0

  while true; do
    OUTPUT=$(wanaku capabilities list --host "${HOST}" --plain 2>&1)
    if echo "${OUTPUT}" | grep -q "${SERVICE_NAME}"; then
      echo "PASS: capability '${SERVICE_NAME}' registered (${ELAPSED}s)"
      return 0
    fi
    if [ "${ELAPSED}" -ge "${TIMEOUT}" ]; then
      echo "FAIL: capability '${SERVICE_NAME}' not registered after ${TIMEOUT}s"
      return 1
    fi
    sleep ${INTERVAL}
    ELAPSED=$((ELAPSED + INTERVAL))
  done
}
```

### Helper: poll for process readiness

```bash
wait_for_process_health() {
  local PORT="$1"
  local TIMEOUT="${2:-60}"
  local INTERVAL=3
  local ELAPSED=0

  while true; do
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:${PORT}/q/health/ready" 2>/dev/null || echo "000")
    if [ "${HTTP_CODE}" = "200" ]; then
      echo "PASS: process on port ${PORT} is healthy (${ELAPSED}s)"
      return 0
    fi
    if [ "${ELAPSED}" -ge "${TIMEOUT}" ]; then
      echo "FAIL: process on port ${PORT} not healthy after ${TIMEOUT}s (last HTTP ${HTTP_CODE})"
      return 1
    fi
    sleep ${INTERVAL}
    ELAPSED=$((ELAPSED + INTERVAL))
  done
}
```

---

## Phase 0: Verify Prerequisites

### Test 0.1: Java is available

```bash
java -version 2>&1
if [ $? -eq 0 ]; then
  echo "PASS: java is available"
else
  echo "FAIL: java is not available"
  exit 1
fi
```

### Test 0.2: Maven is available

```bash
mvn -version 2>&1
if [ $? -eq 0 ]; then
  echo "PASS: mvn is available"
else
  echo "FAIL: mvn is not available"
  exit 1
fi
```

### Test 0.3: Wanaku CLI is available

```bash
wanaku --version 2>&1
if [ $? -eq 0 ]; then
  echo "PASS: wanaku CLI is available"
else
  echo "FAIL: wanaku CLI is not available"
  exit 1
fi
```

---

## Phase 1: Start the Router

Follow [common/start-local.md](common/start-local.md) to build and start the local Wanaku stack.

After completion, `VERSION`, `CLI_JAR`, `WANAKU_ROUTER_URL`, and `WANAKU_PID` must be set.

### Test 1.1: Verify router is healthy

```bash
MAX_RETRIES=30
RETRY_INTERVAL=5
for i in $(seq 1 ${MAX_RETRIES}); do
  HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "${WANAKU_ROUTER_URL}/q/health/ready" 2>/dev/null || echo "000")
  if [ "${HTTP_CODE}" = "200" ]; then
    echo "PASS: router is healthy (attempt ${i})"
    break
  fi
  if [ "${i}" -eq "${MAX_RETRIES}" ]; then
    echo "FAIL: router not healthy after ${MAX_RETRIES} attempts (last HTTP ${HTTP_CODE})"
    exit 1
  fi
  sleep ${RETRY_INTERVAL}
done
```

### Test 1.2: Verify CLI can connect to the router

```bash
wanaku tools list --host "${WANAKU_ROUTER_URL}" --plain
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: CLI can connect to router"
else
  echo "FAIL: CLI cannot connect to router (exit code ${EXIT_CODE})"
  exit 1
fi
```

---

## Phase 2: Scaffold a Quarkus Tool Capability

### Test 2.1: Create a temporary working directory

```bash
ARCHETYPE_WORK_DIR=$(mktemp -d)
echo "Working directory: ${ARCHETYPE_WORK_DIR}"
if [ -d "${ARCHETYPE_WORK_DIR}" ]; then
  echo "PASS: temp directory created"
else
  echo "FAIL: could not create temp directory"
  exit 1
fi
```

### Test 2.2: Scaffold the tool project

```bash
cd "${ARCHETYPE_WORK_DIR}"
wanaku capabilities create tool --name e2e-test-tool
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: scaffolding succeeded"
else
  echo "FAIL: scaffolding failed (exit code ${EXIT_CODE})"
  exit 1
fi
```

### Test 2.3: Verify project structure

The tool archetype generates a directory named `wanaku-tool-service-<sanitized-name>`. Dashes in the name are removed during sanitization.

```bash
TOOL_PROJECT_DIR="${ARCHETYPE_WORK_DIR}/wanaku-tool-service-e2etesttool"
if [ -d "${TOOL_PROJECT_DIR}" ]; then
  echo "PASS: project directory exists"
else
  echo "FAIL: expected directory ${TOOL_PROJECT_DIR} not found"
  ls -1 "${ARCHETYPE_WORK_DIR}"
  exit 1
fi

for FILE in pom.xml src/main/java src/main/resources/application.properties; do
  if [ -e "${TOOL_PROJECT_DIR}/${FILE}" ]; then
    echo "PASS: ${FILE} exists"
  else
    echo "FAIL: ${FILE} not found in project"
  fi
done
```

---

## Phase 3: Build the Quarkus Tool Capability

### Test 3.1: Build with distribution profile

```bash
cd "${TOOL_PROJECT_DIR}"
mvn -DskipTests -Pdist clean package
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: build succeeded"
else
  echo "FAIL: build failed (exit code ${EXIT_CODE})"
  exit 1
fi
```

### Test 3.2: Verify distribution ZIP was created

```bash
TOOL_DIST=$(find "${TOOL_PROJECT_DIR}/target/distributions" -name "*.zip" -type f 2>/dev/null | head -1)
if [ -n "${TOOL_DIST}" ]; then
  echo "PASS: distribution ZIP found: ${TOOL_DIST}"
else
  echo "FAIL: no distribution ZIP in target/distributions/"
  ls -lR "${TOOL_PROJECT_DIR}/target/distributions/" 2>/dev/null
  exit 1
fi
```

### Test 3.3: Verify Quarkus runner JAR exists

```bash
TOOL_RUNNER="${TOOL_PROJECT_DIR}/target/quarkus-app/quarkus-run.jar"
if [ -f "${TOOL_RUNNER}" ]; then
  echo "PASS: quarkus runner JAR exists"
else
  echo "FAIL: quarkus runner JAR not found at ${TOOL_RUNNER}"
  exit 1
fi
```

---

## Phase 4: Start and Register the Tool Capability

### Test 4.1: Start the capability as a background process

The generated capability uses the service name `e2etesttool` (derived from `--name e2e-test-tool` with dashes removed and lowercased). Start it on a dedicated port and point it at the router for registration.

```bash
java -jar "${TOOL_RUNNER}" \
  -Dquarkus.http.port=${TOOL_HTTP_PORT} \
  -Dwanaku.service.registration.uri=${WANAKU_ROUTER_URL} &
TOOL_CAPABILITY_PID=$!
echo "Tool capability started with PID ${TOOL_CAPABILITY_PID}"

if [ -n "${TOOL_CAPABILITY_PID}" ]; then
  echo "PASS: capability process launched"
else
  echo "FAIL: capability process did not launch"
  exit 1
fi
```

### Test 4.2: Wait for capability health check

```bash
wait_for_process_health "${TOOL_HTTP_PORT}" 60
```

### Test 4.3: Wait for capability to register with the router

```bash
wait_for_capability "e2etesttool" "${WANAKU_ROUTER_URL}" 120
```

### Test 4.4: Verify capability appears in capabilities list

```bash
OUTPUT=$(wanaku capabilities list --host "${WANAKU_ROUTER_URL}" --plain 2>&1)
echo "${OUTPUT}" | grep -q "e2etesttool" \
  && echo "PASS: e2etesttool is listed in capabilities" \
  || echo "FAIL: e2etesttool not found in capabilities list"
```

---

## Phase 5: Invoke the Tool via MCP

### Test 5.1: Register a tool backed by the new capability type

The generated capability's service name is `e2etesttool`. Register a tool that uses this type. The default archetype creates a capability that accepts a URI but does not perform real work -- this test verifies the routing path, not the tool output.

```bash
wanaku tools add \
  --host "${WANAKU_ROUTER_URL}" \
  --name e2e-lifecycle-tool \
  --namespace public \
  --description "E2E lifecycle test tool" \
  --uri "e2etesttool://test" \
  --type e2etesttool \
  --property "input:string,Test input parameter" \
  --required input

EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: tool registered"
else
  echo "FAIL: tool registration failed (exit code ${EXIT_CODE})"
  exit 1
fi
```

### Test 5.2: Verify the tool is listed

```bash
OUTPUT=$(wanaku tools list --host "${WANAKU_ROUTER_URL}" --plain 2>&1)
echo "${OUTPUT}" | grep -q "e2e-lifecycle-tool" \
  && echo "PASS: e2e-lifecycle-tool appears in tools list" \
  || echo "FAIL: e2e-lifecycle-tool not found in tools list"
```

### Test 5.3: Verify the tool is visible via MCP

```bash
OUTPUT=$(wanaku mcp tool list --uri "${WANAKU_MCP_SSE_URI}" --plain 2>&1)
EXIT_CODE=$?
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: mcp tool list failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
else
  echo "${OUTPUT}" | grep -q "e2e-lifecycle-tool" \
    && echo "PASS: e2e-lifecycle-tool visible via MCP" \
    || echo "FAIL: e2e-lifecycle-tool not visible via MCP"
fi
```

### Test 5.4: Invoke the tool via MCP

The default archetype tool may not return meaningful data, but the call should succeed (the request reaches the capability and comes back without error).

```bash
OUTPUT=$(wanaku mcp tool \
  --uri "${WANAKU_MCP_SSE_URI}" \
  --name e2e-lifecycle-tool \
  --param input=hello \
  --plain 2>&1)
EXIT_CODE=$?

echo "Exit code: ${EXIT_CODE}"
echo "Output: ${OUTPUT}"

if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: tool invocation succeeded"
else
  echo "WARN: tool invocation returned non-zero (exit code ${EXIT_CODE}) -- check if archetype default handler returns an error"
fi
```

### Test 5.5: Invoke the tool with a missing required parameter

```bash
OUTPUT=$(wanaku mcp tool \
  --uri "${WANAKU_MCP_SSE_URI}" \
  --name e2e-lifecycle-tool \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: missing required parameter caused an error (expected)"
else
  echo "WARN: missing required parameter did not fail -- check response body for error"
  echo "${OUTPUT}"
fi
```

---

## Phase 6: Scaffold and Build a Camel Tool Capability

### Test 6.1: Scaffold a Camel tool project

```bash
cd "${ARCHETYPE_WORK_DIR}"
wanaku capabilities create tool --name e2e-test-camel --type camel
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: Camel tool scaffolding succeeded"
else
  echo "FAIL: Camel tool scaffolding failed (exit code ${EXIT_CODE})"
  exit 1
fi
```

### Test 6.2: Verify Camel tool project structure

```bash
CAMEL_TOOL_DIR="${ARCHETYPE_WORK_DIR}/wanaku-tool-service-e2etestcamel"
if [ -d "${CAMEL_TOOL_DIR}" ]; then
  echo "PASS: Camel tool project directory exists"
else
  echo "FAIL: expected directory ${CAMEL_TOOL_DIR} not found"
  ls -1 "${ARCHETYPE_WORK_DIR}"
  exit 1
fi

for FILE in pom.xml src/main/java src/main/resources/application.properties; do
  if [ -e "${CAMEL_TOOL_DIR}/${FILE}" ]; then
    echo "PASS: ${FILE} exists"
  else
    echo "FAIL: ${FILE} not found in Camel tool project"
  fi
done
```

### Test 6.3: Build the Camel tool with distribution profile

```bash
cd "${CAMEL_TOOL_DIR}"
mvn -DskipTests -Pdist clean package
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: Camel tool build succeeded"
else
  echo "FAIL: Camel tool build failed (exit code ${EXIT_CODE})"
  exit 1
fi
```

### Test 6.4: Verify Camel tool distribution ZIP

```bash
CAMEL_TOOL_DIST=$(find "${CAMEL_TOOL_DIR}/target/distributions" -name "*.zip" -type f 2>/dev/null | head -1)
if [ -n "${CAMEL_TOOL_DIST}" ]; then
  echo "PASS: Camel tool distribution ZIP found: ${CAMEL_TOOL_DIST}"
else
  echo "FAIL: no Camel tool distribution ZIP in target/distributions/"
  exit 1
fi
```

---

## Phase 7: Scaffold and Build a Quarkus Resource Provider

### Test 7.1: Scaffold a resource provider

```bash
cd "${ARCHETYPE_WORK_DIR}"
wanaku capabilities create resource --name e2e-test-provider
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: resource provider scaffolding succeeded"
else
  echo "FAIL: resource provider scaffolding failed (exit code ${EXIT_CODE})"
  exit 1
fi
```

### Test 7.2: Verify resource provider project structure

```bash
PROVIDER_DIR="${ARCHETYPE_WORK_DIR}/wanaku-provider-e2etestprovider"
if [ -d "${PROVIDER_DIR}" ]; then
  echo "PASS: resource provider project directory exists"
else
  echo "FAIL: expected directory ${PROVIDER_DIR} not found"
  ls -1 "${ARCHETYPE_WORK_DIR}"
  exit 1
fi

for FILE in pom.xml src/main/java src/main/resources/application.properties; do
  if [ -e "${PROVIDER_DIR}/${FILE}" ]; then
    echo "PASS: ${FILE} exists"
  else
    echo "FAIL: ${FILE} not found in resource provider project"
  fi
done
```

### Test 7.3: Build the resource provider with distribution profile

```bash
cd "${PROVIDER_DIR}"
mvn -DskipTests -Pdist clean package
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: resource provider build succeeded"
else
  echo "FAIL: resource provider build failed (exit code ${EXIT_CODE})"
  exit 1
fi
```

### Test 7.4: Verify resource provider distribution ZIP

```bash
PROVIDER_DIST=$(find "${PROVIDER_DIR}/target/distributions" -name "*.zip" -type f 2>/dev/null | head -1)
if [ -n "${PROVIDER_DIST}" ]; then
  echo "PASS: resource provider distribution ZIP found: ${PROVIDER_DIST}"
else
  echo "FAIL: no resource provider distribution ZIP in target/distributions/"
  exit 1
fi
```

---

## Phase 8: Scaffold and Build a Camel Resource Provider

### Test 8.1: Scaffold a Camel resource provider

```bash
cd "${ARCHETYPE_WORK_DIR}"
wanaku capabilities create resource --name e2e-test-camel-provider --type camel
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: Camel resource provider scaffolding succeeded"
else
  echo "FAIL: Camel resource provider scaffolding failed (exit code ${EXIT_CODE})"
  exit 1
fi
```

### Test 8.2: Verify Camel resource provider project structure

```bash
CAMEL_PROVIDER_DIR="${ARCHETYPE_WORK_DIR}/wanaku-provider-e2etestcamelprovider"
if [ -d "${CAMEL_PROVIDER_DIR}" ]; then
  echo "PASS: Camel resource provider project directory exists"
else
  echo "FAIL: expected directory ${CAMEL_PROVIDER_DIR} not found"
  ls -1 "${ARCHETYPE_WORK_DIR}"
  exit 1
fi

for FILE in pom.xml src/main/java src/main/resources/application.properties; do
  if [ -e "${CAMEL_PROVIDER_DIR}/${FILE}" ]; then
    echo "PASS: ${FILE} exists"
  else
    echo "FAIL: ${FILE} not found in Camel resource provider project"
  fi
done
```

### Test 8.3: Build the Camel resource provider with distribution profile

```bash
cd "${CAMEL_PROVIDER_DIR}"
mvn -DskipTests -Pdist clean package
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: Camel resource provider build succeeded"
else
  echo "FAIL: Camel resource provider build failed (exit code ${EXIT_CODE})"
  exit 1
fi
```

### Test 8.4: Verify Camel resource provider distribution ZIP

```bash
CAMEL_PROVIDER_DIST=$(find "${CAMEL_PROVIDER_DIR}/target/distributions" -name "*.zip" -type f 2>/dev/null | head -1)
if [ -n "${CAMEL_PROVIDER_DIST}" ]; then
  echo "PASS: Camel resource provider distribution ZIP found: ${CAMEL_PROVIDER_DIST}"
else
  echo "FAIL: no Camel resource provider distribution ZIP in target/distributions/"
  exit 1
fi
```

---

## Phase 9: Negative Tests

### Test 9.1: Scaffold with missing `--name` should fail

```bash
wanaku capabilities create tool 2>&1
EXIT_CODE=$?
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: missing --name rejected (exit code ${EXIT_CODE})"
else
  echo "FAIL: missing --name should have failed"
fi
```

### Test 9.2: Register a tool with a non-existent capability type should fail

```bash
OUTPUT=$(wanaku tools add \
  --host "${WANAKU_ROUTER_URL}" \
  --name nonexistent-type-tool \
  --namespace public \
  --description "Tool with invalid type" \
  --uri "nonexistent://test" \
  --type nonexistent-type-that-does-not-exist \
  --property "x:string,test" 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: registering tool with non-existent capability type failed (exit code ${EXIT_CODE})"
else
  echo "FAIL: registering tool with non-existent capability type should have failed"
fi
```

---

## Phase 10: Cleanup

### Step 10.1: Remove the registered tool

```bash
wanaku tools remove \
  --host "${WANAKU_ROUTER_URL}" \
  --name e2e-lifecycle-tool 2>/dev/null || true
echo "PASS: test tool removed (or was already absent)"
```

### Step 10.2: Stop the tool capability process

```bash
if [ -n "${TOOL_CAPABILITY_PID}" ]; then
  kill "${TOOL_CAPABILITY_PID}" 2>/dev/null || true
  wait "${TOOL_CAPABILITY_PID}" 2>/dev/null || true
  echo "PASS: tool capability process stopped (PID ${TOOL_CAPABILITY_PID})"
fi
```

### Step 10.3: Stop the router

```bash
if [ -n "${WANAKU_PID}" ]; then
  kill "${WANAKU_PID}" 2>/dev/null || true
  wait "${WANAKU_PID}" 2>/dev/null || true
  echo "PASS: router process stopped (PID ${WANAKU_PID})"
fi
```

### Step 10.4: Remove temporary working directory

```bash
if [ -n "${ARCHETYPE_WORK_DIR}" ] && [ -d "${ARCHETYPE_WORK_DIR}" ]; then
  rm -rf "${ARCHETYPE_WORK_DIR}"
  echo "PASS: temp directory removed"
fi
```

---

## Summary Matrix

| Phase | Test ID | Test Name | Priority |
|-------|---------|-----------|----------|
| 0 | 0.1-0.3 | Prerequisites (java, mvn, wanaku) | Critical |
| 1 | 1.1-1.2 | Start and verify router | Critical |
| 2 | 2.1-2.3 | Scaffold Quarkus tool capability | Critical |
| 3 | 3.1-3.3 | Build Quarkus tool capability | Critical |
| 4 | 4.1-4.4 | Start and register Quarkus tool capability | Critical |
| 5 | 5.1-5.5 | Register tool, list via MCP, invoke via MCP, negative param test | Critical |
| 6 | 6.1-6.4 | Scaffold and build Camel tool capability | High |
| 7 | 7.1-7.4 | Scaffold and build Quarkus resource provider | High |
| 8 | 8.1-8.4 | Scaffold and build Camel resource provider | High |
| 9 | 9.1-9.2 | Negative tests (missing name, invalid type) | High |
| 10 | 10.1-10.4 | Cleanup (tool, capability, router, temp dir) | Critical |
