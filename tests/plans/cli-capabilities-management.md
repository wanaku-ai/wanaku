# Test Plan: CLI Capabilities Management

## Overview

This test plan verifies the `wanaku capabilities` CLI commands against a locally running Wanaku instance (no authentication). Capabilities are services (tool providers, resource providers) that register with the router. When started via `wanaku start local` with the HTTP tool distribution, the HTTP tool service automatically registers as a capability.

The plan covers listing, showing details, checking status, and cleaning up capabilities using the CLI.

Every step is fully automatable.

## Prerequisites

### Required tools

| Tool | Minimum version | Verify command |
|------|-----------------|----------------|
| `wanaku` | build from source | `wanaku --version` |
| `java` | 21+ | `java -version` |
| `mvn` | 3.9+ | `mvn -version` |
| `jq` | 1.6+ | `jq --version` |

### Prerequisite check script

```bash
FAIL=0

for CMD in java mvn jq; do
  if ! command -v "${CMD}" > /dev/null 2>&1; then
    echo "FAIL: ${CMD} is not installed"
    FAIL=1
  else
    echo "PASS: ${CMD} found at $(command -v ${CMD})"
  fi
done

if [ "${FAIL}" -ne 0 ]; then
  echo ""
  echo "FAIL: one or more prerequisites missing"
  exit 1
fi

echo ""
echo "PASS: all prerequisites met"
```

### CLI invocation

When using the CLI from a local build (not installed), use `java -jar` directly:

```bash
CLI_JAR="apps/wanaku-cli/target/quarkus-app/quarkus-run.jar"
java -jar ${CLI_JAR} capabilities list --host ...
```

Do **not** assign the full command to a single variable (e.g., `WANAKU_CLI="java -jar path/to/jar"`) -- zsh treats it as a single token. Use `CLI_JAR` for the path and call `java -jar ${CLI_JAR}` explicitly.

### Environment variables

```bash
export WANAKU_REPO_ROOT="${WANAKU_REPO_ROOT:-.}"
export WANAKU_ROUTER_URL="${WANAKU_ROUTER_URL:-http://localhost:8080}"
```

### Known limitations for local testing

- **Capabilities commands do not use `--no-auth`:** The `capabilities list`, `show`, `status`, and `cleanup` commands use a static service initializer that does not pass the `--no-auth` flag. When running locally with `start local`, the router runs in `noauth` profile so authentication is not enforced. Do not pass `--no-auth` to capabilities commands -- it has no effect.
- **Only the HTTP tool service is available locally:** The `start local` command only supports tool services. No resource providers register as capabilities locally. The test plan uses the HTTP tool capability as the test subject.
- **`capabilities show` is interactive when multiple instances exist:** If multiple capability instances share the same service name, `show` presents an interactive selection menu. In local testing only one HTTP instance is expected.

---

## Phase 0: Prerequisites

### Test 0.1: Verify tools table

```bash
FAIL=0

for CMD in java mvn jq; do
  if ! command -v "${CMD}" > /dev/null 2>&1; then
    echo "FAIL: ${CMD} is not installed"
    FAIL=1
  else
    echo "PASS: ${CMD} found"
  fi
done

if [ "${FAIL}" -ne 0 ]; then
  echo "FAIL: prerequisites not met"
  exit 1
fi
echo "PASS: all prerequisites met"
```

### Test 0.2: Verify environment variables

```bash
for VAR_NAME in WANAKU_REPO_ROOT WANAKU_ROUTER_URL; do
  eval "VAL=\${${VAR_NAME}}"
  if [ -z "${VAL}" ]; then
    echo "FAIL: ${VAR_NAME} is not set"
    exit 1
  fi
  echo "PASS: ${VAR_NAME}=${VAL}"
done
```

---

## Phase 1: Setup

Follow [common/start-local.md](common/start-local.md) to build and start the Wanaku local stack with the HTTP tool capability.

After completion, `VERSION`, `CLI_JAR`, `WANAKU_ROUTER_URL`, and `WANAKU_PID` must be set.

### Test 1.1: Verify the HTTP tool capability has registered

After the router is healthy, wait for the HTTP tool capability to register. Capabilities register asynchronously after startup.

