# Test Plan: CLI Service Catalog and Service Template Lifecycle

## Overview

This test plan verifies the full lifecycle of service catalogs and service templates via the `wanaku` CLI against a locally running Wanaku instance (no authentication). It covers initialization, route authoring, expose, package, deploy, listing, template operations, deployment instructions, negative tests, and cleanup.

Every step except the initial build is fully automatable.

## Prerequisites

### Required tools

| Tool | Minimum version | Verify command |
|------|-----------------|----------------|
| `wanaku` | build from source | `wanaku --version` |
| `jq` | 1.6+ | `jq --version` |
| `curl` | any | `curl --version` |
| `base64` | any (coreutils) | `base64 --version 2>/dev/null \|\| echo "available"` |

### Prerequisite check script

```bash
FAIL=0

for CMD in wanaku jq curl base64; do
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
java -jar ${CLI_JAR} service init --name ...
```

Do **not** assign the full command to a single variable (e.g., `WANAKU_CLI="java -jar path/to/jar"`) -- zsh treats it as a single token. Use `CLI_JAR` for the path and call `java -jar ${CLI_JAR}` explicitly.

### Environment variables

```bash
export WANAKU_REPO_ROOT="${WANAKU_REPO_ROOT:-.}"
export WANAKU_ROUTER_URL="${WANAKU_ROUTER_URL:-http://localhost:8080}"
export TEST_CATALOG_NAME="${TEST_CATALOG_NAME:-test-catalog}"
export TEST_CATALOG_DIR="${TEST_CATALOG_DIR:-/tmp/wanaku-test-catalog}"
export TEST_PACKAGE_OUTPUT="${TEST_PACKAGE_OUTPUT:-/tmp/test-catalog.b64}"
```

---

## Phase 1: Setup

Follow [common/start-local.md](common/start-local.md) to build and start a local Wanaku stack. After completion, `WANAKU_ROUTER_URL`, `WANAKU_PID`, and `CLI_JAR` must be set.

---

## Phase 2: Service Catalog -- Init

### Test 2.1: Initialize a new service catalog

```bash
cd "${TEST_CATALOG_DIR%/*}"
rm -rf "${TEST_CATALOG_DIR}" 2>/dev/null || true

wanaku service init --name="${TEST_CATALOG_NAME}" --services=system-a,system-b
EXIT_CODE=$?

if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: service init succeeded"
else
  echo "FAIL: service init failed (exit code ${EXIT_CODE})"
  exit 1
fi
```

### Test 2.2: Verify root directory and index.properties exist

```bash
CATALOG_DIR="${TEST_CATALOG_NAME}"

if [ ! -d "${CATALOG_DIR}" ]; then
  echo "FAIL: catalog directory '${CATALOG_DIR}' not created"
  exit 1
fi
echo "PASS: catalog directory exists"

if [ ! -f "${CATALOG_DIR}/index.properties" ]; then
  echo "FAIL: index.properties not found"
  exit 1
fi
echo "PASS: index.properties exists"
```

### Test 2.3: Verify service subdirectories were created

```bash
for SVC in system-a system-b; do
  if [ ! -d "${CATALOG_DIR}/${SVC}" ]; then
    echo "FAIL: service directory '${SVC}' not created"
    exit 1
  fi
  echo "PASS: ${SVC}/ directory exists"

  for EXT in camel.yaml wanaku-rules.yaml dependencies.txt; do
    FILE="${CATALOG_DIR}/${SVC}/${SVC}.${EXT}"
    if [ ! -f "${FILE}" ]; then
      echo "FAIL: ${FILE} not found"
    else
      echo "PASS: ${FILE} exists"
    fi
  done
done
```

### Test 2.4: Verify index.properties contains correct entries

```bash
INDEX="${CATALOG_DIR}/index.properties"

grep -q "catalog.name=${TEST_CATALOG_NAME}" "${INDEX}" \
  && echo "PASS: catalog.name is set" \
  || echo "FAIL: catalog.name not found or incorrect"

grep -q "catalog.services=" "${INDEX}" \
  && echo "PASS: catalog.services is set" \
  || echo "FAIL: catalog.services not found"

for SVC in system-a system-b; do
  grep -q "catalog.routes.${SVC}=" "${INDEX}" \
    && echo "PASS: catalog.routes.${SVC} is set" \
    || echo "FAIL: catalog.routes.${SVC} not found"

  grep -q "catalog.rules.${SVC}=" "${INDEX}" \
    && echo "PASS: catalog.rules.${SVC} is set" \
    || echo "FAIL: catalog.rules.${SVC} not found"
done
```

