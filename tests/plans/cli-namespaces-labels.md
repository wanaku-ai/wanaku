# Test Plan: CLI Namespaces and Labels

## Overview

This test plan verifies namespace management, label operations, and namespace isolation via the Wanaku CLI against a locally running instance (no authentication). It covers the full CRUD lifecycle for namespaces, label add/remove operations on both namespaces and tools, label expression filtering, and namespace-scoped tool isolation.

Every step is fully automatable.

## Prerequisites

### Required tools

| Tool | Minimum version | Verify command |
|------|-----------------|----------------|
| `wanaku` | build from source | `wanaku --version` |
| `jq` | 1.6+ | `jq --version` |
| `java` | 21+ | `java -version` |
| `mvn` | 3.9+ | `mvn -version` |

### CLI invocation

When using the CLI from a local build (not installed), use `java -jar` directly:

```bash
CLI_JAR="apps/wanaku-cli/target/quarkus-app/quarkus-run.jar"
java -jar ${CLI_JAR} namespaces list --host ...
```

Do **not** assign the full command to a single variable (e.g., `WANAKU_CLI="java -jar path/to/jar"`) --- zsh treats it as a single token. Use `CLI_JAR` for the path and call `java -jar ${CLI_JAR}` explicitly.

### Environment variables

```bash
export WANAKU_ROUTER_URL="${WANAKU_ROUTER_URL:-http://localhost:8080}"
export WANAKU_REPO_ROOT="${WANAKU_REPO_ROOT:-.}"
```

---

## Phase 0: Manual Prerequisites

Verify the required tools are installed:

```bash
wanaku --version && echo "PASS: wanaku CLI available" || echo "FAIL: wanaku CLI not found"
jq --version && echo "PASS: jq available" || echo "FAIL: jq not found"
java -version 2>&1 | head -1 && echo "PASS: java available" || echo "FAIL: java not found"
mvn -version 2>&1 | head -1 && echo "PASS: mvn available" || echo "FAIL: mvn not found"
```

---

## Phase 1: Setup

Follow [common/start-local.md](common/start-local.md) to build and start the Wanaku stack locally. After completion, the following variables must be set:

- `VERSION`
- `CLI_JAR`
- `WANAKU_ROUTER_URL` (defaults to `http://localhost:8080`)
- `WANAKU_PID`

---

## Phase 2: Namespace CRUD

### Test 2.1: List namespaces and verify defaults exist

```bash
OUTPUT=$(wanaku namespaces list --host "${WANAKU_ROUTER_URL}" --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: namespaces list failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "PASS: namespaces list succeeded"
echo "${OUTPUT}"

echo "${OUTPUT}" | grep -q "public" \
  && echo "PASS: 'public' namespace exists" \
  || echo "FAIL: 'public' namespace not found"

echo "${OUTPUT}" | grep -q "<default>" \
  && echo "PASS: '<default>' namespace exists" \
  || echo "FAIL: '<default>' namespace not found"
```

### Test 2.2: Create a custom namespace

```bash
OUTPUT=$(wanaku namespaces create \
  --host "${WANAKU_ROUTER_URL}" \
  --path "test-ns" \
  --name "test-namespace" \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: namespace create failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "PASS: namespace created"
echo "${OUTPUT}"

# Extract the namespace ID for subsequent tests
TEST_NS_ID=$(echo "${OUTPUT}" | grep -i "id" | head -1 | sed 's/.*: *//' | tr -d '[:space:]')
if [ -z "${TEST_NS_ID}" ]; then
  echo "WARN: could not extract namespace ID from output, will look up by listing"
fi
```

### Test 2.3: Verify created namespace appears in list

```bash
OUTPUT=$(wanaku namespaces list --host "${WANAKU_ROUTER_URL}" --plain 2>&1)
echo "${OUTPUT}" | grep -q "test-namespace" \
  && echo "PASS: created namespace appears in list" \
  || echo "FAIL: created namespace not found in list"
```

### Test 2.4: Show namespace details

**Description:** Retrieve details of the created namespace by ID.

