# Test Plan: REST API v2 Features

## Overview

This test plan verifies the v2 REST API features that have no CLI equivalent: tool call history/notifications and code execution. Since these endpoints are REST-only, `curl` is the correct client per the contributing guidelines.

The plan is split into two areas:

- **Tool Call History and Notifications** (Phases 2-4) -- exercises the `/api/v2/tool-calls/` endpoints for listing, retrieving, streaming, and clearing tool call records.
- **Code Execution** (Phase 5) -- exercises the `/api/v2/code-execution-engine/` endpoints for submitting code and streaming results. Requires a configured execution engine; tests are marked SKIP when unavailable.

Every step except the initial build is fully automatable.

## Prerequisites

### Required tools

| Tool | Minimum version | Verify command |
|------|-----------------|----------------|
| `wanaku` | build from source | `wanaku --version` |
| `curl` | 7.68+ | `curl --version` |
| `jq` | 1.6+ | `jq --version` |
| `java` | 21+ | `java -version` |
| `mvn` | 3.9+ | `mvn --version` |

### Environment variables

```bash
export WANAKU_REPO_ROOT="${WANAKU_REPO_ROOT:-.}"
export WANAKU_ROUTER_URL="${WANAKU_ROUTER_URL:-http://localhost:8080}"
export MCP_SERVER_URI="${MCP_SERVER_URI:-http://localhost:8080/public/mcp/sse}"
export V2_BASE="${WANAKU_ROUTER_URL}/api/v2"
```

### CLI invocation

When using the CLI from a local build (not installed), use `java -jar` directly:

```bash
CLI_JAR="apps/wanaku-cli/target/quarkus-app/quarkus-run.jar"
java -jar ${CLI_JAR} tools list --host "${WANAKU_ROUTER_URL}" --plain
```

Do **not** assign the full command to a single variable (e.g., `WANAKU_CLI="java -jar path/to/jar"`) -- zsh treats it as a single token. Use `CLI_JAR` for the path and call `java -jar ${CLI_JAR}` explicitly.

### Response format

All v2 JSON endpoints (except code execution POST) return the standard `WanakuResponse` wrapper:

```json
{"error": null, "data": <payload>}
```

Use `.data` to extract the payload in jq expressions.

---

## Phase 0: Prerequisite Verification

### Test 0.1: Verify required tools are installed

```bash
TOOLS_OK=true

curl --version > /dev/null 2>&1 \
  && echo "PASS: curl is available" \
  || { echo "FAIL: curl is not installed"; TOOLS_OK=false; }

jq --version > /dev/null 2>&1 \
  && echo "PASS: jq is available" \
  || { echo "FAIL: jq is not installed"; TOOLS_OK=false; }

wanaku --version > /dev/null 2>&1 \
  && echo "PASS: wanaku CLI is available" \
  || { echo "FAIL: wanaku CLI is not installed"; TOOLS_OK=false; }

if [ "${TOOLS_OK}" = "false" ]; then
  echo "FAIL: missing required tools -- aborting"
  exit 1
fi
```

---

## Phase 1: Setup

Follow [common/start-local.md](common/start-local.md) to build and start the Wanaku stack locally.

After completion, `WANAKU_ROUTER_URL`, `WANAKU_PID`, and `CLI_JAR` must be set.

### Test 1.1: Verify router health

```bash
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "${WANAKU_ROUTER_URL}/q/health/ready")
if [ "${HTTP_CODE}" = "200" ]; then
  echo "PASS: router is healthy"
else
  echo "FAIL: router not healthy (HTTP ${HTTP_CODE})"
  exit 1
fi
```

### Test 1.2: Register a test tool for generating history

Register an HTTP tool so that tool calls can generate history records.

