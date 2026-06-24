# Test Plan: CLI Toolset Repository Commands

## Overview

This test plan verifies the `wanaku toolset repo` CLI commands for managing toolset repositories
against a locally running Wanaku instance (no authentication). It covers adding, listing, browsing,
and removing toolset repositories, as well as importing tools from a toolset file via `wanaku tools import`.

Every step is fully automatable.

## Prerequisites

### Required tools

| Tool | Minimum version | Verify command |
|------|-----------------|----------------|
| `wanaku` | 0.2.0+ | `wanaku --version` |
| `jq` | 1.6+ | `jq --version` |
| `curl` | any | `curl --version` |

### Environment variables

```bash
export WANAKU_REPO_ROOT="${WANAKU_REPO_ROOT:-.}"
export WANAKU_ROUTER_URL="${WANAKU_ROUTER_URL:-http://localhost:8080}"
export TOOLSET_REPO_URL="${TOOLSET_REPO_URL:-https://github.com/wanaku-ai/wanaku-toolsets}"
export TOOLSET_REPO_NAME="${TOOLSET_REPO_NAME:-test-repo}"
export TOOLSET_IMPORT_URL="${TOOLSET_IMPORT_URL:-https://raw.githubusercontent.com/wanaku-ai/wanaku-toolsets/refs/heads/main/toolsets/currency.json}"
```

### CLI invocation

When using the CLI from a local build (not installed), use `java -jar` directly:

```bash
CLI_JAR="apps/wanaku-cli/target/quarkus-app/quarkus-run.jar"
java -jar ${CLI_JAR} toolset repo list --host ...
```

Do **not** assign the full command to a single variable (e.g., `WANAKU_CLI="java -jar path/to/jar"`) — zsh treats it as a single token. Use `CLI_JAR` for the path and call `java -jar ${CLI_JAR}` explicitly.

---

## Phase 0: Prerequisites Verification

### Test 0.1: Verify required tools are available

```bash
wanaku --version > /dev/null 2>&1 && echo "PASS: wanaku CLI available" || echo "FAIL: wanaku CLI not found"
jq --version > /dev/null 2>&1 && echo "PASS: jq available" || echo "FAIL: jq not found"
curl --version > /dev/null 2>&1 && echo "PASS: curl available" || echo "FAIL: curl not found"
```

### Test 0.2: Verify environment variables are set

```bash
for VAR_NAME in WANAKU_ROUTER_URL TOOLSET_REPO_URL TOOLSET_REPO_NAME TOOLSET_IMPORT_URL; do
  eval "VAL=\${${VAR_NAME}}"
  if [ -z "${VAL}" ]; then
    echo "FAIL: ${VAR_NAME} is not set"
  else
    echo "PASS: ${VAR_NAME}=${VAL}"
  fi
done
```

---

## Phase 1: Setup

Follow [common/start-local.md](common/start-local.md) to build and start the local Wanaku stack.

After completion, `WANAKU_ROUTER_URL`, `WANAKU_PID`, and `CLI_JAR` must be set.

### Test 1.1: Verify router is healthy

```bash
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "${WANAKU_ROUTER_URL}/q/health/ready" 2>/dev/null || echo "000")
if [ "${HTTP_CODE}" = "200" ]; then
  echo "PASS: router is healthy"
else
  echo "FAIL: router not healthy (HTTP ${HTTP_CODE})"
fi
```

### Test 1.2: Verify CLI can connect

```bash
wanaku tools list --host "${WANAKU_ROUTER_URL}" --plain 2>&1
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: CLI can connect to router"
else
  echo "FAIL: CLI cannot connect to router (exit code ${EXIT_CODE})"
fi
```

---

## Phase 2: Help and Usage

### Test 2.1: Verify `wanaku toolset` shows subcommands

```bash
OUTPUT=$(wanaku toolset 2>&1)
echo "${OUTPUT}" | grep -q "repo" && echo "PASS: 'repo' subcommand listed" || echo "FAIL: 'repo' not in usage"
echo "${OUTPUT}" | grep -q "add" && echo "PASS: 'add' subcommand listed" || echo "FAIL: 'add' not in usage"
```

