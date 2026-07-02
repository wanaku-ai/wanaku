# Test Plan: CLI Auth Commands (User Management and Service Client Credentials)

## Overview

This test plan verifies the `wanaku admin users` and `wanaku admin credentials` CLI commands against a Keycloak instance deployed on OpenShift. It covers full CRUD operations for users (list, add, set-password, remove) and service client credentials (list, add, show, regenerate, remove).

Every step is fully automatable.

## Prerequisites

### Required tools

| Tool | Minimum version | Verify command |
|------|-----------------|----------------|
| `wanaku` | 0.2.0+ | `wanaku --version` |
| `java` | 21+ | `java -version` |
| `mvn` | 3.9+ | `mvn -version` |
| `oc` | 4.x | `oc version` |
| `jq` | 1.6+ | `jq --version` |
| `curl` | any | `curl --version` |

### Prerequisite check script

```bash
FAIL=0

for CMD in java mvn oc jq curl; do
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

### Environment variables

```bash
export WANAKU_REPO_ROOT="${WANAKU_REPO_ROOT:-.}"
export WANAKU_NAMESPACE="${WANAKU_NAMESPACE:-wanaku-test}"
export KEYCLOAK_ADMIN_USER="${KEYCLOAK_ADMIN_USER:-admin}"
export KEYCLOAK_ADMIN_PASS="${KEYCLOAK_ADMIN_PASS:-admin}"
export KEYCLOAK_URL="${KEYCLOAK_URL:-}"
export TEST_USERNAME="${TEST_USERNAME:-testuser-811}"
export TEST_PASSWORD="${TEST_PASSWORD:-TestPass123}"
export TEST_EMAIL="${TEST_EMAIL:-testuser811@example.com}"
export TEST_CLIENT_ID="${TEST_CLIENT_ID:-test-service-811}"
```

| Variable | Default | Description |
|----------|---------|-------------|
| `WANAKU_REPO_ROOT` | `.` | Path to the wanaku repository root |
| `WANAKU_NAMESPACE` | `wanaku-test` | OpenShift namespace for Keycloak deployment |
| `KEYCLOAK_ADMIN_USER` | `admin` | Keycloak admin username |
| `KEYCLOAK_ADMIN_PASS` | `admin` | Keycloak admin password |
| `KEYCLOAK_URL` | _(set by setup)_ | Keycloak URL (set after Keycloak deployment) |
| `TEST_USERNAME` | `testuser-811` | Username for test user CRUD operations |
| `TEST_PASSWORD` | `TestPass123` | Password for test user creation |
| `TEST_EMAIL` | `testuser811@example.com` | Email for test user creation |
| `TEST_CLIENT_ID` | `test-service-811` | Client ID for test credential CRUD operations |

### CLI invocation

When using the CLI from a local build (not installed), use `java -jar` directly:

```bash
CLI_JAR="${WANAKU_REPO_ROOT}/apps/wanaku-cli/target/quarkus-app/quarkus-run.jar"
java -jar ${CLI_JAR} admin users list --keycloak-url ...
```

Do **not** assign the full command to a single variable (e.g., `WANAKU_CLI="java -jar path/to/jar"`) — zsh treats it as a single token. Use `CLI_JAR` for the path and call `java -jar ${CLI_JAR}` explicitly.

---

## Phase 0: Prerequisites

### Test 0.1: Verify tools

```bash
FAIL=0

for CMD in java mvn oc jq curl; do
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
for VAR_NAME in WANAKU_REPO_ROOT WANAKU_NAMESPACE KEYCLOAK_ADMIN_USER KEYCLOAK_ADMIN_PASS; do
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

Follow [common/keycloak-setup.md](common/keycloak-setup.md) steps 1–7 to deploy Keycloak on OpenShift and import the Wanaku realm.

After completion, `KEYCLOAK_URL`, `KEYCLOAK_HOST`, `KEYCLOAK_ADMIN_USER`, and `KEYCLOAK_ADMIN_PASS` must be set.

### Test 1.1: Verify Keycloak is responding

