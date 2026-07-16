# Test Plan: Wanaku CLI Forwards and Data Store Commands

## Overview

This test plan verifies the `wanaku forwards` and `wanaku data-store` CLI commands against a locally running Wanaku instance (no authentication). It covers the full CRUD lifecycle for forward targets and data store entries, including label management and negative tests.

Every step except the initial build is fully automatable.

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
java -jar ${CLI_JAR} forwards list --host ...
```

Do **not** assign the full command to a single variable (e.g., `WANAKU_CLI="java -jar path/to/jar"`) -- zsh treats it as a single token. Use `CLI_JAR` for the path and call `java -jar ${CLI_JAR}` explicitly.

### Environment variables

```bash
export WANAKU_REPO_ROOT="${WANAKU_REPO_ROOT:-.}"
export WANAKU_ROUTER_URL="${WANAKU_ROUTER_URL:-http://localhost:8080}"
```

---

## Phase 0: Manual Prerequisites

Verify all required tools are available:

```bash
wanaku --version && echo "PASS: wanaku CLI available" || echo "FAIL: wanaku CLI not found"
jq --version && echo "PASS: jq available" || echo "FAIL: jq not found"
java -version 2>&1 | head -1 && echo "PASS: java available" || echo "FAIL: java not found"
```

---

## Phase 1: Start Wanaku Locally

Follow [common/start-local.md](common/start-local.md). After completion, `WANAKU_ROUTER_URL`, `WANAKU_PID`, and `CLI_JAR` must be set.

Verify the CLI can connect:

```bash
wanaku forwards list --host "${WANAKU_ROUTER_URL}" --plain
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: CLI can connect to router"
else
  echo "FAIL: CLI cannot connect to router (exit code ${EXIT_CODE})"
  exit 1
fi
```

---

## Phase 2: Forward CRUD

### Test 2.1: Add a forward target

```bash
wanaku forwards add \
  --host "${WANAKU_ROUTER_URL}" \
  --name test-forward \
  --service "http://localhost:9999/mcp" \
  --namespace public
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: forward 'test-forward' added"
else
  echo "FAIL: could not add forward (exit code ${EXIT_CODE})"
fi
```

### Test 2.2: Verify forward appears in list

```bash
OUTPUT=$(wanaku forwards list --host "${WANAKU_ROUTER_URL}" --plain 2>&1)
EXIT_CODE=$?
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: forwards list failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
elif echo "${OUTPUT}" | grep -q "test-forward"; then
  echo "PASS: 'test-forward' appears in forwards list"
else
  echo "FAIL: 'test-forward' not found in forwards list"
  echo "${OUTPUT}"
fi
```

### Test 2.3: Remove the forward target

```bash
wanaku forwards remove \
  --host "${WANAKU_ROUTER_URL}" \
  --name test-forward
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: forward 'test-forward' removed"
else
  echo "FAIL: could not remove forward (exit code ${EXIT_CODE})"
fi
```

### Test 2.5: Add a forward using --namespace (name resolution)

This verifies the `--namespace` flag resolves the namespace name to its ID before registering the forward.

```bash
wanaku forwards add \
  --host "${WANAKU_ROUTER_URL}" \
  --name ns-name-test-forward \
  --service "http://localhost:9999/mcp" \
  --namespace public
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: forward 'ns-name-test-forward' added using --namespace"
else
  echo "FAIL: could not add forward with --namespace (exit code ${EXIT_CODE})"
fi

# Clean up
wanaku forwards remove \
  --host "${WANAKU_ROUTER_URL}" \
  --name ns-name-test-forward 2>/dev/null || true
```

### Test 2.6: Verify --namespace with non-existent namespace name fails

```bash
OUTPUT=$(wanaku forwards add \
  --host "${WANAKU_ROUTER_URL}" \
  --name ns-name-invalid-test \
  --service "http://localhost:9999/mcp" \
  --namespace non-existent-namespace 2>&1)