### Test 2.2: Verify `wanaku toolset repo` shows subcommands

```bash
OUTPUT=$(wanaku toolset repo 2>&1)
echo "${OUTPUT}" | grep -q "add" && echo "PASS: 'add' subcommand listed" || echo "FAIL: 'add' not in usage"
echo "${OUTPUT}" | grep -q "list" && echo "PASS: 'list' subcommand listed" || echo "FAIL: 'list' not in usage"
echo "${OUTPUT}" | grep -q "remove" && echo "PASS: 'remove' subcommand listed" || echo "FAIL: 'remove' not in usage"
echo "${OUTPUT}" | grep -q "browse" && echo "PASS: 'browse' subcommand listed" || echo "FAIL: 'browse' not in usage"
```

### Test 2.3: Verify `wanaku toolset repo add --help` shows required options

```bash
OUTPUT=$(wanaku toolset repo add --help 2>&1)
echo "${OUTPUT}" | grep -q "\-\-name" && echo "PASS: --name option listed" || echo "FAIL: --name not in help"
echo "${OUTPUT}" | grep -q "\-\-url" && echo "PASS: --url option listed" || echo "FAIL: --url not in help"
echo "${OUTPUT}" | grep -q "\-\-host" && echo "PASS: --host option listed" || echo "FAIL: --host not in help"
echo "${OUTPUT}" | grep -q "\-\-branch" && echo "PASS: --branch option listed" || echo "FAIL: --branch not in help"
```

---

## Phase 3: Toolset Repo -- Add

### Test 3.1: Add a toolset repository

```bash
wanaku toolset repo add \
  --host "${WANAKU_ROUTER_URL}" \
  --name "${TOOLSET_REPO_NAME}" \
  --url "${TOOLSET_REPO_URL}" \
  --description "Test toolset repository" \
  --plain 2>&1
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: toolset repository '${TOOLSET_REPO_NAME}' added successfully"
else
  echo "FAIL: failed to add toolset repository (exit code ${EXIT_CODE})"
fi
```

### Test 3.2: Add a second toolset repository with a custom branch

```bash
wanaku toolset repo add \
  --host "${WANAKU_ROUTER_URL}" \
  --name "test-repo-branch" \
  --url "${TOOLSET_REPO_URL}" \
  --branch "main" \
  --description "Test repo with explicit branch" \
  --plain 2>&1
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: toolset repository 'test-repo-branch' added with explicit branch"
else
  echo "FAIL: failed to add toolset repository with branch (exit code ${EXIT_CODE})"
fi
```

---

## Phase 4: Toolset Repo -- List

### Test 4.1: List toolset repositories and verify test-repo appears

```bash
OUTPUT=$(wanaku toolset repo list --host "${WANAKU_ROUTER_URL}" --plain 2>&1)
EXIT_CODE=$?
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: toolset repo list failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
else
  echo "${OUTPUT}" | grep -q "${TOOLSET_REPO_NAME}" \
    && echo "PASS: '${TOOLSET_REPO_NAME}' appears in repo list" \
    || echo "FAIL: '${TOOLSET_REPO_NAME}' not found in repo list"

  echo "${OUTPUT}" | grep -q "test-repo-branch" \
    && echo "PASS: 'test-repo-branch' appears in repo list" \
    || echo "FAIL: 'test-repo-branch' not found in repo list"
fi
```

### Test 4.2: List output contains the repository URL

```bash
OUTPUT=$(wanaku toolset repo list --host "${WANAKU_ROUTER_URL}" --plain 2>&1)
echo "${OUTPUT}" | grep -q "${TOOLSET_REPO_URL}" \
  && echo "PASS: repository URL appears in list output" \
  || echo "FAIL: repository URL not found in list output"
```

---

## Phase 5: Toolset Repo -- Browse

### Test 5.1: Browse the test repository catalog

**Note:** The `browse` command takes the repository name as a positional argument, not `--name`.

```bash
OUTPUT=$(wanaku toolset repo browse --host "${WANAKU_ROUTER_URL}" "${TOOLSET_REPO_NAME}" --plain 2>&1)
EXIT_CODE=$?
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: toolset repo browse failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
else
  echo "PASS: toolset repo browse succeeded"
  echo "Catalog contents:"
  echo "${OUTPUT}"
fi
```