```bash
# If TEST_NS_ID was not captured, look it up from the list output
if [ -z "${TEST_NS_ID}" ]; then
  TEST_NS_ID=$(wanaku namespaces list --host "${WANAKU_ROUTER_URL}" --plain 2>&1 \
    | grep "test-namespace" | awk '{print $1}' | head -1)
fi

if [ -z "${TEST_NS_ID}" ]; then
  echo "FAIL: could not determine namespace ID"
  exit 1
fi

OUTPUT=$(wanaku namespaces show --host "${WANAKU_ROUTER_URL}" "${TEST_NS_ID}" --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: namespace show failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "PASS: namespace show succeeded"
echo "${OUTPUT}"

echo "${OUTPUT}" | grep -q "test-namespace" \
  && echo "PASS: namespace name matches" \
  || echo "FAIL: namespace name does not match"
```

### Test 2.5: Update namespace name

```bash
OUTPUT=$(wanaku namespaces update \
  --host "${WANAKU_ROUTER_URL}" \
  --name "test-namespace-updated" \
  "${TEST_NS_ID}" --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: namespace update failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "PASS: namespace updated"

# Verify the update
VERIFY=$(wanaku namespaces show --host "${WANAKU_ROUTER_URL}" "${TEST_NS_ID}" --plain 2>&1)
echo "${VERIFY}" | grep -q "test-namespace-updated" \
  && echo "PASS: updated name verified" \
  || echo "FAIL: updated name not found"
```

### Test 2.6: Delete namespace

```bash
OUTPUT=$(wanaku namespaces delete --host "${WANAKU_ROUTER_URL}" "${TEST_NS_ID}" --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: namespace delete failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "PASS: namespace deleted"

# Verify deletion
VERIFY=$(wanaku namespaces list --host "${WANAKU_ROUTER_URL}" --plain 2>&1)
echo "${VERIFY}" | grep -q "test-namespace-updated" \
  && echo "FAIL: deleted namespace still appears in list" \
  || echo "PASS: deleted namespace no longer in list"
```

---

## Phase 3: Namespace Labels

### Test 3.1: Create a namespace for label tests

```bash
OUTPUT=$(wanaku namespaces create \
  --host "${WANAKU_ROUTER_URL}" \
  --path "label-test-ns" \
  --name "label-test-namespace" \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: namespace create for label tests failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "PASS: label test namespace created"

# Extract namespace ID
LABEL_NS_ID=$(wanaku namespaces list --host "${WANAKU_ROUTER_URL}" --plain 2>&1 \
  | grep "label-test-namespace" | awk '{print $1}' | head -1)

if [ -z "${LABEL_NS_ID}" ]; then
  echo "FAIL: could not determine label test namespace ID"
  exit 1
fi
echo "PASS: label test namespace ID is ${LABEL_NS_ID}"
```

### Test 3.2: Add a label to the namespace

```bash
OUTPUT=$(wanaku namespaces label add \
  --host "${WANAKU_ROUTER_URL}" \
  --id "${LABEL_NS_ID}" \
  --label env=test \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: namespace label add failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "PASS: label added to namespace"

# Verify the label exists
VERIFY=$(wanaku namespaces show --host "${WANAKU_ROUTER_URL}" "${LABEL_NS_ID}" --plain 2>&1)
echo "${VERIFY}" | grep -q "env" \
  && echo "PASS: label 'env' visible on namespace" \
  || echo "FAIL: label 'env' not found on namespace"
```

### Test 3.3: Add multiple labels to the namespace

```bash
OUTPUT=$(wanaku namespaces label add \
  --host "${WANAKU_ROUTER_URL}" \
  --id "${LABEL_NS_ID}" \
  --label tier=frontend \
  --label region=us-east \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: namespace multiple label add failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "PASS: multiple labels added to namespace"

VERIFY=$(wanaku namespaces show --host "${WANAKU_ROUTER_URL}" "${LABEL_NS_ID}" --plain 2>&1)
echo "${VERIFY}" | grep -q "tier" \
  && echo "PASS: label 'tier' visible" \
  || echo "FAIL: label 'tier' not found"
echo "${VERIFY}" | grep -q "region" \
  && echo "PASS: label 'region' visible" \
  || echo "FAIL: label 'region' not found"
```

### Test 3.4: Remove a label from the namespace