EXIT_CODE=$?
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: add forward with non-existent --namespace rejected (exit code ${EXIT_CODE})"
else
  echo "FAIL: add forward with non-existent --namespace should fail"
  wanaku forwards remove --host "${WANAKU_ROUTER_URL}" --name ns-name-invalid-test 2>/dev/null || true
fi
```

---

## Phase 3: Forward Refresh

### Test 3.1: Re-add a forward target for refresh testing

```bash
wanaku forwards add \
  --host "${WANAKU_ROUTER_URL}" \
  --name test-forward-refresh \
  --service "http://localhost:9999/mcp" \
  --namespace public
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: forward 'test-forward-refresh' added"
else
  echo "FAIL: could not add forward (exit code ${EXIT_CODE})"
fi
```

### Test 3.2: Refresh the forward

The target service may not be reachable -- that is acceptable. The command should still exit without crashing.

```bash
wanaku forwards refresh \
  --host "${WANAKU_ROUTER_URL}" \
  --name test-forward-refresh 2>&1
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: forwards refresh completed successfully"
else
  echo "WARN: forwards refresh exited with code ${EXIT_CODE} (target may be unreachable, but command should not crash)"
fi
```

### Test 3.3: Verify the forward still exists after refresh

```bash
OUTPUT=$(wanaku forwards list --host "${WANAKU_ROUTER_URL}" --plain 2>&1)
if echo "${OUTPUT}" | grep -q "test-forward-refresh"; then
  echo "PASS: forward still present after refresh"
else
  echo "FAIL: forward disappeared after refresh"
fi
```

### Test 3.4: Clean up refresh test forward

```bash
wanaku forwards remove \
  --host "${WANAKU_ROUTER_URL}" \
  --name test-forward-refresh 2>/dev/null || true
echo "PASS: refresh test forward cleaned up"
```

---

## Phase 4: Data Store -- Add from File

### Test 4.1: Create a test YAML file

```bash
cat > /tmp/test-datastore.yaml << 'YAMLEOF'
name: test-config
version: 1
settings:
  key1: value1
  key2: value2
YAMLEOF

if [ -f /tmp/test-datastore.yaml ]; then
  echo "PASS: test YAML file created"
else
  echo "FAIL: could not create test YAML file"
fi
```

### Test 4.2: Add data store from file

```bash
wanaku data-store add \
  --host "${WANAKU_ROUTER_URL}" \
  --read-from-file /tmp/test-datastore.yaml \
  --name test-datastore
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: data store 'test-datastore' added from file"
else
  echo "FAIL: could not add data store (exit code ${EXIT_CODE})"
fi
```

---

## Phase 5: Data Store -- List and Get

### Test 5.1: List data stores and verify entry appears

```bash
OUTPUT=$(wanaku data-store list --host "${WANAKU_ROUTER_URL}" --plain 2>&1)
EXIT_CODE=$?
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: data-store list failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
elif echo "${OUTPUT}" | grep -q "test-datastore"; then
  echo "PASS: 'test-datastore' appears in data store list"
else
  echo "FAIL: 'test-datastore' not found in data store list"
  echo "${OUTPUT}"
fi
```

### Test 5.2: Get data store by name and verify content

The `data-store get` command writes the decoded content to a file. Verify the output matches the original.

```bash
wanaku data-store get \
  --host "${WANAKU_ROUTER_URL}" \
  --name test-datastore \
  --output-file /tmp/test-datastore-retrieved.yaml
EXIT_CODE=$?
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: data-store get failed (exit code ${EXIT_CODE})"
elif diff /tmp/test-datastore.yaml /tmp/test-datastore-retrieved.yaml > /dev/null 2>&1; then
  echo "PASS: retrieved content matches original file"
else
  echo "FAIL: retrieved content does not match original"
fi
```

---

## Phase 6: Data Store -- Labels

### Test 6.1: Extract the data store ID

Label commands require `--id`. Extract it from the list output.

```bash
DS_ID=$(wanaku data-store list --host "${WANAKU_ROUTER_URL}" --plain 2>&1 | grep "test-datastore" | awk '{print $1}')
if [ -n "${DS_ID}" ]; then
  echo "PASS: extracted data store ID: ${DS_ID}"