### Test 5.2: Browse output contains repository metadata

```bash
OUTPUT=$(wanaku toolset repo browse --host "${WANAKU_ROUTER_URL}" "${TOOLSET_REPO_NAME}" --plain 2>&1)
echo "${OUTPUT}" | grep -qi "toolset" \
  && echo "PASS: browse output contains toolset entries" \
  || echo "FAIL: browse output does not contain toolset entries"
```

---

## Phase 6: Toolset Repo -- Remove

### Test 6.1: Remove the second test repository

**Note:** The `remove` command takes the repository name as a positional argument, not `--name`.

```bash
wanaku toolset repo remove --host "${WANAKU_ROUTER_URL}" "test-repo-branch" --plain 2>&1
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: toolset repository 'test-repo-branch' removed"
else
  echo "FAIL: failed to remove toolset repository (exit code ${EXIT_CODE})"
fi
```

### Test 6.2: Verify the removed repository no longer appears in list

```bash
OUTPUT=$(wanaku toolset repo list --host "${WANAKU_ROUTER_URL}" --plain 2>&1)
echo "${OUTPUT}" | grep -q "test-repo-branch" \
  && echo "FAIL: 'test-repo-branch' still appears after removal" \
  || echo "PASS: 'test-repo-branch' no longer in list"
```

### Test 6.3: Remove the primary test repository

```bash
wanaku toolset repo remove --host "${WANAKU_ROUTER_URL}" "${TOOLSET_REPO_NAME}" --plain 2>&1
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: toolset repository '${TOOLSET_REPO_NAME}' removed"
else
  echo "FAIL: failed to remove toolset repository (exit code ${EXIT_CODE})"
fi
```

### Test 6.4: Verify list is empty (or no longer contains test-repo)

```bash
OUTPUT=$(wanaku toolset repo list --host "${WANAKU_ROUTER_URL}" --plain 2>&1)
echo "${OUTPUT}" | grep -q "${TOOLSET_REPO_NAME}" \
  && echo "FAIL: '${TOOLSET_REPO_NAME}' still appears after removal" \
  || echo "PASS: '${TOOLSET_REPO_NAME}' no longer in list"
```

---

## Phase 7: Tools Import from Toolset

### Test 7.1: Import tools from a remote toolset URL

```bash
wanaku tools import --host "${WANAKU_ROUTER_URL}" "${TOOLSET_IMPORT_URL}" --plain 2>&1
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: toolset imported from URL"
else
  echo "FAIL: toolset import failed (exit code ${EXIT_CODE})"
fi
```

### Test 7.2: Verify imported tools appear in tools list

```bash
OUTPUT=$(wanaku tools list --host "${WANAKU_ROUTER_URL}" --plain 2>&1)
EXIT_CODE=$?
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: tools list failed (exit code ${EXIT_CODE})"
else
  # The currency toolset should contain at least one tool
  if [ -n "${OUTPUT}" ]; then
    echo "PASS: tools list returned data after import"
    echo "${OUTPUT}"
  else
    echo "FAIL: tools list is empty after import"
  fi
fi
```

### Test 7.3: Import with a default namespace

```bash
wanaku tools import \
  --host "${WANAKU_ROUTER_URL}" \
  --namespace "test-ns" \
  "${TOOLSET_IMPORT_URL}" \
  --plain 2>&1
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: toolset imported with namespace override"
else
  echo "FAIL: toolset import with namespace failed (exit code ${EXIT_CODE})"
fi
```

---

## Phase 8: Negative Tests

### Test 8.1: Add repo without required --name should fail

```bash
wanaku toolset repo add \
  --host "${WANAKU_ROUTER_URL}" \
  --url "${TOOLSET_REPO_URL}" \
  --plain 2>&1
EXIT_CODE=$?
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: missing --name rejected (exit code ${EXIT_CODE})"
else
  echo "FAIL: missing --name should have failed"
fi
```

### Test 8.2: Add repo without required --url should fail

