# Test Plan: CLI Tools, Resources, and Prompts CRUD (Local)

## Overview

This test plan exercises the full CRUD lifecycle for tools, resources, and prompts via the `wanaku` CLI against a locally running Wanaku instance without authentication. It covers add, list, show, edit, label, generate, import, batch removal, and negative tests.

Every step uses the Wanaku CLI. No `curl` or other HTTP clients are used except for the health check (which tests HTTP-level readiness).

## Prerequisites

### Required tools

| Tool | Minimum version | Verify command |
|------|-----------------|----------------|
| `wanaku` | 0.2.0+ | `wanaku --version` |
| `jq` | 1.6+ | `jq --version` |
| `java` | 21+ | `java -version` |
| `mvn` | 3.9+ | `mvn --version` |

### CLI invocation

When using the CLI from a local build (not installed), use `java -jar` directly:

```bash
CLI_JAR="apps/wanaku-cli/target/quarkus-app/quarkus-run.jar"
java -jar ${CLI_JAR} tools list --host "${WANAKU_ROUTER_URL}" --plain
```

Do **not** assign the full command to a single variable (e.g., `WANAKU_CLI="java -jar path/to/jar"`) -- zsh treats it as a single token. Use `CLI_JAR` for the path and call `java -jar ${CLI_JAR}` explicitly.

### Environment variables

```bash
export WANAKU_ROUTER_URL="${WANAKU_ROUTER_URL:-http://localhost:8080}"
export WANAKU_REPO_ROOT="${WANAKU_REPO_ROOT:-.}"
```

---

## Phase 0: Manual Prerequisites

### Step 0.1: Verify required tools are installed

```bash
MISSING=""
for CMD in wanaku jq java mvn; do
  command -v "${CMD}" > /dev/null 2>&1 || MISSING="${MISSING} ${CMD}"
done

if [ -z "${MISSING}" ]; then
  echo "PASS: all required tools are installed"
else
  echo "FAIL: missing tools:${MISSING}"
  exit 1
fi
```

---

## Phase 1: Setup

Follow [common/start-local.md](common/start-local.md). After completion, `WANAKU_ROUTER_URL`, `WANAKU_PID`, `CLI_JAR`, and `VERSION` must be set.

### Step 1.1: Verify CLI can connect to router

```bash
wanaku tools list --host "${WANAKU_ROUTER_URL}" --plain
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: CLI connected to router"
else
  echo "FAIL: CLI cannot connect to router (exit code ${EXIT_CODE})"
  exit 1
fi
```

---

## Phase 2: Tool CRUD

### Test 2.1: Add a tool

```bash
wanaku tools add \
  --host "${WANAKU_ROUTER_URL}" \
  --name test-http-tool \
  --description "Test tool for E2E verification" \
  --uri "https://httpbin.org/get?q={query}" \
  --type http \
  --property "query:string,Search query" \
  --required query
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: tool 'test-http-tool' added"
else
  echo "FAIL: could not add tool (exit code ${EXIT_CODE})"
fi
```

### Test 2.2: Verify tool appears in list

```bash
OUTPUT=$(wanaku tools list --host "${WANAKU_ROUTER_URL}" --plain 2>&1)
echo "${OUTPUT}" | grep -q "test-http-tool" \
  && echo "PASS: 'test-http-tool' appears in tools list" \
  || echo "FAIL: 'test-http-tool' not found in tools list"
```

### Test 2.3: Show tool details

```bash
OUTPUT=$(wanaku tools show --host "${WANAKU_ROUTER_URL}" test-http-tool 2>&1)
EXIT_CODE=$?
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: tools show failed (exit code ${EXIT_CODE})"
else
  echo "${OUTPUT}" | grep -q "test-http-tool" \
    && echo "PASS: show displays tool name" \
    || echo "FAIL: tool name not in show output"
  echo "${OUTPUT}" | grep -q "Test tool for E2E verification" \
    && echo "PASS: show displays description" \
    || echo "FAIL: description not in show output"
  echo "${OUTPUT}" | grep -q "httpbin.org" \
    && echo "PASS: show displays URI" \
    || echo "FAIL: URI not in show output"
  echo "${OUTPUT}" | grep -qi "http" \
    && echo "PASS: show displays type" \
    || echo "FAIL: type not in show output"
fi
```