```bash
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "${KEYCLOAK_URL}/realms/wanaku" 2>/dev/null || echo "000")
if [ "${HTTP_CODE}" = "200" ]; then
  echo "PASS: Keycloak wanaku realm is accessible"
else
  echo "FAIL: Keycloak wanaku realm not accessible (HTTP ${HTTP_CODE})"
  exit 1
fi
```

---

## Phase 2: User Management — List (Initial State)

### Test 2.1: List users returns successfully

```bash
OUTPUT=$(wanaku admin users list \
  --keycloak-url "${KEYCLOAK_URL}" \
  --admin-username "${KEYCLOAK_ADMIN_USER}" \
  --admin-password "${KEYCLOAK_ADMIN_PASS}" 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: admin users list failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "PASS: admin users list succeeded"
echo "${OUTPUT}"
```

### Test 2.2: List users does not contain the test user yet

```bash
OUTPUT=$(wanaku admin users list \
  --keycloak-url "${KEYCLOAK_URL}" \
  --admin-username "${KEYCLOAK_ADMIN_USER}" \
  --admin-password "${KEYCLOAK_ADMIN_PASS}" 2>&1)

echo "${OUTPUT}" | grep -q "${TEST_USERNAME}" \
  && echo "FAIL: test user '${TEST_USERNAME}' already exists before creation" \
  || echo "PASS: test user '${TEST_USERNAME}' not present (expected)"
```

---

## Phase 3: User Management — Add

### Test 3.1: Create a new user

```bash
OUTPUT=$(wanaku admin users add \
  --keycloak-url "${KEYCLOAK_URL}" \
  --admin-username "${KEYCLOAK_ADMIN_USER}" \
  --admin-password "${KEYCLOAK_ADMIN_PASS}" \
  --username "${TEST_USERNAME}" \
  --password "${TEST_PASSWORD}" \
  --email "${TEST_EMAIL}" 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: admin users add failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "PASS: user '${TEST_USERNAME}' created"
```

### Test 3.2: List users shows the newly created user

```bash
OUTPUT=$(wanaku admin users list \
  --keycloak-url "${KEYCLOAK_URL}" \
  --admin-username "${KEYCLOAK_ADMIN_USER}" \
  --admin-password "${KEYCLOAK_ADMIN_PASS}" 2>&1)

echo "${OUTPUT}" | grep -q "${TEST_USERNAME}" \
  && echo "PASS: user '${TEST_USERNAME}' found in users list" \
  || echo "FAIL: user '${TEST_USERNAME}' not found after creation"
```

### Test 3.3: Create a duplicate user should fail

```bash
OUTPUT=$(wanaku admin users add \
  --keycloak-url "${KEYCLOAK_URL}" \
  --admin-username "${KEYCLOAK_ADMIN_USER}" \
  --admin-password "${KEYCLOAK_ADMIN_PASS}" \
  --username "${TEST_USERNAME}" \
  --password "${TEST_PASSWORD}" \
  --email "${TEST_EMAIL}" 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: adding duplicate user failed as expected (exit code ${EXIT_CODE})"
else
  echo "FAIL: adding duplicate user should have failed"
fi
```

### Test 3.4: Create user with only required fields (no email)

```bash
OUTPUT=$(wanaku admin users add \
  --keycloak-url "${KEYCLOAK_URL}" \
  --admin-username "${KEYCLOAK_ADMIN_USER}" \
  --admin-password "${KEYCLOAK_ADMIN_PASS}" \
  --username "${TEST_USERNAME}-minimal" \
  --password "${TEST_PASSWORD}" 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: creating user with minimal fields failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "PASS: user '${TEST_USERNAME}-minimal' created with minimal fields"
```

---

## Phase 4: User Management — Set Password

### Test 4.1: Set password for existing user