```bash
wanaku toolset repo add \
  --host "${WANAKU_ROUTER_URL}" \
  --name "no-url-repo" \
  --plain 2>&1
EXIT_CODE=$?
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: missing --url rejected (exit code ${EXIT_CODE})"
else
  echo "FAIL: missing --url should have failed"
fi
```

### Test 8.3: Browse a non-existent repository should fail

```bash
OUTPUT=$(wanaku toolset repo browse --host "${WANAKU_ROUTER_URL}" "non-existent-repo-12345" --plain 2>&1)
EXIT_CODE=$?
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: browse non-existent repo failed as expected (exit code ${EXIT_CODE})"
else
  echo "FAIL: browse non-existent repo should have failed"
fi
```

### Test 8.4: Remove a non-existent repository should fail gracefully

```bash
OUTPUT=$(wanaku toolset repo remove --host "${WANAKU_ROUTER_URL}" "non-existent-repo-12345" --plain 2>&1)
EXIT_CODE=$?
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: remove non-existent repo failed gracefully (exit code ${EXIT_CODE})"
  echo "${OUTPUT}" | grep -qi "not found" \
    && echo "PASS: error message mentions 'not found'" \
    || echo "WARN: error message does not mention 'not found'"
else
  echo "FAIL: remove non-existent repo should have failed"
fi
```

### Test 8.5: Import from an invalid URL should fail

```bash
OUTPUT=$(wanaku tools import --host "${WANAKU_ROUTER_URL}" "https://invalid.example.com/does-not-exist.json" --plain 2>&1)
EXIT_CODE=$?
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: import from invalid URL failed as expected (exit code ${EXIT_CODE})"
else
  echo "FAIL: import from invalid URL should have failed"
fi
```

### Test 8.6: Connecting to a non-existent host should fail gracefully

```bash
OUTPUT=$(wanaku toolset repo list --host "http://localhost:59999" --plain 2>&1)
EXIT_CODE=$?
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: connection to non-existent host failed gracefully (exit code ${EXIT_CODE})"
else
  echo "FAIL: connection to non-existent host should have failed"
fi
```

---

## Phase 9: Cleanup

### Step 9.1: Remove any remaining test repositories

```bash
wanaku toolset repo remove --host "${WANAKU_ROUTER_URL}" "${TOOLSET_REPO_NAME}" --plain 2>/dev/null || true
wanaku toolset repo remove --host "${WANAKU_ROUTER_URL}" "test-repo-branch" --plain 2>/dev/null || true
echo "PASS: test repositories cleaned up"
```

### Step 9.2: Remove any imported tools

```bash
# List and remove tools that were imported during this test
TOOLS_OUTPUT=$(wanaku tools list --host "${WANAKU_ROUTER_URL}" --plain 2>&1)
# Remove tools by name if they were imported from the currency toolset
# This is best-effort; specific tool names depend on the toolset contents
echo "INFO: review tools list for any test artifacts to remove manually"
echo "${TOOLS_OUTPUT}"
```

### Step 9.3: Stop the local Wanaku process

```bash
if [ -n "${WANAKU_PID}" ]; then
  kill "${WANAKU_PID}" 2>/dev/null || true
  wait "${WANAKU_PID}" 2>/dev/null || true
  echo "PASS: Wanaku process stopped"
fi
```

---

## Test Summary Matrix

| Phase | Test ID | Test Name | Priority |
|-------|---------|-----------|----------|
| 0 | 0.1-0.2 | Prerequisites verification | Critical |
| 1 | 1.1-1.2 | Local setup and health check | Critical |
| 2 | 2.1-2.3 | Help and usage output | Medium |
| 3 | 3.1-3.2 | Add toolset repositories | Critical |
| 4 | 4.1-4.2 | List toolset repositories | Critical |
| 5 | 5.1-5.2 | Browse toolset repository catalog | Critical |
| 6 | 6.1-6.4 | Remove toolset repositories | Critical |
| 7 | 7.1-7.3 | Import tools from toolset URL | High |
| 8 | 8.1-8.6 | Negative tests (missing args, non-existent repos, bad URLs) | High |
| 9 | 9.1-9.3 | Cleanup | Critical |
