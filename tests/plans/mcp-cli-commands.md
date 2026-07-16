# Test Plan: Wanaku MCP CLI Commands

## Overview

This test plan verifies the `wanaku mcp` CLI commands for interacting directly with MCP servers. It is split into two parts:

- **Part 1 — Unit-level validation:** Help output, required option enforcement, and basic command behavior against any MCP server.
- **Part 2 — End-to-end with real capabilities:** Full deployment on OpenShift with the operator, HTTP capability service, and real external APIs (currency conversion).

Every step except the initial `oc login` is fully automatable.

## Prerequisites

### Required tools

| Tool | Minimum version | Verify command |
|------|-----------------|----------------|
| `wanaku` | build from source | `wanaku --version` |
| `jq` | 1.6+ | `jq --version` |

### CLI invocation

When using the CLI from a local build (not installed), use `java -jar` directly:

```bash
CLI_JAR="apps/wanaku-cli/target/quarkus-app/quarkus-run.jar"
java -jar ${CLI_JAR} mcp tool list --uri ...
```

Do **not** assign the full command to a single variable (e.g., `WANAKU_CLI="java -jar path/to/jar"`) — zsh treats it as a single token. Use `CLI_JAR` for the path and call `java -jar ${CLI_JAR}` explicitly.

### Environment variables

```bash
export MCP_SERVER_URI="${MCP_SERVER_URI:-http://localhost:8080/public/mcp/sse}"
```

### MCP server setup

An MCP server must be running and accessible at `MCP_SERVER_URI`. Start the Wanaku stack locally:

```bash
# Build first
mvn -DskipTests -Pdist clean package

# Start the local stack (auth disabled automatically)
VERSION=$(cat core/core-util/target/classes/version.txt)
java -jar apps/wanaku-cli/target/quarkus-app/quarkus-run.jar start local \
  --local-dist apps/wanaku-router-backend/target/distributions/wanaku-router-backend-${VERSION}.zip \
  --local-dist capabilities/tools/wanaku-tool-service-http/target/distributions/wanaku-tool-service-http-${VERSION}.zip
```

Wait for the router health check (curl is used here specifically to test HTTP-level readiness):

```bash
curl -sf http://localhost:8080/q/health/ready > /dev/null && echo "READY" || echo "NOT READY"
```

### Known limitations for local testing

- **No resource providers in `wanaku start local`:** The local start command only supports tool services (`service-http`, `service-exec`, etc.). Resource providers (like `performancestaticfile`) are not available locally. Resource read tests (Phase 12) require a deployed environment.
- **Prompts are not registered by default:** No prompt providers ship with the base installation. Prompt list/get tests will return empty results locally.
- **MCP SSE path:** The public MCP endpoint is `/public/mcp/sse` (not `/mcp`). Using a wrong path will return connection errors.

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

## Part 1 Summary

| Phase | Test ID | Test Name | Priority |
|-------|---------|-----------|----------|
| 1 | 1.1-1.4 | Help and usage output | Medium |
| 2 | 2.1-2.4 | Required option validation | High |
| 3 | 3.1-3.4 | Tool list, call, multi-param, non-existent | Critical |
| 4 | 4.1-4.3 | Resource list, read, non-existent | Critical |
| 5 | 5.1-5.3 | Prompt list, get, non-existent | Critical |
| 6 | 6.1-6.2 | Connection error handling | High |

---
---

# Part 2 — End-to-End: MCP CLI with Real Capabilities on OpenShift

## Overview

This part deploys a full Wanaku stack via the operator (router + HTTP capability), registers real HTTP tools (currency conversion, cat facts), and verifies end-to-end tool invocation through the `wanaku mcp` CLI commands against the deployed router's MCP endpoints.

The first `oc login` step is manual; everything else is automatable.

## Prerequisites

### Required tools