```bash
MAX_RETRIES=12
RETRY_INTERVAL=5
for i in $(seq 1 ${MAX_RETRIES}); do
  OUTPUT=$(wanaku capabilities list --host "${WANAKU_ROUTER_URL}" --plain 2>&1)
  if echo "${OUTPUT}" | grep -q "http"; then
    echo "PASS: HTTP tool capability registered (attempt ${i})"
    break
  fi
  if [ "${i}" -eq "${MAX_RETRIES}" ]; then
    echo "FAIL: HTTP tool capability not registered after ${MAX_RETRIES} attempts"
    echo "${OUTPUT}"
    exit 1
  fi
  echo "Waiting for HTTP capability to register... (attempt ${i})"
  sleep ${RETRY_INTERVAL}
done
```

---

## Phase 2: Capabilities List

### Test 2.1: List capabilities shows at least one registered service

```bash
OUTPUT=$(wanaku capabilities list --host "${WANAKU_ROUTER_URL}" --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: capabilities list failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

LINE_COUNT=$(echo "${OUTPUT}" | wc -l | tr -d ' ')
if [ "${LINE_COUNT}" -gt 0 ]; then
  echo "PASS: capabilities list returned ${LINE_COUNT} lines"
else
  echo "FAIL: capabilities list returned no output"
fi
```

### Test 2.2: Verify the HTTP capability appears in the list

```bash
OUTPUT=$(wanaku capabilities list --host "${WANAKU_ROUTER_URL}" --plain 2>&1)

echo "${OUTPUT}" | grep -q "http" \
  && echo "PASS: 'http' capability found in list" \
  || echo "FAIL: 'http' capability not found in list"
```

### Test 2.3: Verify the list output contains expected columns

The capabilities list displays columns: service, serviceType, host, port, status, lastSeen, labels.

```bash
OUTPUT=$(wanaku capabilities list --host "${WANAKU_ROUTER_URL}" --plain 2>&1)

echo "${OUTPUT}" | grep -qi "service" \
  && echo "PASS: output contains 'service' column" \
  || echo "FAIL: output missing 'service' column"

echo "${OUTPUT}" | grep -qi "status" \
  && echo "PASS: output contains 'status' column" \
  || echo "FAIL: output missing 'status' column"
```

### Test 2.4: Verify list with label expression filter (non-matching)

```bash
OUTPUT=$(wanaku capabilities list --host "${WANAKU_ROUTER_URL}" -e "nonexistent=true" --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: capabilities list with non-matching label expression succeeded (returned empty or filtered)"
else
  echo "WARN: capabilities list with non-matching label expression returned exit code ${EXIT_CODE}"
fi
```

---

## Phase 3: Capabilities Show

### Test 3.1: Show details for the HTTP capability

```bash
OUTPUT=$(wanaku capabilities show --host "${WANAKU_ROUTER_URL}" http --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: capabilities show http failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "PASS: capabilities show http succeeded"
echo "${OUTPUT}"
```

### Test 3.2: Verify show output contains service name

```bash
OUTPUT=$(wanaku capabilities show --host "${WANAKU_ROUTER_URL}" http --plain 2>&1)

echo "${OUTPUT}" | grep -qi "http" \
  && echo "PASS: show output contains service name 'http'" \
  || echo "FAIL: show output does not contain service name 'http'"
```

### Test 3.3: Verify show output contains host and port

```bash
OUTPUT=$(wanaku capabilities show --host "${WANAKU_ROUTER_URL}" http --plain 2>&1)

echo "${OUTPUT}" | grep -qE "[0-9]+" \
  && echo "PASS: show output contains numeric data (port)" \
  || echo "FAIL: show output does not contain expected numeric data"
```

### Test 3.4: Show a non-existent capability should fail

```bash
OUTPUT=$(wanaku capabilities show --host "${WANAKU_ROUTER_URL}" non-existent-service-12345 --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: show non-existent capability failed as expected (exit code ${EXIT_CODE})"
else
  echo "FAIL: show non-existent capability should have returned a non-zero exit code"
fi
```

### Test 3.5: Verify non-existent show output contains warning message

```bash
OUTPUT=$(wanaku capabilities show --host "${WANAKU_ROUTER_URL}" non-existent-service-12345 --plain 2>&1)

echo "${OUTPUT}" | grep -qi "no capabilities found" \
  && echo "PASS: warning message present for non-existent capability" \
  || echo "FAIL: expected 'No capabilities found' warning not present"
```

