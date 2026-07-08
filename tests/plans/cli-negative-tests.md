# Test Plan: CLI Negative / Error Path Tests

## Overview

This test plan exercises negative and error paths across all major Wanaku CLI command groups against a locally running instance (no auth). Every step verifies that the command **fails with a non-zero exit code** and **fails gracefully** (no stack trace, meaningful error message).

All steps are idempotent, POSIX-compatible, and fully automatable.

## Prerequisites

### Required tools

| Tool | Minimum version | Verify command |
|------|-----------------|----------------|
| `wanaku` | 0.2.0+ | `wanaku --version` |
| `jq` | 1.6+ | `jq --version` |
| `java` | 21+ | `java -version` |
| `mvn` | 3.9+ | `mvn -version` |

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
export WANAKU_UNREACHABLE_HOST="${WANAKU_UNREACHABLE_HOST:-http://localhost:59999}"
```

### Helper: assert failure

Reusable function to verify a command fails gracefully. Checks non-zero exit code and absence of Java stack traces.

```bash
assert_failure() {
  local TEST_ID="$1"
  local DESCRIPTION="$2"
  shift 2
  OUTPUT=$("$@" 2>&1)
  EXIT_CODE=$?

  if [ "${EXIT_CODE}" -ne 0 ]; then
    echo "PASS [${TEST_ID}]: ${DESCRIPTION} (exit code ${EXIT_CODE})"
  else
    echo "FAIL [${TEST_ID}]: expected non-zero exit code, got 0"
    echo "  Output: ${OUTPUT}"
    return 1
  fi

  # Verify no Java stack trace leaked to the user
  if echo "${OUTPUT}" | grep -q "at .*(.*\.java:"; then
    echo "FAIL [${TEST_ID}]: stack trace detected in output"
    echo "  Output: ${OUTPUT}"
    return 1
  fi

  return 0
}
```

### Helper: assert graceful failure (may succeed)

Reusable function for "remove non-existent" cases where the CLI may exit 0 with a warning or exit non-zero. Either is acceptable as long as no stack trace appears.

```bash
assert_graceful() {
  local TEST_ID="$1"
  local DESCRIPTION="$2"
  shift 2
  OUTPUT=$("$@" 2>&1)
  EXIT_CODE=$?

  # Check no stack trace
  if echo "${OUTPUT}" | grep -q "at .*(.*\.java:"; then
    echo "FAIL [${TEST_ID}]: stack trace detected in output"
    echo "  Output: ${OUTPUT}"
    return 1
  fi

  echo "PASS [${TEST_ID}]: ${DESCRIPTION} (exit code ${EXIT_CODE})"
  return 0
}
```

---

## Phase 0: Prerequisites Verification

### Test 0.1: Verify required tools

```bash
for CMD in wanaku jq java mvn; do
  if command -v "${CMD}" > /dev/null 2>&1; then
    echo "PASS [0.1]: ${CMD} is available"
  else
    echo "FAIL [0.1]: ${CMD} not found in PATH"
  fi
done
```

### Test 0.2: Verify environment variables

```bash
for VAR_NAME in WANAKU_ROUTER_URL WANAKU_UNREACHABLE_HOST; do
  eval "VAL=\${${VAR_NAME}}"
  if [ -z "${VAL}" ]; then
    echo "FAIL [0.2]: ${VAR_NAME} is not set"
  else
    echo "PASS [0.2]: ${VAR_NAME}=${VAL}"
  fi
done
```

---

## Phase 1: Setup

Follow [common/start-local.md](common/start-local.md) to build and start a local Wanaku stack.

After completion, `WANAKU_ROUTER_URL`, `WANAKU_PID`, and `CLI_JAR` must be set.

Verify router health (curl is used here specifically to test HTTP-level readiness):

```bash
curl -sf "${WANAKU_ROUTER_URL}/q/health/ready" > /dev/null && echo "PASS [1.0]: router is healthy" || echo "FAIL [1.0]: router not healthy"
```

---

## Phase 2: Tools -- Error Cases

### Test 2.1: Add tool with no name

The `--name` option is required by `tools add`.

```bash
assert_failure "2.1" "tools add with no name rejected" \
  wanaku tools add \
    --host "${WANAKU_ROUTER_URL}" \
    --no-auth \
    --namespace public \
    --description "test tool" \
    --uri "http://example.com" \
    --type http