| Tool | Minimum version | Verify command |
|------|-----------------|----------------|
| `oc` | 4.12+ | `oc version --client` |
| `helm` | 3.x | `helm version --short` |
| `wanaku` | build from source | `wanaku --version` |
| `jq` | 1.6+ | `jq --version` |

### Environment variables

```bash
export WANAKU_TEST_RUN_ID="${WANAKU_TEST_RUN_ID:-$(date +%Y%m%d-%H%M%S)-$$}"
export WANAKU_NAMESPACE="${WANAKU_NAMESPACE:-wanaku-mcp-${WANAKU_TEST_RUN_ID}}"
export WANAKU_REPO_ROOT="${WANAKU_REPO_ROOT:-.}"
export WANAKU_ROUTER_IMAGE="${WANAKU_ROUTER_IMAGE:-quay.io/wanaku/wanaku-router-backend:latest}"
export WANAKU_CAPABILITY_HTTP_IMAGE="${WANAKU_CAPABILITY_HTTP_IMAGE:-quay.io/wanaku/wanaku-tool-service-http:latest}"
export WANAKU_PROVIDER_STATIC_FILE_IMAGE="${WANAKU_PROVIDER_STATIC_FILE_IMAGE:-quay.io/wanaku/wanaku-provider-performance-static-file:latest}"
```

### Helper: wait for resource deletion

```bash
wait_for_deletion() {
  local RESOURCE_TYPE="$1"
  local RESOURCE_NAME="$2"
  local NAMESPACE="$3"
  local TIMEOUT="${4:-60}"
  local INTERVAL=3
  local ELAPSED=0

  while oc get "${RESOURCE_TYPE}" "${RESOURCE_NAME}" -n "${NAMESPACE}" > /dev/null 2>&1; do
    if [ "${ELAPSED}" -ge "${TIMEOUT}" ]; then
      echo "FAIL: ${RESOURCE_TYPE}/${RESOURCE_NAME} still exists after ${TIMEOUT}s"
      return 1
    fi
    sleep ${INTERVAL}
    ELAPSED=$((ELAPSED + INTERVAL))
  done
  echo "PASS: ${RESOURCE_TYPE}/${RESOURCE_NAME} deleted (${ELAPSED}s)"
  return 0
}
```

---

## Phase 7: OpenShift Login (MANUAL)

This is the only manual step in Part 2.

### Step 7.1: Log in to OpenShift

```bash
oc login <cluster-api-url> --username=<username> --password=<password>
```

### Step 7.2: Verify login

```bash
oc whoami
oc whoami --show-server
```

---

## Phase 8: Deploy the Wanaku Stack

### Step 8.1: Create namespace

Follow [common/namespace-setup.md](common/namespace-setup.md) to create and verify the namespace.

### Step 8.2: Deploy Keycloak

Follow [common/keycloak-setup.md](common/keycloak-setup.md). After completion, verify:

```bash
for VAR_NAME in KEYCLOAK_HOST KEYCLOAK_URL WANAKU_OIDC_SECRET; do
  eval "VAL=\${${VAR_NAME}}"
  if [ -z "${VAL}" ]; then
    echo "FAIL: ${VAR_NAME} is not set"
    exit 1
  fi
  echo "PASS: ${VAR_NAME} is set"
done
```

### Step 8.3: Install the operator

Follow [common/operator-deployment.md](common/operator-deployment.md) to install the operator via Helm.

### Step 8.4: Create WanakuRouter

```bash
cat <<EOF | oc apply -n "${WANAKU_NAMESPACE}" -f -
apiVersion: "wanaku.ai/v1alpha1"
kind: WanakuRouter
metadata:
  name: wanaku-mcp-test-router
spec:
  auth:
    authServer: "http://keycloak:8080"
    authProxy: "auto"
  router:
    image: ${WANAKU_ROUTER_IMAGE}
    imagePullPolicy: Always
EOF
```

**Wait for Ready:**

