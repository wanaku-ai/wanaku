# Test Plan: CLI Start Local Smoke Test

## Overview

This test plan verifies that `wanaku start local` works end-to-end: build the project, start the local stack, confirm health, register a tool, invoke it via MCP, manage prompts, verify the admin UI is reachable, then shut down cleanly.

This runs locally with **no authentication** (`start local` is always noauth). Every step is fully automatable.

## Prerequisites

### Required tools

| Tool | Minimum version | Verify command |
|------|-----------------|----------------|
| `java` | 21+ | `java -version` |
| `mvn` | 3.9+ | `mvn -version` |
| `jq` | 1.6+ | `jq --version` |
| `curl` | any | `curl --version` |

### Prerequisite check

```bash
java -version 2>&1 || { echo "FAIL: java not found"; exit 1; }
mvn -version 2>&1 || { echo "FAIL: mvn not found"; exit 1; }
jq --version 2>&1 || { echo "FAIL: jq not found"; exit 1; }
curl --version > /dev/null 2>&1 || { echo "FAIL: curl not found"; exit 1; }
echo "PASS: all prerequisites met"
```

### CLI invocation

When using the CLI from a local build (not installed), use `java -jar` directly:

```bash
CLI_JAR="apps/wanaku-cli/target/quarkus-app/quarkus-run.jar"
java -jar ${CLI_JAR} tools list --host ...
```

Do **not** assign the full command to a single variable (e.g., `WANAKU_CLI="java -jar path/to/jar"`) -- zsh treats it as a single token. Use `CLI_JAR` for the path and call `java -jar ${CLI_JAR}` explicitly.

### Environment variables

```bash
export WANAKU_REPO_ROOT="${WANAKU_REPO_ROOT:-.}"
export WANAKU_ROUTER_URL="${WANAKU_ROUTER_URL:-http://localhost:8080}"
export MCP_SERVER_URI="${MCP_SERVER_URI:-http://localhost:8080/public/mcp/sse}"
```

| Variable | Default | Description |
|----------|---------|-------------|
| `WANAKU_REPO_ROOT` | `.` | Path to the Wanaku repository root |
| `WANAKU_ROUTER_URL` | `http://localhost:8080` | Router base URL (no trailing slash) |
| `MCP_SERVER_URI` | `http://localhost:8080/public/mcp/sse` | MCP SSE endpoint for direct MCP commands |

### Known limitations for local testing

- **No resource providers in `wanaku start local`:** The local start command only supports tool services (`service-http`). Resource providers are not available locally. Resource tests are limited to verifying the list command returns successfully.
- **Prompts are not backed by providers:** Prompts are stored in the router and do not require a downstream provider. They can be added, listed, and removed locally.
- **MCP SSE path:** The public MCP endpoint is `/public/mcp/sse` (not `/mcp`). Using a wrong path will return connection errors.

---

## Phase 0: Prerequisites

Verify all required tools are present and environment variables are set.

### Test 0.1: Verify required tools

```bash
java -version 2>&1 | head -1
JAVA_EXIT=$?

mvn -version 2>&1 | head -1
MVN_EXIT=$?

jq --version 2>&1
JQ_EXIT=$?

if [ "${JAVA_EXIT}" -eq 0 ] && [ "${MVN_EXIT}" -eq 0 ] && [ "${JQ_EXIT}" -eq 0 ]; then
  echo "PASS: all required tools present"
else
  echo "FAIL: one or more tools missing (java=${JAVA_EXIT}, mvn=${MVN_EXIT}, jq=${JQ_EXIT})"
  exit 1
fi
```

### Test 0.2: Verify Java version is 21+

```bash
JAVA_VERSION=$(java -version 2>&1 | head -1 | sed 's/.*"\([0-9]*\).*/\1/')
if [ "${JAVA_VERSION}" -ge 21 ]; then
  echo "PASS: Java version ${JAVA_VERSION} >= 21"
else
  echo "FAIL: Java version ${JAVA_VERSION} < 21"
  exit 1
fi
```

---

## Phase 1: Build and Start