### Test 2.4: Edit tool (interactive editor)

**Note:** The `tools edit` command opens a Nano editor for interactive modification. In an automated test environment this requires a wrapper or a direct API update. For manual execution:

```bash
wanaku tools edit --host "${WANAKU_ROUTER_URL}" test-http-tool
```

**Manual verification:** Change the description to "Updated description" in the editor, save and confirm.

```bash
OUTPUT=$(wanaku tools show --host "${WANAKU_ROUTER_URL}" test-http-tool 2>&1)
echo "${OUTPUT}" | grep -q "Updated description" \
  && echo "PASS: description was updated via edit" \
  || echo "FAIL: description was not updated"
```

### Test 2.5: Add labels to tool

```bash
wanaku tools label add \
  --host "${WANAKU_ROUTER_URL}" \
  --name test-http-tool \
  --label env=test \
  --label tier=smoke
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: labels added to tool"
else
  echo "FAIL: could not add labels (exit code ${EXIT_CODE})"
fi
```

### Test 2.6: Verify labels appear in list

```bash
OUTPUT=$(wanaku tools list --host "${WANAKU_ROUTER_URL}" --plain 2>&1)
echo "${OUTPUT}" | grep "test-http-tool" | grep -q "env=test" \
  && echo "PASS: label 'env=test' visible in list" \
  || echo "FAIL: label 'env=test' not visible in list"
echo "${OUTPUT}" | grep "test-http-tool" | grep -q "tier=smoke" \
  && echo "PASS: label 'tier=smoke' visible in list" \
  || echo "FAIL: label 'tier=smoke' not visible in list"
```

### Test 2.7: Remove a label from tool

```bash
wanaku tools label remove \
  --host "${WANAKU_ROUTER_URL}" \
  --name test-http-tool \
  --label env
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: label 'env' removed"
else
  echo "FAIL: could not remove label (exit code ${EXIT_CODE})"
fi

# Verify env label is gone but tier remains
OUTPUT=$(wanaku tools show --host "${WANAKU_ROUTER_URL}" test-http-tool 2>&1)
echo "${OUTPUT}" | grep -q "env=test" \
  && echo "FAIL: label 'env=test' still present after removal" \
  || echo "PASS: label 'env=test' confirmed removed"
echo "${OUTPUT}" | grep -q "tier=smoke" \
  && echo "PASS: label 'tier=smoke' still present" \
  || echo "FAIL: label 'tier=smoke' unexpectedly removed"
```

### Test 2.8: Remove tool

```bash
wanaku tools remove \
  --host "${WANAKU_ROUTER_URL}" \
  --name test-http-tool
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: tool 'test-http-tool' removed"
else
  echo "FAIL: could not remove tool (exit code ${EXIT_CODE})"
fi
```

### Test 2.9: Verify tool is gone

```bash
OUTPUT=$(wanaku tools list --host "${WANAKU_ROUTER_URL}" --plain 2>&1)
echo "${OUTPUT}" | grep -q "test-http-tool" \
  && echo "FAIL: 'test-http-tool' still in list after removal" \
  || echo "PASS: 'test-http-tool' confirmed removed from list"
```

---

## Phase 3: Tool OpenAPI Generation

### Test 3.1: Create a minimal OpenAPI spec file