```bash
oc wait wanakurouter/wanaku-mcp-test-router \
  --for=condition=Ready \
  --timeout=120s \
  -n "${WANAKU_NAMESPACE}"
```

### Step 8.5: Create WanakuCapability (HTTP tool service)

```bash
cat <<EOF | oc apply -n "${WANAKU_NAMESPACE}" -f -
apiVersion: "wanaku.ai/v1alpha1"
kind: WanakuCapability
metadata:
  name: wanaku-mcp-test-capabilities
spec:
  auth:
    authServer: "http://keycloak:8080"
    authProxy: "auto"
  secrets:
    oidcCredentialsSecret: "${WANAKU_OIDC_SECRET}"
  routerRef: wanaku-mcp-test-router
  capabilities:
    - name: wanaku-http
      image: ${WANAKU_CAPABILITY_HTTP_IMAGE}
      imagePullPolicy: Always
    - name: wanaku-static-file
      image: ${WANAKU_PROVIDER_STATIC_FILE_IMAGE}
      imagePullPolicy: Always
EOF
```

**Wait for Ready:**

```bash
oc wait wanakucapability/wanaku-mcp-test-capabilities \
  --for=condition=Ready \
  --timeout=120s \
  -n "${WANAKU_NAMESPACE}"
```

### Step 8.6: Wait for all pods to be ready

```bash
oc wait --for=condition=ready pod -l app=wanaku-http \
  --timeout=120s -n "${WANAKU_NAMESPACE}"
echo "PASS: HTTP capability pod is ready"

oc wait --for=condition=ready pod -l app=wanaku-static-file \
  --timeout=120s -n "${WANAKU_NAMESPACE}"
echo "PASS: static file provider pod is ready"
```

### Step 8.7: Set router URL variables

```bash
export WANAKU_ROUTER_HOST=$(oc get route wanaku-mcp-test-router -n "${WANAKU_NAMESPACE}" -o jsonpath='{.spec.host}')
export WANAKU_ROUTER_URL="http://${WANAKU_ROUTER_HOST}"
export WANAKU_MCP_SSE_URI="${WANAKU_ROUTER_URL}/public/mcp/sse"

if [ -z "${WANAKU_ROUTER_HOST}" ]; then
  echo "FAIL: could not retrieve router host from route"
  exit 1
fi
echo "PASS: router URL is ${WANAKU_ROUTER_URL}"
echo "PASS: MCP SSE URI is ${WANAKU_MCP_SSE_URI}"
```

### Step 8.8: Wait for router health

```bash
MAX_RETRIES=24
RETRY_INTERVAL=5
for i in $(seq 1 ${MAX_RETRIES}); do
  HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "${WANAKU_ROUTER_URL}/q/health/ready" 2>/dev/null || echo "000")
  if [ "${HTTP_CODE}" = "200" ]; then
    echo "PASS: router is healthy (attempt ${i})"
    break
  fi
  if [ "${i}" -eq "${MAX_RETRIES}" ]; then
    echo "FAIL: router not healthy after ${MAX_RETRIES} attempts (last HTTP ${HTTP_CODE})"
    exit 1
  fi
  echo "Waiting for router... (attempt ${i}, HTTP ${HTTP_CODE})"
  sleep ${RETRY_INTERVAL}
done
```

### Step 8.9: Verify OIDC login via router

Follow [common/oidc-login-verification.md](common/oidc-login-verification.md) to verify end-to-end OIDC authentication through the router.

---

## Phase 9: Register Real HTTP Tools

### Test 9.1: Register the currency conversion tool