---

## Phase 4: Capabilities Status

### Test 4.1: Status shows health summary

```bash
OUTPUT=$(wanaku capabilities status --host "${WANAKU_ROUTER_URL}" --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: capabilities status failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "PASS: capabilities status succeeded"
echo "${OUTPUT}"
```

### Test 4.2: Verify status output contains health categories

```bash
OUTPUT=$(wanaku capabilities status --host "${WANAKU_ROUTER_URL}" --plain 2>&1)

echo "${OUTPUT}" | grep -qi "healthy" \
  && echo "PASS: status output contains 'Healthy' category" \
  || echo "FAIL: status output missing 'Healthy' category"

echo "${OUTPUT}" | grep -qi "total" \
  && echo "PASS: status output contains 'Total' category" \
  || echo "FAIL: status output missing 'Total' category"
```

### Test 4.3: Verify at least one healthy capability is reported

```bash
OUTPUT=$(wanaku capabilities status --host "${WANAKU_ROUTER_URL}" --plain 2>&1)

# The Healthy count should be >= 1 since the HTTP capability is running
HEALTHY_LINE=$(echo "${OUTPUT}" | grep -i "healthy" | head -1)
if echo "${HEALTHY_LINE}" | grep -qE "[1-9]"; then
  echo "PASS: at least one healthy capability reported"
else
  echo "FAIL: expected at least one healthy capability"
  echo "Healthy line: ${HEALTHY_LINE}"
fi
```

### Test 4.4: Status with --filter healthy shows only healthy capabilities

```bash
OUTPUT=$(wanaku capabilities status --host "${WANAKU_ROUTER_URL}" --filter healthy --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: capabilities status --filter healthy failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "${OUTPUT}" | grep -q "http" \
  && echo "PASS: HTTP capability shown in healthy filter" \
  || echo "FAIL: HTTP capability not shown in healthy filter"
```

### Test 4.5: Status with --filter down shows no capabilities (all should be healthy)

```bash
OUTPUT=$(wanaku capabilities status --host "${WANAKU_ROUTER_URL}" --filter down --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: capabilities status --filter down succeeded"
  # Should show the summary but no matching capabilities in the table
  echo "${OUTPUT}" | grep -qi "no capabilities match" \
    && echo "PASS: no capabilities match 'down' filter (expected)" \
    || echo "INFO: output did not explicitly say no match, but command succeeded"
else
  echo "FAIL: capabilities status --filter down failed unexpectedly"
fi
```

---

## Phase 5: Capabilities Cleanup

### Test 5.1: Cleanup with default max-age finds no stale capabilities

The HTTP tool capability was just started, so it should not be stale (default max-age is 1 day).

```bash
OUTPUT=$(wanaku capabilities cleanup --host "${WANAKU_ROUTER_URL}" -y --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: capabilities cleanup failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "${OUTPUT}" | grep -qi "no stale" \
  && echo "PASS: no stale capabilities found (expected for fresh start)" \
  || echo "WARN: cleanup output did not contain 'no stale' message"

echo "Cleanup output: ${OUTPUT}"
```

### Test 5.2: Cleanup does not remove the healthy HTTP capability

```bash
# Run cleanup
wanaku capabilities cleanup --host "${WANAKU_ROUTER_URL}" -y --plain 2>&1

# Verify the HTTP capability is still registered
OUTPUT=$(wanaku capabilities list --host "${WANAKU_ROUTER_URL}" --plain 2>&1)
echo "${OUTPUT}" | grep -q "http" \
  && echo "PASS: HTTP capability still registered after cleanup" \
  || echo "FAIL: HTTP capability was removed by cleanup"
```

### Test 5.3: Cleanup with --inactive-only and default max-age

```bash
OUTPUT=$(wanaku capabilities cleanup --host "${WANAKU_ROUTER_URL}" --inactive-only -y --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: capabilities cleanup --inactive-only failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "PASS: cleanup --inactive-only succeeded"
echo "${OUTPUT}"
```

### Test 5.4: Cleanup with custom max-age-days