---

## Phase 3: Service Catalog -- Create Route Files

### Test 3.1: Write a Camel route into system-a

**Description:** Replace the skeleton route with a minimal route containing a known route ID.

```bash
cat > "${CATALOG_DIR}/system-a/system-a.camel.yaml" <<'ROUTE_EOF'
- route:
    id: get-system-a-data
    description: Retrieve data from system A
    from:
      uri: direct:get-system-a-data
      steps:
        - log:
            message: "Processing system-a request"
ROUTE_EOF

if [ -s "${CATALOG_DIR}/system-a/system-a.camel.yaml" ]; then
  echo "PASS: system-a route file written"
else
  echo "FAIL: system-a route file is empty or missing"
fi
```

### Test 3.2: Verify the route ID is present

```bash
grep -q "id: get-system-a-data" "${CATALOG_DIR}/system-a/system-a.camel.yaml" \
  && echo "PASS: route ID 'get-system-a-data' found in route file" \
  || echo "FAIL: route ID 'get-system-a-data' not found"
```

### Test 3.3: Write a Camel route into system-b

```bash
cat > "${CATALOG_DIR}/system-b/system-b.camel.yaml" <<'ROUTE_EOF'
- route:
    id: get-system-b-data
    description: Retrieve data from system B
    from:
      uri: direct:get-system-b-data
      steps:
        - log:
            message: "Processing system-b request"
ROUTE_EOF

if [ -s "${CATALOG_DIR}/system-b/system-b.camel.yaml" ]; then
  echo "PASS: system-b route file written"
else
  echo "FAIL: system-b route file is empty or missing"
fi
```

---

## Phase 4: Service Catalog -- Expose

### Test 4.1: Generate Wanaku rules from Camel routes

```bash
wanaku service expose --path="${CATALOG_DIR}" --namespace=default
EXIT_CODE=$?

if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: service expose succeeded"
else
  echo "FAIL: service expose failed (exit code ${EXIT_CODE})"
  exit 1
fi
```

### Test 4.2: Verify rules file was generated for system-a

```bash
RULES_FILE="${CATALOG_DIR}/system-a/system-a.wanaku-rules.yaml"

if [ ! -f "${RULES_FILE}" ]; then
  echo "FAIL: rules file not found: ${RULES_FILE}"
  exit 1
fi

grep -q "get-system-a-data" "${RULES_FILE}" \
  && echo "PASS: route ID 'get-system-a-data' present in rules file" \
  || echo "FAIL: route ID 'get-system-a-data' not found in rules file"

grep -q "namespace" "${RULES_FILE}" \
  && echo "PASS: namespace entry present in rules file" \
  || echo "FAIL: namespace entry not found in rules file"
```

### Test 4.3: Verify rules file was generated for system-b

```bash
RULES_FILE="${CATALOG_DIR}/system-b/system-b.wanaku-rules.yaml"

if [ ! -f "${RULES_FILE}" ]; then
  echo "FAIL: rules file not found: ${RULES_FILE}"
  exit 1
fi

grep -q "get-system-b-data" "${RULES_FILE}" \
  && echo "PASS: route ID 'get-system-b-data' present in rules file" \
  || echo "FAIL: route ID 'get-system-b-data' not found in rules file"
```

---

## Phase 5: Service Catalog -- Package

### Test 5.1: Package the service catalog

```bash
wanaku service package --path="${CATALOG_DIR}" -o "${TEST_PACKAGE_OUTPUT}"
EXIT_CODE=$?

if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: service package succeeded"
else
  echo "FAIL: service package failed (exit code ${EXIT_CODE})"
  exit 1
fi
```

### Test 5.2: Verify package output file exists and is non-empty

```bash
if [ ! -f "${TEST_PACKAGE_OUTPUT}" ]; then
  echo "FAIL: package output file not found: ${TEST_PACKAGE_OUTPUT}"
  exit 1
fi
echo "PASS: package output file exists"

FILE_SIZE=$(wc -c < "${TEST_PACKAGE_OUTPUT}" | tr -d ' ')
if [ "${FILE_SIZE}" -gt 0 ]; then
  echo "PASS: package output file is non-empty (${FILE_SIZE} bytes)"
else
  echo "FAIL: package output file is empty"
fi
```