```bash
cat > /tmp/wanaku-test-openapi.json <<'SPEC_EOF'
{
  "openapi": "3.0.3",
  "info": {
    "title": "Test API",
    "version": "1.0.0"
  },
  "servers": [
    {
      "url": "https://httpbin.org"
    }
  ],
  "paths": {
    "/get": {
      "get": {
        "operationId": "getRequest",
        "summary": "Returns GET data",
        "parameters": [
          {
            "name": "q",
            "in": "query",
            "required": false,
            "schema": {
              "type": "string"
            },
            "description": "A query parameter"
          }
        ],
        "responses": {
          "200": {
            "description": "Successful response"
          }
        }
      }
    },
    "/post": {
      "post": {
        "operationId": "postRequest",
        "summary": "Returns POST data",
        "responses": {
          "200": {
            "description": "Successful response"
          }
        }
      }
    }
  }
}
SPEC_EOF

if [ -f /tmp/wanaku-test-openapi.json ]; then
  echo "PASS: OpenAPI spec file created"
else
  echo "FAIL: could not create OpenAPI spec file"
fi
```

### Test 3.2: Generate tools from OpenAPI spec

```bash
wanaku tools generate \
  --output-file /tmp/wanaku-test-toolset.json \
  /tmp/wanaku-test-openapi.json
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: tools generated from OpenAPI spec"
else
  echo "FAIL: tools generate failed (exit code ${EXIT_CODE})"
fi

if [ -f /tmp/wanaku-test-toolset.json ]; then
  echo "PASS: generated toolset file exists"
else
  echo "FAIL: generated toolset file not found"
fi
```

### Test 3.3: Import generated tools

```bash
wanaku tools import \
  --host "${WANAKU_ROUTER_URL}" \
  /tmp/wanaku-test-toolset.json
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: toolset imported"
else
  echo "FAIL: tools import failed (exit code ${EXIT_CODE})"
fi
```

### Test 3.4: Verify imported tools appear in list

```bash
OUTPUT=$(wanaku tools list --host "${WANAKU_ROUTER_URL}" --plain 2>&1)
echo "${OUTPUT}" | grep -qi "getRequest\|get-request\|get_request\|get" \
  && echo "PASS: at least one generated tool visible in list" \
  || echo "FAIL: no generated tools found in list"
```

### Test 3.5: Cleanup generated tools

Remove tools that were imported from the generated toolset. Identify them by listing and filtering:

```bash
# Get tool names from the generated file
GENERATED_TOOLS=$(jq -r '.[].name // empty' /tmp/wanaku-test-toolset.json 2>/dev/null)
if [ -z "${GENERATED_TOOLS}" ]; then
  # Alternate structure: may be a single object or different key
  GENERATED_TOOLS=$(jq -r '.name // empty' /tmp/wanaku-test-toolset.json 2>/dev/null)
fi

for TOOL_NAME in ${GENERATED_TOOLS}; do
  wanaku tools remove --host "${WANAKU_ROUTER_URL}" --name "${TOOL_NAME}" 2>/dev/null || true
done
echo "PASS: generated tools cleaned up"

rm -f /tmp/wanaku-test-openapi.json /tmp/wanaku-test-toolset.json
```

---

## Phase 4: Resource CRUD

### Test 4.1: Expose a resource

```bash
wanaku resources expose \
  --host "${WANAKU_ROUTER_URL}" \
  --name test-resource \
  --location "file:///tmp/test-resource.txt" \
  --type file \
  --mimeType text/plain \
  --description "Test resource for E2E verification"
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: resource 'test-resource' exposed"
else
  echo "FAIL: could not expose resource (exit code ${EXIT_CODE})"
fi
```

### Test 4.2: Verify resource appears in list

```bash
OUTPUT=$(wanaku resources list --host "${WANAKU_ROUTER_URL}" --plain 2>&1)
echo "${OUTPUT}" | grep -q "test-resource" \
  && echo "PASS: 'test-resource' appears in resources list" \
  || echo "FAIL: 'test-resource' not found in resources list"
```

### Test 4.3: Show resource details