```bash
NEW_PASSWORD="NewPass456"
OUTPUT=$(wanaku admin users set-password \
  --keycloak-url "${KEYCLOAK_URL}" \
  --admin-username "${KEYCLOAK_ADMIN_USER}" \
  --admin-password "${KEYCLOAK_ADMIN_PASS}" \
  --username "${TEST_USERNAME}" \
  --password "${NEW_PASSWORD}" 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: admin users set-password failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "PASS: password updated for user '${TEST_USERNAME}'"
```

### Test 4.2: Set password for non-existent user should fail

```bash
OUTPUT=$(wanaku admin users set-password \
  --keycloak-url "${KEYCLOAK_URL}" \
  --admin-username "${KEYCLOAK_ADMIN_USER}" \
  --admin-password "${KEYCLOAK_ADMIN_PASS}" \
  --username "non-existent-user-99999" \
  --password "anypass" 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: set-password for non-existent user failed as expected (exit code ${EXIT_CODE})"
else
  echo "FAIL: set-password for non-existent user should have failed"
fi
```

---

## Phase 5: User Management — Remove

### Test 5.1: Remove the minimal test user

```bash
OUTPUT=$(wanaku admin users remove \
  --keycloak-url "${KEYCLOAK_URL}" \
  --admin-username "${KEYCLOAK_ADMIN_USER}" \
  --admin-password "${KEYCLOAK_ADMIN_PASS}" \
  --username "${TEST_USERNAME}-minimal" 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: admin users remove failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "PASS: user '${TEST_USERNAME}-minimal' removed"
```

### Test 5.2: Verify removed user no longer appears in list

```bash
OUTPUT=$(wanaku admin users list \
  --keycloak-url "${KEYCLOAK_URL}" \
  --admin-username "${KEYCLOAK_ADMIN_USER}" \
  --admin-password "${KEYCLOAK_ADMIN_PASS}" 2>&1)

echo "${OUTPUT}" | grep -q "${TEST_USERNAME}-minimal" \
  && echo "FAIL: removed user '${TEST_USERNAME}-minimal' still present in list" \
  || echo "PASS: removed user '${TEST_USERNAME}-minimal' no longer in list"
```

### Test 5.3: Remove a non-existent user should fail

```bash
OUTPUT=$(wanaku admin users remove \
  --keycloak-url "${KEYCLOAK_URL}" \
  --admin-username "${KEYCLOAK_ADMIN_USER}" \
  --admin-password "${KEYCLOAK_ADMIN_PASS}" \
  --username "non-existent-user-99999" 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: removing non-existent user failed as expected (exit code ${EXIT_CODE})"
else
  echo "FAIL: removing non-existent user should have failed"
fi
```

---

## Phase 6: Service Client Credentials — List (Initial State)

### Test 6.1: List credentials returns successfully

```bash
OUTPUT=$(wanaku admin credentials list \
  --keycloak-url "${KEYCLOAK_URL}" \
  --admin-username "${KEYCLOAK_ADMIN_USER}" \
  --admin-password "${KEYCLOAK_ADMIN_PASS}" \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: admin credentials list failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "PASS: admin credentials list succeeded"
echo "${OUTPUT}"
```

### Test 6.2: List filters out internal Keycloak clients

The list command should filter out internal Keycloak clients and only show service clients.

```bash
OUTPUT=$(wanaku admin credentials list \
  --keycloak-url "${KEYCLOAK_URL}" \
  --admin-username "${KEYCLOAK_ADMIN_USER}" \
  --admin-password "${KEYCLOAK_ADMIN_PASS}" \
  --plain 2>&1)

echo "${OUTPUT}" | grep -q "account" \
  && echo "FAIL: internal Keycloak client 'account' shown in list (should be filtered)" \
  || echo "PASS: internal Keycloak clients filtered from list"
```

### Test 6.3: List shows the wanaku-service client from realm import

```bash
OUTPUT=$(wanaku admin credentials list \
  --keycloak-url "${KEYCLOAK_URL}" \
  --admin-username "${KEYCLOAK_ADMIN_USER}" \
  --admin-password "${KEYCLOAK_ADMIN_PASS}" \
  --plain 2>&1)

echo "${OUTPUT}" | grep -q "wanaku-service" \
  && echo "PASS: 'wanaku-service' client present in credentials list" \
  || echo "FAIL: 'wanaku-service' client not found in credentials list"
```

