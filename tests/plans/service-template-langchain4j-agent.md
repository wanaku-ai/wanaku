# Test Plan: Camel LangChain4j Agent Service Template (#1181)

## Overview

This test plan verifies the new `camel-langchain4j-agent-tool` service template added for issue #1181. It covers template structure validation, listing, instantiation with property substitution, deployment as a service catalog, and end-to-end MCP tool invocation against a live LangChain4j AI agent backed by an LLM provider.

Every step is fully automatable.

## Prerequisites

### Required tools

| Tool | Minimum version | Verify command |
|------|-----------------|----------------|
| `wanaku` | build from source | `wanaku --version` |
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
# Template naming -- adapt these if the implementation uses different names
export TEMPLATE_NAME="${TEMPLATE_NAME:-camel-langchain4j-agent-tool}"
export SYSTEM_NAME="${SYSTEM_NAME:-langchain4j-agent}"
export ROUTE_ID="${ROUTE_ID:-langchain4j-agent-chat}"

# Router
export WANAKU_HOST="${WANAKU_HOST:-http://localhost:8080}"
export MCP_SERVER_URI="${MCP_SERVER_URI:-http://localhost:8080/public/mcp/sse}"

# LLM backend configuration (required for Phase 6 end-to-end)
# These values are passed to the template instantiation API as properties.
# The template's service.properties uses Forage-prefixed keys
# (forage.wanaku.agent.*) with {{placeholder}} values that the
# instantiation engine substitutes.
#
# PRIMARY approach: local Ollama (no API key required)
#   export AGENT_MODEL_KIND=ollama
#   export AGENT_MODEL_NAME=llama3.2
#   export AGENT_BASE_URL=http://localhost:11434
#   export AGENT_API_KEY=unused
#
# ALTERNATIVE: OpenRouter (cloud, requires API key from https://openrouter.ai)
#   export AGENT_MODEL_KIND=openai           # OpenRouter uses OpenAI-compatible API
#   export AGENT_MODEL_NAME=meta-llama/llama-3-8b-instruct
#   export AGENT_BASE_URL=https://openrouter.ai/api/v1
#   export AGENT_API_KEY=sk-or-...
#
# ALTERNATIVE: Direct OpenAI (cloud, requires API key from https://platform.openai.com)
#   export AGENT_MODEL_KIND=openai
#   export AGENT_MODEL_NAME=gpt-4o
#   export AGENT_BASE_URL=https://api.openai.com/v1
#   export AGENT_API_KEY=sk-...
#
export AGENT_MODEL_KIND="${AGENT_MODEL_KIND:-ollama}"
export AGENT_MODEL_NAME="${AGENT_MODEL_NAME:-llama3.2}"
export AGENT_BASE_URL="${AGENT_BASE_URL:-http://localhost:11434}"
export AGENT_API_KEY="${AGENT_API_KEY:-unused}"

# Template directory (relative to repo root)
export TEMPLATE_BASE_DIR="${TEMPLATE_BASE_DIR:-services/service-templates/src/main/services}"
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
  echo "WARN: CIC_JAR not found -- Phase 6 (end-to-end) will be skipped"
fi
```

### LLM Backend Setup (required for Phase 6)

Phase 6 sends a prompt to a real LLM. The template ships with `forage-model-ollama` as the
default provider, so the primary approach uses a **local Ollama instance**. Cloud providers
are documented as alternatives for CI environments or when Ollama is not available.

#### Option A: Local Ollama (recommended)

Install Ollama (<https://ollama.com/download>) and pull a small model:

```bash
# Install (macOS example -- see https://ollama.com/download for other platforms)
brew install ollama

# Start the Ollama server (if not already running as a service)
ollama serve &

# Pull a model -- llama3.2 is small (~2 GB) and sufficient for testing
ollama pull llama3.2
```

Verify:

```bash
curl -sf http://localhost:11434/api/tags | jq '.models[].name' | grep -q "llama3.2" \
  && echo "PASS: Ollama is running and llama3.2 is available" \
  || echo "FAIL: Ollama not running or llama3.2 not pulled"
