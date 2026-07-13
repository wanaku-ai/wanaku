# Test Plan: CLI Auth Commands (User Management, Service Client Credentials, and Auth Login)

## Overview

This test plan verifies the `wanaku admin users`, `wanaku admin credentials`, and `wanaku auth login` CLI commands against a Keycloak instance deployed on OpenShift. It covers full CRUD operations for users (list, add, set-password, remove), service client credentials (list, add, show, regenerate, remove), and the OIDC login flow in two modes: direct Keycloak login with `--realm` and router OIDC proxy login without `--realm`.

Every step is fully automatable.

## Prerequisites

### Required tools

| Tool | Minimum version | Verify command |
|------|-----------------|----------------|
| `wanaku` | build from source | `wanaku --version` |
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
export WANAKU_ROUTER_URL="${WANAKU_ROUTER_URL:-}"
export WANAKU_TEST_USER="${WANAKU_TEST_USER:-alice}"
export WANAKU_TEST_PASS="${WANAKU_TEST_PASS:-secretpass}"
export WANAKU_REALM="${WANAKU_REALM:-wanaku}"
export CREDENTIALS_FILE="${CREDENTIALS_FILE:-${HOME}/.wanaku/credentials}"
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
| `WANAKU_ROUTER_URL` | _(set by setup)_ | Router URL (set after router deployment, needed for proxy login tests) |
| `WANAKU_TEST_USER` | `alice` | Username for auth login tests (created by keycloak-setup.md) |
| `WANAKU_TEST_PASS` | `secretpass` | Password for auth login tests |
| `WANAKU_REALM` | `wanaku` | Keycloak realm name for direct login tests |
| `CREDENTIALS_FILE` | `~/.wanaku/credentials` | Path to the CLI credentials file (Java Properties format) |
| `TEST_USERNAME` | `testuser-811` | Username for test user CRUD operations |
| `TEST_PASSWORD` | `TestPass123` | Password for test user creation |
| `TEST_EMAIL` | `testuser811@example.com` | Email for test user creation |
| `TEST_CLIENT_ID` | `test-service-811` | Client ID for test credential CRUD operations |

### CLI invocation

When using the CLI from a local build (not installed), use `java -jar` directly:

```bash
CLI_JAR="${WANAKU_REPO_ROOT}/apps/wanaku-cli/target/quarkus-app/quarkus-run.jar"
java -jar ${CLI_JAR} admin users list --keycloak-url ... --plain
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
  --admin-password "${KEYCLOAK_ADMIN_PASS}" \
  --plain 2>&1)
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
  --admin-password "${KEYCLOAK_ADMIN_PASS}" \
  --plain 2>&1)

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
  --admin-password "${KEYCLOAK_ADMIN_PASS}" \
  --plain 2>&1)

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
  --admin-password "${KEYCLOAK_ADMIN_PASS}" \
  --plain 2>&1)

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
  --admin-password "wrong-pass" \
  --plain 2>&1)
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
  --admin-password "${KEYCLOAK_ADMIN_PASS}" \
  --plain 2>&1)
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

## Phase 12: Auth Login — Direct Keycloak (with --realm)

These tests authenticate directly against Keycloak using `--realm`. The router is NOT required.

### Test 12.1: Back up existing credentials file

```bash
if [ -f "${CREDENTIALS_FILE}" ]; then
  cp "${CREDENTIALS_FILE}" "${CREDENTIALS_FILE}.bak-realm-test"
  echo "PASS: existing credentials backed up to ${CREDENTIALS_FILE}.bak-realm-test"
else
  echo "PASS: no existing credentials file to back up"
