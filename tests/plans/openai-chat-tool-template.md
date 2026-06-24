# Test Plan: OpenAI Chat Tool Service Template (PR #1429)

## Overview

This test plan verifies the new `openai-chat-tool` service template added in PR #1429 (closes #1184). It covers template structure validation, listing, instantiation with property substitution, deployment as a service catalog, and end-to-end MCP tool invocation against a live OpenAI API.

Every step is fully automatable.

## Prerequisites

### Required tools

| Tool | Minimum version | Verify command |
|------|-----------------|----------------|
| `wanaku` | 0.2.0+ | `wanaku --version` |
| `curl` | any | `curl --version` |
| `jq` | 1.6+ | `jq --version` |
| `java` | 21+ | `java --version` |
| `mvn` | 3.9+ | `mvn --version` |
| `gh` | 2.x | `gh --version` |

### Prerequisite check script

```bash
#!/bin/bash
set -e

FAIL=0

for CMD in curl jq java mvn gh; do
  if ! command -v "${CMD}" > /dev/null 2>&1; then
    echo "FAIL: ${CMD} is not installed"
    FAIL=1
  else
    echo "PASS: ${CMD} found at $(command -v ${CMD})"
  fi
done

JAVA_MAJOR=$(java --version 2>&1 | head -1 | sed 's/.*"\([0-9]*\)\..*/\1/')
if [ "${JAVA_MAJOR}" -lt 21 ]; then
  echo "FAIL: Java 21+ required, found ${JAVA_MAJOR}"
  FAIL=1
else
  echo "PASS: Java ${JAVA_MAJOR}"
fi

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
export WANAKU_HOST="${WANAKU_HOST:-http://localhost:8080}"
export OPENAI_API_KEY="${OPENAI_API_KEY:-test-key-placeholder}"
export OPENAI_MODEL="${OPENAI_MODEL:-gpt-4}"
export OPENAI_ORG="${OPENAI_ORG:-}"
export MCP_SERVER_URI="${MCP_SERVER_URI:-http://localhost:8080/public/mcp/sse}"
```

### Build, re-augment, and start the router

> [!IMPORTANT]
> The router ships with OIDC authentication enabled. Running locally without a Keycloak instance requires
> Quarkus re-augmentation to disable OIDC at build time. The steps below handle this. Without re-augmentation
> the router will fail to start.

#### Step 1: Build the project and unzip the router distribution

```bash
mvn -DskipTests -Pdist clean package

VERSION=$(cat core/core-util/target/classes/version.txt)
ROUTER_ZIP="apps/wanaku-router-backend/target/distributions/wanaku-router-backend-${VERSION}.zip"
ROUTER_DIR="/tmp/wanaku-router"

rm -rf "${ROUTER_DIR}"
mkdir -p "${ROUTER_DIR}"
unzip -q "${ROUTER_ZIP}" -d "${ROUTER_DIR}"

ls "${ROUTER_DIR}/quarkus-app/quarkus-run.jar" > /dev/null \
  && echo "PASS: router distribution extracted" \
  || { echo "FAIL: quarkus-run.jar not found"; exit 1; }
```

#### Step 2: Re-augment to disable OIDC

```bash
java -Dquarkus.launch.rebuild=true \
     -Dquarkus.log.level=WARNING \
     -Dquarkus.oidc.enabled=false \
     -Dquarkus.oidc-proxy.enabled=false \
     -jar "${ROUTER_DIR}/quarkus-app/quarkus-run.jar"

echo "PASS: re-augmentation complete"
```

#### Step 3: Start the router

```bash
WANAKU_HTTP_AUTH=none java -Dquarkus.profile=local -jar "${ROUTER_DIR}/quarkus-app/quarkus-run.jar" &
ROUTER_PID=$!
echo "Router started with PID ${ROUTER_PID}"
```

Wait for the router health check:

```bash
for i in $(seq 1 30); do
  if curl -sf "${WANAKU_HOST}/q/health/ready" > /dev/null 2>&1; then
    echo "PASS: Router is ready"
    break
  fi
  sleep 2
done
curl -sf "${WANAKU_HOST}/q/health/ready" > /dev/null || { echo "FAIL: Router not ready after 60s"; exit 1; }
```

### Camel Integration Capability JAR (required for Phase 6)

Phase 6 (end-to-end invocation) requires the Camel Integration Capability fat JAR. Download it from the [early-access release](https://github.com/wanaku-ai/camel-integration-capability/releases/tag/early-access):

```bash
CIC_DIR="${CIC_DIR:-/tmp/camel-integration-capability}"
mkdir -p "${CIC_DIR}"

gh release download early-access \
  --repo wanaku-ai/camel-integration-capability \
  --pattern "camel-integration-capability-main-*-jar-with-dependencies.jar" \
  --dir "${CIC_DIR}" \
  --clobber

export CIC_JAR=$(ls "${CIC_DIR}"/camel-integration-capability-main-*-jar-with-dependencies.jar | head -1)
```

Verify:

```bash
if [ -n "${CIC_JAR}" ] && [ -f "${CIC_JAR}" ]; then
  echo "PASS: CIC_JAR found at ${CIC_JAR}"
else
  echo "WARN: CIC_JAR not found — Phase 6 (end-to-end) will be skipped"
fi
```

### CLI invocation

When using the CLI from a local build (not installed), use `java -jar` directly:

```bash
CLI_JAR="apps/wanaku-cli/target/quarkus-app/quarkus-run.jar"
java -jar ${CLI_JAR} service template list --host ${WANAKU_HOST}
```

Do **not** assign the full command to a single variable — zsh treats it as a single token. Use `CLI_JAR` for the path and call `java -jar ${CLI_JAR}` explicitly.

---

## Phase 1: Template File Structure Validation

Verify that all files in the `openai-chat-tool` template are present, well-formed, and consistent.

### Test 1.1: Verify template directory exists with all required files

```bash
TEMPLATE_DIR="services/service-templates/src/main/services/openai-chat-tool"

REQUIRED_FILES=(
  "index.properties"
  "openai-chat/openai-chat.camel.yaml"
  "openai-chat/openai-chat.dependencies.txt"
  "openai-chat/openai-chat.rules.yaml"
  "openai-chat/service.properties"
)

FAIL=0
for f in "${REQUIRED_FILES[@]}"; do
  if [ -f "${TEMPLATE_DIR}/${f}" ]; then
    echo "PASS: ${f} exists"
  else
    echo "FAIL: ${f} missing"
    FAIL=1
  fi
done

[ "${FAIL}" -eq 0 ] && echo "PASS: all required files present" || exit 1
```

### Test 1.2: Validate index.properties structure

```bash
TEMPLATE_DIR="services/service-templates/src/main/services/openai-chat-tool"
INDEX="${TEMPLATE_DIR}/index.properties"

grep -q "^catalog.name=openai-chat-tool" "${INDEX}" && echo "PASS: catalog.name" || echo "FAIL: catalog.name"
grep -q "^catalog.description=" "${INDEX}" && echo "PASS: catalog.description" || echo "FAIL: catalog.description"
grep -q "^catalog.services=openai-chat" "${INDEX}" && echo "PASS: catalog.services" || echo "FAIL: catalog.services"
grep -q "^catalog.routes.openai-chat=" "${INDEX}" && echo "PASS: catalog.routes" || echo "FAIL: catalog.routes"
grep -q "^catalog.rules.openai-chat=" "${INDEX}" && echo "PASS: catalog.rules" || echo "FAIL: catalog.rules"
grep -q "^catalog.dependencies.openai-chat=" "${INDEX}" && echo "PASS: catalog.dependencies" || echo "FAIL: catalog.dependencies"
grep -q "^catalog.properties.openai-chat=" "${INDEX}" && echo "PASS: catalog.properties" || echo "FAIL: catalog.properties"
```

### Test 1.3: Validate Camel route has required elements

```bash
ROUTE_FILE="services/service-templates/src/main/services/openai-chat-tool/openai-chat/openai-chat.camel.yaml"

grep -q "id: openai-chat-completion" "${ROUTE_FILE}" && echo "PASS: route ID present" || echo "FAIL: route ID missing"
grep -q "uri: direct:wanaku" "${ROUTE_FILE}" && echo "PASS: starts from direct:wanaku" || echo "FAIL: missing direct:wanaku"
grep -q "CamelOpenAI.operation" "${ROUTE_FILE}" && echo "PASS: sets OpenAI operation header" || echo "FAIL: missing operation header"
grep -q "CamelOpenAI.model" "${ROUTE_FILE}" && echo "PASS: sets model header" || echo "FAIL: missing model header"
grep -q "CamelOpenAI.temperature" "${ROUTE_FILE}" && echo "PASS: sets temperature header" || echo "FAIL: missing temperature header"
grep -q "CamelOpenAI.maxTokens" "${ROUTE_FILE}" && echo "PASS: sets maxTokens header" || echo "FAIL: missing maxTokens header"
grep -q 'uri: "openai:' "${ROUTE_FILE}" && echo "PASS: sends to openai endpoint" || echo "FAIL: missing openai endpoint"
```

### Test 1.4: Validate rules.yaml exposes an MCP tool

```bash
RULES_FILE="services/service-templates/src/main/services/openai-chat-tool/openai-chat/openai-chat.rules.yaml"

grep -q "mcp:" "${RULES_FILE}" && echo "PASS: mcp root" || echo "FAIL: missing mcp root"
grep -q "tools:" "${RULES_FILE}" && echo "PASS: tools section" || echo "FAIL: missing tools section"
grep -q "openai-chat-completion:" "${RULES_FILE}" && echo "PASS: tool name matches route ID" || echo "FAIL: tool name mismatch"
grep -q "wanaku_body" "${RULES_FILE}" && echo "PASS: wanaku_body property" || echo "FAIL: missing wanaku_body"
grep -q "required: true" "${RULES_FILE}" && echo "PASS: wanaku_body is required" || echo "FAIL: wanaku_body not required"
```

### Test 1.5: Validate dependencies.txt

```bash
DEPS_FILE="services/service-templates/src/main/services/openai-chat-tool/openai-chat/openai-chat.dependencies.txt"

grep -q "org.apache.camel:camel-openai" "${DEPS_FILE}" && echo "PASS: camel-openai dependency" || echo "FAIL: missing camel-openai"
```

### Test 1.6: Validate service.properties has parameterized placeholders

```bash
PROPS_FILE="services/service-templates/src/main/services/openai-chat-tool/openai-chat/service.properties"

grep -q '{{openai.api.key}}' "${PROPS_FILE}" && echo "PASS: api.key placeholder" || echo "FAIL: api.key not parameterized"
grep -q '{{openai.model}}' "${PROPS_FILE}" && echo "PASS: model placeholder" || echo "FAIL: model not parameterized"
grep -q '{{openai.organization}}' "${PROPS_FILE}" && echo "PASS: organization placeholder" || echo "FAIL: organization not parameterized"
grep -q "openai.operation=chatCompletion" "${PROPS_FILE}" && echo "PASS: operation default" || echo "FAIL: operation default missing"
grep -q "openai.temperature=0.7" "${PROPS_FILE}" && echo "PASS: temperature default" || echo "FAIL: temperature default missing"
grep -q "openai.max.tokens=1024" "${PROPS_FILE}" && echo "PASS: max.tokens default" || echo "FAIL: max.tokens default missing"
```

### Test 1.7: Verify route ID in rules matches route ID in Camel YAML

```bash
TEMPLATE_DIR="services/service-templates/src/main/services/openai-chat-tool"

ROUTE_ID=$(grep 'id:' "${TEMPLATE_DIR}/openai-chat/openai-chat.camel.yaml" | head -1 | awk '{print $2}')
RULES_ROUTE_ID=$(grep 'id:' "${TEMPLATE_DIR}/openai-chat/openai-chat.rules.yaml" | awk -F'"' '{print $2}')

if [ "${ROUTE_ID}" = "${RULES_ROUTE_ID}" ]; then
  echo "PASS: route IDs match (${ROUTE_ID})"
else
  echo "FAIL: route ID mismatch: camel=${ROUTE_ID}, rules=${RULES_ROUTE_ID}"
fi
```

---

## Phase 2: Template Listing via REST API

### Test 2.1: Template appears in the list endpoint

```bash
RESPONSE=$(curl -sf "${WANAKU_HOST}/api/v1/service-template/list" | jq '.')

echo "${RESPONSE}" | jq -e '.data[] | select(.name == "openai-chat-tool")' > /dev/null 2>&1 \
  && echo "PASS: openai-chat-tool in template list" \
  || echo "FAIL: openai-chat-tool not found in template list"
```

### Test 2.2: Template appears when searching by name

```bash
RESPONSE=$(curl -sf "${WANAKU_HOST}/api/v1/service-template/list?search=openai" | jq '.')

COUNT=$(echo "${RESPONSE}" | jq '.data | length')
echo "${RESPONSE}" | jq -e '.data[] | select(.name == "openai-chat-tool")' > /dev/null 2>&1 \
  && echo "PASS: search by 'openai' returns template" \
  || echo "FAIL: search by 'openai' did not return template"
```

### Test 2.3: Template detail via GET endpoint

```bash
RESPONSE=$(curl -sf "${WANAKU_HOST}/api/v1/service-template/get?name=openai-chat-tool" | jq '.')

echo "${RESPONSE}" | jq -e '.data.name == "openai-chat-tool"' > /dev/null 2>&1 \
  && echo "PASS: template name correct" \
  || echo "FAIL: template name mismatch"

echo "${RESPONSE}" | jq -e '.data.services | length > 0' > /dev/null 2>&1 \
  && echo "PASS: services not empty" \
  || echo "FAIL: services array empty"

echo "${RESPONSE}" | jq -r '.data.services[0].name' | grep -q "openai-chat" \
  && echo "PASS: service name is openai-chat" \
  || echo "FAIL: unexpected service name"
```

### Test 2.4: Template properties endpoint returns parameterized keys

```bash
RESPONSE=$(curl -sf "${WANAKU_HOST}/api/v1/service-template/properties?name=openai-chat-tool" | jq '.')

echo "${RESPONSE}" | jq -e '.data' > /dev/null 2>&1 \
  && echo "PASS: properties returned" \
  || echo "FAIL: no properties returned"

echo "${RESPONSE}" | jq -r '.data | keys[]' | grep -q "openai-chat" \
  && echo "PASS: openai-chat system found in properties" \
  || echo "FAIL: openai-chat system not in properties"
```

---

## Phase 3: Template Listing via CLI

### Test 3.1: CLI lists the template

```bash
CLI_JAR="apps/wanaku-cli/target/quarkus-app/quarkus-run.jar"
OUTPUT=$(java -jar ${CLI_JAR} service template list --host "${WANAKU_HOST}" 2>&1)

echo "${OUTPUT}" | grep -q "openai-chat-tool" \
  && echo "PASS: CLI lists openai-chat-tool" \
  || echo "FAIL: openai-chat-tool not in CLI output"
```

### Test 3.2: CLI search filters correctly

```bash
CLI_JAR="apps/wanaku-cli/target/quarkus-app/quarkus-run.jar"
OUTPUT=$(java -jar ${CLI_JAR} service template list --host "${WANAKU_HOST}" --search "openai" 2>&1)

echo "${OUTPUT}" | grep -q "openai-chat-tool" \
  && echo "PASS: CLI search returns openai-chat-tool" \
  || echo "FAIL: CLI search did not return openai-chat-tool"
```

---

## Phase 4: Template Instantiation

### Test 4.1: Instantiate with required properties via REST

```bash
RESPONSE=$(curl -sf -X POST "${WANAKU_HOST}/api/v1/service-template/instantiate" \
  -H "Content-Type: application/json" \
  -d '{
    "templateName": "openai-chat-tool",
    "properties": {
      "openai.api.key": "'"${OPENAI_API_KEY}"'",
      "openai.model": "'"${OPENAI_MODEL}"'",
      "openai.organization": "'"${OPENAI_ORG}"'"
    }
  }' | jq '.')

echo "${RESPONSE}" | jq -e '.data' > /dev/null 2>&1 \
  && echo "PASS: instantiation returned a catalog" \
  || echo "FAIL: instantiation failed"

echo "${RESPONSE}" | jq -e '.error == null or .error == ""' > /dev/null 2>&1 \
  && echo "PASS: no error in response" \
  || { echo "FAIL: error in response"; echo "${RESPONSE}" | jq '.error'; }
```

### Test 4.2: Instantiate with custom service name via REST

```bash
RESPONSE=$(curl -sf -X POST "${WANAKU_HOST}/api/v1/service-template/instantiate" \
  -H "Content-Type: application/json" \
  -d '{
    "templateName": "openai-chat-tool",
    "properties": {
      "openai.api.key": "'"${OPENAI_API_KEY}"'",
      "openai.model": "gpt-3.5-turbo",
      "openai.organization": ""
    },
    "serviceName": "my-custom-openai",
    "serviceSystem": "custom-openai-system"
  }' | jq '.')

echo "${RESPONSE}" | jq -e '.data' > /dev/null 2>&1 \
  && echo "PASS: instantiation with custom name succeeded" \
  || echo "FAIL: instantiation with custom name failed"
```

### Test 4.3: Instantiate via CLI with --property flags

```bash
CLI_JAR="apps/wanaku-cli/target/quarkus-app/quarkus-run.jar"
OUTPUT=$(java -jar ${CLI_JAR} service template instantiate \
  --host "${WANAKU_HOST}" \
  --name openai-chat-tool \
  --property "openai.api.key=${OPENAI_API_KEY}" \
  --property "openai.model=${OPENAI_MODEL}" \
  --property "openai.organization=${OPENAI_ORG}" 2>&1)

echo "${OUTPUT}" | grep -qi "success\|created" \
  && echo "PASS: CLI instantiation succeeded" \
  || { echo "FAIL: CLI instantiation failed"; echo "${OUTPUT}"; }
```

### Test 4.4: Instantiate via CLI with --properties-from file

```bash
CLI_JAR="apps/wanaku-cli/target/quarkus-app/quarkus-run.jar"
PROPS_FILE=$(mktemp)

cat > "${PROPS_FILE}" <<EOF
openai.api.key=${OPENAI_API_KEY}
openai.model=${OPENAI_MODEL}
openai.organization=${OPENAI_ORG}
EOF

OUTPUT=$(java -jar ${CLI_JAR} service template instantiate \
  --host "${WANAKU_HOST}" \
  --name openai-chat-tool \
  --properties-from "${PROPS_FILE}" 2>&1)

rm -f "${PROPS_FILE}"

echo "${OUTPUT}" | grep -qi "success\|created" \
  && echo "PASS: CLI instantiation from file succeeded" \
  || { echo "FAIL: CLI instantiation from file failed"; echo "${OUTPUT}"; }
```

### Test 4.5: Instantiate non-existent template returns 404

```bash
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${WANAKU_HOST}/api/v1/service-template/instantiate" \
  -H "Content-Type: application/json" \
  -d '{
    "templateName": "does-not-exist",
    "properties": {}
  }')

[ "${HTTP_CODE}" = "404" ] \
  && echo "PASS: 404 returned for non-existent template" \
  || echo "FAIL: expected 404, got ${HTTP_CODE}"
```

---

## Phase 5: Instantiated Catalog Verification

After instantiation, verify the resulting service catalog is properly registered and the MCP tool is discoverable.

### Test 5.1: Instantiated catalog appears in service catalog list

```bash
RESPONSE=$(curl -sf "${WANAKU_HOST}/api/v1/service-catalog/list" | jq '.')

echo "${RESPONSE}" | jq -e '.data[] | select(.name | test("openai"))' > /dev/null 2>&1 \
  && echo "PASS: openai catalog appears in service catalog list" \
  || echo "FAIL: openai catalog not in service catalog list"
```

### Test 5.2: MCP tool is discoverable via tools list

```bash
RESPONSE=$(curl -sf "${WANAKU_HOST}/api/v1/tools/list" | jq '.')

echo "${RESPONSE}" | jq -e '.data[] | select(.name | test("openai-chat"))' > /dev/null 2>&1 \
  && echo "PASS: openai-chat-completion tool is registered" \
  || echo "FAIL: openai-chat-completion tool not found"
```

### Test 5.3: Tool has correct input schema

```bash
RESPONSE=$(curl -sf "${WANAKU_HOST}/api/v1/tools/list" | jq '.')

TOOL=$(echo "${RESPONSE}" | jq '.data[] | select(.name | test("openai-chat"))')

echo "${TOOL}" | jq -e '.inputSchema.properties.wanaku_body' > /dev/null 2>&1 \
  && echo "PASS: wanaku_body property in schema" \
  || echo "FAIL: wanaku_body missing from schema"

echo "${TOOL}" | jq -e '.inputSchema.required | index("wanaku_body")' > /dev/null 2>&1 \
  && echo "PASS: wanaku_body is required" \
  || echo "FAIL: wanaku_body not marked required"
```

---

## Phase 6: End-to-End MCP Tool Invocation (requires valid OpenAI API key + Camel Integration Capability)

> **Note:** This phase requires:
> 1. A valid `OPENAI_API_KEY` (not `test-key-placeholder`)
> 2. A built Camel Integration Capability JAR (`CIC_JAR`)
>
> If either is missing, all tests in this phase are skipped.

### Test 6.0: Pre-flight check

```bash
if [ "${OPENAI_API_KEY}" = "test-key-placeholder" ]; then
  echo "SKIP: OPENAI_API_KEY not set — skipping all Phase 6 tests"
  exit 0
fi

if [ -z "${CIC_JAR}" ] || [ ! -f "${CIC_JAR}" ]; then
  echo "SKIP: CIC_JAR not set or not found — skipping all Phase 6 tests"
  echo "  Build camel-integration-capability and set CIC_JAR (see Prerequisites)"
  exit 0
fi

echo "PASS: pre-flight checks passed"
```

### Test 6.1: Launch the Camel Integration Capability with the instantiated catalog

The Camel Integration Capability must be started with a `--service-catalog` pointing to the instantiated `openai-chat-tool` catalog, so it downloads the routes, rules, and dependencies from the router and registers its gRPC endpoint for tool execution.

```bash
CATALOG_NAME="openai-chat-tool"
CATALOG_SYSTEM="openai-chat"

java -jar "${CIC_JAR}" \
  --registration-url "${WANAKU_HOST}" \
  --registration-announce-address localhost \
  --grpc-port 9190 \
  --name openai-camel \
  --service-catalog "${CATALOG_NAME}" \
  --service-catalog-system "${CATALOG_SYSTEM}" &

CIC_PID=$!
echo "Camel Integration Capability started with PID ${CIC_PID}"

# Wait for the capability to register with the router
sleep 15

# Verify the process is still running
if kill -0 "${CIC_PID}" 2>/dev/null; then
  echo "PASS: Camel Integration Capability is running"
else
  echo "FAIL: Camel Integration Capability exited prematurely"
  exit 1
fi
```

### Test 6.2: Verify capability registered with the router

```bash
RESPONSE=$(curl -sf "${WANAKU_HOST}/api/v1/tools/list" | jq '.')

TOOL=$(echo "${RESPONSE}" | jq '.data[] | select(.name | test("openai-chat"))')
if [ -n "${TOOL}" ]; then
  echo "PASS: openai-chat tool registered after capability launch"
  TARGET=$(echo "${TOOL}" | jq -r '.uri // .target // empty')
  if echo "${TARGET}" | grep -q "localhost:9190\|grpc"; then
    echo "PASS: tool target points to capability gRPC endpoint"
  else
    echo "INFO: tool target: ${TARGET}"
  fi
else
  echo "FAIL: openai-chat tool not found after capability launch"
fi
```

### Test 6.3: Invoke via MCP CLI

```bash
CLI_JAR="apps/wanaku-cli/target/quarkus-app/quarkus-run.jar"
OUTPUT=$(java -jar ${CLI_JAR} mcp tool invoke \
  --uri "${MCP_SERVER_URI}" \
  --tool-name "openai-chat-completion" \
  --arg "wanaku_body=Say hello in exactly three words." 2>&1)

echo "${OUTPUT}" | grep -qiv "error\|exception\|fail" \
  && echo "PASS: MCP CLI tool invoke succeeded" \
  || { echo "FAIL: MCP CLI tool invoke failed"; echo "${OUTPUT}"; }
```

### Test 6.4: Invoke with optional parameters (system message, temperature)

```bash
RESPONSE=$(curl -sf -X POST "${WANAKU_HOST}/api/v1/tools/call" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "openai-chat-completion",
    "arguments": {
      "wanaku_body": "What is 2+2?",
      "systemMessage": "You are a math tutor. Answer concisely.",
      "temperature": "0.1"
    }
  }' | jq '.')

echo "${RESPONSE}" | jq -e '.data' > /dev/null 2>&1 \
  && echo "PASS: tool call with optional params returned data" \
  || { echo "FAIL: tool call with optional params failed"; echo "${RESPONSE}"; }
```

### Test 6.5: Stop the Camel Integration Capability

```bash
if [ -n "${CIC_PID}" ] && kill -0 "${CIC_PID}" 2>/dev/null; then
  kill "${CIC_PID}"
  wait "${CIC_PID}" 2>/dev/null
  echo "PASS: Camel Integration Capability stopped (PID ${CIC_PID})"
else
  echo "INFO: Camel Integration Capability already stopped"
fi
```

---

## Phase 7: Negative and Edge Case Tests

### Test 7.1: Instantiate with empty properties (should use defaults)

```bash
RESPONSE=$(curl -sf -X POST "${WANAKU_HOST}/api/v1/service-template/instantiate" \
  -H "Content-Type: application/json" \
  -d '{
    "templateName": "openai-chat-tool",
    "properties": {}
  }' | jq '.')

echo "${RESPONSE}" | jq -e '.data' > /dev/null 2>&1 \
  && echo "PASS: instantiation with empty properties succeeded (placeholders retained)" \
  || echo "INFO: instantiation with empty properties may fail if validation requires keys"
```

### Test 7.2: Template download returns Base64-encoded data

```bash
RESPONSE=$(curl -sf "${WANAKU_HOST}/api/v1/service-template/download?name=openai-chat-tool" | jq '.')

echo "${RESPONSE}" | jq -e '.data.data' > /dev/null 2>&1 \
  && echo "PASS: download returned base64 data" \
  || echo "FAIL: download did not return base64 data"

DATA=$(echo "${RESPONSE}" | jq -r '.data.data')
echo "${DATA}" | base64 -d 2>/dev/null | file - | grep -q "Zip\|archive" \
  && echo "PASS: base64 decodes to a ZIP archive" \
  || echo "FAIL: base64 does not decode to a ZIP archive"
```

### Test 7.3: Camel route system message conditional logic

Verify the Camel route handles the system message choice:
- When `systemMessage` header is set, messages array includes both system and user roles.
- When `systemMessage` header is empty, messages array only includes user role.

```bash
ROUTE_FILE="services/service-templates/src/main/services/openai-chat-tool/openai-chat/openai-chat.camel.yaml"

grep -q '"role": "system"' "${ROUTE_FILE}" \
  && echo "PASS: system role present in conditional branch" \
  || echo "FAIL: system role missing"

grep -q '"role": "user"' "${ROUTE_FILE}" \
  && echo "PASS: user role present" \
  || echo "FAIL: user role missing"

grep -c 'otherwise' "${ROUTE_FILE}" | grep -q "1" \
  && echo "PASS: otherwise branch exists for no-system-message case" \
  || echo "FAIL: otherwise branch missing"
```

---

## Phase 8: Cleanup

### Test 8.1: Remove instantiated catalogs

```bash
curl -sf -X DELETE "${WANAKU_HOST}/api/v1/service-catalog/remove?name=openai-chat-tool" > /dev/null 2>&1 \
  && echo "PASS: default catalog removed" \
  || echo "INFO: default catalog may not exist"

curl -sf -X DELETE "${WANAKU_HOST}/api/v1/service-catalog/remove?name=my-custom-openai" > /dev/null 2>&1 \
  && echo "PASS: custom catalog removed" \
  || echo "INFO: custom catalog may not exist"
```

### Test 8.2: Verify cleanup

```bash
RESPONSE=$(curl -sf "${WANAKU_HOST}/api/v1/service-catalog/list" | jq '.')

echo "${RESPONSE}" | jq -e '.data[] | select(.name | test("openai"))' > /dev/null 2>&1 \
  && echo "FAIL: openai catalog still present after cleanup" \
  || echo "PASS: no openai catalogs remain"
```

### Test 8.3: Stop the router

```bash
if [ -n "${ROUTER_PID}" ] && kill -0 "${ROUTER_PID}" 2>/dev/null; then
  kill "${ROUTER_PID}"
  wait "${ROUTER_PID}" 2>/dev/null
  echo "PASS: Router stopped (PID ${ROUTER_PID})"
else
  echo "INFO: Router already stopped"
fi
```

---

## Test Summary

| Phase | Test ID | Test Name | Priority |
|-------|---------|-----------|----------|
| 1 | 1.1 | Template directory and files exist | P0 |
| 1 | 1.2 | index.properties structure valid | P0 |
| 1 | 1.3 | Camel route has required elements | P0 |
| 1 | 1.4 | rules.yaml exposes MCP tool | P0 |
| 1 | 1.5 | Dependencies include camel-openai | P0 |
| 1 | 1.6 | service.properties has parameterized placeholders | P0 |
| 1 | 1.7 | Route IDs match between camel.yaml and rules.yaml | P0 |
| 2 | 2.1 | Template appears in REST list | P0 |
| 2 | 2.2 | Template appears in search results | P1 |
| 2 | 2.3 | Template detail via GET | P1 |
| 2 | 2.4 | Template properties endpoint | P1 |
| 3 | 3.1 | CLI lists the template | P1 |
| 3 | 3.2 | CLI search filters correctly | P2 |
| 4 | 4.1 | Instantiate with required properties (REST) | P0 |
| 4 | 4.2 | Instantiate with custom service name (REST) | P1 |
| 4 | 4.3 | Instantiate via CLI with --property | P1 |
| 4 | 4.4 | Instantiate via CLI with --properties-from | P2 |
| 4 | 4.5 | Non-existent template returns 404 | P1 |
| 5 | 5.1 | Catalog appears in service catalog list | P0 |
| 5 | 5.2 | MCP tool is discoverable | P0 |
| 5 | 5.3 | Tool has correct input schema | P1 |
| 6 | 6.0 | Pre-flight check (API key + CIC JAR) | P0 |
| 6 | 6.1 | Launch Camel Integration Capability with catalog | P0 |
| 6 | 6.2 | Verify capability registered with router | P0 |
| 6 | 6.3 | Invoke tool via MCP CLI (live API) | P1 |
| 6 | 6.4 | Invoke with optional params (systemMessage, temperature) | P1 |
| 6 | 6.5 | Stop the Camel Integration Capability | P1 |
| 7 | 7.1 | Instantiate with empty properties | P2 |
| 7 | 7.2 | Template download returns valid ZIP | P2 |
| 7 | 7.3 | System message conditional logic | P1 |
| 8 | 8.1 | Remove instantiated catalogs | P2 |
| 8 | 8.2 | Verify cleanup | P2 |
| 8 | 8.3 | Stop the router | P2 |