```

No API key is needed. Set the environment variables (these are the defaults):

```bash
export AGENT_MODEL_KIND=ollama
export AGENT_MODEL_NAME=llama3.2
export AGENT_BASE_URL=http://localhost:11434
export AGENT_API_KEY=unused
```

#### Option B: OpenRouter (cloud alternative)

OpenRouter (<https://openrouter.ai>) provides an OpenAI-compatible API that fronts many
models. It is useful in CI where Ollama cannot run.

1. Create an account and generate an API key at <https://openrouter.ai/keys>.
2. Set environment variables:

```bash
export AGENT_MODEL_KIND=openai
export AGENT_MODEL_NAME=meta-llama/llama-3-8b-instruct
export AGENT_BASE_URL=https://openrouter.ai/api/v1
export AGENT_API_KEY=sk-or-YOUR_KEY_HERE
```

#### Option C: Direct OpenAI (cloud alternative)

1. Obtain an API key from <https://platform.openai.com/api-keys>.
2. Set environment variables:

```bash
export AGENT_MODEL_KIND=openai
export AGENT_MODEL_NAME=gpt-4o
export AGENT_BASE_URL=https://api.openai.com/v1
export AGENT_API_KEY=sk-YOUR_KEY_HERE
```

> [!NOTE]
> The `forage-model-ollama` dependency is bundled in the template. When using a cloud provider
> (Options B or C), the Camel Integration Capability must also have the appropriate Forage
> model JAR on its classpath (e.g., `forage-model-openai`). Check the Forage documentation
> for details.

### CLI invocation

When using the CLI from a local build (not installed), use `java -jar` directly:

```bash
CLI_JAR="apps/wanaku-cli/target/quarkus-app/quarkus-run.jar"
java -jar ${CLI_JAR} service template list --host ${WANAKU_HOST}
```

Do **not** assign the full command to a single variable -- zsh treats it as a single token. Use `CLI_JAR` for the path and call `java -jar ${CLI_JAR}` explicitly.

---

## Phase 1: Template File Structure Validation

Verify that all files in the LangChain4j agent template are present, well-formed, and consistent.

### Test 1.1: Verify template directory exists with all required files

```bash
TEMPLATE_DIR="${TEMPLATE_BASE_DIR}/${TEMPLATE_NAME}"

