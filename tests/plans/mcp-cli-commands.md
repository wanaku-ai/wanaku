# Test Plan: Wanaku MCP CLI Commands

## Overview

This test plan verifies the `wanaku mcp` CLI commands for interacting directly with MCP servers. It covers tool invocation, resource reading, prompt retrieval, and listing operations.

Every step is fully automatable.

## Prerequisites

### Required tools

| Tool | Minimum version | Verify command |
|------|-----------------|----------------|
| `wanaku` | 0.2.0+ | `wanaku --version` |
| `curl` | any | `curl --version` |
| `jq` | 1.6+ | `jq --version` |

### Environment variables

```bash
export MCP_SERVER_URI="${MCP_SERVER_URI:-http://localhost:8080/mcp}"
```

### MCP server setup

An MCP server must be running and accessible at `MCP_SERVER_URI`. You can use the project's mock MCP server for testing:

```bash
cd tests/mcp-servers/wanaku-performance-test-mock-mcp
mvn quarkus:dev
```

Or start a Wanaku router with capabilities registered.

---

## Phase 1: Help and Usage

### Test 1.1: Verify `wanaku mcp` shows usage

```bash
OUTPUT=$(wanaku mcp 2>&1)
echo "${OUTPUT}" | grep -q "tool" && echo "PASS: 'tool' subcommand listed" || echo "FAIL: 'tool' not in usage"
echo "${OUTPUT}" | grep -q "resource" && echo "PASS: 'resource' subcommand listed" || echo "FAIL: 'resource' not in usage"
echo "${OUTPUT}" | grep -q "prompt" && echo "PASS: 'prompt' subcommand listed" || echo "FAIL: 'prompt' not in usage"
```

### Test 1.2: Verify `wanaku mcp tool --help` shows options

```bash
OUTPUT=$(wanaku mcp tool --help 2>&1)
echo "${OUTPUT}" | grep -q "\-\-uri" && echo "PASS: --uri option listed" || echo "FAIL: --uri not in help"
echo "${OUTPUT}" | grep -q "\-\-name" && echo "PASS: --name option listed" || echo "FAIL: --name not in help"
echo "${OUTPUT}" | grep -q "\-\-param" && echo "PASS: --param option listed" || echo "FAIL: --param not in help"
echo "${OUTPUT}" | grep -q "list" && echo "PASS: 'list' subcommand listed" || echo "FAIL: 'list' not in help"
```

### Test 1.3: Verify `wanaku mcp resource --help` shows options

```bash
OUTPUT=$(wanaku mcp resource --help 2>&1)
echo "${OUTPUT}" | grep -q "\-\-uri" && echo "PASS: --uri option listed" || echo "FAIL: --uri not in help"
echo "${OUTPUT}" | grep -q "\-\-resource-uri" && echo "PASS: --resource-uri option listed" || echo "FAIL: --resource-uri not in help"
echo "${OUTPUT}" | grep -q "list" && echo "PASS: 'list' subcommand listed" || echo "FAIL: 'list' not in help"
```

### Test 1.4: Verify `wanaku mcp prompt --help` shows options

```bash
OUTPUT=$(wanaku mcp prompt --help 2>&1)
echo "${OUTPUT}" | grep -q "\-\-uri" && echo "PASS: --uri option listed" || echo "FAIL: --uri not in help"
echo "${OUTPUT}" | grep -q "\-\-name" && echo "PASS: --name option listed" || echo "FAIL: --name not in help"
echo "${OUTPUT}" | grep -q "\-\-arg" && echo "PASS: --arg option listed" || echo "FAIL: --arg not in help"
echo "${OUTPUT}" | grep -q "list" && echo "PASS: 'list' subcommand listed" || echo "FAIL: 'list' not in help"
```

---

## Phase 2: Required Option Validation

### Test 2.1: `wanaku mcp tool` without `--uri` should fail

```bash
wanaku mcp tool --name test 2>&1
EXIT_CODE=$?
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: missing --uri rejected (exit code ${EXIT_CODE})"
else
  echo "FAIL: missing --uri should fail"
fi
```

### Test 2.2: `wanaku mcp tool` without `--name` should fail

```bash
wanaku mcp tool --uri "${MCP_SERVER_URI}" 2>&1
EXIT_CODE=$?
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: missing --name rejected (exit code ${EXIT_CODE})"
else
  echo "FAIL: missing --name should fail"
fi
```

### Test 2.3: `wanaku mcp resource` without `--resource-uri` should fail

```bash
wanaku mcp resource --uri "${MCP_SERVER_URI}" 2>&1
EXIT_CODE=$?
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: missing --resource-uri rejected (exit code ${EXIT_CODE})"
else
  echo "FAIL: missing --resource-uri should fail"
fi
```

### Test 2.4: `wanaku mcp tool list` without `--uri` should fail

```bash
wanaku mcp tool list 2>&1
EXIT_CODE=$?
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: missing --uri rejected (exit code ${EXIT_CODE})"
else
  echo "FAIL: missing --uri should fail"
fi
```

---

## Phase 3: Tool Operations

### Test 3.1: List tools from MCP server

```bash
OUTPUT=$(wanaku mcp tool list --uri "${MCP_SERVER_URI}" --plain 2>&1)
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: tool list returned successfully"
  echo "Tools found:"
  echo "${OUTPUT}"
else
  echo "FAIL: tool list failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
fi
```

### Test 3.2: Call a tool with parameters

**Description:** Call a known tool on the MCP server with parameters and verify the result is printed.