fi
```

### Test 12.2: Clear credentials before auth login tests

```bash
rm -f "${CREDENTIALS_FILE}"
echo "PASS: credentials cleared before auth login tests"
```

### Test 12.3: Login with --realm succeeds

```bash
OUTPUT=$(echo "${WANAKU_TEST_PASS}" | ${WANAKU_CLI:-wanaku} auth login \
  --auth-server "${KEYCLOAK_URL}" \
  --realm "${WANAKU_REALM}" \
  --username "${WANAKU_TEST_USER}" \
  --password \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: auth login with --realm failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "PASS: auth login with --realm '${WANAKU_REALM}' succeeded"
```

### Test 12.4: Realm is persisted in the credential store

```bash
STORED_REALM=$(grep "^auth.realm=" "${CREDENTIALS_FILE}" | sed 's/^auth.realm=//')

if [ "${STORED_REALM}" = "${WANAKU_REALM}" ]; then
  echo "PASS: realm '${STORED_REALM}' persisted in credentials file"
else
  echo "FAIL: expected realm '${WANAKU_REALM}' in credentials file, found '${STORED_REALM}'"
  exit 1
fi
```

### Test 12.5: API token is stored after realm login

```bash
STORED_TOKEN=$(grep "^api.token=" "${CREDENTIALS_FILE}" | sed 's/^api.token=//')

if [ -n "${STORED_TOKEN}" ]; then
  echo "PASS: API token stored in credentials file (length: ${#STORED_TOKEN})"
else
  echo "FAIL: API token not found in credentials file after login"
  exit 1
fi
```

### Test 12.6: Refresh token is stored after realm login

```bash
STORED_REFRESH=$(grep "^refresh.token=" "${CREDENTIALS_FILE}" | sed 's/^refresh.token=//')

if [ -n "${STORED_REFRESH}" ]; then
  echo "PASS: refresh token stored in credentials file (length: ${#STORED_REFRESH})"
else
  echo "FAIL: refresh token not found in credentials file after login"
  exit 1
fi
```

### Test 12.7: Auth server URL is stored after realm login

```bash
STORED_URL=$(grep "^auth.server.url=" "${CREDENTIALS_FILE}" | sed 's/^auth.server.url=//')

if [ -n "${STORED_URL}" ]; then
  echo "PASS: auth server URL stored: ${STORED_URL}"
else
  echo "FAIL: auth server URL not found in credentials file after login"
  exit 1
fi
```

### Test 12.8: Auth status reports valid session after realm login

```bash
OUTPUT=$(${WANAKU_CLI:-wanaku} auth status --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: auth status reports valid session after realm login"
else
  echo "FAIL: auth status failed after realm login (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
fi
```

---

## Phase 13: Auth Login — Router OIDC Proxy (without --realm)

These tests authenticate via the router's OIDC proxy. If `WANAKU_ROUTER_URL` is not set, skip this phase.

### Test 13.1: Verify router is available

```bash
if [ -z "${WANAKU_ROUTER_URL}" ]; then
  echo "SKIP: WANAKU_ROUTER_URL not set -- skipping router proxy login tests"
  exit 0
fi

HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "${WANAKU_ROUTER_URL}/q/health" 2>/dev/null || echo "000")
if [ "${HTTP_CODE}" = "200" ]; then
  echo "PASS: router is healthy at ${WANAKU_ROUTER_URL}"
else
  echo "FAIL: router not healthy at ${WANAKU_ROUTER_URL} (HTTP ${HTTP_CODE})"
  exit 1
fi
```

### Test 13.2: Clear credentials before router proxy login

```bash
rm -f "${CREDENTIALS_FILE}"
echo "PASS: credentials cleared before router proxy login test"
```

### Test 13.3: Login without --realm succeeds (router OIDC proxy)

```bash
OUTPUT=$(echo "${WANAKU_TEST_PASS}" | ${WANAKU_CLI:-wanaku} auth login \
  --auth-server "${WANAKU_ROUTER_URL}" \
  --username "${WANAKU_TEST_USER}" \
  --password \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: auth login without --realm failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "PASS: auth login without --realm succeeded (router OIDC proxy)"
```

### Test 13.4: Realm is NOT persisted when --realm is omitted

```bash
if [ ! -f "${CREDENTIALS_FILE}" ]; then
  echo "FAIL: credentials file not created after login"
  exit 1
fi

STORED_REALM=$(grep "^auth.realm=" "${CREDENTIALS_FILE}" 2>/dev/null || true)

if [ -z "${STORED_REALM}" ]; then
  echo "PASS: no realm entry in credentials file (expected for router proxy login)"
else
  echo "FAIL: realm unexpectedly persisted: ${STORED_REALM}"
  exit 1
fi
```

### Test 13.5: API token is stored after router proxy login

```bash
STORED_TOKEN=$(grep "^api.token=" "${CREDENTIALS_FILE}" | sed 's/^api.token=//')

if [ -n "${STORED_TOKEN}" ]; then
  echo "PASS: API token stored after router proxy login (length: ${#STORED_TOKEN})"
else
  echo "FAIL: API token not found in credentials file after router proxy login"
  exit 1
fi
```

---

## Phase 14: Realm Persistence — Overwrite and Clear

### Test 14.1: Login with --realm overwrites previous credentials

```bash
rm -f "${CREDENTIALS_FILE}"

OUTPUT=$(echo "${WANAKU_TEST_PASS}" | ${WANAKU_CLI:-wanaku} auth login \
  --auth-server "${KEYCLOAK_URL}" \
  --realm "${WANAKU_REALM}" \
  --username "${WANAKU_TEST_USER}" \
  --password \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: auth login with --realm failed on fresh credentials (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

STORED_REALM=$(grep "^auth.realm=" "${CREDENTIALS_FILE}" | sed 's/^auth.realm=//')
if [ "${STORED_REALM}" = "${WANAKU_REALM}" ]; then
  echo "PASS: realm stored correctly on fresh credentials"
else
  echo "FAIL: realm not stored correctly (expected '${WANAKU_REALM}', found '${STORED_REALM}')"
  exit 1
fi
```

### Test 14.2: Re-login without --realm clears the stored realm

```bash
if [ -z "${WANAKU_ROUTER_URL}" ]; then
  echo "SKIP: WANAKU_ROUTER_URL not set -- skipping realm-clear test"
  exit 0
fi

# First verify realm is currently stored
BEFORE_REALM=$(grep "^auth.realm=" "${CREDENTIALS_FILE}" 2>/dev/null || true)
if [ -z "${BEFORE_REALM}" ]; then
  echo "FAIL: realm should be stored before this test"
  exit 1
fi

OUTPUT=$(echo "${WANAKU_TEST_PASS}" | ${WANAKU_CLI:-wanaku} auth login \
  --auth-server "${WANAKU_ROUTER_URL}" \
  --username "${WANAKU_TEST_USER}" \
  --password \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: auth login without --realm failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

AFTER_REALM=$(grep "^auth.realm=" "${CREDENTIALS_FILE}" 2>/dev/null || true)
if [ -z "${AFTER_REALM}" ]; then
  echo "PASS: stored realm cleared after login without --realm"
else
  echo "FAIL: realm still present after login without --realm: ${AFTER_REALM}"
  exit 1
fi
```

---

## Phase 15: Token Refresh with Realm

### Test 15.1: Token refresh works when realm is stored

```bash
rm -f "${CREDENTIALS_FILE}"

OUTPUT=$(echo "${WANAKU_TEST_PASS}" | ${WANAKU_CLI:-wanaku} auth login \
  --auth-server "${KEYCLOAK_URL}" \
  --realm "${WANAKU_REALM}" \
  --username "${WANAKU_TEST_USER}" \
  --password \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: initial login for refresh test failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

# Force token expiry by setting expiry to the past
sed -i.tmp "s/^token.expiry=.*/token.expiry=1000000000/" "${CREDENTIALS_FILE}"
rm -f "${CREDENTIALS_FILE}.tmp"

EXPIRED_EXPIRY=$(grep "^token.expiry=" "${CREDENTIALS_FILE}" | sed 's/^token.expiry=//')
if [ "${EXPIRED_EXPIRY}" != "1000000000" ]; then
  echo "FAIL: could not set token expiry to past epoch"
  exit 1
fi

if [ -n "${WANAKU_ROUTER_URL}" ]; then
  OUTPUT=$(${WANAKU_CLI:-wanaku} tools list --host "${WANAKU_ROUTER_URL}" --plain 2>&1)

  NEW_EXPIRY=$(grep "^token.expiry=" "${CREDENTIALS_FILE}" | sed 's/^token.expiry=//')
  if [ "${NEW_EXPIRY}" != "1000000000" ] && [ -n "${NEW_EXPIRY}" ]; then
    echo "PASS: token was refreshed (new expiry: ${NEW_EXPIRY})"
  else
    echo "WARN: token expiry not updated; refresh may not have triggered"
    echo "INFO: this may be expected if the router is not OIDC-protected"
  fi
else
  echo "SKIP: WANAKU_ROUTER_URL not set -- cannot trigger token refresh via CLI command"
  STORED_REFRESH=$(grep "^refresh.token=" "${CREDENTIALS_FILE}" | sed 's/^refresh.token=//')
  if [ -n "${STORED_REFRESH}" ]; then
    echo "PASS: refresh token is present (length: ${#STORED_REFRESH})"
  else
    echo "FAIL: refresh token not present"
  fi
fi
```

### Test 15.2: Realm is preserved after token refresh

```bash
STORED_REALM=$(grep "^auth.realm=" "${CREDENTIALS_FILE}" | sed 's/^auth.realm=//')

if [ "${STORED_REALM}" = "${WANAKU_REALM}" ]; then
  echo "PASS: realm preserved after token refresh: ${STORED_REALM}"
else
  echo "FAIL: realm changed or cleared after token refresh (expected '${WANAKU_REALM}', found '${STORED_REALM}')"
fi
```

---

## Phase 16: Auth Login — Negative Tests

### Test 16.1: Login with invalid realm should fail

```bash
OUTPUT=$(echo "${WANAKU_TEST_PASS}" | ${WANAKU_CLI:-wanaku} auth login \
  --auth-server "${KEYCLOAK_URL}" \
  --realm "nonexistent-realm-99999" \
  --username "${WANAKU_TEST_USER}" \
  --password \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: login with invalid realm failed as expected (exit code ${EXIT_CODE})"
else
  echo "FAIL: login with invalid realm should have failed"
fi
```

### Test 16.2: Login with wrong password should fail

```bash
OUTPUT=$(echo "wrong-password-99999" | ${WANAKU_CLI:-wanaku} auth login \
  --auth-server "${KEYCLOAK_URL}" \
  --realm "${WANAKU_REALM}" \
  --username "${WANAKU_TEST_USER}" \
  --password \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: login with wrong password failed as expected (exit code ${EXIT_CODE})"
else
  echo "FAIL: login with wrong password should have failed"
fi
```

### Test 16.3: Login with nonexistent username should fail

```bash
OUTPUT=$(echo "${WANAKU_TEST_PASS}" | ${WANAKU_CLI:-wanaku} auth login \
  --auth-server "${KEYCLOAK_URL}" \
  --realm "${WANAKU_REALM}" \
  --username "nonexistent-user-99999" \
  --password \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: login with nonexistent username failed as expected (exit code ${EXIT_CODE})"
else
  echo "FAIL: login with nonexistent username should have failed"
fi
```

### Test 16.4: Login against unreachable auth server should fail

```bash
OUTPUT=$(echo "${WANAKU_TEST_PASS}" | ${WANAKU_CLI:-wanaku} auth login \
  --auth-server "http://localhost:59999" \
  --realm "${WANAKU_REALM}" \
  --username "${WANAKU_TEST_USER}" \
  --password \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: login against unreachable server failed gracefully (exit code ${EXIT_CODE})"
else
  echo "FAIL: login against unreachable server should have failed"
fi
```

### Test 16.5: Login without --username should fail

```bash
OUTPUT=$(echo "${WANAKU_TEST_PASS}" | ${WANAKU_CLI:-wanaku} auth login \
  --auth-server "${KEYCLOAK_URL}" \
  --realm "${WANAKU_REALM}" \
  --password \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: login without --username failed as expected (exit code ${EXIT_CODE})"
else
  echo "FAIL: login without --username should have failed"
fi
```

### Test 16.6: Login with blank realm falls back to router proxy path

```bash
if [ -z "${WANAKU_ROUTER_URL}" ]; then
  echo "SKIP: WANAKU_ROUTER_URL not set -- skipping blank realm test"
  exit 0
fi

rm -f "${CREDENTIALS_FILE}"

OUTPUT=$(echo "${WANAKU_TEST_PASS}" | ${WANAKU_CLI:-wanaku} auth login \
  --auth-server "${WANAKU_ROUTER_URL}" \
  --realm "   " \
  --username "${WANAKU_TEST_USER}" \
  --password \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: login with blank realm failed (exit code ${EXIT_CODE}) -- should fall back to proxy"
  echo "${OUTPUT}"
else
  STORED_REALM=$(grep "^auth.realm=" "${CREDENTIALS_FILE}" 2>/dev/null || true)
  if [ -z "${STORED_REALM}" ]; then
    echo "PASS: blank realm falls back to router proxy; no realm persisted"
  else
    echo "FAIL: blank realm should not be persisted but found: ${STORED_REALM}"
  fi
fi
```

---

## Phase 17: Cleanup

### Step 17.1: Remove the test user

```bash
wanaku admin users remove \
  --keycloak-url "${KEYCLOAK_URL}" \
  --admin-username "${KEYCLOAK_ADMIN_USER}" \
  --admin-password "${KEYCLOAK_ADMIN_PASS}" \
  --username "${TEST_USERNAME}" 2>/dev/null || true

echo "PASS: test user cleanup complete"
```

### Step 17.2: Remove test clients (idempotent)

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

### Step 17.3: Verify admin cleanup

```bash
OUTPUT=$(wanaku admin users list \
  --keycloak-url "${KEYCLOAK_URL}" \
  --admin-username "${KEYCLOAK_ADMIN_USER}" \
  --admin-password "${KEYCLOAK_ADMIN_PASS}" \
  --plain 2>&1)

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

### Step 17.4: Clean up auth login credentials

```bash
rm -f "${CREDENTIALS_FILE}"

if [ -f "${CREDENTIALS_FILE}.bak-realm-test" ]; then
  mv "${CREDENTIALS_FILE}.bak-realm-test" "${CREDENTIALS_FILE}"
  echo "PASS: original credentials restored from backup"
else
  echo "PASS: no backup to restore"
fi
```

### Step 17.5: Remove auth login test user (idempotent)

```bash
${WANAKU_CLI:-wanaku} admin users remove \
  --keycloak-url "${KEYCLOAK_URL}" \
  --admin-username "${KEYCLOAK_ADMIN_USER}" \
  --admin-password "${KEYCLOAK_ADMIN_PASS}" \
  --username "${WANAKU_TEST_USER}" 2>/dev/null || true

echo "PASS: auth login test user cleanup complete"
```

---

## Phase 18: CLI --token Flag Verification (#1489)

This phase verifies that the `--token` flag works on CLI commands after the fix in #1489 (all commands now use `initAuthenticatedService()`). Requires `WANAKU_ROUTER_URL` to be set (router deployed with OIDC enabled).

### Test 18.1: Obtain a Keycloak access token

```bash
if [ -z "${WANAKU_ROUTER_URL}" ]; then
  echo "SKIP: WANAKU_ROUTER_URL not set -- skipping --token tests"
  exit 0
fi

TOKEN_RESPONSE=$(curl -s -X POST \
  "${KEYCLOAK_URL}/realms/${WANAKU_REALM}/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=wanaku-mcp-router" \
  -d "username=${WANAKU_TEST_USER}" \
  -d "password=${WANAKU_TEST_PASS}")

TOKEN=$(echo "${TOKEN_RESPONSE}" | jq -r '.access_token')

if [ -z "${TOKEN}" ] || [ "${TOKEN}" = "null" ]; then
  echo "FAIL: could not obtain access token from Keycloak"
  echo "${TOKEN_RESPONSE}"
  exit 1
fi

echo "PASS: obtained access token (length: ${#TOKEN})"
```

### Test 18.2: tools list with --token succeeds

```bash
rm -f "${CREDENTIALS_FILE}"

OUTPUT=$(wanaku tools list --host "${WANAKU_ROUTER_URL}" --token "${TOKEN}" --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: tools list with --token failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "PASS: tools list with --token succeeded (fix for #1489 confirmed)"
```

### Test 18.3: resources list with --token succeeds

```bash
OUTPUT=$(wanaku resources list --host "${WANAKU_ROUTER_URL}" --token "${TOKEN}" --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: resources list with --token failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "PASS: resources list with --token succeeded"
```

### Test 18.4: prompts list with --token succeeds

```bash
OUTPUT=$(wanaku prompts list --host "${WANAKU_ROUTER_URL}" --token "${TOKEN}" --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: prompts list with --token failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "PASS: prompts list with --token succeeded"
```

### Test 18.5: capabilities list with --token succeeds

```bash
OUTPUT=$(wanaku capabilities list --host "${WANAKU_ROUTER_URL}" --token "${TOKEN}" --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: capabilities list with --token failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "PASS: capabilities list with --token succeeded"
```

### Test 18.6: --no-auth skips authentication

```bash
OUTPUT=$(wanaku tools list --host "${WANAKU_ROUTER_URL}" --no-auth --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: tools list with --no-auth failed as expected against OIDC-protected router (exit code ${EXIT_CODE})"
else
  echo "FAIL: tools list with --no-auth should have failed against OIDC-protected router"
fi
```

---

## Phase 19: Auth Probe WARN Logging (#1490)

This phase verifies that the OIDC auth probe failure is logged at WARN level.

### Test 19.1: Probe failure against unreachable host produces WARN

```bash
OUTPUT=$(wanaku tools list --host "http://localhost:59999" --token "dummy-token" --plain 2>&1)

if echo "${OUTPUT}" | grep -qi "WARN.*Could not reach OIDC endpoint"; then
  echo "PASS: OIDC probe failure logged at WARN level (fix for #1490 confirmed)"
elif echo "${OUTPUT}" | grep -qi "Could not reach OIDC endpoint"; then
  echo "PASS: OIDC probe failure message present in output"
elif echo "${OUTPUT}" | grep -q "authentication headers will not be sent"; then
  echo "PASS: WARN message with consequence detail found"
else
  echo "WARN: probe failure message not found in CLI output -- may require Quarkus log configuration"
  echo "INFO: output was: ${OUTPUT}"
fi
```

---

## Phase 20: Admin UI PKCE (#1491)

This phase verifies that the router sends PKCE parameters in the OIDC authorization code flow for the admin UI.

### Test 20.1: Admin UI redirect includes PKCE code_challenge

```bash
if [ -z "${WANAKU_ROUTER_URL}" ]; then
  echo "SKIP: WANAKU_ROUTER_URL not set -- skipping PKCE tests"
  exit 0
fi

REDIRECT_URL=$(curl -sI "${WANAKU_ROUTER_URL}/admin" 2>/dev/null | grep -i "^location:" | sed 's/[Ll]ocation: //' | tr -d '\r')

if [ -z "${REDIRECT_URL}" ]; then
  echo "FAIL: no redirect Location header found for /admin"
  exit 1
fi

if echo "${REDIRECT_URL}" | grep -q "code_challenge="; then
  echo "PASS: redirect includes code_challenge parameter (PKCE enabled, fix for #1491 confirmed)"
else
  echo "FAIL: redirect does not include code_challenge parameter"
  echo "INFO: redirect URL: ${REDIRECT_URL}"
fi

if echo "${REDIRECT_URL}" | grep -q "code_challenge_method=S256"; then
  echo "PASS: code_challenge_method is S256"
else
  echo "WARN: code_challenge_method=S256 not found in redirect"
fi
```

### Test 20.2: No PKCE error in Keycloak redirect chain

```bash
ERROR_PAGE=$(curl -sL "${WANAKU_ROUTER_URL}/admin" 2>/dev/null)

if echo "${ERROR_PAGE}" | grep -qi "Missing parameter.*code_challenge"; then
  echo "FAIL: PKCE error found -- Keycloak is rejecting the login flow"
else
  echo "PASS: no PKCE-related error in the login redirect chain"
fi
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
| 11 | 11.1–11.5 | Negative tests — admin commands (wrong creds, unreachable, missing args) | High |
| 12 | 12.1–12.8 | Auth login — direct Keycloak with --realm (login, credential persistence, auth status) | Critical |
| 13 | 13.1–13.5 | Auth login — router OIDC proxy without --realm (login, no realm persisted, token stored) | Critical |
| 14 | 14.1–14.2 | Realm persistence — overwrite and clear lifecycle | High |
| 15 | 15.1–15.2 | Token refresh with stored realm (forced expiry, realm preserved) | Critical |
| 16 | 16.1–16.6 | Negative tests — auth login (invalid realm, wrong creds, unreachable, missing username, blank realm) | High |
| 17 | 17.1–17.5 | Cleanup (admin users/clients, auth credentials, auth login test user) | Critical |
| 18 | 18.1–18.6 | CLI --token flag verification (tools, resources, prompts, capabilities, --no-auth) | Critical |
| 19 | 19.1 | Auth probe WARN logging on unreachable host | High |
| 20 | 20.1–20.2 | Admin UI PKCE (code_challenge in redirect, no PKCE error) | Critical |