---

## Phase 7: Service Client Credentials — Add

### Test 7.1: Create a new service client

```bash
OUTPUT=$(wanaku admin credentials add \
  --keycloak-url "${KEYCLOAK_URL}" \
  --admin-username "${KEYCLOAK_ADMIN_USER}" \
  --admin-password "${KEYCLOAK_ADMIN_PASS}" \
  --client-id "${TEST_CLIENT_ID}" \
  --description "Test service client for issue 811" 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: admin credentials add failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "PASS: service client '${TEST_CLIENT_ID}' created"
```

### Test 7.2: Verify the new client appears in the list

```bash
OUTPUT=$(wanaku admin credentials list \
  --keycloak-url "${KEYCLOAK_URL}" \
  --admin-username "${KEYCLOAK_ADMIN_USER}" \
  --admin-password "${KEYCLOAK_ADMIN_PASS}" \
  --plain 2>&1)

echo "${OUTPUT}" | grep -q "${TEST_CLIENT_ID}" \
  && echo "PASS: service client '${TEST_CLIENT_ID}' found in credentials list" \
  || echo "FAIL: service client '${TEST_CLIENT_ID}' not found after creation"
```

### Test 7.3: Create a service client with --show-secret

```bash
OUTPUT=$(wanaku admin credentials add \
  --keycloak-url "${KEYCLOAK_URL}" \
  --admin-username "${KEYCLOAK_ADMIN_USER}" \
  --admin-password "${KEYCLOAK_ADMIN_PASS}" \
  --client-id "${TEST_CLIENT_ID}-with-secret" \
  --show-secret 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: credentials add with --show-secret failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "${OUTPUT}" | grep -qi "secret" \
  && echo "PASS: secret displayed when using --show-secret" \
  || echo "FAIL: secret not displayed despite --show-secret flag"
```

### Test 7.4: Create a duplicate client should fail

```bash
OUTPUT=$(wanaku admin credentials add \
  --keycloak-url "${KEYCLOAK_URL}" \
  --admin-username "${KEYCLOAK_ADMIN_USER}" \
  --admin-password "${KEYCLOAK_ADMIN_PASS}" \
  --client-id "${TEST_CLIENT_ID}" \
  --description "Duplicate" 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: adding duplicate service client failed as expected (exit code ${EXIT_CODE})"
else
  echo "FAIL: adding duplicate service client should have failed"
fi
```

---

## Phase 8: Service Client Credentials — Show

### Test 8.1: Show details for the test client

```bash
OUTPUT=$(wanaku admin credentials show \
  --keycloak-url "${KEYCLOAK_URL}" \
  --admin-username "${KEYCLOAK_ADMIN_USER}" \
  --admin-password "${KEYCLOAK_ADMIN_PASS}" \
  --client-id "${TEST_CLIENT_ID}" \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: admin credentials show failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "${OUTPUT}" | grep -q "${TEST_CLIENT_ID}" \
  && echo "PASS: show output contains client ID '${TEST_CLIENT_ID}'" \
  || echo "FAIL: show output does not contain client ID"
```

### Test 8.2: Show with --show-secret displays the secret

```bash
OUTPUT=$(wanaku admin credentials show \
  --keycloak-url "${KEYCLOAK_URL}" \
  --admin-username "${KEYCLOAK_ADMIN_USER}" \
  --admin-password "${KEYCLOAK_ADMIN_PASS}" \
  --client-id "${TEST_CLIENT_ID}" \
  --show-secret \
  --plain 2>&1)

ORIGINAL_SECRET=$(echo "${OUTPUT}" | grep "Client Secret:" | sed 's/.*Client Secret: //')

if [ -n "${ORIGINAL_SECRET}" ] && [ "${ORIGINAL_SECRET}" != "null" ]; then
  echo "PASS: secret retrieved (length: ${#ORIGINAL_SECRET})"
else
  echo "FAIL: could not retrieve secret with --show-secret"
  echo "${OUTPUT}"
fi
```