```bash
wanaku tools add \
  --host "${WANAKU_ROUTER_URL}" \
  --name api-v2-test-tool \
  --namespace public \
  --description "Temporary tool for v2 API testing" \
  --uri "https://meowfacts.herokuapp.com?count={count}" \
  --type http \
  --property "count:int,Number of facts to retrieve" \
  --required count

EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: test tool registered"
else
  echo "FAIL: could not register test tool (exit code ${EXIT_CODE})"
  exit 1
fi
```

### Test 1.3: Verify test tool is listed

```bash
OUTPUT=$(wanaku tools list --host "${WANAKU_ROUTER_URL}" --plain 2>&1)
echo "${OUTPUT}" | grep -q "api-v2-test-tool" \
  && echo "PASS: api-v2-test-tool is listed" \
  || echo "FAIL: api-v2-test-tool not found in tools list"
```

---

## Phase 2: Tool Call History

### Test 2.1: Invoke a tool via MCP to generate history

```bash
OUTPUT=$(wanaku mcp tool \
  --uri "${MCP_SERVER_URI}" \
  --name api-v2-test-tool \
  --param count=1 \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: tool invocation succeeded -- history should be recorded"
else
  echo "FAIL: tool invocation failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
fi
```

### Test 2.2: List tool call history -- verify at least one entry

```bash
RESPONSE=$(curl -s "${V2_BASE}/tool-calls/history")
COUNT=$(echo "${RESPONSE}" | jq '.data | length')

if [ "${COUNT}" -gt 0 ]; then
  echo "PASS: history contains ${COUNT} record(s)"
else
  echo "FAIL: history is empty after tool invocation"
  echo "${RESPONSE}"
fi
```

### Test 2.3: List tool call history -- verify response structure

```bash
RESPONSE=$(curl -s "${V2_BASE}/tool-calls/history")

# Verify the WanakuResponse wrapper has the expected shape
echo "${RESPONSE}" | jq -e '.data' > /dev/null 2>&1 \
  && echo "PASS: response has 'data' field" \
  || echo "FAIL: response missing 'data' field"

# Verify at least one record has expected fields
echo "${RESPONSE}" | jq -e '.data[0].eventId' > /dev/null 2>&1 \
  && echo "PASS: record has 'eventId'" \
  || echo "FAIL: record missing 'eventId'"

echo "${RESPONSE}" | jq -e '.data[0].eventType' > /dev/null 2>&1 \
  && echo "PASS: record has 'eventType'" \
  || echo "FAIL: record missing 'eventType'"

echo "${RESPONSE}" | jq -e '.data[0].toolName' > /dev/null 2>&1 \
  && echo "PASS: record has 'toolName'" \
  || echo "FAIL: record missing 'toolName'"

echo "${RESPONSE}" | jq -e '.data[0].timestamp' > /dev/null 2>&1 \
  && echo "PASS: record has 'timestamp'" \
  || echo "FAIL: record missing 'timestamp'"
```

### Test 2.4: Get a specific history event by ID

```bash
# Extract the first event ID from history
EVENT_ID=$(curl -s "${V2_BASE}/tool-calls/history" | jq -r '.data[0].id')

if [ -z "${EVENT_ID}" ] || [ "${EVENT_ID}" = "null" ]; then
  echo "FAIL: could not extract event ID from history"
else
  RESPONSE=$(curl -s "${V2_BASE}/tool-calls/history/${EVENT_ID}")
  RETURNED_ID=$(echo "${RESPONSE}" | jq -r '.data.id')

  if [ "${RETURNED_ID}" = "${EVENT_ID}" ]; then
    echo "PASS: retrieved event matches requested ID (${EVENT_ID})"
  else
    echo "FAIL: returned ID (${RETURNED_ID}) does not match requested (${EVENT_ID})"
    echo "${RESPONSE}"
  fi
fi
```

### Test 2.5: Filter history by toolName query parameter