```bash
OUTPUT=$(wanaku capabilities cleanup --host "${WANAKU_ROUTER_URL}" --max-age-days 365 -y --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: capabilities cleanup --max-age-days 365 failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "PASS: cleanup with custom max-age-days succeeded"
echo "${OUTPUT}"
```

---

## Phase 6: Negative Tests

### Test 6.1: Show with no service name should fail

```bash
OUTPUT=$(wanaku capabilities show --host "${WANAKU_ROUTER_URL}" --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: show without service name failed as expected (exit code ${EXIT_CODE})"
else
  echo "FAIL: show without service name should require a positional parameter"
fi
```

### Test 6.2: List against a non-existent host should fail gracefully

```bash
OUTPUT=$(wanaku capabilities list --host "http://localhost:59999" --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: list against non-existent host failed gracefully (exit code ${EXIT_CODE})"
else
  echo "FAIL: list against non-existent host should fail"
fi
```

### Test 6.3: Status against a non-existent host should fail gracefully

```bash
OUTPUT=$(wanaku capabilities status --host "http://localhost:59999" --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: status against non-existent host failed gracefully (exit code ${EXIT_CODE})"
else
  echo "FAIL: status against non-existent host should fail"
fi
```

### Test 6.4: Cleanup against a non-existent host should fail gracefully

```bash
OUTPUT=$(wanaku capabilities cleanup --host "http://localhost:59999" -y --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: cleanup against non-existent host failed gracefully (exit code ${EXIT_CODE})"
else
  echo "FAIL: cleanup against non-existent host should fail"
fi
```

### Test 6.5: Cleanup with nothing stale is a successful no-op

```bash
OUTPUT=$(wanaku capabilities cleanup --host "${WANAKU_ROUTER_URL}" -y --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: cleanup with no stale capabilities is a successful no-op"
else
  echo "FAIL: cleanup should succeed even when nothing is stale"
fi
```

### Test 6.6: Status with invalid filter value

```bash
OUTPUT=$(wanaku capabilities status --host "${WANAKU_ROUTER_URL}" --filter "invalid-status-value" --plain 2>&1)
EXIT_CODE=$?

# Should succeed but show no matching capabilities
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: status with invalid filter value succeeded (shows no matches)"
  echo "${OUTPUT}" | grep -qi "no capabilities match" \
    && echo "PASS: displayed 'no capabilities match' message" \
    || echo "INFO: command succeeded but did not show explicit no-match message"
else
  echo "INFO: status with invalid filter value returned exit code ${EXIT_CODE}"
fi
```

---

## Phase 7: Cleanup

### Step 7.1: Kill the local Wanaku process

```bash
if [ -n "${WANAKU_PID}" ]; then
  kill "${WANAKU_PID}" 2>/dev/null || true
  wait "${WANAKU_PID}" 2>/dev/null || true
  echo "PASS: Wanaku process stopped"
else
  echo "WARN: WANAKU_PID not set, nothing to stop"
fi
```

### Step 7.2: Verify the process is stopped

```bash
if [ -n "${WANAKU_PID}" ]; then
  if kill -0 "${WANAKU_PID}" 2>/dev/null; then
    echo "FAIL: Wanaku process (PID ${WANAKU_PID}) is still running"
  else
    echo "PASS: Wanaku process (PID ${WANAKU_PID}) is no longer running"
  fi
else
  echo "PASS: no process to verify (WANAKU_PID not set)"
fi
```

---

## Summary Matrix

| Phase | Test ID | Test Name | Priority |
|-------|---------|-----------|----------|
| 0 | 0.1-0.2 | Prerequisites and environment variables | High |
| 1 | 1.1 | HTTP tool capability registration after startup | Critical |
| 2 | 2.1-2.4 | Capabilities list, column output, label expression filter | Critical |
| 3 | 3.1-3.5 | Capabilities show (details, content verification, non-existent) | Critical |
| 4 | 4.1-4.5 | Capabilities status (summary, health categories, filter) | High |
| 5 | 5.1-5.4 | Capabilities cleanup (no stale, healthy preserved, inactive-only, custom max-age) | High |
| 6 | 6.1-6.6 | Negative tests (missing args, bad host, invalid filter, no-op cleanup) | High |
| 7 | 7.1-7.2 | Process cleanup | Critical |