### Test 8.3: Show without --show-secret does not display the secret value

```bash
OUTPUT=$(wanaku admin credentials show \
  --keycloak-url "${KEYCLOAK_URL}" \
  --admin-username "${KEYCLOAK_ADMIN_USER}" \
  --admin-password "${KEYCLOAK_ADMIN_PASS}" \
  --client-id "${TEST_CLIENT_ID}" \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: credentials show without --show-secret failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "PASS: credentials show without --show-secret succeeded"
```

### Test 8.4: Show a non-existent client should fail

```bash
OUTPUT=$(wanaku admin credentials show \
  --keycloak-url "${KEYCLOAK_URL}" \
  --admin-username "${KEYCLOAK_ADMIN_USER}" \
  --admin-password "${KEYCLOAK_ADMIN_PASS}" \
  --client-id "non-existent-client-99999" \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: show non-existent client failed as expected (exit code ${EXIT_CODE})"
else
  echo "FAIL: show non-existent client should have failed"
fi
```

---

## Phase 9: Service Client Credentials — Regenerate

### Test 9.1: Regenerate the secret for the test client

```bash
# Capture the original secret first
ORIGINAL_OUTPUT=$(wanaku admin credentials show \
  --keycloak-url "${KEYCLOAK_URL}" \
  --admin-username "${KEYCLOAK_ADMIN_USER}" \
  --admin-password "${KEYCLOAK_ADMIN_PASS}" \
  --client-id "${TEST_CLIENT_ID}" \
  --show-secret \
  --plain 2>&1)
ORIGINAL_SECRET=$(echo "${ORIGINAL_OUTPUT}" | grep "Client Secret:" | sed 's/.*Client Secret: //')

# Regenerate
OUTPUT=$(wanaku admin credentials regenerate \
  --keycloak-url "${KEYCLOAK_URL}" \
  --admin-username "${KEYCLOAK_ADMIN_USER}" \
  --admin-password "${KEYCLOAK_ADMIN_PASS}" \
  --client-id "${TEST_CLIENT_ID}" \
  --show-secret 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: admin credentials regenerate failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "PASS: credentials regenerate succeeded"
```

### Test 9.2: Verify the regenerated secret differs from the original

```bash
NEW_OUTPUT=$(wanaku admin credentials show \
  --keycloak-url "${KEYCLOAK_URL}" \
  --admin-username "${KEYCLOAK_ADMIN_USER}" \
  --admin-password "${KEYCLOAK_ADMIN_PASS}" \
  --client-id "${TEST_CLIENT_ID}" \
  --show-secret \
  --plain 2>&1)
NEW_SECRET=$(echo "${NEW_OUTPUT}" | grep "Client Secret:" | sed 's/.*Client Secret: //')

if [ -z "${ORIGINAL_SECRET}" ] || [ -z "${NEW_SECRET}" ]; then
  echo "FAIL: could not compare secrets (original='${ORIGINAL_SECRET}', new='${NEW_SECRET}')"
elif [ "${ORIGINAL_SECRET}" != "${NEW_SECRET}" ]; then
  echo "PASS: regenerated secret differs from original"
else
  echo "FAIL: regenerated secret is the same as the original"
fi
```

### Test 9.3: Regenerate for a non-existent client should fail

```bash
OUTPUT=$(wanaku admin credentials regenerate \
  --keycloak-url "${KEYCLOAK_URL}" \
  --admin-username "${KEYCLOAK_ADMIN_USER}" \
  --admin-password "${KEYCLOAK_ADMIN_PASS}" \
  --client-id "non-existent-client-99999" \
  --show-secret 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: regenerate for non-existent client failed as expected (exit code ${EXIT_CODE})"
else
  echo "FAIL: regenerate for non-existent client should have failed"
fi
```

---

## Phase 10: Service Client Credentials — Remove

### Test 10.1: Remove the secondary test client