### Test 5.3: Verify the output is valid Base64

```bash
base64 -d < "${TEST_PACKAGE_OUTPUT}" > /dev/null 2>&1
EXIT_CODE=$?

if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: package output is valid Base64"
else
  echo "FAIL: package output is not valid Base64 (exit code ${EXIT_CODE})"
fi
```

### Test 5.4: Verify decoded content is a valid ZIP

```bash
base64 -d < "${TEST_PACKAGE_OUTPUT}" > /tmp/test-catalog-verify.zip 2>/dev/null
file /tmp/test-catalog-verify.zip | grep -qi "zip" \
  && echo "PASS: decoded content is a ZIP archive" \
  || echo "FAIL: decoded content is not a ZIP archive"
rm -f /tmp/test-catalog-verify.zip
```

---

## Phase 6: Service Catalog -- Deploy

### Test 6.1: Deploy the service catalog to the router

```bash
wanaku service deploy --path="${CATALOG_DIR}" --host="${WANAKU_ROUTER_URL}"
EXIT_CODE=$?

if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: service deploy succeeded"
else
  echo "FAIL: service deploy failed (exit code ${EXIT_CODE})"
  exit 1
fi
```

---

## Phase 7: Service Catalog -- List and Verify

### Test 7.1: List deployed service catalogs

```bash
OUTPUT=$(wanaku service catalog list --host="${WANAKU_ROUTER_URL}" --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: service catalog list failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi
echo "PASS: service catalog list succeeded"
```

### Test 7.2: Verify the test catalog appears in the list

```bash
echo "${OUTPUT}" | grep -q "${TEST_CATALOG_NAME}" \
  && echo "PASS: '${TEST_CATALOG_NAME}' found in catalog list" \
  || echo "FAIL: '${TEST_CATALOG_NAME}' not found in catalog list"
```

### Test 7.3: Remove the test catalog by name

```bash
wanaku service catalog remove --host="${WANAKU_ROUTER_URL}" --name="${TEST_CATALOG_NAME}" 2>&1
EXIT_CODE=$?

if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: service catalog remove succeeded"
else
  echo "FAIL: service catalog remove failed (exit code ${EXIT_CODE})"
  exit 1
fi
```

### Test 7.4: Verify the catalog no longer appears in the list

```bash
OUTPUT=$(wanaku service catalog list --host="${WANAKU_ROUTER_URL}" --plain 2>&1)

echo "${OUTPUT}" | grep -q "${TEST_CATALOG_NAME}" \
  && echo "FAIL: '${TEST_CATALOG_NAME}' still found in catalog list after removal" \
  || echo "PASS: '${TEST_CATALOG_NAME}' no longer in catalog list"
```

### Test 7.5: Re-deploy the test catalog for subsequent phases

```bash
wanaku service deploy --path="${CATALOG_DIR}" --host="${WANAKU_ROUTER_URL}"
EXIT_CODE=$?

if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: service re-deploy succeeded"
else
  echo "FAIL: service re-deploy failed (exit code ${EXIT_CODE})"
  exit 1
fi
```

---

## Phase 8: Service Template -- List

### Test 8.1: List all available service templates

```bash
OUTPUT=$(wanaku service template list --host="${WANAKU_ROUTER_URL}" --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: service template list failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi
echo "PASS: service template list succeeded"
echo "${OUTPUT}"
```

### Test 8.2: Verify at least one built-in template is listed

```bash
FOUND_TEMPLATE=0
for TEMPLATE in activemq6-tool kafka-tool sql-tool github-pullrequest-source-tool; do
  if echo "${OUTPUT}" | grep -q "${TEMPLATE}"; then
    echo "PASS: built-in template '${TEMPLATE}' found"
    FOUND_TEMPLATE=1
  fi
done

if [ "${FOUND_TEMPLATE}" -eq 0 ]; then
  echo "FAIL: no built-in templates found in template list"
fi
```

### Test 8.3: List templates with search filter

```bash
FILTERED=$(wanaku service template list --host="${WANAKU_ROUTER_URL}" --search activemq --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: filtered template list failed (exit code ${EXIT_CODE})"
  echo "${FILTERED}"
  exit 1
fi
echo "PASS: filtered template list succeeded"

echo "${FILTERED}" | grep -q "activemq" \
  && echo "PASS: filtered results contain 'activemq'" \
  || echo "FAIL: filtered results do not contain 'activemq'"
```