```bash
OUTPUT=$(wanaku resources show --host "${WANAKU_ROUTER_URL}" test-resource 2>&1)
EXIT_CODE=$?
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: resources show failed (exit code ${EXIT_CODE})"
else
  echo "${OUTPUT}" | grep -q "test-resource" \
    && echo "PASS: show displays resource name" \
    || echo "FAIL: resource name not in show output"
  echo "${OUTPUT}" | grep -q "Test resource for E2E verification" \
    && echo "PASS: show displays description" \
    || echo "FAIL: description not in show output"
  echo "${OUTPUT}" | grep -q "file:///tmp/test-resource.txt" \
    && echo "PASS: show displays location" \
    || echo "FAIL: location not in show output"
  echo "${OUTPUT}" | grep -q "text/plain" \
    && echo "PASS: show displays mimeType" \
    || echo "FAIL: mimeType not in show output"
fi
```

### Test 4.4: Add labels to resource

```bash
wanaku resources label add \
  --host "${WANAKU_ROUTER_URL}" \
  --name test-resource \
  --label env=test
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: label added to resource"
else
  echo "FAIL: could not add label to resource (exit code ${EXIT_CODE})"
fi

OUTPUT=$(wanaku resources show --host "${WANAKU_ROUTER_URL}" test-resource 2>&1)
echo "${OUTPUT}" | grep -q "env=test" \
  && echo "PASS: label 'env=test' visible in resource details" \
  || echo "FAIL: label 'env=test' not visible in resource details"
```

### Test 4.5: Remove label from resource

```bash
wanaku resources label remove \
  --host "${WANAKU_ROUTER_URL}" \
  --name test-resource \
  --label env
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: label removed from resource"
else
  echo "FAIL: could not remove label from resource (exit code ${EXIT_CODE})"
fi

OUTPUT=$(wanaku resources show --host "${WANAKU_ROUTER_URL}" test-resource 2>&1)
echo "${OUTPUT}" | grep -q "env=test" \
  && echo "FAIL: label 'env=test' still present after removal" \
  || echo "PASS: label 'env=test' confirmed removed"
```

### Test 4.6: Remove resource

```bash
wanaku resources remove \
  --host "${WANAKU_ROUTER_URL}" \
  --name test-resource
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: resource 'test-resource' removed"
else
  echo "FAIL: could not remove resource (exit code ${EXIT_CODE})"
fi
```

### Test 4.7: Verify resource is gone

```bash
OUTPUT=$(wanaku resources list --host "${WANAKU_ROUTER_URL}" --plain 2>&1)
echo "${OUTPUT}" | grep -q "test-resource" \
  && echo "FAIL: 'test-resource' still in list after removal" \
  || echo "PASS: 'test-resource' confirmed removed from list"
```

---

## Phase 5: Prompt CRUD

### Test 5.1: Add a prompt

```bash
wanaku prompts add \
  --host "${WANAKU_ROUTER_URL}" \
  --name test-prompt \
  --description "Test prompt for E2E verification" \
  --message "user:text:Review this code: {{code}}" \
  --argument "code:The code to review:true"
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: prompt 'test-prompt' added"
else
  echo "FAIL: could not add prompt (exit code ${EXIT_CODE})"
fi
```

### Test 5.2: Verify prompt appears in list

```bash
OUTPUT=$(wanaku prompts list --host "${WANAKU_ROUTER_URL}" --plain 2>&1)
echo "${OUTPUT}" | grep -q "test-prompt" \
  && echo "PASS: 'test-prompt' appears in prompts list" \
  || echo "FAIL: 'test-prompt' not found in prompts list"
```

### Test 5.3: Edit prompt description

```bash
wanaku prompts edit \
  --host "${WANAKU_ROUTER_URL}" \
  --name test-prompt \
  --description "Updated prompt description"
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: prompt edit succeeded"
else
  echo "FAIL: prompt edit failed (exit code ${EXIT_CODE})"
fi
```