else
  echo "FAIL: could not extract data store ID from list output"
fi
```

### Test 6.2: Add labels to the data store

```bash
wanaku data-store label add \
  --host "${WANAKU_ROUTER_URL}" \
  --id "${DS_ID}" \
  --label env=test \
  --label type=yaml
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: labels added to data store"
else
  echo "FAIL: could not add labels (exit code ${EXIT_CODE})"
fi
```

### Test 6.3: Verify labels appear in list output

```bash
OUTPUT=$(wanaku data-store list --host "${WANAKU_ROUTER_URL}" --plain 2>&1)
if echo "${OUTPUT}" | grep "test-datastore" | grep -q "env"; then
  echo "PASS: label 'env' visible in list output"
else
  echo "FAIL: label 'env' not visible in list output"
fi

if echo "${OUTPUT}" | grep "test-datastore" | grep -q "type"; then
  echo "PASS: label 'type' visible in list output"
else
  echo "FAIL: label 'type' not visible in list output"
fi
```

### Test 6.4: Remove a label from the data store

```bash
wanaku data-store label remove \
  --host "${WANAKU_ROUTER_URL}" \
  --id "${DS_ID}" \
  --label type
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: label 'type' removed"
else
  echo "FAIL: could not remove label (exit code ${EXIT_CODE})"
fi
```

### Test 6.5: Verify label was removed

```bash
OUTPUT=$(wanaku data-store list --host "${WANAKU_ROUTER_URL}" --plain 2>&1)
if echo "${OUTPUT}" | grep "test-datastore" | grep -q "type=yaml"; then
  echo "FAIL: label 'type=yaml' still present after removal"
else
  echo "PASS: label 'type' no longer present"
fi
```

---

## Phase 7: Data Store -- Remove

### Test 7.1: Remove the data store by name

```bash
wanaku data-store remove \
  --host "${WANAKU_ROUTER_URL}" \
  --name test-datastore
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: data store 'test-datastore' removed"
else
  echo "FAIL: could not remove data store (exit code ${EXIT_CODE})"
fi
```

### Test 7.2: Verify data store is gone from list

```bash
OUTPUT=$(wanaku data-store list --host "${WANAKU_ROUTER_URL}" --plain 2>&1)
if echo "${OUTPUT}" | grep -q "test-datastore"; then
  echo "FAIL: 'test-datastore' still appears after removal"
else
  echo "PASS: 'test-datastore' no longer in data store list"
fi
```

---

## Phase 8: Multiple Data Stores with Label Expression

### Test 8.1: Add three data stores with a shared label

```bash
for i in 1 2 3; do
  cat > /tmp/test-batch-${i}.yaml << EOF
batch_item: ${i}
EOF

  wanaku data-store add \
    --host "${WANAKU_ROUTER_URL}" \
    --read-from-file /tmp/test-batch-${i}.yaml \
    --name "batch-ds-${i}"
  EXIT_CODE=$?
  if [ "${EXIT_CODE}" -eq 0 ]; then
    echo "PASS: batch data store 'batch-ds-${i}' added"
  else
    echo "FAIL: could not add 'batch-ds-${i}' (exit code ${EXIT_CODE})"
  fi
done
```

### Test 8.2: Add the `batch=true` label to all three using label expression

First add a `batch=true` label to each data store individually (since we need the IDs):

```bash
for i in 1 2 3; do
  DS_ID=$(wanaku data-store list --host "${WANAKU_ROUTER_URL}" --plain 2>&1 | grep "batch-ds-${i}" | awk '{print $1}')
  if [ -z "${DS_ID}" ]; then
    echo "FAIL: could not find ID for batch-ds-${i}"
    continue
  fi

  wanaku data-store label add \
    --host "${WANAKU_ROUTER_URL}" \
    --id "${DS_ID}" \
    --label batch=true
  EXIT_CODE=$?
  if [ "${EXIT_CODE}" -eq 0 ]; then
    echo "PASS: label 'batch=true' added to batch-ds-${i}"
  else
    echo "FAIL: could not add label to batch-ds-${i} (exit code ${EXIT_CODE})"
  fi