```

### Test 2.2: Add tool with no URI

The `--uri` option is required by `tools add`.

```bash
assert_failure "2.2" "tools add with no URI rejected" \
  wanaku tools add \
    --host "${WANAKU_ROUTER_URL}" \
    --no-auth \
    --name "neg-test-no-uri" \
    --namespace public \
    --description "test tool" \
    --type http
```

### Test 2.3: Add tool with no type

The `--type` option is required by `tools add`.

```bash
assert_failure "2.3" "tools add with no type rejected" \
  wanaku tools add \
    --host "${WANAKU_ROUTER_URL}" \
    --no-auth \
    --name "neg-test-no-type" \
    --namespace public \
    --description "test tool" \
    --uri "http://example.com"
```

### Test 2.4: Add tool with invalid type

A type that has no registered downstream service should be rejected.

```bash
assert_failure "2.4" "tools add with invalid type rejected" \
  wanaku tools add \
    --host "${WANAKU_ROUTER_URL}" \
    --no-auth \
    --name "neg-test-bad-type" \
    --namespace public \
    --description "test tool" \
    --uri "http://example.com" \
    --type "nonexistent-type-12345"
```

### Test 2.5: Add duplicate tool name

First register a tool, then try to add another with the same name.

```bash
# Setup: register a tool
wanaku tools add \
  --host "${WANAKU_ROUTER_URL}" \
  --no-auth \
  --name "neg-test-duplicate" \
  --namespace public \
  --description "first tool" \
  --uri "http://example.com/first" \
  --type http 2>&1
SETUP_EXIT=$?

if [ "${SETUP_EXIT}" -ne 0 ]; then
  echo "SKIP [2.5]: could not register setup tool (no http capability?)"
else
  # Attempt to add duplicate
  OUTPUT=$(wanaku tools add \
    --host "${WANAKU_ROUTER_URL}" \
    --no-auth \
    --name "neg-test-duplicate" \
    --namespace public \
    --description "second tool" \
    --uri "http://example.com/second" \
    --type http 2>&1)
  EXIT_CODE=$?

  if [ "${EXIT_CODE}" -ne 0 ]; then
    echo "PASS [2.5]: duplicate tool name rejected (exit code ${EXIT_CODE})"
  else
    echo "WARN [2.5]: duplicate tool was accepted -- server may allow overwrite"
  fi

  # Cleanup
  wanaku tools remove --host "${WANAKU_ROUTER_URL}" --no-auth --name "neg-test-duplicate" 2>/dev/null || true
fi
```

### Test 2.6: Show non-existent tool

The `tools show` command takes the tool name as a positional parameter.

```bash
assert_failure "2.6" "show non-existent tool rejected" \
  wanaku tools show \
    --host "${WANAKU_ROUTER_URL}" \
    --no-auth \
    "nonexistent-tool-12345"
```

### Test 2.7: Edit non-existent tool

The `tools edit` command takes the tool name as a positional parameter.

```bash
assert_failure "2.7" "edit non-existent tool rejected" \
  wanaku tools edit \
    --host "${WANAKU_ROUTER_URL}" \
    --no-auth \
    "nonexistent-tool-12345"
```

### Test 2.8: Remove non-existent tool

```bash
assert_graceful "2.8" "remove non-existent tool handled gracefully" \
  wanaku tools remove \
    --host "${WANAKU_ROUTER_URL}" \
    --no-auth \
    --name "nonexistent-tool-12345"
```

### Test 2.9: Label add on non-existent tool

The `tools label add` command requires `--name` and `--label`.

```bash
assert_failure "2.9" "label add on non-existent tool rejected" \
  wanaku tools label add \
    --host "${WANAKU_ROUTER_URL}" \
    --no-auth \
    --name "nonexistent-tool-12345" \
    --label "env=test"