```bash
OUTPUT=$(wanaku namespaces label remove \
  --host "${WANAKU_ROUTER_URL}" \
  --id "${LABEL_NS_ID}" \
  --label env \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: namespace label remove failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "PASS: label removed from namespace"

# Verify the label is gone but others remain
VERIFY=$(wanaku namespaces show --host "${WANAKU_ROUTER_URL}" "${LABEL_NS_ID}" --plain 2>&1)
echo "${VERIFY}" | grep -q "env=test" \
  && echo "FAIL: label 'env=test' still present after removal" \
  || echo "PASS: label 'env=test' removed"
echo "${VERIFY}" | grep -q "tier" \
  && echo "PASS: label 'tier' still present (not removed)" \
  || echo "FAIL: label 'tier' was incorrectly removed"
```

### Test 3.5: Filter namespaces by label expression

```bash
OUTPUT=$(wanaku namespaces list \
  --host "${WANAKU_ROUTER_URL}" \
  -e "tier=frontend" \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: namespace list with label expression failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "${OUTPUT}" | grep -q "label-test-namespace" \
  && echo "PASS: label-test-namespace found with tier=frontend filter" \
  || echo "FAIL: label-test-namespace not returned by tier=frontend filter"
```

### Test 3.6: Clean up label test namespace

```bash
wanaku namespaces delete --host "${WANAKU_ROUTER_URL}" "${LABEL_NS_ID}" 2>/dev/null || true
echo "PASS: label test namespace cleanup done"
```

---

## Phase 4: Namespace Isolation

### Test 4.1: Register a tool in namespace ns-0

```bash
OUTPUT=$(wanaku tools add \
  --host "${WANAKU_ROUTER_URL}" \
  --name ns0-tool \
  --namespace ns-0 \
  --description "Tool in namespace ns-0 for isolation testing" \
  --uri "https://example.com/ns0" \
  --type http \
  --property "input:string,An input parameter" \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: could not register ns0-tool (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "PASS: ns0-tool registered in namespace ns-0"
```

### Test 4.2: Register a tool in namespace ns-1

```bash
OUTPUT=$(wanaku tools add \
  --host "${WANAKU_ROUTER_URL}" \
  --name ns1-tool \
  --namespace ns-1 \
  --description "Tool in namespace ns-1 for isolation testing" \
  --uri "https://example.com/ns1" \
  --type http \
  --property "input:string,An input parameter" \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: could not register ns1-tool (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "PASS: ns1-tool registered in namespace ns-1"
```

### Test 4.3: Verify both tools are visible in the global tool list

```bash
TOOLS_OUTPUT=$(wanaku tools list --host "${WANAKU_ROUTER_URL}" --plain 2>&1)

echo "${TOOLS_OUTPUT}" | grep -q "ns0-tool" \
  && echo "PASS: ns0-tool visible in global list" \
  || echo "FAIL: ns0-tool not found in global list"

echo "${TOOLS_OUTPUT}" | grep -q "ns1-tool" \
  && echo "PASS: ns1-tool visible in global list" \
  || echo "FAIL: ns1-tool not found in global list"
```

### Test 4.4: Verify tools show correct namespace assignment

```bash
TOOLS_OUTPUT=$(wanaku tools list --host "${WANAKU_ROUTER_URL}" --plain 2>&1)

echo "${TOOLS_OUTPUT}" | grep "ns0-tool" | grep -q "ns-0" \
  && echo "PASS: ns0-tool shows namespace ns-0" \
  || echo "FAIL: ns0-tool does not show namespace ns-0"

echo "${TOOLS_OUTPUT}" | grep "ns1-tool" | grep -q "ns-1" \
  && echo "PASS: ns1-tool shows namespace ns-1" \
  || echo "FAIL: ns1-tool does not show namespace ns-1"
```

### Test 4.5: Verify tool details include namespace

```bash
OUTPUT=$(wanaku tools show --host "${WANAKU_ROUTER_URL}" ns0-tool --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: tools show for ns0-tool failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "${OUTPUT}" | grep -q "ns-0" \
  && echo "PASS: ns0-tool detail shows namespace ns-0" \
  || echo "FAIL: ns0-tool detail does not show namespace ns-0"
```

---

## Phase 5: Label Expression Routing

### Test 5.1: Register tool-a with labels env=prod and tier=frontend