### Test 5.4: Verify prompt description was updated

```bash
OUTPUT=$(wanaku prompts list --host "${WANAKU_ROUTER_URL}" --plain 2>&1)
echo "${OUTPUT}" | grep -q "Updated prompt description" \
  && echo "PASS: prompt description updated" \
  || echo "FAIL: prompt description not updated in list output"
```

### Test 5.5: Remove prompt

```bash
wanaku prompts remove \
  --host "${WANAKU_ROUTER_URL}" \
  --name test-prompt
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: prompt 'test-prompt' removed"
else
  echo "FAIL: could not remove prompt (exit code ${EXIT_CODE})"
fi
```

### Test 5.6: Verify prompt is gone

```bash
OUTPUT=$(wanaku prompts list --host "${WANAKU_ROUTER_URL}" --plain 2>&1)
echo "${OUTPUT}" | grep -q "test-prompt" \
  && echo "FAIL: 'test-prompt' still in list after removal" \
  || echo "PASS: 'test-prompt' confirmed removed from list"
```

---

## Phase 6: Batch Operations

### Test 6.1: Register 3 tools with the same label

```bash
for i in 1 2 3; do
  wanaku tools add \
    --host "${WANAKU_ROUTER_URL}" \
    --name "batch-tool-${i}" \
    --description "Batch test tool ${i}" \
    --uri "https://httpbin.org/get?id=${i}" \
    --type http \
    --label env=batch-test
  EXIT_CODE=$?
  if [ "${EXIT_CODE}" -eq 0 ]; then
    echo "PASS: batch-tool-${i} added"
  else
    echo "FAIL: could not add batch-tool-${i} (exit code ${EXIT_CODE})"
  fi
done
```

### Test 6.2: Verify all 3 tools are listed

```bash
OUTPUT=$(wanaku tools list --host "${WANAKU_ROUTER_URL}" --plain 2>&1)
COUNT=0
for i in 1 2 3; do
  echo "${OUTPUT}" | grep -q "batch-tool-${i}" && COUNT=$((COUNT + 1))
done
if [ "${COUNT}" -eq 3 ]; then
  echo "PASS: all 3 batch tools are listed"
else
  echo "FAIL: only ${COUNT}/3 batch tools found in list"
fi
```

### Test 6.3: Batch remove tools by label expression

```bash
wanaku tools remove \
  --host "${WANAKU_ROUTER_URL}" \
  -e "env=batch-test" \
  -y
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: batch remove by label expression succeeded"
else
  echo "FAIL: batch remove failed (exit code ${EXIT_CODE})"
fi
```

### Test 6.4: Verify all 3 tools are removed

```bash
OUTPUT=$(wanaku tools list --host "${WANAKU_ROUTER_URL}" --plain 2>&1)
REMAINING=0
for i in 1 2 3; do
  echo "${OUTPUT}" | grep -q "batch-tool-${i}" && REMAINING=$((REMAINING + 1))
done
if [ "${REMAINING}" -eq 0 ]; then
  echo "PASS: all batch tools removed"
else
  echo "FAIL: ${REMAINING} batch tool(s) still present after removal"
fi
```

---

## Phase 7: Negative Tests

### Test 7.1: Add tool with duplicate name should fail

```bash
# First, add a tool
wanaku tools add \
  --host "${WANAKU_ROUTER_URL}" \
  --name duplicate-tool \
  --description "First instance" \
  --uri "https://httpbin.org/get" \
  --type http
EXIT_CODE=$?
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: could not add initial tool for duplicate test"
fi

# Attempt to add a tool with the same name
OUTPUT=$(wanaku tools add \
  --host "${WANAKU_ROUTER_URL}" \
  --name duplicate-tool \
  --description "Duplicate instance" \
  --uri "https://httpbin.org/post" \
  --type http 2>&1)
EXIT_CODE=$?
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: duplicate tool name rejected (exit code ${EXIT_CODE})"
else
  echo "FAIL: duplicate tool name was accepted -- expected rejection"
fi

# Cleanup
wanaku tools remove --host "${WANAKU_ROUTER_URL}" --name duplicate-tool 2>/dev/null || true
```