```

### Test 2.10: Generate from non-existent OpenAPI file

The `tools generate` command takes the spec location as a positional parameter.

```bash
assert_failure "2.10" "generate from non-existent file rejected" \
  wanaku tools generate /tmp/nonexistent-openapi-spec-12345.yaml
```

---

## Phase 3: Resources -- Error Cases

### Test 3.1: Expose resource with no name

The `--name` option is required by `resources expose`.

```bash
assert_failure "3.1" "resources expose with no name rejected" \
  wanaku resources expose \
    --host "${WANAKU_ROUTER_URL}" \
    --no-auth \
    --namespace public \
    --description "test resource" \
    --location "http://example.com/data" \
    --type http
```

### Test 3.2: Expose resource with no location

The `--location` option is required by `resources expose`.

```bash
assert_failure "3.2" "resources expose with no location rejected" \
  wanaku resources expose \
    --host "${WANAKU_ROUTER_URL}" \
    --no-auth \
    --name "neg-test-no-location" \
    --namespace public \
    --description "test resource" \
    --type http
```

### Test 3.3: Expose resource with no type

The `--type` option is required by `resources expose`.

```bash
assert_failure "3.3" "resources expose with no type rejected" \
  wanaku resources expose \
    --host "${WANAKU_ROUTER_URL}" \
    --no-auth \
    --name "neg-test-no-type" \
    --namespace public \
    --description "test resource" \
    --location "http://example.com/data"
```

### Test 3.4: Expose resource with duplicate name

First register a resource, then try to add another with the same name.

```bash
# Setup: register a resource (type must match a running provider)
wanaku resources expose \
  --host "${WANAKU_ROUTER_URL}" \
  --no-auth \
  --name "neg-test-dup-resource" \
  --namespace public \
  --description "first resource" \
  --location "http://example.com/data" \
  --type http 2>&1
SETUP_EXIT=$?

if [ "${SETUP_EXIT}" -ne 0 ]; then
  echo "SKIP [3.4]: could not register setup resource (no matching provider?)"
else
  OUTPUT=$(wanaku resources expose \
    --host "${WANAKU_ROUTER_URL}" \
    --no-auth \
    --name "neg-test-dup-resource" \
    --namespace public \
    --description "second resource" \
    --location "http://example.com/other" \
    --type http 2>&1)
  EXIT_CODE=$?

  if [ "${EXIT_CODE}" -ne 0 ]; then
    echo "PASS [3.4]: duplicate resource name rejected (exit code ${EXIT_CODE})"
  else
    echo "WARN [3.4]: duplicate resource was accepted -- server may allow overwrite"
  fi

  # Cleanup
  wanaku resources remove --host "${WANAKU_ROUTER_URL}" --no-auth --name "neg-test-dup-resource" 2>/dev/null || true
fi
```

### Test 3.5: Show non-existent resource

The `resources show` command takes the resource name as a positional parameter.

```bash
assert_failure "3.5" "show non-existent resource rejected" \
  wanaku resources show \
    --host "${WANAKU_ROUTER_URL}" \
    --no-auth \
    "nonexistent-resource-12345"
```

### Test 3.6: Remove non-existent resource

```bash
assert_graceful "3.6" "remove non-existent resource handled gracefully" \
  wanaku resources remove \
    --host "${WANAKU_ROUTER_URL}" \
    --no-auth \
    --name "nonexistent-resource-12345"
```

---

## Phase 4: Prompts -- Error Cases

### Test 4.1: Add prompt with no name

The `--name` option is required by `prompts add`.

```bash
assert_failure "4.1" "prompts add with no name rejected" \
  wanaku prompts add \
    --host "${WANAKU_ROUTER_URL}" \
    --no-auth \
    --namespace public \
    --description "test prompt" \
    --message "user:text:Hello"