---

## Phase 9: Service Template -- Instantiate

### Test 9.1: Instantiate a template with properties

**Description:** Instantiate the `activemq6-tool` template with test property values. This creates and deploys a service catalog from the template.

```bash
OUTPUT=$(wanaku service template instantiate \
  --host="${WANAKU_ROUTER_URL}" \
  --name activemq6-tool \
  --property broker.url=tcp://localhost:61616 \
  --property broker.username=admin \
  --property broker.password=admin \
  --property queue.name=test.queue 2>&1)
EXIT_CODE=$?

echo "${OUTPUT}"

if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: template instantiate succeeded"
else
  echo "FAIL: template instantiate failed (exit code ${EXIT_CODE})"
fi
```

### Test 9.2: Verify instantiated catalog appears in catalog list

```bash
CATALOG_OUTPUT=$(wanaku service catalog list --host="${WANAKU_ROUTER_URL}" --plain 2>&1)

echo "${CATALOG_OUTPUT}" | grep -qi "activemq" \
  && echo "PASS: instantiated activemq catalog found in catalog list" \
  || echo "WARN: instantiated activemq catalog not found in catalog list (may use a different name)"
```

---

## Phase 10: Service Catalog -- Deployment Instructions

### Test 10.1: Get local deployment instructions for the test catalog

```bash
OUTPUT=$(wanaku service instructions \
  --host="${WANAKU_ROUTER_URL}" \
  --name="${TEST_CATALOG_NAME}" \
  --model=local 2>&1)
EXIT_CODE=$?

echo "${OUTPUT}"

if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: deployment instructions returned"
else
  echo "FAIL: deployment instructions failed (exit code ${EXIT_CODE})"
fi
```

### Test 10.2: Verify instructions contain meaningful content

```bash
if [ -n "${OUTPUT}" ]; then
  echo "PASS: instructions output is non-empty"
else
  echo "FAIL: instructions output is empty"
fi
```

### Test 10.3: Get Kubernetes deployment instructions

```bash
K8S_OUTPUT=$(wanaku service instructions \
  --host="${WANAKU_ROUTER_URL}" \
  --name="${TEST_CATALOG_NAME}" \
  --model=kubernetes 2>&1)
EXIT_CODE=$?

echo "${K8S_OUTPUT}"

if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: kubernetes deployment instructions returned"
else
  echo "FAIL: kubernetes deployment instructions failed (exit code ${EXIT_CODE})"
fi
```

---

## Phase 11: Negative Tests

### Test 11.1: Init with missing --name should fail

```bash
wanaku service init --services=a,b 2>&1
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: init without --name rejected (exit code ${EXIT_CODE})"
else
  echo "FAIL: init without --name should fail"
  rm -rf a b 2>/dev/null || true
fi
```

### Test 11.2: Init with missing --services should fail

```bash
wanaku service init --name=should-fail 2>&1
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: init without --services rejected (exit code ${EXIT_CODE})"
else
  echo "FAIL: init without --services should fail"
  rm -rf should-fail 2>/dev/null || true
fi
```

### Test 11.3: Init when directory already exists should fail

```bash
wanaku service init --name="${TEST_CATALOG_NAME}" --services=x 2>&1
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: init with existing directory rejected (exit code ${EXIT_CODE})"
else
  echo "FAIL: init with existing directory should fail"
fi
```

### Test 11.4: Expose with invalid path should fail

```bash
wanaku service expose --path=/tmp/nonexistent-catalog-99999 2>&1
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: expose with invalid path rejected (exit code ${EXIT_CODE})"
else
  echo "FAIL: expose with invalid path should fail"
fi
```

### Test 11.5: Package with non-existent path should fail

```bash
wanaku service package --path=/tmp/nonexistent-catalog-99999 2>&1
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: package with non-existent path rejected (exit code ${EXIT_CODE})"
else
  echo "FAIL: package with non-existent path should fail"
fi
```

### Test 11.6: Deploy with unreachable host should fail

```bash
wanaku service deploy --path="${CATALOG_DIR}" --host="http://localhost:59999" 2>&1
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: deploy with unreachable host rejected (exit code ${EXIT_CODE})"
else
  echo "FAIL: deploy with unreachable host should fail"
fi
```

### Test 11.7: Template instantiate with non-existent template should fail