done
```

### Test 8.3: List data stores filtered by label expression

```bash
OUTPUT=$(wanaku data-store list --host "${WANAKU_ROUTER_URL}" -e "batch=true" --plain 2>&1)
EXIT_CODE=$?
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: data-store list with expression failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
else
  COUNT=$(echo "${OUTPUT}" | grep -c "batch-ds-")
  if [ "${COUNT}" -eq 3 ]; then
    echo "PASS: all 3 batch data stores returned by label expression"
  else
    echo "FAIL: expected 3 batch data stores, found ${COUNT}"
    echo "${OUTPUT}"
  fi
fi
```

### Test 8.4: Remove all batch data stores individually

Note: `data-store remove` does not support `--label-expression`. Remove each entry by name.

```bash
for i in 1 2 3; do
  wanaku data-store remove \
    --host "${WANAKU_ROUTER_URL}" \
    --name "batch-ds-${i}" 2>/dev/null || true
done

OUTPUT=$(wanaku data-store list --host "${WANAKU_ROUTER_URL}" --plain 2>&1)
if echo "${OUTPUT}" | grep -q "batch-ds-"; then
  echo "FAIL: some batch data stores still present after removal"
  echo "${OUTPUT}"
else
  echo "PASS: all batch data stores removed"
fi
```

---

## Phase 9: Negative Tests

### Test 9.1: Add forward with no name should fail

```bash
wanaku forwards add \
  --host "${WANAKU_ROUTER_URL}" \
  --service "http://localhost:9999/mcp" \
  --namespace public 2>&1
EXIT_CODE=$?
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: forwards add without --name rejected (exit code ${EXIT_CODE})"
else
  echo "FAIL: forwards add without --name should fail"
fi
```

### Test 9.2: Add forward with no service URL should fail

```bash
wanaku forwards add \
  --host "${WANAKU_ROUTER_URL}" \
  --name negative-test-forward \
  --namespace public 2>&1
EXIT_CODE=$?
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: forwards add without --service rejected (exit code ${EXIT_CODE})"
else
  echo "FAIL: forwards add without --service should fail"
  # Clean up in case it was created
  wanaku forwards remove --host "${WANAKU_ROUTER_URL}" --name negative-test-forward 2>/dev/null || true
fi
```

### Test 9.3: Add forward with no namespace identifier should fail

```bash
wanaku forwards add \
  --host "${WANAKU_ROUTER_URL}" \
  --name negative-test-forward \
  --service "http://localhost:9999/mcp" 2>&1
EXIT_CODE=$?
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: forwards add without --namespace or --namespace-id rejected (exit code ${EXIT_CODE})"
else
  echo "FAIL: forwards add without a namespace identifier should fail"
  wanaku forwards remove --host "${WANAKU_ROUTER_URL}" --name negative-test-forward 2>/dev/null || true
fi
```

### Test 9.4: Remove non-existent forward should fail gracefully

```bash
OUTPUT=$(wanaku forwards remove \
  --host "${WANAKU_ROUTER_URL}" \
  --name "does-not-exist-12345" 2>&1)
EXIT_CODE=$?
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: removing non-existent forward failed gracefully (exit code ${EXIT_CODE})"
else
  echo "FAIL: removing non-existent forward should fail"
fi
```

### Test 9.5: Add data store with non-existent file should fail

```bash
wanaku data-store add \
  --host "${WANAKU_ROUTER_URL}" \
  --read-from-file /tmp/this-file-does-not-exist-12345.yaml \
  --name negative-ds 2>&1
EXIT_CODE=$?
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: data-store add with non-existent file rejected (exit code ${EXIT_CODE})"
else
  echo "FAIL: data-store add with non-existent file should fail"
  wanaku data-store remove --host "${WANAKU_ROUTER_URL}" --name negative-ds 2>/dev/null || true