```

### Test 4.2: Add prompt with no description

The `--description` option is required by `prompts add`.

```bash
assert_failure "4.2" "prompts add with no description rejected" \
  wanaku prompts add \
    --host "${WANAKU_ROUTER_URL}" \
    --no-auth \
    --name "neg-test-no-desc" \
    --namespace public \
    --message "user:text:Hello"
```

### Test 4.3: Edit non-existent prompt

The `--name` option is required by `prompts edit` and the prompt must exist.

```bash
assert_failure "4.3" "edit non-existent prompt rejected" \
  wanaku prompts edit \
    --host "${WANAKU_ROUTER_URL}" \
    --no-auth \
    --name "nonexistent-prompt-12345" \
    --description "updated description"
```

### Test 4.4: Remove non-existent prompt

```bash
assert_graceful "4.4" "remove non-existent prompt handled gracefully" \
  wanaku prompts remove \
    --host "${WANAKU_ROUTER_URL}" \
    --no-auth \
    --name "nonexistent-prompt-12345"
```

---

## Phase 5: Forwards -- Error Cases

### Test 5.1: Add forward with no name

The `--name` option is required by `forwards add`.

```bash
assert_failure "5.1" "forwards add with no name rejected" \
  wanaku forwards add \
    --host "${WANAKU_ROUTER_URL}" \
    --no-auth \
    --service "http://example.com:8080"
```

### Test 5.2: Add forward with no service

The `--service` option is required by `forwards add`.

```bash
assert_failure "5.2" "forwards add with no service rejected" \
  wanaku forwards add \
    --host "${WANAKU_ROUTER_URL}" \
    --no-auth \
    --name "neg-test-no-service"
```

### Test 5.3: Add forward with no namespace identifier should fail

Providing neither `--namespace` nor `--namespace-name` should be rejected.

```bash
assert_failure "5.3" "forwards add with no namespace identifier rejected" \
  wanaku forwards add \
  --host "${WANAKU_ROUTER_URL}" \
  --no-auth \
  --name "neg-test-no-ns" \
  --service "http://example.com:8080"
```

### Test 5.4: Add forward with both --namespace and --namespace-name should fail

Both flags cannot be used together.

```bash
assert_failure "5.4" "forwards add with both --namespace and --namespace-name rejected" \
  wanaku forwards add \
  --host "${WANAKU_ROUTER_URL}" \
  --no-auth \
  --name "neg-test-both-ns" \
  --service "http://example.com:8080" \
  --namespace public \
  --namespace-name public
```
### Test 5.5: Add duplicate forward name

First register a forward, then try to add another with the same name.

```bash
wanaku forwards add \
  --host "${WANAKU_ROUTER_URL}" \
  --no-auth \
  --name "neg-test-dup-forward" \
  --service "http://example.com:8080" 2>&1
SETUP_EXIT=$?

if [ "${SETUP_EXIT}" -ne 0 ]; then
  echo "SKIP [5.5]: could not register setup forward"
else
  OUTPUT=$(wanaku forwards add \
    --host "${WANAKU_ROUTER_URL}" \
    --no-auth \
    --name "neg-test-dup-forward" \
    --service "http://other.example.com:8080" 2>&1)
  EXIT_CODE=$?

  if [ "${EXIT_CODE}" -ne 0 ]; then
    echo "PASS [5.5]: duplicate forward name rejected (exit code ${EXIT_CODE})"
  else
    echo "WARN [5.5]: duplicate forward was accepted -- server may allow overwrite"
  fi

  # Cleanup
  wanaku forwards remove --host "${WANAKU_ROUTER_URL}" --no-auth --name "neg-test-dup-forward" 2>/dev/null || true
fi
```

### Test 5.6: Remove non-existent forward

```bash
assert_failure "5.5" "refresh non-existent forward rejected" \
  wanaku forwards refresh \
    --host "${WANAKU_ROUTER_URL}" \
    --no-auth \
    --name "nonexistent-forward-12345"