Follow [common/start-local.md](common/start-local.md) to build the project, start the local stack, and wait for health.

After completion, the following variables must be set:

- `VERSION` -- Wanaku version string
- `CLI_JAR` -- path to the CLI JAR
- `WANAKU_ROUTER_URL` -- router base URL (`http://localhost:8080`)
- `WANAKU_PID` -- PID of the background Wanaku process

### Test 1.1: Verify CLI JAR exists

```bash
CLI_JAR="apps/wanaku-cli/target/quarkus-app/quarkus-run.jar"
if [ -f "${CLI_JAR}" ]; then
  echo "PASS: CLI JAR exists at ${CLI_JAR}"
else
  echo "FAIL: CLI JAR not found at ${CLI_JAR}"
  exit 1
fi
```

### Test 1.2: Verify router is healthy

```bash
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "${WANAKU_ROUTER_URL}/q/health/ready" 2>/dev/null || echo "000")
if [ "${HTTP_CODE}" = "200" ]; then
  echo "PASS: router is healthy"
else
  echo "FAIL: router not healthy (HTTP ${HTTP_CODE})"
  exit 1
fi
```

### Test 1.3: Verify CLI can connect to router

```bash
java -jar ${CLI_JAR} tools list --host "${WANAKU_ROUTER_URL}" --plain 2>&1
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: CLI can connect to router"
else
  echo "FAIL: CLI cannot connect to router (exit code ${EXIT_CODE})"
  exit 1
fi
```

---

## Phase 2: Smoke Test -- Tools

### Test 2.1: Register an HTTP tool

```bash
java -jar ${CLI_JAR} tools add \
  --host "${WANAKU_ROUTER_URL}" \
  --name smoke-test-tool \
  --namespace public \
  --description "Smoke test tool for local validation" \
  --uri "https://httpbin.org/get?param={value}" \
  --type http \
  --property "value:string,A test value" \
  --required value

EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: smoke-test-tool registered"
else
  echo "FAIL: could not register smoke-test-tool (exit code ${EXIT_CODE})"
  exit 1
fi
```

### Test 2.2: List tools and verify smoke-test-tool appears

```bash
OUTPUT=$(java -jar ${CLI_JAR} tools list --host "${WANAKU_ROUTER_URL}" --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: tools list command failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "${OUTPUT}" | grep -q "smoke-test-tool" \
  && echo "PASS: smoke-test-tool is listed" \
  || echo "FAIL: smoke-test-tool not found in tools list"
```

### Test 2.3: Show tool details

```bash
OUTPUT=$(java -jar ${CLI_JAR} tools show --host "${WANAKU_ROUTER_URL}" --plain smoke-test-tool 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: tools show command failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "${OUTPUT}" | grep -q "smoke-test-tool" \
  && echo "PASS: tool details contain tool name" \
  || echo "FAIL: tool details do not contain tool name"

echo "${OUTPUT}" | grep -q "httpbin" \
  && echo "PASS: tool details contain URI" \
  || echo "FAIL: tool details do not contain URI"
```

### Test 2.4: Call the tool via MCP

```bash
OUTPUT=$(java -jar ${CLI_JAR} mcp tool \
  --uri "${MCP_SERVER_URI}" \
  --name smoke-test-tool \
  --param value=hello \
  --plain 2>&1)
EXIT_CODE=$?

echo "Exit code: ${EXIT_CODE}"
echo "Output: ${OUTPUT}"

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: MCP tool call failed (exit code ${EXIT_CODE})"
  exit 1
fi

if [ -n "${OUTPUT}" ]; then
  echo "PASS: MCP tool call returned non-empty response"
else
  echo "FAIL: MCP tool call returned empty response"
fi
```

### Test 2.5: List tools via MCP and verify smoke-test-tool appears

```bash
OUTPUT=$(java -jar ${CLI_JAR} mcp tool list --uri "${MCP_SERVER_URI}" --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: MCP tool list failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "${OUTPUT}" | grep -q "smoke-test-tool" \
  && echo "PASS: smoke-test-tool visible via MCP" \
  || echo "FAIL: smoke-test-tool not visible via MCP"
```