### Test 7.2: Show non-existent tool should fail

```bash
OUTPUT=$(wanaku tools show --host "${WANAKU_ROUTER_URL}" non-existent-tool-99999 2>&1)
EXIT_CODE=$?
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: show non-existent tool failed as expected (exit code ${EXIT_CODE})"
else
  echo "${OUTPUT}" | grep -qi "not found\|warning" \
    && echo "PASS: show non-existent tool returned not-found message" \
    || echo "FAIL: show non-existent tool did not fail or indicate not-found"
fi
```

### Test 7.3: Remove non-existent tool should fail gracefully

```bash
OUTPUT=$(wanaku tools remove --host "${WANAKU_ROUTER_URL}" --name non-existent-tool-99999 2>&1)
EXIT_CODE=$?
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: remove non-existent tool failed gracefully (exit code ${EXIT_CODE})"
else
  echo "FAIL: remove non-existent tool should have returned a non-zero exit code"
fi
```

### Test 7.4: Expose resource with missing required fields should fail

The `--location`, `--type`, `--name`, and `--description` options are required. Omitting `--name` should cause a CLI error.

```bash
OUTPUT=$(wanaku resources expose \
  --host "${WANAKU_ROUTER_URL}" \
  --location "file:///tmp/test.txt" \
  --type file \
  --description "Missing name field" 2>&1)
EXIT_CODE=$?
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: resource with missing required field rejected (exit code ${EXIT_CODE})"
else
  echo "FAIL: resource with missing required field was accepted"
fi
```

### Test 7.5: Add prompt with no messages

```bash
OUTPUT=$(wanaku prompts add \
  --host "${WANAKU_ROUTER_URL}" \
  --name empty-prompt \
  --description "Prompt with no messages" 2>&1)
EXIT_CODE=$?
# A prompt with no messages may be accepted or rejected depending on implementation.
# Record the outcome for analysis.
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: prompt with no messages rejected (exit code ${EXIT_CODE})"
else
  echo "WARN: prompt with no messages was accepted -- verify if this is intended behavior"
  # Cleanup
  wanaku prompts remove --host "${WANAKU_ROUTER_URL}" --name empty-prompt 2>/dev/null || true
fi
```

### Test 7.6: Remove non-existent prompt should fail gracefully

```bash
OUTPUT=$(wanaku prompts remove --host "${WANAKU_ROUTER_URL}" --name non-existent-prompt-99999 2>&1)
EXIT_CODE=$?
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: remove non-existent prompt failed gracefully (exit code ${EXIT_CODE})"
else
  echo "FAIL: remove non-existent prompt should have returned a non-zero exit code"
fi
```

### Test 7.7: Show non-existent resource should fail

```bash
OUTPUT=$(wanaku resources show --host "${WANAKU_ROUTER_URL}" non-existent-resource-99999 2>&1)
EXIT_CODE=$?
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: show non-existent resource failed as expected (exit code ${EXIT_CODE})"
else
  echo "${OUTPUT}" | grep -qi "not found\|warning" \
    && echo "PASS: show non-existent resource returned not-found message" \
    || echo "FAIL: show non-existent resource did not fail or indicate not-found"
fi
```

### Test 7.8: Remove non-existent resource should fail gracefully

```bash
OUTPUT=$(wanaku resources remove --host "${WANAKU_ROUTER_URL}" --name non-existent-resource-99999 2>&1)
EXIT_CODE=$?
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: remove non-existent resource failed gracefully (exit code ${EXIT_CODE})"
else
  echo "FAIL: remove non-existent resource should have returned a non-zero exit code"
fi
```

---

## Phase 8: Cleanup

All cleanup steps are idempotent and safe to re-run.