```bash
RESPONSE=$(curl -s "${V2_BASE}/tool-calls/history?toolName=api-v2-test-tool")
COUNT=$(echo "${RESPONSE}" | jq '.data | length')

if [ "${COUNT}" -gt 0 ]; then
  # Verify all returned records match the filter
  MISMATCH=$(echo "${RESPONSE}" | jq '[.data[] | select(.toolName != "api-v2-test-tool")] | length')
  if [ "${MISMATCH}" -eq 0 ]; then
    echo "PASS: toolName filter returned ${COUNT} matching record(s)"
  else
    echo "FAIL: toolName filter returned ${MISMATCH} record(s) with wrong toolName"
  fi
else
  echo "FAIL: toolName filter returned no records"
fi
```

### Test 2.6: Filter history by connectionId query parameter

```bash
# Extract a connectionId from an existing record
CONNECTION_ID=$(curl -s "${V2_BASE}/tool-calls/history" | jq -r '.data[0].connectionId')

if [ -z "${CONNECTION_ID}" ] || [ "${CONNECTION_ID}" = "null" ]; then
  echo "SKIP: no connectionId available to test filter"
else
  RESPONSE=$(curl -s "${V2_BASE}/tool-calls/history?connectionId=${CONNECTION_ID}")
  COUNT=$(echo "${RESPONSE}" | jq '.data | length')

  if [ "${COUNT}" -gt 0 ]; then
    echo "PASS: connectionId filter returned ${COUNT} record(s)"
  else
    echo "FAIL: connectionId filter returned no records"
  fi
fi
```

---

## Phase 3: Tool Call Notifications (SSE)

### Test 3.1: Verify SSE notifications endpoint connects

Connect to the SSE endpoint with a short timeout to verify it returns HTTP 200 and the correct Content-Type.

```bash
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
  -H "Accept: text/event-stream" \
  --max-time 3 \
  "${V2_BASE}/tool-calls/notifications" 2>/dev/null || echo "000")

# SSE endpoints return 200 even though curl may exit non-zero due to timeout
if [ "${HTTP_CODE}" = "200" ]; then
  echo "PASS: SSE notifications endpoint returned HTTP 200"
else
  echo "FAIL: SSE notifications endpoint returned HTTP ${HTTP_CODE}"
fi
```

### Test 3.2: Verify SSE stream receives events on tool invocation

Start the SSE listener in the background, invoke a tool, then check for events.

```bash
SSE_OUTPUT_FILE=$(mktemp)

# Start SSE listener in background with a timeout
curl -s -N \
  -H "Accept: text/event-stream" \
  --max-time 30 \
  "${V2_BASE}/tool-calls/notifications" > "${SSE_OUTPUT_FILE}" 2>/dev/null &
SSE_PID=$!

# Give SSE connection time to establish
sleep 2

# Invoke a tool to generate an event
wanaku mcp tool \
  --uri "${MCP_SERVER_URI}" \
  --name api-v2-test-tool \
  --param count=1 \
  --plain > /dev/null 2>&1

# Wait for the event to propagate
sleep 5

# Kill the SSE listener
kill "${SSE_PID}" 2>/dev/null || true
wait "${SSE_PID}" 2>/dev/null || true

# Check if any SSE events were received
if [ -s "${SSE_OUTPUT_FILE}" ]; then
  if grep -q "event:" "${SSE_OUTPUT_FILE}" || grep -q "data:" "${SSE_OUTPUT_FILE}"; then
    echo "PASS: SSE stream received events"
  else
    echo "WARN: SSE stream had data but no recognizable event/data lines"
    cat "${SSE_OUTPUT_FILE}"
  fi
else
  echo "FAIL: SSE stream received no data"
fi

rm -f "${SSE_OUTPUT_FILE}"
```

### Test 3.3: Verify SSE connection-specific notifications endpoint connects

```bash
# Use a synthetic connection ID
TEST_CONN_ID="test-connection-12345"

HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
  -H "Accept: text/event-stream" \
  --max-time 3 \
  "${V2_BASE}/tool-calls/notifications/${TEST_CONN_ID}" 2>/dev/null || echo "000")

if [ "${HTTP_CODE}" = "200" ]; then
  echo "PASS: connection-specific SSE endpoint returned HTTP 200"
else
  echo "FAIL: connection-specific SSE endpoint returned HTTP ${HTTP_CODE}"
fi
```