```bash
wanaku service template instantiate \
  --host="${WANAKU_ROUTER_URL}" \
  --name="nonexistent-template-99999" \
  --property key=value 2>&1
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: instantiate non-existent template rejected (exit code ${EXIT_CODE})"
else
  echo "FAIL: instantiate non-existent template should fail"
fi
```

### Test 11.8: Catalog list with unreachable host should fail

```bash
wanaku service catalog list --host="http://localhost:59999" 2>&1
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: catalog list with unreachable host rejected (exit code ${EXIT_CODE})"
else
  echo "FAIL: catalog list with unreachable host should fail"
fi
```

### Test 11.9: Instructions with missing --name should fail

```bash
wanaku service instructions --host="${WANAKU_ROUTER_URL}" --model=local 2>&1
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: instructions without --name rejected (exit code ${EXIT_CODE})"
else
  echo "FAIL: instructions without --name should fail"
fi
```

### Test 11.10: Instructions with missing --model should fail

```bash
wanaku service instructions --host="${WANAKU_ROUTER_URL}" --name="${TEST_CATALOG_NAME}" 2>&1
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: instructions without --model rejected (exit code ${EXIT_CODE})"
else
  echo "FAIL: instructions without --model should fail"
fi
```

### Test 11.11: Catalog remove with missing --name should fail

```bash
wanaku service catalog remove --host="${WANAKU_ROUTER_URL}" 2>&1
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: catalog remove without --name rejected (exit code ${EXIT_CODE})"
else
  echo "FAIL: catalog remove without --name should fail"
fi
```

### Test 11.12: Catalog remove with non-existent catalog should fail

```bash
wanaku service catalog remove --host="${WANAKU_ROUTER_URL}" --name="nonexistent-catalog-99999" 2>&1
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: catalog remove for non-existent catalog rejected (exit code ${EXIT_CODE})"
else
  echo "FAIL: catalog remove for non-existent catalog should fail"
fi
```

### Test 11.13: Catalog remove with unreachable host should fail

```bash
wanaku service catalog remove --host="http://localhost:59999" --name="${TEST_CATALOG_NAME}" 2>&1
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: catalog remove with unreachable host rejected (exit code ${EXIT_CODE})"
else
  echo "FAIL: catalog remove with unreachable host should fail"
fi
```

---

## Phase 12: Cleanup

### Step 12.1: Remove deployed catalogs via CLI

```bash
# Remove the test catalog
wanaku service catalog remove --host="${WANAKU_ROUTER_URL}" --name="${TEST_CATALOG_NAME}" 2>/dev/null || true
echo "Removed catalog: ${TEST_CATALOG_NAME}"

# Remove the instantiated activemq catalog (name may vary)
wanaku service catalog remove --host="${WANAKU_ROUTER_URL}" --name="activemq6-tool" 2>/dev/null || true
echo "Removed catalog: activemq6-tool (if present)"

echo "PASS: catalog cleanup complete"
```

### Step 12.2: Remove temporary files and directories

```bash
rm -rf "${TEST_CATALOG_DIR}" 2>/dev/null || true
rm -rf "${TEST_CATALOG_NAME}" 2>/dev/null || true
rm -f "${TEST_PACKAGE_OUTPUT}" 2>/dev/null || true
rm -rf should-fail 2>/dev/null || true

echo "PASS: temporary files removed"
```

### Step 12.3: Stop the local Wanaku instance

Follow the shutdown steps in [common/start-local.md](common/start-local.md):

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
| 1 | -- | Setup (build and start local) | Critical |
| 2 | 2.1-2.4 | Service catalog init and structure verification | Critical |
| 3 | 3.1-3.3 | Create Camel route files for services | Critical |
| 4 | 4.1-4.3 | Expose routes and generate rules | Critical |
| 5 | 5.1-5.4 | Package into Base64-encoded ZIP | High |
| 6 | 6.1 | Deploy to running router | Critical |
| 7 | 7.1-7.5 | List, remove, verify removal, and re-deploy catalog | Critical |
| 8 | 8.1-8.3 | List and filter service templates | High |
| 9 | 9.1-9.2 | Instantiate template into catalog | High |
| 10 | 10.1-10.3 | Deployment instructions (local and Kubernetes) | Medium |
| 11 | 11.1-11.13 | Negative tests (missing args, bad paths, unreachable host, catalog remove) | High |
| 12 | 12.1-12.3 | Cleanup (catalogs, temp files, process) | Critical |