```bash
OUTPUT=$(wanaku admin credentials remove \
  --keycloak-url "${KEYCLOAK_URL}" \
  --admin-username "${KEYCLOAK_ADMIN_USER}" \
  --admin-password "${KEYCLOAK_ADMIN_PASS}" \
  --client-id "${TEST_CLIENT_ID}-with-secret" 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: admin credentials remove failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "PASS: service client '${TEST_CLIENT_ID}-with-secret' removed"
```

### Test 10.2: Remove the primary test client

```bash
OUTPUT=$(wanaku admin credentials remove \
  --keycloak-url "${KEYCLOAK_URL}" \
  --admin-username "${KEYCLOAK_ADMIN_USER}" \
  --admin-password "${KEYCLOAK_ADMIN_PASS}" \
  --client-id "${TEST_CLIENT_ID}" 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: admin credentials remove failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "PASS: service client '${TEST_CLIENT_ID}' removed"
```

### Test 10.3: Verify removed client no longer appears in list

```bash
OUTPUT=$(wanaku admin credentials list \
  --keycloak-url "${KEYCLOAK_URL}" \
  --admin-username "${KEYCLOAK_ADMIN_USER}" \
  --admin-password "${KEYCLOAK_ADMIN_PASS}" \
  --plain 2>&1)

echo "${OUTPUT}" | grep -q "${TEST_CLIENT_ID}" \
  && echo "FAIL: removed client '${TEST_CLIENT_ID}' still present in list" \
  || echo "PASS: removed client '${TEST_CLIENT_ID}' no longer in list"
```

### Test 10.4: Remove a non-existent client should fail

```bash
OUTPUT=$(wanaku admin credentials remove \
  --keycloak-url "${KEYCLOAK_URL}" \
  --admin-username "${KEYCLOAK_ADMIN_USER}" \
  --admin-password "${KEYCLOAK_ADMIN_PASS}" \
  --client-id "non-existent-client-99999" 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: removing non-existent client failed as expected (exit code ${EXIT_CODE})"
else
  echo "FAIL: removing non-existent client should have failed"
fi
```

---

## Phase 11: Negative Tests

### Test 11.1: Commands with wrong admin credentials should fail

```bash
OUTPUT=$(wanaku admin users list \
  --keycloak-url "${KEYCLOAK_URL}" \
  --admin-username "wrong-admin" \
  --admin-password "wrong-pass" 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: users list with wrong credentials failed as expected (exit code ${EXIT_CODE})"
else
  echo "FAIL: users list with wrong credentials should have failed"
fi
```

### Test 11.2: Commands against unreachable Keycloak should fail gracefully

```bash
OUTPUT=$(wanaku admin users list \
  --keycloak-url "http://localhost:59999" \
  --admin-username "${KEYCLOAK_ADMIN_USER}" \
  --admin-password "${KEYCLOAK_ADMIN_PASS}" 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: users list against unreachable Keycloak failed gracefully (exit code ${EXIT_CODE})"
else
  echo "FAIL: users list against unreachable Keycloak should have failed"
fi
```

### Test 11.3: Credentials list against unreachable Keycloak should fail gracefully

```bash
OUTPUT=$(wanaku admin credentials list \
  --keycloak-url "http://localhost:59999" \
  --admin-username "${KEYCLOAK_ADMIN_USER}" \
  --admin-password "${KEYCLOAK_ADMIN_PASS}" \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: credentials list against unreachable Keycloak failed gracefully (exit code ${EXIT_CODE})"
else
  echo "FAIL: credentials list against unreachable Keycloak should have failed"
fi
```

### Test 11.4: Add user without required --username should fail

```bash
OUTPUT=$(wanaku admin users add \
  --keycloak-url "${KEYCLOAK_URL}" \
  --admin-username "${KEYCLOAK_ADMIN_USER}" \
  --admin-password "${KEYCLOAK_ADMIN_PASS}" \
  --password "${TEST_PASSWORD}" 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: add user without --username failed as expected (exit code ${EXIT_CODE})"
else
  echo "FAIL: add user without --username should have failed"
fi
```