### Step 8.1: Remove any remaining test artifacts

```bash
for TOOL in test-http-tool duplicate-tool batch-tool-1 batch-tool-2 batch-tool-3; do
  wanaku tools remove --host "${WANAKU_ROUTER_URL}" --name "${TOOL}" 2>/dev/null || true
done

wanaku resources remove --host "${WANAKU_ROUTER_URL}" --name test-resource 2>/dev/null || true

for PROMPT in test-prompt empty-prompt; do
  wanaku prompts remove --host "${WANAKU_ROUTER_URL}" --name "${PROMPT}" 2>/dev/null || true
done

echo "PASS: test artifacts cleaned up"
```

### Step 8.2: Remove temporary files

```bash
rm -f /tmp/wanaku-test-openapi.json /tmp/wanaku-test-toolset.json
echo "PASS: temporary files removed"
```

### Step 8.3: Stop the local Wanaku process

```bash
if [ -n "${WANAKU_PID}" ]; then
  kill "${WANAKU_PID}" 2>/dev/null || true
  wait "${WANAKU_PID}" 2>/dev/null || true
  echo "PASS: Wanaku process stopped"
else
  echo "PASS: no Wanaku PID to stop (may have been started externally)"
fi
```

---

## Summary Matrix

| Phase | Test ID | Test Name | Priority |
|-------|---------|-----------|----------|
| 0 | 0.1 | Verify required tools | High |
| 1 | 1.1 | CLI connects to router | Critical |
| 2 | 2.1 | Add a tool | Critical |
| 2 | 2.2 | Tool appears in list | Critical |
| 2 | 2.3 | Show tool details | Critical |
| 2 | 2.4 | Edit tool (interactive) | Medium |
| 2 | 2.5 | Add labels to tool | High |
| 2 | 2.6 | Labels visible in list | High |
| 2 | 2.7 | Remove a label from tool | High |
| 2 | 2.8 | Remove tool | Critical |
| 2 | 2.9 | Tool gone after removal | Critical |
| 3 | 3.1 | Create OpenAPI spec file | High |
| 3 | 3.2 | Generate tools from OpenAPI | High |
| 3 | 3.3 | Import generated tools | High |
| 3 | 3.4 | Imported tools in list | High |
| 3 | 3.5 | Cleanup generated tools | Medium |
| 4 | 4.1 | Expose a resource | Critical |
| 4 | 4.2 | Resource appears in list | Critical |
| 4 | 4.3 | Show resource details | Critical |
| 4 | 4.4 | Add labels to resource | High |
| 4 | 4.5 | Remove label from resource | High |
| 4 | 4.6 | Remove resource | Critical |
| 4 | 4.7 | Resource gone after removal | Critical |
| 5 | 5.1 | Add a prompt | Critical |
| 5 | 5.2 | Prompt appears in list | Critical |
| 5 | 5.3 | Edit prompt description | High |
| 5 | 5.4 | Prompt description updated | High |
| 5 | 5.5 | Remove prompt | Critical |
| 5 | 5.6 | Prompt gone after removal | Critical |
| 6 | 6.1 | Register 3 batch tools | High |
| 6 | 6.2 | All batch tools listed | High |
| 6 | 6.3 | Batch remove by label | High |
| 6 | 6.4 | All batch tools removed | High |
| 7 | 7.1 | Duplicate tool name rejected | High |
| 7 | 7.2 | Show non-existent tool fails | Medium |
| 7 | 7.3 | Remove non-existent tool fails | Medium |
| 7 | 7.4 | Resource missing required field | Medium |
| 7 | 7.5 | Prompt with no messages | Medium |
| 7 | 7.6 | Remove non-existent prompt fails | Medium |
| 7 | 7.7 | Show non-existent resource fails | Medium |
| 7 | 7.8 | Remove non-existent resource fails | Medium |
| 8 | 8.1-8.3 | Cleanup | Critical |