### Test 2.6: Call a non-existent tool should fail

```bash
OUTPUT=$(java -jar ${CLI_JAR} mcp tool \
  --uri "${MCP_SERVER_URI}" \
  --name "nonexistent-tool-12345" \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: non-existent tool call failed as expected (exit code ${EXIT_CODE})"
else
  echo "FAIL: non-existent tool call should have failed"
fi
```

---

## Phase 3: Smoke Test -- Resources

No resource providers ship with `start local` by default. This phase verifies the resources commands function correctly against the router even without providers.

### Test 3.1: List resources returns successfully

```bash
OUTPUT=$(java -jar ${CLI_JAR} resources list --host "${WANAKU_ROUTER_URL}" --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: resources list returned successfully (empty is expected)"
else
  echo "FAIL: resources list failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
fi
```

### Test 3.2: List resources via MCP returns successfully

```bash
OUTPUT=$(java -jar ${CLI_JAR} mcp resource list --uri "${MCP_SERVER_URI}" --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: MCP resource list returned successfully"
else
  echo "FAIL: MCP resource list failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
fi
```

---

## Phase 4: Smoke Test -- Prompts

### Test 4.1: Add a prompt

```bash
java -jar ${CLI_JAR} prompts add \
  --host "${WANAKU_ROUTER_URL}" \
  --name smoke-test-prompt \
  --namespace public \
  --description "Smoke prompt for local validation" \
  --message "user:text:Hello {{name}}" \
  --argument "name:The name to greet:true"

EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: smoke-test-prompt added"
else
  echo "FAIL: could not add smoke-test-prompt (exit code ${EXIT_CODE})"
  exit 1
fi
```

### Test 4.2: List prompts and verify smoke-test-prompt appears

```bash
OUTPUT=$(java -jar ${CLI_JAR} prompts list --host "${WANAKU_ROUTER_URL}" --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: prompts list command failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "${OUTPUT}" | grep -q "smoke-test-prompt" \
  && echo "PASS: smoke-test-prompt is listed" \
  || echo "FAIL: smoke-test-prompt not found in prompts list"
```

### Test 4.3: List prompts via MCP and verify smoke-test-prompt appears

```bash
OUTPUT=$(java -jar ${CLI_JAR} mcp prompt list --uri "${MCP_SERVER_URI}" --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: MCP prompt list failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "${OUTPUT}" | grep -q "smoke-test-prompt" \
  && echo "PASS: smoke-test-prompt visible via MCP" \
  || echo "FAIL: smoke-test-prompt not visible via MCP"
```

### Test 4.4: Remove the prompt

```bash
java -jar ${CLI_JAR} prompts remove \
  --host "${WANAKU_ROUTER_URL}" \
  --name smoke-test-prompt

EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: smoke-test-prompt removed"
else
  echo "FAIL: could not remove smoke-test-prompt (exit code ${EXIT_CODE})"
fi
```

### Test 4.5: Verify prompt is no longer listed

```bash
OUTPUT=$(java -jar ${CLI_JAR} prompts list --host "${WANAKU_ROUTER_URL}" --plain 2>&1)

echo "${OUTPUT}" | grep -q "smoke-test-prompt"
if [ $? -ne 0 ]; then
  echo "PASS: smoke-test-prompt no longer listed after removal"
else
  echo "FAIL: smoke-test-prompt still appears after removal"
fi
```

### Test 4.6: Remove a non-existent prompt should fail

```bash
OUTPUT=$(java -jar ${CLI_JAR} prompts remove \
  --host "${WANAKU_ROUTER_URL}" \
  --name "nonexistent-prompt-12345" 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: removing non-existent prompt failed as expected (exit code ${EXIT_CODE})"
else
  echo "FAIL: removing non-existent prompt should have failed"
fi
```

---

## Phase 5: Smoke Test -- Admin UI

Curl is used here specifically to test HTTP-level reachability of the admin UI static assets.

### Test 5.1: Verify admin UI returns HTTP 200