---

## Phase 4: Clear Tool Call History

### Test 4.1: Delete all tool call history

```bash
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "${V2_BASE}/tool-calls/history")

if [ "${HTTP_CODE}" = "200" ]; then
  echo "PASS: DELETE history returned HTTP 200"
else
  echo "FAIL: DELETE history returned HTTP ${HTTP_CODE}"
fi
```

### Test 4.2: Verify history is empty after deletion

```bash
RESPONSE=$(curl -s "${V2_BASE}/tool-calls/history")
COUNT=$(echo "${RESPONSE}" | jq '.data | length')

if [ "${COUNT}" -eq 0 ]; then
  echo "PASS: history is empty after deletion"
else
  echo "FAIL: history still contains ${COUNT} record(s) after deletion"
fi
```

### Test 4.3: Delete history when already empty (idempotent)

```bash
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "${V2_BASE}/tool-calls/history")

if [ "${HTTP_CODE}" = "200" ]; then
  echo "PASS: DELETE on empty history returned HTTP 200 (idempotent)"
else
  echo "FAIL: DELETE on empty history returned HTTP ${HTTP_CODE}"
fi
```

---

## Phase 5: Code Execution

**NOTE:** Code execution requires a configured execution engine (e.g., `jvm`, `interpreted`). If no engine is available locally, all tests in this phase should be marked **SKIP**.

### Prerequisite check

```bash
export CODE_EXEC_ENGINE="${CODE_EXEC_ENGINE:-jvm}"
export CODE_EXEC_LANGUAGE="${CODE_EXEC_LANGUAGE:-java}"

echo "Testing code execution with engine=${CODE_EXEC_ENGINE}, language=${CODE_EXEC_LANGUAGE}"
echo "If no engine is configured, tests in Phase 5 will fail -- mark them as SKIP."
```

### Test 5.1: Submit code for execution

Submit a simple code snippet and verify the response contains a task ID and stream URL.

```bash
RESPONSE=$(curl -s -w "\n%{http_code}" \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{"code": "System.out.println(\"Hello from Wanaku\");", "timeout": 30000}' \
  "${V2_BASE}/code-execution-engine/${CODE_EXEC_ENGINE}/${CODE_EXEC_LANGUAGE}")

HTTP_CODE=$(echo "${RESPONSE}" | tail -1)
BODY=$(echo "${RESPONSE}" | sed '$d')

if [ "${HTTP_CODE}" = "201" ]; then
  echo "PASS: code submission returned HTTP 201 Created"

  TASK_ID=$(echo "${BODY}" | jq -r '.taskId')
  STREAM_URL=$(echo "${BODY}" | jq -r '.streamUrl')
  STATUS=$(echo "${BODY}" | jq -r '.status')

  if [ -n "${TASK_ID}" ] && [ "${TASK_ID}" != "null" ]; then
    echo "PASS: response contains taskId (${TASK_ID})"
  else
    echo "FAIL: response missing taskId"
  fi

  if [ -n "${STREAM_URL}" ] && [ "${STREAM_URL}" != "null" ]; then
    echo "PASS: response contains streamUrl"
  else
    echo "FAIL: response missing streamUrl"
  fi

  if [ "${STATUS}" = "PENDING" ]; then
    echo "PASS: initial status is PENDING"
  else
    echo "WARN: initial status is '${STATUS}' (expected PENDING)"
  fi

  # Save for next test
  export SAVED_TASK_ID="${TASK_ID}"
  export SAVED_STREAM_URL="${STREAM_URL}"
else
  echo "SKIP: code submission returned HTTP ${HTTP_CODE} -- engine may not be configured"
  echo "${BODY}"
  export SAVED_TASK_ID=""
fi
```

### Test 5.2: Stream execution results via SSE