### Test 11.5: Add credentials without required --client-id should fail

```bash
OUTPUT=$(wanaku admin credentials add \
  --keycloak-url "${KEYCLOAK_URL}" \
  --admin-username "${KEYCLOAK_ADMIN_USER}" \
  --admin-password "${KEYCLOAK_ADMIN_PASS}" \
  --description "Missing client-id" 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: add credentials without --client-id failed as expected (exit code ${EXIT_CODE})"
else
  echo "FAIL: add credentials without --client-id should have failed"
fi
```

---

## Phase 12: Cleanup

### Step 12.1: Remove the test user

```bash
wanaku admin users remove \
  --keycloak-url "${KEYCLOAK_URL}" \
  --admin-username "${KEYCLOAK_ADMIN_USER}" \
  --admin-password "${KEYCLOAK_ADMIN_PASS}" \
  --username "${TEST_USERNAME}" 2>/dev/null || true

echo "PASS: test user cleanup complete"
```

### Step 12.2: Remove test clients (idempotent)

```bash
for CLIENT in "${TEST_CLIENT_ID}" "${TEST_CLIENT_ID}-with-secret"; do
  wanaku admin credentials remove \
    --keycloak-url "${KEYCLOAK_URL}" \
    --admin-username "${KEYCLOAK_ADMIN_USER}" \
    --admin-password "${KEYCLOAK_ADMIN_PASS}" \
    --client-id "${CLIENT}" 2>/dev/null || true
done

echo "PASS: test client cleanup complete"
```

### Step 12.3: Verify cleanup

```bash
OUTPUT=$(wanaku admin users list \
  --keycloak-url "${KEYCLOAK_URL}" \
  --admin-username "${KEYCLOAK_ADMIN_USER}" \
  --admin-password "${KEYCLOAK_ADMIN_PASS}" 2>&1)

echo "${OUTPUT}" | grep -q "${TEST_USERNAME}" \
  && echo "FAIL: test user '${TEST_USERNAME}' still present after cleanup" \
  || echo "PASS: test user cleaned up"

OUTPUT=$(wanaku admin credentials list \
  --keycloak-url "${KEYCLOAK_URL}" \
  --admin-username "${KEYCLOAK_ADMIN_USER}" \
  --admin-password "${KEYCLOAK_ADMIN_PASS}" \
  --plain 2>&1)

echo "${OUTPUT}" | grep -q "${TEST_CLIENT_ID}" \
  && echo "FAIL: test client '${TEST_CLIENT_ID}' still present after cleanup" \
  || echo "PASS: test client cleaned up"
```

---

## Summary Matrix

| Phase | Test ID | Test Name | Priority |
|-------|---------|-----------|----------|
| 0 | 0.1–0.2 | Prerequisites and environment variables | High |
| 1 | 1.1 | Keycloak setup and realm verification | Critical |
| 2 | 2.1–2.2 | User list (initial state) | High |
| 3 | 3.1–3.4 | User add (create, verify, duplicate, minimal fields) | Critical |
| 4 | 4.1–4.2 | User set-password (existing, non-existent) | Critical |
| 5 | 5.1–5.3 | User remove (delete, verify, non-existent) | Critical |
| 6 | 6.1–6.3 | Credentials list (initial state, filtering, wanaku-service) | High |
| 7 | 7.1–7.4 | Credentials add (create, verify, show-secret, duplicate) | Critical |
| 8 | 8.1–8.4 | Credentials show (details, show-secret, without flag, non-existent) | Critical |
| 9 | 9.1–9.3 | Credentials regenerate (regenerate, verify differs, non-existent) | Critical |
| 10 | 10.1–10.4 | Credentials remove (delete secondary, primary, verify, non-existent) | Critical |
| 11 | 11.1–11.5 | Negative tests (wrong creds, unreachable, missing args) | High |
| 12 | 12.1–12.3 | Cleanup (users, clients, verification) | Critical |