REQUIRED_FILES=(
  "index.properties"
  "${SYSTEM_NAME}/${SYSTEM_NAME}.camel.yaml"
  "${SYSTEM_NAME}/${SYSTEM_NAME}.dependencies.txt"
  "${SYSTEM_NAME}/${SYSTEM_NAME}.rules.yaml"
  "${SYSTEM_NAME}/service.properties"
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
TEMPLATE_DIR="${TEMPLATE_BASE_DIR}/${TEMPLATE_NAME}"
INDEX="${TEMPLATE_DIR}/index.properties"

grep -q "^catalog.name=${TEMPLATE_NAME}" "${INDEX}" \
  && echo "PASS: catalog.name" || echo "FAIL: catalog.name"
grep -q "^catalog.description=" "${INDEX}" \
  && echo "PASS: catalog.description" || echo "FAIL: catalog.description"
grep -q "^catalog.services=${SYSTEM_NAME}" "${INDEX}" \
  && echo "PASS: catalog.services" || echo "FAIL: catalog.services"
grep -q "^catalog.routes.${SYSTEM_NAME}=" "${INDEX}" \
  && echo "PASS: catalog.routes" || echo "FAIL: catalog.routes"
grep -q "^catalog.rules.${SYSTEM_NAME}=" "${INDEX}" \
  && echo "PASS: catalog.rules" || echo "FAIL: catalog.rules"
grep -q "^catalog.dependencies.${SYSTEM_NAME}=" "${INDEX}" \
  && echo "PASS: catalog.dependencies" || echo "FAIL: catalog.dependencies"
grep -q "^catalog.properties.${SYSTEM_NAME}=" "${INDEX}" \
  && echo "PASS: catalog.properties" || echo "FAIL: catalog.properties"
```

### Test 1.3: Validate Camel route has required elements

```bash
ROUTE_FILE="${TEMPLATE_BASE_DIR}/${TEMPLATE_NAME}/${SYSTEM_NAME}/${SYSTEM_NAME}.camel.yaml"

grep -q "id: ${ROUTE_ID}" "${ROUTE_FILE}" \
  && echo "PASS: route ID present" || echo "FAIL: route ID missing"
grep -q "uri: direct:wanaku" "${ROUTE_FILE}" \
  && echo "PASS: starts from direct:wanaku" || echo "FAIL: missing direct:wanaku"
grep -q "langchain4j-agent" "${ROUTE_FILE}" \
  && echo "PASS: sends to langchain4j-agent endpoint" || echo "FAIL: missing langchain4j-agent endpoint"
```

### Test 1.4: Validate rules.yaml exposes an MCP tool

```bash
RULES_FILE="${TEMPLATE_BASE_DIR}/${TEMPLATE_NAME}/${SYSTEM_NAME}/${SYSTEM_NAME}.rules.yaml"

grep -q "mcp:" "${RULES_FILE}" \
  && echo "PASS: mcp root" || echo "FAIL: missing mcp root"
grep -q "tools:" "${RULES_FILE}" \
  && echo "PASS: tools section" || echo "FAIL: missing tools section"
grep -q "${ROUTE_ID}:" "${RULES_FILE}" \
  && echo "PASS: tool name matches route ID" || echo "FAIL: tool name mismatch"
grep -q "wanaku_body" "${RULES_FILE}" \
  && echo "PASS: wanaku_body property" || echo "FAIL: missing wanaku_body"
grep -q "required: true" "${RULES_FILE}" \
  && echo "PASS: wanaku_body is required" || echo "FAIL: wanaku_body not required"
```

### Test 1.5: Validate dependencies.txt

```bash
DEPS_FILE="${TEMPLATE_BASE_DIR}/${TEMPLATE_NAME}/${SYSTEM_NAME}/${SYSTEM_NAME}.dependencies.txt"

grep -q "org.apache.camel:camel-langchain4j-agent" "${DEPS_FILE}" \
  && echo "PASS: camel-langchain4j-agent dependency" \
  || echo "FAIL: missing camel-langchain4j-agent"
```

### Test 1.6: Validate service.properties has parameterized placeholders

The LangChain4j agent component requires at minimum an API key for the backing LLM and a model name. Verify that these are parameterized using `{{key}}` Camel property placeholders.

```bash
PROPS_FILE="${TEMPLATE_BASE_DIR}/${TEMPLATE_NAME}/${SYSTEM_NAME}/service.properties"

# tool.description must be present
grep -q "^tool.description=" "${PROPS_FILE}" \
  && echo "PASS: tool.description present" || echo "FAIL: tool.description missing"

# At least one property must use {{...}} placeholder syntax for user-supplied values
grep -q '{{.*}}' "${PROPS_FILE}" \
  && echo "PASS: parameterized placeholder(s) found" \
  || echo "FAIL: no parameterized placeholders found"

# Verify API key is parameterized (not a hardcoded literal)
grep '\.api\.key\|\.apiKey\|api-key' "${PROPS_FILE}" | grep -q '{{' \
  && echo "PASS: API key is parameterized" \
  || echo "FAIL: API key not found or not parameterized"

# Verify model name is parameterized or has a sensible default
grep -q 'model' "${PROPS_FILE}" \
  && echo "PASS: model property present" \
  || echo "FAIL: model property missing"
```

### Test 1.7: Verify route ID in rules matches route ID in Camel YAML

```bash
TEMPLATE_DIR="${TEMPLATE_BASE_DIR}/${TEMPLATE_NAME}"

ROUTE_ID_FROM_CAMEL=$(grep 'id:' "${TEMPLATE_DIR}/${SYSTEM_NAME}/${SYSTEM_NAME}.camel.yaml" | head -1 | awk '{print $2}')
ROUTE_ID_FROM_RULES=$(grep 'id:' "${TEMPLATE_DIR}/${SYSTEM_NAME}/${SYSTEM_NAME}.rules.yaml" | awk -F'"' '{print $2}')

if [ "${ROUTE_ID_FROM_CAMEL}" = "${ROUTE_ID_FROM_RULES}" ]; then
  echo "PASS: route IDs match (${ROUTE_ID_FROM_CAMEL})"
else
  echo "FAIL: route ID mismatch: camel=${ROUTE_ID_FROM_CAMEL}, rules=${ROUTE_ID_FROM_RULES}"
fi
```

### Test 1.8: Validate no credentials are hardcoded

```bash
TEMPLATE_DIR="${TEMPLATE_BASE_DIR}/${TEMPLATE_NAME}"

FAIL=0
for f in $(find "${TEMPLATE_DIR}" -type f); do
  # Check for patterns that look like hardcoded API keys (sk-..., key-..., etc.)
  if grep -Eq '(sk-[a-zA-Z0-9]{20,}|key-[a-zA-Z0-9]{20,}|Bearer [a-zA-Z0-9]{20,})' "${f}"; then
    echo "FAIL: possible hardcoded credential in ${f}"
    FAIL=1
  fi
done

[ "${FAIL}" -eq 0 ] \
  && echo "PASS: no hardcoded credentials detected" \
  || echo "FAIL: hardcoded credentials found"
```

---

## Phase 2: Template Listing via REST API

> **Note:** These tests use `curl` because they specifically verify HTTP-level behavior
> (status codes, JSON response structure, query parameters). For functional interactions,
> Phases 3-4 use the Wanaku CLI.

### Test 2.1: Template appears in the list endpoint

```bash
RESPONSE=$(curl -sf "${WANAKU_HOST}/api/v1/service-template/list" | jq '.')

echo "${RESPONSE}" | jq -e '.data[] | select(.name == "'"${TEMPLATE_NAME}"'")' > /dev/null 2>&1 \
  && echo "PASS: ${TEMPLATE_NAME} in template list" \
  || echo "FAIL: ${TEMPLATE_NAME} not found in template list"
```

### Test 2.2: Template appears when searching by name

```bash
RESPONSE=$(curl -sf "${WANAKU_HOST}/api/v1/service-template/list?search=langchain4j" | jq '.')

echo "${RESPONSE}" | jq -e '.data[] | select(.name == "'"${TEMPLATE_NAME}"'")' > /dev/null 2>&1 \
  && echo "PASS: search by 'langchain4j' returns template" \
  || echo "FAIL: search by 'langchain4j' did not return template"
```

### Test 2.3: Template detail via GET endpoint

```bash
RESPONSE=$(curl -sf "${WANAKU_HOST}/api/v1/service-template/get?name=${TEMPLATE_NAME}" | jq '.')

echo "${RESPONSE}" | jq -e '.data.name == "'"${TEMPLATE_NAME}"'"' > /dev/null 2>&1 \
  && echo "PASS: template name correct" \
  || echo "FAIL: template name mismatch"

echo "${RESPONSE}" | jq -e '.data.services | length > 0' > /dev/null 2>&1 \
  && echo "PASS: services not empty" \
  || echo "FAIL: services array empty"

echo "${RESPONSE}" | jq -r '.data.services[0].name' | grep -q "${SYSTEM_NAME}" \
  && echo "PASS: service name is ${SYSTEM_NAME}" \
  || echo "FAIL: unexpected service name"
```

### Test 2.4: Template properties endpoint returns parameterized keys

```bash
RESPONSE=$(curl -sf "${WANAKU_HOST}/api/v1/service-template/properties?name=${TEMPLATE_NAME}" | jq '.')

echo "${RESPONSE}" | jq -e '.data' > /dev/null 2>&1 \
  && echo "PASS: properties returned" \
  || echo "FAIL: no properties returned"

echo "${RESPONSE}" | jq -r '.data | keys[]' | grep -q "${SYSTEM_NAME}" \
  && echo "PASS: ${SYSTEM_NAME} system found in properties" \
  || echo "FAIL: ${SYSTEM_NAME} system not in properties"
```

---

## Phase 3: Template Listing via CLI

### Test 3.1: CLI lists the template

```bash
CLI_JAR="apps/wanaku-cli/target/quarkus-app/quarkus-run.jar"
OUTPUT=$(java -jar ${CLI_JAR} service template list --host "${WANAKU_HOST}" 2>&1)

echo "${OUTPUT}" | grep -q "${TEMPLATE_NAME}" \
  && echo "PASS: CLI lists ${TEMPLATE_NAME}" \
  || echo "FAIL: ${TEMPLATE_NAME} not in CLI output"
```

### Test 3.2: CLI search filters correctly

```bash
CLI_JAR="apps/wanaku-cli/target/quarkus-app/quarkus-run.jar"
OUTPUT=$(java -jar ${CLI_JAR} service template list --host "${WANAKU_HOST}" --search "langchain4j" 2>&1)

echo "${OUTPUT}" | grep -q "${TEMPLATE_NAME}" \
  && echo "PASS: CLI search returns ${TEMPLATE_NAME}" \
  || echo "FAIL: CLI search did not return ${TEMPLATE_NAME}"
```

---

## Phase 4: Template Instantiation

### Test 4.1: Instantiate with required properties via REST

```bash
RESPONSE=$(curl -sf -X POST "${WANAKU_HOST}/api/v1/service-template/instantiate" \
  -H "Content-Type: application/json" \
  -d '{
    "templateName": "'"${TEMPLATE_NAME}"'",
    "properties": {
      "forage.wanaku.agent.model.kind": "'"${AGENT_MODEL_KIND}"'",
      "forage.wanaku.agent.model.name": "'"${AGENT_MODEL_NAME}"'",
      "forage.wanaku.agent.base.url": "'"${AGENT_BASE_URL}"'",
      "forage.wanaku.agent.api.key": "'"${AGENT_API_KEY}"'"
    }
  }' | jq '.')

echo "${RESPONSE}" | jq -e '.data' > /dev/null 2>&1 \
  && echo "PASS: instantiation returned a catalog" \
  || echo "FAIL: instantiation failed"

echo "${RESPONSE}" | jq -e '.error == null or .error == ""' > /dev/null 2>&1 \
  && echo "PASS: no error in response" \
  || { echo "FAIL: error in response"; echo "${RESPONSE}" | jq '.error'; }
```

> **Note:** The property key names (`forage.wanaku.agent.model.kind`, etc.) match the
> Forage-prefixed keys in `service.properties`. The instantiation engine substitutes by
> matching property keys, not placeholder names.

### Test 4.2: Instantiate with custom service name via REST

```bash
RESPONSE=$(curl -sf -X POST "${WANAKU_HOST}/api/v1/service-template/instantiate" \
  -H "Content-Type: application/json" \
  -d '{
    "templateName": "'"${TEMPLATE_NAME}"'",
    "properties": {
      "forage.wanaku.agent.model.kind": "'"${AGENT_MODEL_KIND}"'",
      "forage.wanaku.agent.model.name": "'"${AGENT_MODEL_NAME}"'",
      "forage.wanaku.agent.base.url": "'"${AGENT_BASE_URL}"'",
      "forage.wanaku.agent.api.key": "'"${AGENT_API_KEY}"'"
    },
    "serviceName": "my-custom-langchain4j",
    "serviceSystem": "custom-langchain4j-system"
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
  --name "${TEMPLATE_NAME}" \
  --property "forage.wanaku.agent.model.kind=${AGENT_MODEL_KIND}" \
  --property "forage.wanaku.agent.model.name=${AGENT_MODEL_NAME}" \
  --property "forage.wanaku.agent.base.url=${AGENT_BASE_URL}" \
  --property "forage.wanaku.agent.api.key=${AGENT_API_KEY}" 2>&1)

echo "${OUTPUT}" | grep -qi "success\|created" \
  && echo "PASS: CLI instantiation succeeded" \
  || { echo "FAIL: CLI instantiation failed"; echo "${OUTPUT}"; }
```

### Test 4.4: Instantiate via CLI with --properties-from file

```bash
CLI_JAR="apps/wanaku-cli/target/quarkus-app/quarkus-run.jar"
PROPS_FILE=$(mktemp)

cat > "${PROPS_FILE}" <<EOF
agent.model.kind=${AGENT_MODEL_KIND}
agent.model.name=${AGENT_MODEL_NAME}
agent.base.url=${AGENT_BASE_URL}
agent.api.key=${AGENT_API_KEY}
EOF

OUTPUT=$(java -jar ${CLI_JAR} service template instantiate \
  --host "${WANAKU_HOST}" \
  --name "${TEMPLATE_NAME}" \
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
RESPONSE=$(curl -sf "${WANAKU_HOST}/api/v1/service-catalog" | jq '.')

echo "${RESPONSE}" | jq -e '.data[] | select(.name | test("langchain4j"))' > /dev/null 2>&1 \
  && echo "PASS: langchain4j catalog appears in service catalog list" \
  || echo "FAIL: langchain4j catalog not in service catalog list"
```

### Test 5.2: MCP tool is discoverable via tools list

```bash
RESPONSE=$(curl -sf "${WANAKU_HOST}/api/v1/tools" | jq '.')

echo "${RESPONSE}" | jq -e '.data[] | select(.name | test("langchain4j"))' > /dev/null 2>&1 \
  && echo "PASS: langchain4j agent tool is registered" \
  || echo "FAIL: langchain4j agent tool not found"
```

### Test 5.3: Tool has correct input schema

```bash
RESPONSE=$(curl -sf "${WANAKU_HOST}/api/v1/tools" | jq '.')

TOOL=$(echo "${RESPONSE}" | jq '.data[] | select(.name | test("langchain4j"))')

echo "${TOOL}" | jq -e '.inputSchema.properties.wanaku_body' > /dev/null 2>&1 \
  && echo "PASS: wanaku_body property in schema" \
  || echo "FAIL: wanaku_body missing from schema"

echo "${TOOL}" | jq -e '.inputSchema.required | index("wanaku_body")' > /dev/null 2>&1 \
  && echo "PASS: wanaku_body is required" \
  || echo "FAIL: wanaku_body not marked required"
```

---

## Phase 6: End-to-End MCP Tool Invocation (requires LLM backend + Camel Integration Capability)

> **Note:** This phase sends a prompt to a real LLM and verifies the round-trip through the
> MCP tool. It requires:
>
> 1. A reachable LLM backend -- either a **local Ollama instance** (default, recommended) or a
>    cloud provider with a valid API key (see *Prerequisites > LLM Backend Setup*).
> 2. A built Camel Integration Capability JAR (`CIC_JAR`).
>
> If neither backend is available or the CIC JAR is missing, all tests in this phase are skipped.

### Test 6.0: Pre-flight check

Verify that either (a) Ollama is running locally, or (b) a cloud API key has been configured.
Also verify the CIC JAR is available.

```bash
PHASE6_SKIP=0

# Check LLM backend availability
if [ "${AGENT_MODEL_KIND}" = "ollama" ]; then
  # Ollama mode: verify the server is reachable
  if curl -sf http://localhost:11434/api/tags > /dev/null 2>&1; then
    echo "PASS: Ollama is running at ${AGENT_BASE_URL}"
    # Verify the requested model is pulled
    if curl -sf http://localhost:11434/api/tags | grep -q "${AGENT_MODEL_NAME}"; then
      echo "PASS: model '${AGENT_MODEL_NAME}' is available"
    else
      echo "WARN: model '${AGENT_MODEL_NAME}' not found -- pull it with: ollama pull ${AGENT_MODEL_NAME}"
      echo "  Available models:"
      curl -sf http://localhost:11434/api/tags | jq -r '.models[].name' 2>/dev/null || echo "  (could not list models)"
      PHASE6_SKIP=1
    fi
  else
    echo "SKIP: Ollama not running at localhost:11434"
    echo "  Start it with: ollama serve"
    echo "  Or switch to a cloud provider (see Prerequisites > LLM Backend Setup)"
    PHASE6_SKIP=1
  fi
else
  # Cloud provider mode: verify API key is not the default placeholder
  if [ "${AGENT_API_KEY}" = "unused" ] || [ -z "${AGENT_API_KEY}" ]; then
    echo "SKIP: AGENT_MODEL_KIND=${AGENT_MODEL_KIND} but AGENT_API_KEY is not set"
    echo "  Set AGENT_API_KEY to a valid API key for your provider"
    PHASE6_SKIP=1
  else
    echo "PASS: cloud provider configured (kind=${AGENT_MODEL_KIND}, model=${AGENT_MODEL_NAME})"
  fi
fi

# Check CIC JAR
if [ -z "${CIC_JAR}" ] || [ ! -f "${CIC_JAR}" ]; then
  echo "SKIP: CIC_JAR not set or not found -- skipping all Phase 6 tests"
  echo "  Download it from the camel-integration-capability early-access release (see Prerequisites)"
  PHASE6_SKIP=1
fi

if [ "${PHASE6_SKIP}" -ne 0 ]; then
  echo ""
  echo "SKIP: Phase 6 pre-flight failed -- skipping all end-to-end tests"
  exit 0
fi

echo "PASS: pre-flight checks passed (backend=${AGENT_MODEL_KIND}, model=${AGENT_MODEL_NAME})"
```

### Test 6.1: Launch the Camel Integration Capability with the instantiated catalog

The Camel Integration Capability must be started with a `--service-catalog` pointing to the instantiated `camel-langchain4j-agent-tool` catalog, so it downloads the routes, rules, and dependencies from the router and registers its gRPC endpoint for tool execution.

```bash
CATALOG_NAME="${TEMPLATE_NAME}"
CATALOG_SYSTEM="${SYSTEM_NAME}"

java -jar "${CIC_JAR}" \
  --registration-url "${WANAKU_HOST}" \
  --registration-announce-address localhost \
  --grpc-port 9190 \
  --name langchain4j-camel \
  --service-catalog "${CATALOG_NAME}" \
  --service-catalog-system "${CATALOG_SYSTEM}" &

CIC_PID=$!
echo "Camel Integration Capability started with PID ${CIC_PID}"

# Wait for the capability to register with the router
for i in $(seq 1 15); do
  if kill -0 "${CIC_PID}" 2>/dev/null; then
    echo "INFO: CIC still running (attempt ${i}/15)"
  else
    echo "FAIL: Camel Integration Capability exited prematurely"
    exit 1
  fi
  sleep 2
done

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
RESPONSE=$(curl -sf "${WANAKU_HOST}/api/v1/tools" | jq '.')

TOOL=$(echo "${RESPONSE}" | jq '.data[] | select(.name | test("langchain4j"))')
if [ -n "${TOOL}" ]; then
  echo "PASS: langchain4j tool registered after capability launch"
  TARGET=$(echo "${TOOL}" | jq -r '.uri // .target // empty')
  if echo "${TARGET}" | grep -q "localhost:9190\|grpc"; then
    echo "PASS: tool target points to capability gRPC endpoint"
  else
    echo "INFO: tool target: ${TARGET}"
  fi
else
  echo "FAIL: langchain4j tool not found after capability launch"
fi
```

### Test 6.3: Invoke via MCP CLI

```bash
CLI_JAR="apps/wanaku-cli/target/quarkus-app/quarkus-run.jar"
OUTPUT=$(java -jar ${CLI_JAR} mcp tool \
  --uri "${MCP_SERVER_URI}" \
  --name "${ROUTE_ID}" \
  --param "wanaku_body=Say hello in exactly three words." 2>&1)

echo "${OUTPUT}" | grep -qiv "error\|exception\|fail" \
  && echo "PASS: MCP CLI tool invoke succeeded" \
  || { echo "FAIL: MCP CLI tool invoke failed"; echo "${OUTPUT}"; }
```

### Test 6.4: Invoke with optional parameters (system message)

```bash
CLI_JAR="apps/wanaku-cli/target/quarkus-app/quarkus-run.jar"
OUTPUT=$(java -jar ${CLI_JAR} mcp tool \
  --uri "${MCP_SERVER_URI}" \
  --name "${ROUTE_ID}" \
  --param "wanaku_body=What is 2+2?" \
  --param "systemMessage=You are a math tutor. Answer concisely." 2>&1)

echo "${OUTPUT}" | grep -qiv "error\|exception\|fail" \
  && echo "PASS: MCP CLI tool invoke with optional params succeeded" \
  || { echo "FAIL: MCP CLI tool invoke with optional params failed"; echo "${OUTPUT}"; }
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
    "templateName": "'"${TEMPLATE_NAME}"'",
    "properties": {}
  }' | jq '.')

echo "${RESPONSE}" | jq -e '.data' > /dev/null 2>&1 \
  && echo "PASS: instantiation with empty properties succeeded (placeholders retained)" \
  || echo "INFO: instantiation with empty properties may fail if validation requires keys"
```

### Test 7.2: Template download returns Base64-encoded data

```bash
RESPONSE=$(curl -sf "${WANAKU_HOST}/api/v1/service-template/download?name=${TEMPLATE_NAME}" | jq '.')

echo "${RESPONSE}" | jq -e '.data.data' > /dev/null 2>&1 \
  && echo "PASS: download returned base64 data" \
  || echo "FAIL: download did not return base64 data"

DATA=$(echo "${RESPONSE}" | jq -r '.data.data')
echo "${DATA}" | base64 -d 2>/dev/null | file - | grep -q "Zip\|archive" \
  && echo "PASS: base64 decodes to a ZIP archive" \
  || echo "FAIL: base64 does not decode to a ZIP archive"
```

### Test 7.3: Validate route uses the langchain4j-agent Camel component

Verify the Camel route properly references the `langchain4j-agent` component URI, not a
different LLM component. The component URI pattern is `langchain4j-agent:<agentName>`.

```bash
ROUTE_FILE="${TEMPLATE_BASE_DIR}/${TEMPLATE_NAME}/${SYSTEM_NAME}/${SYSTEM_NAME}.camel.yaml"

grep -q 'langchain4j-agent:' "${ROUTE_FILE}" \
  && echo "PASS: route uses langchain4j-agent component URI" \
  || echo "FAIL: route does not use langchain4j-agent component URI"
```

### Test 7.4: Verify template does not duplicate existing templates

Ensure the new template does not accidentally share the same `catalog.name` or `catalog.services` value as another template.

```bash
TEMPLATE_DIR="${TEMPLATE_BASE_DIR}/${TEMPLATE_NAME}"
INDEX="${TEMPLATE_DIR}/index.properties"
CATALOG_NAME_VAL=$(grep '^catalog.name=' "${INDEX}" | cut -d= -f2)
CATALOG_SERVICES_VAL=$(grep '^catalog.services=' "${INDEX}" | cut -d= -f2)

DUP_COUNT=0
for OTHER_INDEX in $(find "${TEMPLATE_BASE_DIR}" -name "index.properties" -not -path "*/${TEMPLATE_NAME}/*"); do
  if grep -q "^catalog.name=${CATALOG_NAME_VAL}$" "${OTHER_INDEX}"; then
    echo "FAIL: catalog.name '${CATALOG_NAME_VAL}' duplicated in ${OTHER_INDEX}"
    DUP_COUNT=$((DUP_COUNT + 1))
  fi
  if grep -q "^catalog.services=${CATALOG_SERVICES_VAL}$" "${OTHER_INDEX}"; then
    echo "FAIL: catalog.services '${CATALOG_SERVICES_VAL}' duplicated in ${OTHER_INDEX}"
    DUP_COUNT=$((DUP_COUNT + 1))
  fi
done

[ "${DUP_COUNT}" -eq 0 ] \
  && echo "PASS: no naming collisions with existing templates" \
  || echo "FAIL: ${DUP_COUNT} naming collision(s) found"
```

### Test 7.5: Search with unrelated term does not return this template

```bash
RESPONSE=$(curl -sf "${WANAKU_HOST}/api/v1/service-template/list?search=zzz-nonexistent-zzz" | jq '.')

echo "${RESPONSE}" | jq -e '.data[] | select(.name == "'"${TEMPLATE_NAME}"'")' > /dev/null 2>&1 \
  && echo "FAIL: template incorrectly returned for unrelated search" \
  || echo "PASS: template correctly excluded from unrelated search"
```

---

## Phase 8: Cleanup

### Test 8.1: Remove instantiated catalogs

```bash
curl -sf -X DELETE "${WANAKU_HOST}/api/v1/service-catalog/remove?name=${TEMPLATE_NAME}" > /dev/null 2>&1 \
  && echo "PASS: default catalog removed" \
  || echo "INFO: default catalog may not exist"

curl -sf -X DELETE "${WANAKU_HOST}/api/v1/service-catalog/remove?name=my-custom-langchain4j" > /dev/null 2>&1 \
  && echo "PASS: custom catalog removed" \
  || echo "INFO: custom catalog may not exist"
```

### Test 8.2: Verify cleanup

```bash
RESPONSE=$(curl -sf "${WANAKU_HOST}/api/v1/service-catalog" | jq '.')

echo "${RESPONSE}" | jq -e '.data[] | select(.name | test("langchain4j"))' > /dev/null 2>&1 \
  && echo "FAIL: langchain4j catalog still present after cleanup" \
  || echo "PASS: no langchain4j catalogs remain"
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
| 1 | 1.5 | Dependencies include camel-langchain4j-agent | P0 |
| 1 | 1.6 | service.properties has parameterized placeholders | P0 |
| 1 | 1.7 | Route IDs match between camel.yaml and rules.yaml | P0 |
| 1 | 1.8 | No credentials hardcoded in template files | P0 |
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
| 6 | 6.0 | Pre-flight check (LLM backend + CIC JAR) | P0 |
| 6 | 6.1 | Launch Camel Integration Capability with catalog | P0 |
| 6 | 6.2 | Verify capability registered with router | P0 |
| 6 | 6.3 | Invoke tool via MCP CLI (live API) | P1 |
| 6 | 6.4 | Invoke with optional params (systemMessage) | P1 |
| 6 | 6.5 | Stop the Camel Integration Capability | P1 |
| 7 | 7.1 | Instantiate with empty properties | P2 |
| 7 | 7.2 | Template download returns valid ZIP | P2 |
| 7 | 7.3 | Route uses langchain4j-agent component | P1 |
| 7 | 7.4 | No naming collisions with existing templates | P1 |
| 7 | 7.5 | Unrelated search excludes this template | P2 |
| 8 | 8.1 | Remove instantiated catalogs | P2 |
| 8 | 8.2 | Verify cleanup | P2 |
| 8 | 8.3 | Stop the router | P2 |