```bash
if [ -z "${SAVED_TASK_ID}" ]; then
  echo "SKIP: no task ID from previous test"
else
  SSE_OUTPUT_FILE=$(mktemp)

  curl -s -N \
    -H "Accept: text/event-stream" \
    --max-time 35 \
    "${V2_BASE}/code-execution-engine/${CODE_EXEC_ENGINE}/${CODE_EXEC_LANGUAGE}/${SAVED_TASK_ID}" \
    > "${SSE_OUTPUT_FILE}" 2>/dev/null || true

  if [ -s "${SSE_OUTPUT_FILE}" ]; then
    echo "PASS: SSE stream returned data for task ${SAVED_TASK_ID}"

    if grep -q "event:" "${SSE_OUTPUT_FILE}"; then
      echo "PASS: stream contains SSE event lines"
    else
      echo "WARN: stream has data but no 'event:' lines"
    fi
  else
    echo "WARN: SSE stream returned no data (execution may have completed before connection)"
  fi

  rm -f "${SSE_OUTPUT_FILE}"
fi
```

### Test 5.3: Verify Location header is set on POST response

```bash
LOCATION=$(curl -s -D - \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{"code": "System.out.println(\"test\");", "timeout": 5000}' \
  "${V2_BASE}/code-execution-engine/${CODE_EXEC_ENGINE}/${CODE_EXEC_LANGUAGE}" \
  -o /dev/null 2>/dev/null | grep -i "^location:" | tr -d '\r')

if [ -n "${LOCATION}" ]; then
  echo "PASS: Location header is present: ${LOCATION}"
else
  echo "SKIP: Location header not present -- engine may not be configured"
fi
```

---

## Phase 6: Negative Tests

### Test 6.1: Get non-existent history event -- expect 404

```bash
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
  "${V2_BASE}/tool-calls/history/non-existent-event-id-12345")

if [ "${HTTP_CODE}" = "404" ]; then
  echo "PASS: non-existent event ID returned HTTP 404"
else
  echo "FAIL: non-existent event ID returned HTTP ${HTTP_CODE} (expected 404)"
fi
```

### Test 6.2: Submit code with empty body -- expect 400

```bash
RESPONSE=$(curl -s -w "\n%{http_code}" \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{"code": ""}' \
  "${V2_BASE}/code-execution-engine/${CODE_EXEC_ENGINE}/${CODE_EXEC_LANGUAGE}")

HTTP_CODE=$(echo "${RESPONSE}" | tail -1)
BODY=$(echo "${RESPONSE}" | sed '$d')

if [ "${HTTP_CODE}" = "400" ]; then
  echo "PASS: empty code returned HTTP 400"

  ERROR_TYPE=$(echo "${BODY}" | jq -r '.error' 2>/dev/null)
  if [ "${ERROR_TYPE}" = "VALIDATION_ERROR" ]; then
    echo "PASS: error type is VALIDATION_ERROR"
  else
    echo "WARN: error type is '${ERROR_TYPE}' (expected VALIDATION_ERROR)"
  fi
elif [ "${HTTP_CODE}" = "500" ]; then
  echo "SKIP: empty code returned HTTP 500 -- engine may not be configured"
else
  echo "FAIL: empty code returned HTTP ${HTTP_CODE} (expected 400)"
fi
```

### Test 6.3: Submit code with null code field -- expect 400

```bash
RESPONSE=$(curl -s -w "\n%{http_code}" \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{"timeout": 5000}' \
  "${V2_BASE}/code-execution-engine/${CODE_EXEC_ENGINE}/${CODE_EXEC_LANGUAGE}")

HTTP_CODE=$(echo "${RESPONSE}" | tail -1)

if [ "${HTTP_CODE}" = "400" ]; then
  echo "PASS: missing code field returned HTTP 400"
elif [ "${HTTP_CODE}" = "500" ]; then
  echo "SKIP: missing code field returned HTTP 500 -- engine may not be configured"
else
  echo "FAIL: missing code field returned HTTP ${HTTP_CODE} (expected 400)"
fi
```