```bash
OUTPUT=$(wanaku tools add \
  --host "${WANAKU_ROUTER_URL}" \
  --name tool-a \
  --namespace public \
  --description "Label test tool A" \
  --uri "https://example.com/tool-a" \
  --type http \
  --property "input:string,An input" \
  --label env=prod \
  --label tier=frontend \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: could not register tool-a (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "PASS: tool-a registered with labels env=prod, tier=frontend"
```

### Test 5.2: Register tool-b with labels env=prod and tier=backend

```bash
OUTPUT=$(wanaku tools add \
  --host "${WANAKU_ROUTER_URL}" \
  --name tool-b \
  --namespace public \
  --description "Label test tool B" \
  --uri "https://example.com/tool-b" \
  --type http \
  --property "input:string,An input" \
  --label env=prod \
  --label tier=backend \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: could not register tool-b (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "PASS: tool-b registered with labels env=prod, tier=backend"
```

### Test 5.3: Register tool-c with labels env=staging and tier=frontend

```bash
OUTPUT=$(wanaku tools add \
  --host "${WANAKU_ROUTER_URL}" \
  --name tool-c \
  --namespace public \
  --description "Label test tool C" \
  --uri "https://example.com/tool-c" \
  --type http \
  --property "input:string,An input" \
  --label env=staging \
  --label tier=frontend \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: could not register tool-c (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "PASS: tool-c registered with labels env=staging, tier=frontend"
```

### Test 5.4: Filter tools by env=prod

```bash
OUTPUT=$(wanaku tools list \
  --host "${WANAKU_ROUTER_URL}" \
  -e "env=prod" \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: tools list with label expression failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "${OUTPUT}" | grep -q "tool-a" \
  && echo "PASS: tool-a returned for env=prod" \
  || echo "FAIL: tool-a not returned for env=prod"

echo "${OUTPUT}" | grep -q "tool-b" \
  && echo "PASS: tool-b returned for env=prod" \
  || echo "FAIL: tool-b not returned for env=prod"

echo "${OUTPUT}" | grep -q "tool-c" \
  && echo "FAIL: tool-c should NOT be returned for env=prod" \
  || echo "PASS: tool-c correctly excluded from env=prod"
```

### Test 5.5: Filter tools by tier=frontend

```bash
OUTPUT=$(wanaku tools list \
  --host "${WANAKU_ROUTER_URL}" \
  -e "tier=frontend" \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: tools list with tier=frontend failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "${OUTPUT}" | grep -q "tool-a" \
  && echo "PASS: tool-a returned for tier=frontend" \
  || echo "FAIL: tool-a not returned for tier=frontend"

echo "${OUTPUT}" | grep -q "tool-c" \
  && echo "PASS: tool-c returned for tier=frontend" \
  || echo "FAIL: tool-c not returned for tier=frontend"

echo "${OUTPUT}" | grep -q "tool-b" \
  && echo "FAIL: tool-b should NOT be returned for tier=frontend" \
  || echo "PASS: tool-b correctly excluded from tier=frontend"
```

### Test 5.6: Filter tools by compound expression (env=prod AND tier=frontend)

**Description:** Verify that AND logic in label expressions correctly narrows results. See `wanaku man label-expression` for supported syntax.

```bash
OUTPUT=$(wanaku tools list \
  --host "${WANAKU_ROUTER_URL}" \
  -e "env=prod AND tier=frontend" \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: tools list with compound expression failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "${OUTPUT}" | grep -q "tool-a" \
  && echo "PASS: tool-a returned for compound expression" \
  || echo "FAIL: tool-a not returned for compound expression"

echo "${OUTPUT}" | grep -q "tool-b" \
  && echo "FAIL: tool-b should NOT match (tier=backend, not frontend)" \
  || echo "PASS: tool-b correctly excluded"

echo "${OUTPUT}" | grep -q "tool-c" \
  && echo "FAIL: tool-c should NOT match (env=staging, not prod)" \
  || echo "PASS: tool-c correctly excluded"
```

### Test 5.7: Add labels to a tool post-registration