```

---

## Phase 6: Data Store -- Error Cases

### Test 6.1: Add with non-existent file

The `--read-from-file` option is required by `datastores add`.

```bash
assert_failure "6.1" "datastores add with non-existent file rejected" \
  wanaku datastores add \
    --host "${WANAKU_ROUTER_URL}" \
    --no-auth \
    --read-from-file "/tmp/nonexistent-datastore-file-12345.bin"
```

### Test 6.2: Get non-existent data store by name

The `datastores get` command requires either `--id` or `--name`.

```bash
assert_failure "6.2" "datastores get non-existent name rejected" \
  wanaku datastores get \
    --host "${WANAKU_ROUTER_URL}" \
    --no-auth \
    --name "nonexistent-datastore-12345"
```

### Test 6.3: Get with neither id nor name

```bash
assert_failure "6.3" "datastores get with no id or name rejected" \
  wanaku datastores get \
    --host "${WANAKU_ROUTER_URL}" \
    --no-auth
```

### Test 6.4: Remove non-existent data store

```bash
assert_graceful "6.4" "remove non-existent datastore handled gracefully" \
  wanaku datastores remove \
    --host "${WANAKU_ROUTER_URL}" \
    --no-auth \
    --name "nonexistent-datastore-12345"
```

### Test 6.5: Remove with neither id nor name

```bash
assert_failure "6.5" "datastores remove with no id or name rejected" \
  wanaku datastores remove \
    --host "${WANAKU_ROUTER_URL}" \
    --no-auth
```

---

## Phase 7: Namespaces -- Error Cases

### Test 7.1: Delete non-existent namespace

The `namespaces delete` command takes the namespace ID as a positional parameter.

```bash
assert_graceful "7.1" "delete non-existent namespace handled gracefully" \
  wanaku namespaces delete \
    --host "${WANAKU_ROUTER_URL}" \
    --no-auth \
    "nonexistent-namespace-id-12345"
```

### Test 7.2: Create namespace with no path

The `--path` option is required by `namespaces create`.

```bash
assert_failure "7.2" "namespaces create with no path rejected" \
  wanaku namespaces create \
    --host "${WANAKU_ROUTER_URL}" \
    --no-auth \
    --name "neg-test-ns"
```

---

## Phase 8: Service -- Error Cases

### Test 8.1: Service init with no name

The `--name` option is required by `service init`.

```bash
assert_failure "8.1" "service init with no name rejected" \
  wanaku service init \
    --services "system1"
```

### Test 8.2: Service init with no services

The `--services` option is required by `service init`.

```bash
assert_failure "8.2" "service init with no services rejected" \
  wanaku service init \
    --name "neg-test-init"
```

### Test 8.3: Service expose with invalid path (no index.properties)

```bash
assert_failure "8.3" "service expose with invalid path rejected" \
  wanaku service expose \
    --path "/tmp/nonexistent-service-catalog-12345"
```

### Test 8.4: Service package with non-existent path

```bash
assert_failure "8.4" "service package with non-existent path rejected" \
  wanaku service package \
    --path "/tmp/nonexistent-service-catalog-12345"
```

### Test 8.5: Service deploy with non-existent path

```bash
assert_failure "8.5" "service deploy with non-existent path rejected" \
  wanaku service deploy \
    --path "/tmp/nonexistent-service-catalog-12345" \
    --host "${WANAKU_ROUTER_URL}"
```

### Test 8.6: Service deploy with unreachable host

Create a minimal valid service catalog, then try to deploy to an unreachable host.

```bash
NEG_TEST_DIR=$(mktemp -d)
wanaku service init --name "${NEG_TEST_DIR}/neg-svc" --services "testsys" 2>/dev/null || true

# Only run if init succeeded
if [ -f "${NEG_TEST_DIR}/neg-svc/index.properties" ]; then
  # Generate rules so the catalog is complete
  wanaku service expose --path "${NEG_TEST_DIR}/neg-svc" 2>/dev/null || true

  assert_failure "8.6" "service deploy to unreachable host rejected" \
    wanaku service deploy \
      --path "${NEG_TEST_DIR}/neg-svc" \
      --host "${WANAKU_UNREACHABLE_HOST}"