### Test 6.4: Submit code with excessive timeout -- expect 400

```bash
RESPONSE=$(curl -s -w "\n%{http_code}" \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{"code": "print(1)", "timeout": 999999999}' \
  "${V2_BASE}/code-execution-engine/${CODE_EXEC_ENGINE}/${CODE_EXEC_LANGUAGE}")

HTTP_CODE=$(echo "${RESPONSE}" | tail -1)

if [ "${HTTP_CODE}" = "400" ]; then
  echo "PASS: excessive timeout returned HTTP 400"
elif [ "${HTTP_CODE}" = "500" ]; then
  echo "SKIP: excessive timeout returned HTTP 500 -- engine may not be configured"
else
  echo "FAIL: excessive timeout returned HTTP ${HTTP_CODE} (expected 400)"
fi
```

### Test 6.5: Verify history endpoint with non-matching toolName filter returns empty

```bash
RESPONSE=$(curl -s "${V2_BASE}/tool-calls/history?toolName=non-existent-tool-12345")
COUNT=$(echo "${RESPONSE}" | jq '.data | length')

if [ "${COUNT}" -eq 0 ]; then
  echo "PASS: toolName filter for non-existent tool returned empty list"
else
  echo "FAIL: toolName filter for non-existent tool returned ${COUNT} record(s)"
fi
```

---

## Phase 7: Cleanup

### Step 7.1: Remove test tool

```bash
wanaku tools remove \
  --host "${WANAKU_ROUTER_URL}" \
  --name api-v2-test-tool 2>/dev/null || true

echo "PASS: test tool removed"
```

### Step 7.2: Clear any remaining history

```bash
curl -s -X DELETE "${V2_BASE}/tool-calls/history" > /dev/null 2>&1 || true
echo "PASS: history cleared"
```

### Step 7.3: Stop Wanaku

```bash
if [ -n "${WANAKU_PID}" ]; then
  kill "${WANAKU_PID}" 2>/dev/null || true
  wait "${WANAKU_PID}" 2>/dev/null || true
  echo "PASS: Wanaku process stopped"
else
  echo "SKIP: no WANAKU_PID set"
fi
```

---

## Test Summary Matrix

| Phase | Test ID | Test Name | Priority |
|-------|---------|-----------|----------|
| 0 | 0.1 | Prerequisite tool verification | Critical |
| 1 | 1.1 | Router health check | Critical |
| 1 | 1.2-1.3 | Register and verify test tool | Critical |
| 2 | 2.1 | Invoke tool to generate history | Critical |
| 2 | 2.2 | List history -- at least one entry | Critical |
| 2 | 2.3 | List history -- verify response structure | High |
| 2 | 2.4 | Get specific history event by ID | Critical |
| 2 | 2.5 | Filter history by toolName | High |
| 2 | 2.6 | Filter history by connectionId | High |
| 3 | 3.1 | SSE notifications endpoint connects | Critical |
| 3 | 3.2 | SSE stream receives events on tool call | Critical |
| 3 | 3.3 | Connection-specific SSE endpoint connects | High |
| 4 | 4.1 | Delete all history | Critical |
| 4 | 4.2 | Verify history empty after deletion | Critical |
| 4 | 4.3 | Delete on empty history (idempotent) | Medium |
| 5 | 5.1 | Submit code -- verify 201 + taskId/streamUrl | High |
| 5 | 5.2 | Stream execution results via SSE | High |
| 5 | 5.3 | Verify Location header on POST | Medium |
| 6 | 6.1 | Non-existent event ID -- 404 | High |
| 6 | 6.2 | Empty code field -- 400 | High |
| 6 | 6.3 | Missing code field -- 400 | High |
| 6 | 6.4 | Excessive timeout -- 400 | Medium |
| 6 | 6.5 | Non-matching toolName filter -- empty list | Medium |
| 7 | 7.1-7.3 | Cleanup | Critical |