```bash
${WANAKU_CLI:-wanaku} tools add \
  --host "${WANAKU_ROUTER_URL}" \
  --no-auth \
  --name free-currency-conversion-api \
  --namespace public \
  --description "Free currency conversion API" \
  --uri "https://economia.awesomeapi.com.br/last/{firstCurrency}-{secondCurrency}" \
  --type http \
  --property "firstCurrency:string,The first currency (3 letter code, e.g.: USD, EUR)" \
  --property "secondCurrency:string,The second currency (3 letter code, e.g.: BRL, CZK)" \
  --required firstCurrency \
  --required secondCurrency

if [ $? -eq 0 ]; then
  echo "PASS: currency conversion tool registered"
else
  echo "FAIL: could not register currency conversion tool"
  exit 1
fi
```

### Test 9.2: Register the cat facts tool

```bash
${WANAKU_CLI:-wanaku} tools add \
  --host "${WANAKU_ROUTER_URL}" \
  --no-auth \
  --name meow-facts \
  --namespace public \
  --description "Retrieve random facts about cats" \
  --uri "https://meowfacts.herokuapp.com?count={count}" \
  --type http \
  --property "count:int,The number of facts to retrieve" \
  --required count

if [ $? -eq 0 ]; then
  echo "PASS: meow-facts tool registered"
else
  echo "FAIL: could not register meow-facts tool"
  exit 1
fi
```

### Test 9.3: Verify tools are listed via CLI

```bash
TOOLS_OUTPUT=$(wanaku tools list --host "${WANAKU_ROUTER_URL}" --no-auth --plain 2>&1)

echo "${TOOLS_OUTPUT}" | grep -q "free-currency-conversion-api" \
  && echo "PASS: currency conversion tool is listed" \
  || echo "FAIL: currency conversion tool not found"

echo "${TOOLS_OUTPUT}" | grep -q "meow-facts" \
  && echo "PASS: meow-facts tool is listed" \
  || echo "FAIL: meow-facts tool not found"
```

---

## Phase 10: List Tools via MCP CLI

### Test 10.1: List tools via MCP SSE endpoint

```bash
OUTPUT=$(wanaku mcp tool list --uri "${WANAKU_MCP_SSE_URI}" --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: wanaku mcp tool list failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "PASS: tool list succeeded"
echo "${OUTPUT}"

echo "${OUTPUT}" | grep -q "free-currency-conversion-api" \
  && echo "PASS: currency conversion tool visible via MCP" \
  || echo "FAIL: currency conversion tool not visible via MCP"

echo "${OUTPUT}" | grep -q "meow-facts" \
  && echo "PASS: meow-facts tool visible via MCP" \
  || echo "FAIL: meow-facts tool not visible via MCP"
```

---

## Phase 11: Invoke Real Tools via MCP CLI

### Test 11.1: Call the currency conversion tool (USD to EUR)

**Description:** Invoke the currency conversion API via the deployed HTTP capability and verify the response contains exchange rate data.

```bash
OUTPUT=$(wanaku mcp tool \
  --uri "${WANAKU_MCP_SSE_URI}" \
  --name free-currency-conversion-api \
  --param firstCurrency=USD \
  --param secondCurrency=EUR \
  --plain 2>&1)
EXIT_CODE=$?

echo "Exit code: ${EXIT_CODE}"
echo "Output: ${OUTPUT}"

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: currency conversion tool call failed"
  exit 1
fi

# The API returns JSON with currency pair data (e.g., "USDEUR")
echo "${OUTPUT}" | grep -qi "USD" \
  && echo "PASS: response contains currency data" \
  || echo "FAIL: response does not contain expected currency data"
```

### Test 11.2: Call the currency conversion tool (EUR to BRL)

**Description:** Verify a different currency pair also works, confirming parameter interpolation.

```bash
OUTPUT=$(wanaku mcp tool \
  --uri "${WANAKU_MCP_SSE_URI}" \
  --name free-currency-conversion-api \
  --param firstCurrency=EUR \
  --param secondCurrency=BRL \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: EUR-BRL conversion tool call failed"
  echo "${OUTPUT}"
  exit 1
fi

echo "${OUTPUT}" | grep -qi "EUR" \
  && echo "PASS: EUR-BRL response contains currency data" \
  || echo "FAIL: EUR-BRL response does not contain expected data"
```