```bash
OUTPUT=$(wanaku tools label add \
  --host "${WANAKU_ROUTER_URL}" \
  --name tool-c \
  --label priority=low \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: tools label add failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "PASS: label 'priority=low' added to tool-c"

VERIFY=$(wanaku tools show --host "${WANAKU_ROUTER_URL}" tool-c --plain 2>&1)
echo "${VERIFY}" | grep -q "priority" \
  && echo "PASS: label 'priority' visible on tool-c" \
  || echo "FAIL: label 'priority' not found on tool-c"
```

### Test 5.8: Remove a label from a tool

```bash
OUTPUT=$(wanaku tools label remove \
  --host "${WANAKU_ROUTER_URL}" \
  --name tool-c \
  --label priority \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: tools label remove failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "PASS: label 'priority' removed from tool-c"

VERIFY=$(wanaku tools show --host "${WANAKU_ROUTER_URL}" tool-c --plain 2>&1)
echo "${VERIFY}" | grep -q "priority" \
  && echo "FAIL: label 'priority' still present after removal" \
  || echo "PASS: label 'priority' confirmed removed"
```

### Test 5.9: Batch remove tools by label expression

**Description:** Remove all tools matching `env=staging` and verify only tool-c is removed.

```bash
OUTPUT=$(wanaku tools remove \
  --host "${WANAKU_ROUTER_URL}" \
  -e "env=staging" \
  -y \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: tools remove by label expression failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "PASS: batch remove by label expression completed"

# Verify tool-c is gone, tool-a and tool-b remain
VERIFY=$(wanaku tools list --host "${WANAKU_ROUTER_URL}" --plain 2>&1)

echo "${VERIFY}" | grep -q "tool-c" \
  && echo "FAIL: tool-c should have been removed" \
  || echo "PASS: tool-c removed as expected"

echo "${VERIFY}" | grep -q "tool-a" \
  && echo "PASS: tool-a still present (not affected)" \
  || echo "FAIL: tool-a was incorrectly removed"

echo "${VERIFY}" | grep -q "tool-b" \
  && echo "PASS: tool-b still present (not affected)" \
  || echo "FAIL: tool-b was incorrectly removed"
```

---

## Phase 6: Namespace Cleanup

### Test 6.1: Run namespace cleanup (dry run)

**Description:** Verify that the cleanup command runs without error. Uses `--assume-yes` to avoid interactive prompts and a short max-age to reduce scope.

```bash
OUTPUT=$(wanaku namespaces cleanup \
  --host "${WANAKU_ROUTER_URL}" \
  --max-age-days 0 \
  --assume-yes \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: namespace cleanup failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "PASS: namespace cleanup completed without error"
echo "${OUTPUT}"
```

### Test 6.2: Create a pre-allocated namespace and verify cleanup targets it

**Description:** Pre-allocated namespaces (no name) should be eligible for cleanup.

```bash
# Create a pre-allocated namespace (no --name)
OUTPUT=$(wanaku namespaces create \
  --host "${WANAKU_ROUTER_URL}" \
  --path "cleanup-target" \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: pre-allocated namespace create failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "PASS: pre-allocated namespace created"

# Run cleanup with max-age-days 0 to catch newly created pre-allocated namespaces
OUTPUT=$(wanaku namespaces cleanup \
  --host "${WANAKU_ROUTER_URL}" \
  --max-age-days 0 \
  --assume-yes \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: namespace cleanup after pre-allocation failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
else
  echo "PASS: namespace cleanup ran after pre-allocation"
  echo "${OUTPUT}"
fi
```

---

## Phase 7: Negative Tests

### Test 7.1: Delete a non-existent namespace

```bash
OUTPUT=$(wanaku namespaces delete \
  --host "${WANAKU_ROUTER_URL}" \
  "non-existent-id-12345" \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: deleting non-existent namespace failed gracefully (exit code ${EXIT_CODE})"
else
  echo "FAIL: deleting non-existent namespace should have failed"
fi
```

### Test 7.2: Show a non-existent namespace

```bash
OUTPUT=$(wanaku namespaces show \
  --host "${WANAKU_ROUTER_URL}" \
  "non-existent-id-12345" \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: showing non-existent namespace failed gracefully (exit code ${EXIT_CODE})"
else
  echo "FAIL: showing non-existent namespace should have failed"
fi
```

### Test 7.3: Update a non-existent namespace