fi
```

### Test 9.6: Get non-existent data store should fail

```bash
wanaku data-store get \
  --host "${WANAKU_ROUTER_URL}" \
  --name "does-not-exist-12345" \
  --output-file /tmp/negative-ds-output.yaml 2>&1
EXIT_CODE=$?
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: data-store get for non-existent entry failed (exit code ${EXIT_CODE})"
else
  echo "FAIL: data-store get for non-existent entry should fail"
fi
rm -f /tmp/negative-ds-output.yaml 2>/dev/null || true
```

### Test 9.7: Data store get without --id or --name should fail

```bash
wanaku data-store get \
  --host "${WANAKU_ROUTER_URL}" \
  --output-file /tmp/negative-ds-output.yaml 2>&1
EXIT_CODE=$?
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: data-store get without --id or --name rejected (exit code ${EXIT_CODE})"
else
  echo "FAIL: data-store get without --id or --name should fail"
fi
rm -f /tmp/negative-ds-output.yaml 2>/dev/null || true
```

### Test 9.8: Data store remove without --id or --name should fail

```bash
wanaku data-store remove \
  --host "${WANAKU_ROUTER_URL}" 2>&1
EXIT_CODE=$?
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: data-store remove without --id or --name rejected (exit code ${EXIT_CODE})"
else
  echo "FAIL: data-store remove without --id or --name should fail"
fi
```

### Test 9.9: Refresh non-existent forward should fail gracefully

```bash
OUTPUT=$(wanaku forwards refresh \
  --host "${WANAKU_ROUTER_URL}" \
  --name "does-not-exist-12345" 2>&1)
EXIT_CODE=$?
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: refreshing non-existent forward failed gracefully (exit code ${EXIT_CODE})"
else
  echo "FAIL: refreshing non-existent forward should fail"
fi
```

---

## Phase 10: Cleanup

### Step 10.1: Remove all test artifacts

```bash
wanaku forwards remove --host "${WANAKU_ROUTER_URL}" --name test-forward 2>/dev/null || true
wanaku forwards remove --host "${WANAKU_ROUTER_URL}" --name test-forward-refresh 2>/dev/null || true
wanaku forwards remove --host "${WANAKU_ROUTER_URL}" --name negative-test-forward 2>/dev/null || true

wanaku data-store remove --host "${WANAKU_ROUTER_URL}" --name test-datastore 2>/dev/null || true
for i in 1 2 3; do
  wanaku data-store remove --host "${WANAKU_ROUTER_URL}" --name "batch-ds-${i}" 2>/dev/null || true
done

echo "PASS: test artifacts cleaned up"
```

### Step 10.2: Clean up temporary files

```bash
rm -f /tmp/test-datastore.yaml 2>/dev/null || true
rm -f /tmp/test-datastore-retrieved.yaml 2>/dev/null || true
rm -f /tmp/test-batch-1.yaml /tmp/test-batch-2.yaml /tmp/test-batch-3.yaml 2>/dev/null || true
rm -f /tmp/negative-ds-output.yaml 2>/dev/null || true
echo "PASS: temporary files cleaned up"
```

### Step 10.3: Stop the local stack

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
| 0 | 0.1 | Prerequisites check | High |
| 1 | 1.1 | Start local stack and verify connectivity | Critical |
| 2 | 2.1-2.6 | Forward add, list, remove, verify gone, namespace resolution | Critical |
| 3 | 3.1-3.4 | Forward refresh lifecycle | High |
| 4 | 4.1-4.2 | Data store add from file | Critical |
| 5 | 5.1-5.2 | Data store list and get with content verification | Critical |
| 6 | 6.1-6.5 | Data store label add, verify, remove, verify | High |
| 7 | 7.1-7.2 | Data store remove and verify gone | Critical |
| 8 | 8.1-8.4 | Multiple data stores with label expression filtering | High |
| 9 | 9.1-9.9 | Negative tests (missing args, non-existent entries) | High |
| 10 | 10.1-10.3 | Cleanup (artifacts, temp files, process) | Critical |