### Test 11.3: Call the cat facts tool

**Description:** Invoke meow-facts and verify the response contains fact data.

```bash
OUTPUT=$(wanaku mcp tool \
  --uri "${WANAKU_MCP_SSE_URI}" \
  --name meow-facts \
  --param count=2 \
  --plain 2>&1)
EXIT_CODE=$?

echo "Exit code: ${EXIT_CODE}"
echo "Output: ${OUTPUT}"

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: meow-facts tool call failed"
  exit 1
fi

# Response should be non-empty and contain some data
if [ -n "${OUTPUT}" ]; then
  echo "PASS: meow-facts returned data"
else
  echo "FAIL: meow-facts returned empty response"
fi
```

### Test 11.4: Call a tool with missing required parameters

**Description:** The MCP server or capability should handle missing required parameters gracefully.

```bash
OUTPUT=$(wanaku mcp tool \
  --uri "${WANAKU_MCP_SSE_URI}" \
  --name free-currency-conversion-api \
  --param firstCurrency=USD \
  --plain 2>&1)
EXIT_CODE=$?

echo "Exit code: ${EXIT_CODE}"
# Missing secondCurrency — the API may return an error or the capability may reject it
if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: missing required parameter caused an error (expected)"
else
  echo "WARN: missing required parameter did not fail — check if API returned an error in the response body"
  echo "${OUTPUT}"
fi
```

---

## Phase 12: Register and Read Resources via MCP CLI

### Test 12.1: Register a resource backed by the static file provider

**Description:** Expose a resource via the CLI that is backed by the `performancestaticfile` provider type. The static file provider returns the fixed content `1234567890`.

```bash
${WANAKU_CLI:-wanaku} resources expose \
  --host "${WANAKU_ROUTER_URL}" \
  --no-auth \
  --location "performancestaticfile://test-file.txt" \
  --type performancestaticfile \
  --name "test-static-resource" \
  --namespace public \
  --description "A static file resource for MCP CLI testing" \
  --mimeType "text/plain"

if [ $? -eq 0 ]; then
  echo "PASS: static file resource registered"
else
  echo "FAIL: could not register static file resource"
  exit 1
fi
```

### Test 12.2: Verify resource is listed via CLI

```bash
RESOURCES_OUTPUT=$(wanaku resources list --host "${WANAKU_ROUTER_URL}" --no-auth --plain 2>&1)

echo "${RESOURCES_OUTPUT}" | grep -q "test-static-resource" \
  && echo "PASS: test-static-resource is listed" \
  || echo "FAIL: test-static-resource not found"
```

### Test 12.3: List resources via MCP SSE endpoint

```bash
OUTPUT=$(wanaku mcp resource list --uri "${WANAKU_MCP_SSE_URI}" --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: wanaku mcp resource list failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
  exit 1
fi

echo "PASS: resource list succeeded"
echo "${OUTPUT}"

echo "${OUTPUT}" | grep -q "test-static-resource" \
  && echo "PASS: test-static-resource visible via MCP" \
  || echo "FAIL: test-static-resource not visible via MCP"
```

### Test 12.4: Read the static file resource via MCP

**Description:** Read the registered resource through the MCP endpoint. The static file provider should return its fixed content (`1234567890`).

```bash
OUTPUT=$(wanaku mcp resource \
  --uri "${WANAKU_MCP_SSE_URI}" \
  --resource-uri "performancestaticfile://test-file.txt" \
  --plain 2>&1)
EXIT_CODE=$?

echo "Exit code: ${EXIT_CODE}"
echo "Output: ${OUTPUT}"

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: resource read failed (exit code ${EXIT_CODE})"
  exit 1
fi

echo "${OUTPUT}" | grep -q "1234567890" \
  && echo "PASS: resource content matches expected static data" \
  || echo "FAIL: resource content does not contain expected '1234567890'"
```