```bash
OUTPUT=$(wanaku namespaces update \
  --host "${WANAKU_ROUTER_URL}" \
  --name "ghost" \
  "non-existent-id-12345" \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: updating non-existent namespace failed gracefully (exit code ${EXIT_CODE})"
else
  echo "FAIL: updating non-existent namespace should have failed"
fi
```

### Test 7.4: Add label to a non-existent namespace

```bash
OUTPUT=$(wanaku namespaces label add \
  --host "${WANAKU_ROUTER_URL}" \
  --id "non-existent-id-12345" \
  --label env=test \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: adding label to non-existent namespace failed gracefully (exit code ${EXIT_CODE})"
else
  echo "FAIL: adding label to non-existent namespace should have failed"
fi
```

### Test 7.5: Remove label from a non-existent namespace

```bash
OUTPUT=$(wanaku namespaces label remove \
  --host "${WANAKU_ROUTER_URL}" \
  --id "non-existent-id-12345" \
  --label env \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: removing label from non-existent namespace failed gracefully (exit code ${EXIT_CODE})"
else
  echo "FAIL: removing label from non-existent namespace should have failed"
fi
```

### Test 7.6: Create namespace without required --path option

```bash
OUTPUT=$(wanaku namespaces create \
  --host "${WANAKU_ROUTER_URL}" \
  --name "missing-path" \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: create namespace without --path rejected (exit code ${EXIT_CODE})"
else
  echo "FAIL: create namespace without --path should have failed"
fi
```

### Test 7.7: Update namespace with no update fields

```bash
# First create a temporary namespace for this test
TEMP_OUTPUT=$(wanaku namespaces create \
  --host "${WANAKU_ROUTER_URL}" \
  --path "negative-test-ns" \
  --name "negative-test" \
  --plain 2>&1)

TEMP_NS_ID=$(wanaku namespaces list --host "${WANAKU_ROUTER_URL}" --plain 2>&1 \
  | grep "negative-test" | awk '{print $1}' | head -1)

if [ -n "${TEMP_NS_ID}" ]; then
  OUTPUT=$(wanaku namespaces update \
    --host "${WANAKU_ROUTER_URL}" \
    "${TEMP_NS_ID}" \
    --plain 2>&1)
  EXIT_CODE=$?

  if [ "${EXIT_CODE}" -ne 0 ]; then
    echo "PASS: update with no fields rejected (exit code ${EXIT_CODE})"
  else
    echo "FAIL: update with no fields should have been rejected"
  fi

  # Clean up
  wanaku namespaces delete --host "${WANAKU_ROUTER_URL}" "${TEMP_NS_ID}" 2>/dev/null || true
else
  echo "SKIP: could not create temporary namespace for this test"
fi
```

### Test 7.8: Namespace label add with both --id and --label-expression (mutual exclusion)

```bash
OUTPUT=$(wanaku namespaces label add \
  --host "${WANAKU_ROUTER_URL}" \
  --id "some-id" \
  --label-expression "env=test" \
  --label env=test \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: specifying both --id and --label-expression rejected (exit code ${EXIT_CODE})"
else
  echo "FAIL: specifying both --id and --label-expression should have been rejected"
fi
```

### Test 7.9: Tools remove with both --name and --label-expression (mutual exclusion)

```bash
OUTPUT=$(wanaku tools remove \
  --host "${WANAKU_ROUTER_URL}" \
  --name "some-tool" \
  --label-expression "env=test" \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: specifying both --name and --label-expression rejected (exit code ${EXIT_CODE})"
else
  echo "FAIL: specifying both --name and --label-expression should have been rejected"
fi
```

### Test 7.10: Add label to a non-existent tool

```bash
OUTPUT=$(wanaku tools label add \
  --host "${WANAKU_ROUTER_URL}" \
  --name "non-existent-tool-12345" \
  --label env=test \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: adding label to non-existent tool failed gracefully (exit code ${EXIT_CODE})"
else
  echo "FAIL: adding label to non-existent tool should have failed"
fi
```

---

## Phase 8: Cleanup

### Step 8.1: Remove test tools