```bash
# Replace TOOL_NAME with an actual tool from the server (e.g., "echo", "http-request")
TOOL_NAME="${TEST_TOOL_NAME:-echo}"
TOOL_PARAM="${TEST_TOOL_PARAM:-message=hello}"

OUTPUT=$(wanaku mcp tool --uri "${MCP_SERVER_URI}" --name "${TOOL_NAME}" --param "${TOOL_PARAM}" --plain 2>&1)
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: tool call returned successfully"
  echo "Result: ${OUTPUT}"
else
  echo "FAIL: tool call failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
fi
```

### Test 3.3: Call a tool with multiple parameters

```bash
TOOL_NAME="${TEST_TOOL_NAME:-echo}"

OUTPUT=$(wanaku mcp tool --uri "${MCP_SERVER_URI}" --name "${TOOL_NAME}" --param key1=value1 --param key2=value2 --plain 2>&1)
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: tool call with multiple params succeeded"
else
  echo "FAIL: tool call with multiple params failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
fi
```

### Test 3.4: Call a non-existent tool should fail

```bash
OUTPUT=$(wanaku mcp tool --uri "${MCP_SERVER_URI}" --name "non-existent-tool-12345" --plain 2>&1)
EXIT_CODE=$?
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: non-existent tool call failed as expected (exit code ${EXIT_CODE})"
else
  echo "FAIL: non-existent tool call should have failed"
fi
```

---

## Phase 4: Resource Operations

### Test 4.1: List resources from MCP server

```bash
OUTPUT=$(wanaku mcp resource list --uri "${MCP_SERVER_URI}" --plain 2>&1)
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: resource list returned successfully"
  echo "Resources found:"
  echo "${OUTPUT}"
else
  echo "FAIL: resource list failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
fi
```

### Test 4.2: Read a resource

**Description:** Read a known resource from the MCP server and verify content is printed.

```bash
# Replace RESOURCE_URI with an actual resource URI from the server
RESOURCE_URI="${TEST_RESOURCE_URI:-file:///test}"

OUTPUT=$(wanaku mcp resource --uri "${MCP_SERVER_URI}" --resource-uri "${RESOURCE_URI}" --plain 2>&1)
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: resource read returned successfully"
  echo "Content: ${OUTPUT}"
else
  echo "FAIL: resource read failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
fi
```

### Test 4.3: Read a non-existent resource should fail

```bash
OUTPUT=$(wanaku mcp resource --uri "${MCP_SERVER_URI}" --resource-uri "file:///non-existent-12345" --plain 2>&1)
EXIT_CODE=$?
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: non-existent resource read failed as expected"
else
  echo "WARN: non-existent resource read did not fail — server may return empty content"
fi
```

---

## Phase 5: Prompt Operations

### Test 5.1: List prompts from MCP server

```bash
OUTPUT=$(wanaku mcp prompt list --uri "${MCP_SERVER_URI}" --plain 2>&1)
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: prompt list returned successfully"
  echo "Prompts found:"
  echo "${OUTPUT}"
else
  echo "FAIL: prompt list failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
fi
```

### Test 5.2: Get a prompt with arguments

**Description:** Render a known prompt from the MCP server with arguments.

```bash
# Replace PROMPT_NAME with an actual prompt from the server
PROMPT_NAME="${TEST_PROMPT_NAME:-greeting}"
PROMPT_ARG="${TEST_PROMPT_ARG:-name=Alice}"

OUTPUT=$(wanaku mcp prompt --uri "${MCP_SERVER_URI}" --name "${PROMPT_NAME}" --arg "${PROMPT_ARG}" --plain 2>&1)
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: prompt get returned successfully"
  echo "Messages: ${OUTPUT}"
else
  echo "FAIL: prompt get failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
fi
```

### Test 5.3: Get a non-existent prompt should fail

```bash
OUTPUT=$(wanaku mcp prompt --uri "${MCP_SERVER_URI}" --name "non-existent-prompt-12345" --plain 2>&1)
EXIT_CODE=$?
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: non-existent prompt get failed as expected"
else
  echo "FAIL: non-existent prompt get should have failed"
fi
```

---

## Phase 6: Connection Error Handling

### Test 6.1: Connecting to a non-existent server should fail gracefully

```bash
OUTPUT=$(wanaku mcp tool list --uri "http://localhost:59999/mcp" --plain 2>&1)
EXIT_CODE=$?
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: connection to non-existent server failed gracefully (exit code ${EXIT_CODE})"
else
  echo "FAIL: connection to non-existent server should fail"
fi
```

### Test 6.2: Invalid URI should fail gracefully

```bash
OUTPUT=$(wanaku mcp tool list --uri "not-a-valid-uri" --plain 2>&1)
EXIT_CODE=$?
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: invalid URI failed gracefully (exit code ${EXIT_CODE})"
else
  echo "FAIL: invalid URI should fail"
fi
```

---

## Test Summary Matrix

| Phase | Test ID | Test Name | Priority |
|-------|---------|-----------|----------|
| 1 | 1.1-1.4 | Help and usage output | Medium |
| 2 | 2.1-2.4 | Required option validation | High |
| 3 | 3.1-3.4 | Tool list, call, multi-param, non-existent | Critical |
| 4 | 4.1-4.3 | Resource list, read, non-existent | Critical |
| 5 | 5.1-5.3 | Prompt list, get, non-existent | Critical |
| 6 | 6.1-6.2 | Connection error handling | High |