### Test 12.5: Read a non-existent resource via MCP

```bash
OUTPUT=$(wanaku mcp resource \
  --uri "${WANAKU_MCP_SSE_URI}" \
  --resource-uri "performancestaticfile://does-not-exist.txt" \
  --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "PASS: non-existent resource read failed as expected (exit code ${EXIT_CODE})"
else
  echo "WARN: non-existent resource read did not fail — server may return empty content"
  echo "${OUTPUT}"
fi
```

---

## Phase 13: List Prompts via MCP CLI

### Test 13.1: List prompts via MCP SSE endpoint

```bash
OUTPUT=$(wanaku mcp prompt list --uri "${WANAKU_MCP_SSE_URI}" --plain 2>&1)
EXIT_CODE=$?

if [ "${EXIT_CODE}" -ne 0 ]; then
  echo "FAIL: wanaku mcp prompt list failed (exit code ${EXIT_CODE})"
  echo "${OUTPUT}"
else
  echo "PASS: prompt list succeeded"
  echo "${OUTPUT}"
fi
```

---

## Phase 14: Cleanup

### Step 14.1: Remove registered tools and resources

```bash
${WANAKU_CLI:-wanaku} tools remove \
  --host "${WANAKU_ROUTER_URL}" \
  --no-auth \
  --name free-currency-conversion-api 2>/dev/null || true

${WANAKU_CLI:-wanaku} tools remove \
  --host "${WANAKU_ROUTER_URL}" \
  --no-auth \
  --name meow-facts 2>/dev/null || true

${WANAKU_CLI:-wanaku} resources remove \
  --host "${WANAKU_ROUTER_URL}" \
  --no-auth \
  --name test-static-resource 2>/dev/null || true

echo "PASS: tools and resources removed"
```

### Step 14.2: Delete capability and router CRs

```bash
oc delete wanakucapability wanaku-mcp-test-capabilities -n "${WANAKU_NAMESPACE}" --ignore-not-found=true
wait_for_deletion deployment wanaku-http "${WANAKU_NAMESPACE}" 60

oc delete wanakurouter wanaku-mcp-test-router -n "${WANAKU_NAMESPACE}" --ignore-not-found=true
wait_for_deletion deployment wanaku-mcp-test-router-mcp-router "${WANAKU_NAMESPACE}" 60
```

### Step 14.3: Full cleanup

Follow [common/cleanup.md](common/cleanup.md) for remaining teardown (Keycloak, operator, namespace).

---

## Full Test Summary Matrix

| Phase | Test ID | Test Name | Priority | Part |
|-------|---------|-----------|----------|------|
| 1 | 1.1-1.4 | Help and usage output | Medium | 1 |
| 2 | 2.1-2.4 | Required option validation | High | 1 |
| 3 | 3.1-3.4 | Tool list, call, multi-param, non-existent | Critical | 1 |
| 4 | 4.1-4.3 | Resource list, read, non-existent | Critical | 1 |
| 5 | 5.1-5.3 | Prompt list, get, non-existent | Critical | 1 |
| 6 | 6.1-6.2 | Connection error handling | High | 1 |
| 7 | 7.1-7.2 | OpenShift login (MANUAL) | Critical | 2 |
| 8 | 8.1-8.8 | Stack deployment (operator, router, capability) | Critical | 2 |
| 9 | 9.1-9.3 | Register real HTTP tools | Critical | 2 |
| 10 | 10.1 | List tools via MCP SSE endpoint | Critical | 2 |
| 11 | 11.1-11.4 | Invoke real tools (currency, cat facts, missing param) | Critical | 2 |
| 12 | 12.1-12.5 | Register, list, and read resources via MCP endpoint | Critical | 2 |
| 13 | 13.1 | List prompts via MCP endpoint | High | 2 |
| 14 | 14.1-14.3 | Cleanup | Critical | 2 |