```bash
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "${WANAKU_ROUTER_URL}/admin/" 2>/dev/null || echo "000")
if [ "${HTTP_CODE}" = "200" ]; then
  echo "PASS: admin UI accessible at /admin/ (HTTP 200)"
else
  echo "FAIL: admin UI not accessible (HTTP ${HTTP_CODE})"
fi
```

### Test 5.2: Verify admin UI serves HTML content

```bash
CONTENT_TYPE=$(curl -s -o /dev/null -w "%{content_type}" "${WANAKU_ROUTER_URL}/admin/" 2>/dev/null || echo "")
echo "${CONTENT_TYPE}" | grep -qi "text/html" \
  && echo "PASS: admin UI serves HTML content" \
  || echo "FAIL: admin UI content-type is not HTML (got: ${CONTENT_TYPE})"
```

---

## Phase 6: Cleanup

### Step 6.1: Remove registered tools

```bash
java -jar ${CLI_JAR} tools remove \
  --host "${WANAKU_ROUTER_URL}" \
  --name smoke-test-tool 2>/dev/null || true
echo "PASS: smoke-test-tool removed (or already absent)"
```

### Step 6.2: Remove registered prompts (idempotent)

```bash
java -jar ${CLI_JAR} prompts remove \
  --host "${WANAKU_ROUTER_URL}" \
  --name smoke-test-prompt 2>/dev/null || true
echo "PASS: smoke-test-prompt removed (or already absent)"
```

### Step 6.3: Kill the Wanaku process

```bash
if [ -n "${WANAKU_PID}" ]; then
  kill "${WANAKU_PID}" 2>/dev/null || true
  wait "${WANAKU_PID}" 2>/dev/null || true
  echo "PASS: Wanaku process ${WANAKU_PID} stopped"
else
  echo "PASS: no WANAKU_PID set, nothing to stop"
fi
```

### Step 6.4: Verify process is gone

```bash
if [ -n "${WANAKU_PID}" ]; then
  kill -0 "${WANAKU_PID}" 2>/dev/null
  if [ $? -ne 0 ]; then
    echo "PASS: Wanaku process ${WANAKU_PID} is no longer running"
  else
    echo "FAIL: Wanaku process ${WANAKU_PID} is still running"
  fi
else
  echo "PASS: no WANAKU_PID set, nothing to verify"
fi
```

---

## Test Summary

| Phase | Test ID | Test Name | Priority |
|-------|---------|-----------|----------|
| 0 | 0.1 | Verify required tools | Critical |
| 0 | 0.2 | Verify Java version >= 21 | Critical |
| 1 | 1.1 | Verify CLI JAR exists | Critical |
| 1 | 1.2 | Verify router is healthy | Critical |
| 1 | 1.3 | Verify CLI can connect to router | Critical |
| 2 | 2.1 | Register an HTTP tool | Critical |
| 2 | 2.2 | List tools and verify registration | Critical |
| 2 | 2.3 | Show tool details | High |
| 2 | 2.4 | Call tool via MCP | Critical |
| 2 | 2.5 | List tools via MCP | High |
| 2 | 2.6 | Call non-existent tool (negative) | High |
| 3 | 3.1 | List resources returns successfully | Medium |
| 3 | 3.2 | List resources via MCP returns successfully | Medium |
| 4 | 4.1 | Add a prompt | Critical |
| 4 | 4.2 | List prompts and verify registration | Critical |
| 4 | 4.3 | List prompts via MCP | High |
| 4 | 4.4 | Remove the prompt | Critical |
| 4 | 4.5 | Verify prompt removed from list | High |
| 4 | 4.6 | Remove non-existent prompt (negative) | High |
| 5 | 5.1 | Admin UI returns HTTP 200 | High |
| 5 | 5.2 | Admin UI serves HTML content | Medium |
| 6 | 6.1 | Remove registered tools | Critical |
| 6 | 6.2 | Remove registered prompts | Critical |
| 6 | 6.3 | Kill the Wanaku process | Critical |
| 6 | 6.4 | Verify process is gone | High |