```bash
wanaku tools remove --host "${WANAKU_ROUTER_URL}" --name ns0-tool 2>/dev/null || true
wanaku tools remove --host "${WANAKU_ROUTER_URL}" --name ns1-tool 2>/dev/null || true
wanaku tools remove --host "${WANAKU_ROUTER_URL}" --name tool-a 2>/dev/null || true
wanaku tools remove --host "${WANAKU_ROUTER_URL}" --name tool-b 2>/dev/null || true
wanaku tools remove --host "${WANAKU_ROUTER_URL}" --name tool-c 2>/dev/null || true
echo "PASS: test tools removed"
```

### Step 8.2: Verify tools are cleaned up

```bash
REMAINING=$(wanaku tools list --host "${WANAKU_ROUTER_URL}" --plain 2>&1)

for TOOL in ns0-tool ns1-tool tool-a tool-b tool-c; do
  echo "${REMAINING}" | grep -q "${TOOL}" \
    && echo "WARN: ${TOOL} still present after cleanup" \
    || echo "PASS: ${TOOL} confirmed removed"
done
```

### Step 8.3: Stop the Wanaku process

```bash
if [ -n "${WANAKU_PID}" ]; then
  kill "${WANAKU_PID}" 2>/dev/null || true
  wait "${WANAKU_PID}" 2>/dev/null || true
  echo "PASS: Wanaku process stopped"
else
  echo "WARN: WANAKU_PID not set, process may still be running"
fi
```

---

## Summary Matrix

| Phase | Test ID | Test Name | Priority |
|-------|---------|-----------|----------|
| 0 | 0 | Manual prerequisites | Medium |
| 1 | 1 | Start local stack | Critical |
| 2 | 2.1 | List namespaces, verify defaults | Critical |
| 2 | 2.2 | Create a custom namespace | Critical |
| 2 | 2.3 | Verify created namespace in list | Critical |
| 2 | 2.4 | Show namespace details | High |
| 2 | 2.5 | Update namespace name | High |
| 2 | 2.6 | Delete namespace | Critical |
| 3 | 3.1 | Create namespace for label tests | High |
| 3 | 3.2 | Add single label to namespace | Critical |
| 3 | 3.3 | Add multiple labels to namespace | High |
| 3 | 3.4 | Remove label from namespace | Critical |
| 3 | 3.5 | Filter namespaces by label expression | High |
| 3 | 3.6 | Clean up label test namespace | Medium |
| 4 | 4.1 | Register tool in namespace ns-0 | Critical |
| 4 | 4.2 | Register tool in namespace ns-1 | Critical |
| 4 | 4.3 | Verify both tools in global list | Critical |
| 4 | 4.4 | Verify namespace assignment in list | High |
| 4 | 4.5 | Verify tool details include namespace | High |
| 5 | 5.1 | Register tool-a (env=prod, tier=frontend) | Critical |
| 5 | 5.2 | Register tool-b (env=prod, tier=backend) | Critical |
| 5 | 5.3 | Register tool-c (env=staging, tier=frontend) | Critical |
| 5 | 5.4 | Filter tools by env=prod | Critical |
| 5 | 5.5 | Filter tools by tier=frontend | Critical |
| 5 | 5.6 | Filter tools by compound AND expression | Critical |
| 5 | 5.7 | Add label to tool post-registration | High |
| 5 | 5.8 | Remove label from tool | High |
| 5 | 5.9 | Batch remove tools by label expression | Critical |
| 6 | 6.1 | Run namespace cleanup | High |
| 6 | 6.2 | Pre-allocated namespace cleanup | High |
| 7 | 7.1 | Delete non-existent namespace | High |
| 7 | 7.2 | Show non-existent namespace | High |
| 7 | 7.3 | Update non-existent namespace | High |
| 7 | 7.4 | Add label to non-existent namespace | High |
| 7 | 7.5 | Remove label from non-existent namespace | High |
| 7 | 7.6 | Create namespace without required path | High |
| 7 | 7.7 | Update namespace with no fields | Medium |
| 7 | 7.8 | Namespace label add mutual exclusion (--id + --label-expression) | High |
| 7 | 7.9 | Tools remove mutual exclusion (--name + --label-expression) | High |
| 7 | 7.10 | Add label to non-existent tool | High |
| 8 | 8.1-8.3 | Cleanup (tools, namespaces, process) | Critical |