else
  echo "SKIP [8.6]: service init did not produce expected files"
fi

# Cleanup
rm -rf "${NEG_TEST_DIR}" 2>/dev/null || true
```

### Test 8.7: Service init with already existing directory

```bash
NEG_TEST_DIR=$(mktemp -d)
mkdir -p "${NEG_TEST_DIR}/neg-existing"

OUTPUT=$(wanaku service init --name "${NEG_TEST_DIR}/neg-existing" --services "sys1" 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS [8.7]: service init with existing directory rejected (exit code ${EXIT_CODE})"
else
  echo "FAIL [8.7]: service init should reject existing directory"
fi

# Cleanup
rm -rf "${NEG_TEST_DIR}" 2>/dev/null || true
```

---

## Phase 9: Connection Errors

All commands pointing to an unreachable host should fail gracefully with a clear error message and no stack trace.

### Test 9.1: Tools list with unreachable host

```bash
assert_failure "9.1" "tools list with unreachable host fails gracefully" \
  wanaku tools list \
    --host "${WANAKU_UNREACHABLE_HOST}" \
    --no-auth \
    --plain
```

### Test 9.2: Resources list with unreachable host

```bash
assert_failure "9.2" "resources list with unreachable host fails gracefully" \
  wanaku resources list \
    --host "${WANAKU_UNREACHABLE_HOST}" \
    --no-auth \
    --plain
```

### Test 9.3: Prompts list with unreachable host

```bash
assert_failure "9.3" "prompts list with unreachable host fails gracefully" \
  wanaku prompts list \
    --host "${WANAKU_UNREACHABLE_HOST}" \
    --no-auth \
    --plain
```

### Test 9.4: Forwards list with unreachable host

```bash
assert_failure "9.4" "forwards list with unreachable host fails gracefully" \
  wanaku forwards list \
    --host "${WANAKU_UNREACHABLE_HOST}" \
    --no-auth \
    --plain
```

### Test 9.5: Datastores list with unreachable host

```bash
assert_failure "9.5" "datastores list with unreachable host fails gracefully" \
  wanaku datastores list \
    --host "${WANAKU_UNREACHABLE_HOST}" \
    --no-auth \
    --plain
```

### Test 9.6: Namespaces list with unreachable host

```bash
assert_failure "9.6" "namespaces list with unreachable host fails gracefully" \
  wanaku namespaces list \
    --host "${WANAKU_UNREACHABLE_HOST}" \
    --no-auth \
    --plain
```

### Test 9.7: Tools add with unreachable host

```bash
assert_failure "9.7" "tools add with unreachable host fails gracefully" \
  wanaku tools add \
    --host "${WANAKU_UNREACHABLE_HOST}" \
    --no-auth \
    --name "neg-test-unreachable" \
    --namespace public \
    --description "test" \
    --uri "http://example.com" \
    --type http
```

---

## Phase 10: Cleanup

### Step 10.1: Remove any leftover test data

```bash
for TOOL_NAME in neg-test-duplicate neg-test-no-uri neg-test-no-type neg-test-bad-type neg-test-unreachable; do
  wanaku tools remove --host "${WANAKU_ROUTER_URL}" --no-auth --name "${TOOL_NAME}" 2>/dev/null || true
done

for RESOURCE_NAME in neg-test-dup-resource neg-test-no-location neg-test-no-type; do
  wanaku resources remove --host "${WANAKU_ROUTER_URL}" --no-auth --name "${RESOURCE_NAME}" 2>/dev/null || true
done

wanaku forwards remove --host "${WANAKU_ROUTER_URL}" --no-auth --name "neg-test-dup-forward" 2>/dev/null || true

echo "PASS [10.1]: test data cleaned up"
```

### Step 10.2: Stop the local stack

```bash
if [ -n "${WANAKU_PID}" ]; then
  kill "${WANAKU_PID}" 2>/dev/null || true
  wait "${WANAKU_PID}" 2>/dev/null || true
  echo "PASS [10.2]: Wanaku process stopped"
else
  echo "SKIP [10.2]: WANAKU_PID not set"
fi
```

---

## Summary Matrix

| Phase | Test ID | Test Name | Priority | Category |
|-------|---------|-----------|----------|----------|
| 0 | 0.1-0.2 | Prerequisites verification | Critical | Setup |
| 1 | 1.0 | Router health check | Critical | Setup |
| 2 | 2.1 | Tool add: missing --name | High | Validation |
| 2 | 2.2 | Tool add: missing --uri | High | Validation |
| 2 | 2.3 | Tool add: missing --type | High | Validation |
| 2 | 2.4 | Tool add: invalid type | High | Validation |
| 2 | 2.5 | Tool add: duplicate name | Medium | Validation |
| 2 | 2.6 | Tool show: non-existent | High | Error handling |
| 2 | 2.7 | Tool edit: non-existent | High | Error handling |
| 2 | 2.8 | Tool remove: non-existent | Medium | Error handling |
| 2 | 2.9 | Tool label add: non-existent | High | Error handling |
| 2 | 2.10 | Tool generate: non-existent OpenAPI | High | Error handling |
| 3 | 3.1 | Resource expose: missing --name | High | Validation |
| 3 | 3.2 | Resource expose: missing --location | High | Validation |
| 3 | 3.3 | Resource expose: missing --type | High | Validation |
| 3 | 3.4 | Resource expose: duplicate name | Medium | Validation |
| 3 | 3.5 | Resource show: non-existent | High | Error handling |
| 3 | 3.6 | Resource remove: non-existent | Medium | Error handling |
| 4 | 4.1 | Prompt add: missing --name | High | Validation |
| 4 | 4.2 | Prompt add: missing --description | High | Validation |
| 4 | 4.3 | Prompt edit: non-existent | High | Error handling |
| 4 | 4.4 | Prompt remove: non-existent | Medium | Error handling |
| 5 | 5.1 | Forward add: missing --name | High | Validation |
| 5 | 5.2 | Forward add: missing --service | High | Validation |
| 5 | 5.3 | Forward add: duplicate name | Medium | Validation |
| 5 | 5.4 | Forward remove: non-existent | Medium | Error handling |
| 5 | 5.5 | Forward refresh: non-existent | High | Error handling |
| 6 | 6.1 | Datastore add: non-existent file | High | Validation |
| 6 | 6.2 | Datastore get: non-existent name | High | Error handling |
| 6 | 6.3 | Datastore get: no id or name | High | Validation |
| 6 | 6.4 | Datastore remove: non-existent | Medium | Error handling |
| 6 | 6.5 | Datastore remove: no id or name | High | Validation |
| 7 | 7.1 | Namespace delete: non-existent | Medium | Error handling |
| 7 | 7.2 | Namespace create: missing --path | High | Validation |
| 8 | 8.1 | Service init: missing --name | High | Validation |
| 8 | 8.2 | Service init: missing --services | High | Validation |
| 8 | 8.3 | Service expose: invalid path | High | Error handling |
| 8 | 8.4 | Service package: non-existent path | High | Error handling |
| 8 | 8.5 | Service deploy: non-existent path | High | Error handling |
| 8 | 8.6 | Service deploy: unreachable host | High | Connection |
| 8 | 8.7 | Service init: existing directory | High | Error handling |
| 9 | 9.1 | Tools list: unreachable host | Critical | Connection |
| 9 | 9.2 | Resources list: unreachable host | Critical | Connection |
| 9 | 9.3 | Prompts list: unreachable host | Critical | Connection |
| 9 | 9.4 | Forwards list: unreachable host | Critical | Connection |
| 9 | 9.5 | Datastores list: unreachable host | Critical | Connection |
| 9 | 9.6 | Namespaces list: unreachable host | Critical | Connection |
| 9 | 9.7 | Tools add: unreachable host | Critical | Connection |
| 10 | 10.1-10.2 | Cleanup | Critical | Teardown |
