# Test Plan: Camel Qdrant Vector Search Service Template (#1182)

## Overview

This test plan verifies the new `camel-qdrant-search-tool` service template added for issue #1182. It covers template structure validation, listing, instantiation with property substitution, deployment as a service catalog, and end-to-end MCP tool invocation against a live Qdrant vector database performing similarity search.

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
| `docker` or `podman` | 20+ | `docker --version` or `podman --version` |

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

# Accept either docker or podman
CONTAINER_RUNTIME=""
if command -v docker > /dev/null 2>&1; then
  CONTAINER_RUNTIME="docker"
elif command -v podman > /dev/null 2>&1; then
  CONTAINER_RUNTIME="podman"
fi

if [ -z "${CONTAINER_RUNTIME}" ]; then
  echo "FAIL: neither docker nor podman is installed"
  FAIL=1
else
  echo "PASS: ${CONTAINER_RUNTIME} found at $(command -v ${CONTAINER_RUNTIME})"
  export CONTAINER_RUNTIME
fi

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
# Container runtime (auto-detected: docker or podman)
export CONTAINER_RUNTIME="${CONTAINER_RUNTIME:-}"

# Template naming -- adapt these if the implementation uses different names
export TEMPLATE_NAME="${TEMPLATE_NAME:-camel-qdrant-search-tool}"
export SYSTEM_NAME="${SYSTEM_NAME:-qdrant-search}"
export ROUTE_ID="${ROUTE_ID:-qdrant-similarity-search}"

# Router
export WANAKU_HOST="${WANAKU_HOST:-http://localhost:8080}"
export MCP_SERVER_URI="${MCP_SERVER_URI:-http://localhost:8080/public/mcp/sse}"

# Qdrant server configuration
# The gRPC port (6334) is used by camel-qdrant; the REST API port (6333) is
# used for test data seeding via curl.
export QDRANT_HOST="${QDRANT_HOST:-localhost}"
export QDRANT_GRPC_PORT="${QDRANT_GRPC_PORT:-6334}"
export QDRANT_REST_PORT="${QDRANT_REST_PORT:-6333}"
export QDRANT_API_KEY="${QDRANT_API_KEY:-}"
export QDRANT_COLLECTION="${QDRANT_COLLECTION:-wanaku-test-collection}"

# Embedding dimension -- must match the vectors seeded in the test collection.
# 4 is used for lightweight test vectors; real workloads use 384, 768, 1536, etc.
export QDRANT_VECTOR_DIMENSION="${QDRANT_VECTOR_DIMENSION:-4}"

# Template directory (relative to repo root)
export TEMPLATE_BASE_DIR="${TEMPLATE_BASE_DIR:-services/service-templates/src/main/services}"
```

| Variable | Default | Description |
|----------|---------|-------------|
| `TEMPLATE_NAME` | `camel-qdrant-search-tool` | Template directory and catalog name |
| `SYSTEM_NAME` | `qdrant-search` | Service system name within the template |
| `ROUTE_ID` | `qdrant-similarity-search` | Camel route ID and MCP tool name |
| `WANAKU_HOST` | `http://localhost:8080` | Router base URL |
| `MCP_SERVER_URI` | `http://localhost:8080/public/mcp/sse` | MCP SSE endpoint |
| `QDRANT_HOST` | `localhost` | Qdrant gRPC host |
| `QDRANT_GRPC_PORT` | `6334` | Qdrant gRPC port (used by camel-qdrant) |
| `QDRANT_REST_PORT` | `6333` | Qdrant REST API port (used for test data seeding) |
| `QDRANT_API_KEY` | _(empty)_ | Qdrant API key (optional for local) |
| `QDRANT_COLLECTION` | `wanaku-test-collection` | Qdrant collection name for test data |
| `QDRANT_VECTOR_DIMENSION` | `4` | Vector dimension for test collection |
| `TEMPLATE_BASE_DIR` | `services/service-templates/src/main/services` | Template base directory |

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

### Qdrant Server Setup (required for Phase 6)

Phase 6 sends a vectorized search query to a live Qdrant instance. The recommended approach
is a **local container** (Docker or Podman).

#### Option A: Local container (Docker or Podman)

```bash
${CONTAINER_RUNTIME} run -d --name qdrant-test \
  -p "${QDRANT_REST_PORT}:6333" \
  -p "${QDRANT_GRPC_PORT}:6334" \
  qdrant/qdrant
```

Wait for Qdrant to become healthy:

```bash
for i in $(seq 1 20); do
  if curl -sf "http://${QDRANT_HOST}:${QDRANT_REST_PORT}/healthz" > /dev/null 2>&1; then
    echo "PASS: Qdrant is healthy"
    break
  fi
  sleep 2
done
curl -sf "http://${QDRANT_HOST}:${QDRANT_REST_PORT}/healthz" > /dev/null \
  || { echo "FAIL: Qdrant not healthy after 40s"; exit 1; }
```

#### Option B: Qdrant Cloud

1. Create a cluster at <https://cloud.qdrant.io>.
2. Set environment variables:

```bash
export QDRANT_HOST=your-cluster.qdrant.io
export QDRANT_GRPC_PORT=6334
export QDRANT_REST_PORT=6333
export QDRANT_API_KEY=your-api-key
```

#### Seed test data

Create a test collection and insert sample vectors for similarity search testing:

```bash
# Create collection
curl -sf -X PUT "http://${QDRANT_HOST}:${QDRANT_REST_PORT}/collections/${QDRANT_COLLECTION}" \
  -H "Content-Type: application/json" \
  ${QDRANT_API_KEY:+-H "api-key: ${QDRANT_API_KEY}"} \
  -d '{
    "vectors": {
      "size": '"${QDRANT_VECTOR_DIMENSION}"',
      "distance": "Cosine"
    }
  }' | jq '.' \
  && echo "PASS: collection created" \
  || { echo "FAIL: collection creation failed"; exit 1; }

# Insert test vectors with payloads
curl -sf -X PUT "http://${QDRANT_HOST}:${QDRANT_REST_PORT}/collections/${QDRANT_COLLECTION}/points" \
  -H "Content-Type: application/json" \
  ${QDRANT_API_KEY:+-H "api-key: ${QDRANT_API_KEY}"} \
  -d '{
    "points": [
      {
        "id": 1,
        "vector": [0.1, 0.2, 0.3, 0.4],
        "payload": {"content": "Apache Camel integration framework", "source": "test"}
      },
      {
        "id": 2,
        "vector": [0.5, 0.6, 0.7, 0.8],
        "payload": {"content": "Qdrant vector database for similarity search", "source": "test"}
      },
      {
        "id": 3,
        "vector": [0.9, 0.1, 0.2, 0.3],
        "payload": {"content": "Wanaku MCP router for AI tool orchestration", "source": "test"}
      }
    ]
  }' | jq '.' \
  && echo "PASS: test vectors inserted" \
  || { echo "FAIL: vector insertion failed"; exit 1; }

# Verify data
POINT_COUNT=$(curl -sf "http://${QDRANT_HOST}:${QDRANT_REST_PORT}/collections/${QDRANT_COLLECTION}" \
  ${QDRANT_API_KEY:+-H "api-key: ${QDRANT_API_KEY}"} | jq '.result.points_count')
[ "${POINT_COUNT}" -ge 3 ] \
  && echo "PASS: collection has ${POINT_COUNT} points" \
  || { echo "FAIL: expected at least 3 points, got ${POINT_COUNT}"; exit 1; }
```

### CLI invocation

When using the CLI from a local build (not installed), use `java -jar` directly:

```bash
CLI_JAR="apps/wanaku-cli/target/quarkus-app/quarkus-run.jar"
java -jar ${CLI_JAR} service template list --host ${WANAKU_HOST}
```

Do **not** assign the full command to a single variable -- zsh treats it as a single token. Use `CLI_JAR` for the path and call `java -jar ${CLI_JAR}` explicitly.

---

## Phase 1: Template File Structure Validation

Verify that all files in the Qdrant search template are present, well-formed, and consistent.

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

The camel-qdrant component requires the `CamelQdrantAction` header set to `SIMILARITY_SEARCH`
and uses the `qdrant:<collection>` URI pattern. The route must start from `direct:wanaku`.

```bash
ROUTE_FILE="${TEMPLATE_BASE_DIR}/${TEMPLATE_NAME}/${SYSTEM_NAME}/${SYSTEM_NAME}.camel.yaml"

grep -q "id: ${ROUTE_ID}" "${ROUTE_FILE}" \
  && echo "PASS: route ID present" || echo "FAIL: route ID missing"
grep -q "uri: direct:wanaku" "${ROUTE_FILE}" \
  && echo "PASS: starts from direct:wanaku" || echo "FAIL: missing direct:wanaku"
grep -q "CamelQdrantAction" "${ROUTE_FILE}" \
  && echo "PASS: sets CamelQdrantAction header" || echo "FAIL: missing CamelQdrantAction header"
grep -q "SIMILARITY_SEARCH" "${ROUTE_FILE}" \
  && echo "PASS: uses SIMILARITY_SEARCH action" || echo "FAIL: missing SIMILARITY_SEARCH action"
grep -q 'qdrant:' "${ROUTE_FILE}" \
  && echo "PASS: sends to qdrant endpoint" || echo "FAIL: missing qdrant endpoint"
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

grep -q "org.apache.camel:camel-qdrant" "${DEPS_FILE}" \
  && echo "PASS: camel-qdrant dependency" \
  || echo "FAIL: missing camel-qdrant"
```

### Test 1.6: Validate service.properties has parameterized placeholders

The Qdrant component requires at minimum a host, port, and collection name. Verify that
connection parameters are parameterized using `{{key}}` Camel property placeholders.

```bash
PROPS_FILE="${TEMPLATE_BASE_DIR}/${TEMPLATE_NAME}/${SYSTEM_NAME}/service.properties"

# tool.description must be present
grep -q "^tool.description=" "${PROPS_FILE}" \
  && echo "PASS: tool.description present" || echo "FAIL: tool.description missing"

# At least one property must use {{...}} placeholder syntax for user-supplied values
grep -q '{{.*}}' "${PROPS_FILE}" \
  && echo "PASS: parameterized placeholder(s) found" \
  || echo "FAIL: no parameterized placeholders found"

# Verify Qdrant host is configurable
grep -qi 'host\|qdrant\.host' "${PROPS_FILE}" \
  && echo "PASS: host property present" \
  || echo "FAIL: host property missing"

# Verify Qdrant port is configurable
grep -qi 'port\|qdrant\.port' "${PROPS_FILE}" \
  && echo "PASS: port property present" \
  || echo "FAIL: port property missing"

# Verify collection is configurable
grep -qi 'collection\|qdrant\.collection' "${PROPS_FILE}" \
  && echo "PASS: collection property present" \
  || echo "FAIL: collection property missing"

# If an API key property exists, verify it is parameterized (not a hardcoded literal)
if grep -qi 'api.key\|apiKey\|api-key' "${PROPS_FILE}"; then
  grep -i 'api.key\|apiKey\|api-key' "${PROPS_FILE}" | grep -q '{{' \
    && echo "PASS: API key is parameterized" \
    || echo "FAIL: API key not parameterized"
else
  echo "INFO: no API key property found (acceptable for local-only defaults)"
fi
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
RESPONSE=$(curl -sf "${WANAKU_HOST}/api/v1/service-template/list?search=qdrant" | jq '.')

echo "${RESPONSE}" | jq -e '.data[] | select(.name == "'"${TEMPLATE_NAME}"'")' > /dev/null 2>&1 \
  && echo "PASS: search by 'qdrant' returns template" \
  || echo "FAIL: search by 'qdrant' did not return template"
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
OUTPUT=$(java -jar ${CLI_JAR} service template list --host "${WANAKU_HOST}" --search "qdrant" 2>&1)

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
      "qdrant.host": "'"${QDRANT_HOST}"'",
      "qdrant.port": "'"${QDRANT_GRPC_PORT}"'",
      "qdrant.collection": "'"${QDRANT_COLLECTION}"'"
    }
  }' | jq '.')

echo "${RESPONSE}" | jq -e '.data' > /dev/null 2>&1 \
  && echo "PASS: instantiation returned a catalog" \
  || echo "FAIL: instantiation failed"

echo "${RESPONSE}" | jq -e '.error == null or .error == ""' > /dev/null 2>&1 \
  && echo "PASS: no error in response" \
  || { echo "FAIL: error in response"; echo "${RESPONSE}" | jq '.error'; }
```

> **Note:** The property key names (`qdrant.host`, `qdrant.port`, `qdrant.collection`) must
> match the keys defined in `service.properties`. The instantiation engine substitutes by
> matching property keys, not placeholder names. Adjust these keys if the implementation
> uses different prefixes (e.g., `forage.qdrant.*`).

### Test 4.2: Instantiate with custom service name via REST

```bash
RESPONSE=$(curl -sf -X POST "${WANAKU_HOST}/api/v1/service-template/instantiate" \
  -H "Content-Type: application/json" \
  -d '{
    "templateName": "'"${TEMPLATE_NAME}"'",
    "properties": {
      "qdrant.host": "'"${QDRANT_HOST}"'",
      "qdrant.port": "'"${QDRANT_GRPC_PORT}"'",
      "qdrant.collection": "'"${QDRANT_COLLECTION}"'"
    },
    "serviceName": "my-custom-qdrant",
    "serviceSystem": "custom-qdrant-system"
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
  --property "qdrant.host=${QDRANT_HOST}" \
  --property "qdrant.port=${QDRANT_GRPC_PORT}" \
  --property "qdrant.collection=${QDRANT_COLLECTION}" 2>&1)

echo "${OUTPUT}" | grep -qi "success\|created" \
  && echo "PASS: CLI instantiation succeeded" \
  || { echo "FAIL: CLI instantiation failed"; echo "${OUTPUT}"; }
```

### Test 4.4: Instantiate via CLI with --properties-from file

```bash
CLI_JAR="apps/wanaku-cli/target/quarkus-app/quarkus-run.jar"
PROPS_FILE=$(mktemp)

cat > "${PROPS_FILE}" <<EOF
qdrant.host=${QDRANT_HOST}
qdrant.port=${QDRANT_GRPC_PORT}
qdrant.collection=${QDRANT_COLLECTION}
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

echo "${RESPONSE}" | jq -e '.data[] | select(.name | test("qdrant"))' > /dev/null 2>&1 \
  && echo "PASS: qdrant catalog appears in service catalog list" \
  || echo "FAIL: qdrant catalog not in service catalog list"
```

### Test 5.2: MCP tool is discoverable via tools list

```bash
RESPONSE=$(curl -sf "${WANAKU_HOST}/api/v1/tools" | jq '.')

echo "${RESPONSE}" | jq -e '.data[] | select(.name | test("qdrant"))' > /dev/null 2>&1 \
  && echo "PASS: qdrant search tool is registered" \
  || echo "FAIL: qdrant search tool not found"
```

### Test 5.3: Tool has correct input schema

```bash
RESPONSE=$(curl -sf "${WANAKU_HOST}/api/v1/tools" | jq '.')

TOOL=$(echo "${RESPONSE}" | jq '.data[] | select(.name | test("qdrant"))')

echo "${TOOL}" | jq -e '.inputSchema.properties.wanaku_body' > /dev/null 2>&1 \
  && echo "PASS: wanaku_body property in schema" \
  || echo "FAIL: wanaku_body missing from schema"

echo "${TOOL}" | jq -e '.inputSchema.required | index("wanaku_body")' > /dev/null 2>&1 \
  && echo "PASS: wanaku_body is required" \
  || echo "FAIL: wanaku_body not marked required"
```

---

## Phase 6: End-to-End MCP Tool Invocation (requires Qdrant + Camel Integration Capability)

> **Note:** This phase sends a vectorized search query to a live Qdrant instance and verifies
> the round-trip through the MCP tool. It requires:
>
> 1. A reachable Qdrant instance with seeded test data (see *Prerequisites > Qdrant Server Setup*).
> 2. A built Camel Integration Capability JAR (`CIC_JAR`).
>
> If either is unavailable, all tests in this phase are skipped.
>
> **Important:** The camel-qdrant component expects **embedding vectors** as input for similarity
> search, not raw text. In a real-world deployment, an embedding model (e.g., via
> `camel-langchain4j-embeddings`) would convert the text query to a vector before the Qdrant
> similarity search. For this test plan, we verify the route structure and end-to-end plumbing.
> If the template includes an integrated embedding step, the tool can be invoked with plain text.
> Otherwise, the test verifies the route executes without errors rather than asserting specific
> search result content.

### Test 6.0: Pre-flight check

Verify that a Qdrant instance is reachable and the CIC JAR is available.

```bash
PHASE6_SKIP=0

# Check Qdrant availability
if curl -sf "http://${QDRANT_HOST}:${QDRANT_REST_PORT}/healthz" > /dev/null 2>&1; then
  echo "PASS: Qdrant is running at ${QDRANT_HOST}:${QDRANT_REST_PORT}"

  # Verify test collection exists
  COLLECTION_STATUS=$(curl -sf "http://${QDRANT_HOST}:${QDRANT_REST_PORT}/collections/${QDRANT_COLLECTION}" \
    ${QDRANT_API_KEY:+-H "api-key: ${QDRANT_API_KEY}"} | jq -r '.status' 2>/dev/null)
  if [ "${COLLECTION_STATUS}" = "ok" ]; then
    POINT_COUNT=$(curl -sf "http://${QDRANT_HOST}:${QDRANT_REST_PORT}/collections/${QDRANT_COLLECTION}" \
      ${QDRANT_API_KEY:+-H "api-key: ${QDRANT_API_KEY}"} | jq '.result.points_count')
    echo "PASS: collection '${QDRANT_COLLECTION}' exists with ${POINT_COUNT} points"
  else
    echo "WARN: collection '${QDRANT_COLLECTION}' not found -- seed test data first"
    echo "  See Prerequisites > Qdrant Server Setup > Seed test data"
    PHASE6_SKIP=1
  fi
else
  echo "SKIP: Qdrant not running at ${QDRANT_HOST}:${QDRANT_REST_PORT}"
  echo "  Start it with: ${CONTAINER_RUNTIME} run -d -p 6333:6333 -p 6334:6334 qdrant/qdrant"
  PHASE6_SKIP=1
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

echo "PASS: pre-flight checks passed (qdrant=${QDRANT_HOST}:${QDRANT_GRPC_PORT}, collection=${QDRANT_COLLECTION})"
```

### Test 6.1: Launch the Camel Integration Capability with the instantiated catalog

The Camel Integration Capability must be started with a `--service-catalog` pointing to the
instantiated `camel-qdrant-search-tool` catalog, so it downloads the routes, rules, and
dependencies from the router and registers its gRPC endpoint for tool execution.

```bash
CATALOG_NAME="${TEMPLATE_NAME}"
CATALOG_SYSTEM="${SYSTEM_NAME}"

java -jar "${CIC_JAR}" \
  --registration-url "${WANAKU_HOST}" \
  --registration-announce-address localhost \
  --grpc-port 9190 \
  --name qdrant-camel \
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

TOOL=$(echo "${RESPONSE}" | jq '.data[] | select(.name | test("qdrant"))')
if [ -n "${TOOL}" ]; then
  echo "PASS: qdrant tool registered after capability launch"
  TARGET=$(echo "${TOOL}" | jq -r '.uri // .target // empty')
  if echo "${TARGET}" | grep -q "localhost:9190\|grpc"; then
    echo "PASS: tool target points to capability gRPC endpoint"
  else
    echo "INFO: tool target: ${TARGET}"
  fi
else
  echo "FAIL: qdrant tool not found after capability launch"
fi
```

### Test 6.3: Invoke via MCP CLI

```bash
CLI_JAR="apps/wanaku-cli/target/quarkus-app/quarkus-run.jar"
OUTPUT=$(java -jar ${CLI_JAR} mcp tool \
  --uri "${MCP_SERVER_URI}" \
  --name "${ROUTE_ID}" \
  --param "wanaku_body=search for integration framework" 2>&1)

echo "${OUTPUT}" | grep -qiv "error\|exception\|fail" \
  && echo "PASS: MCP CLI tool invoke succeeded" \
  || { echo "FAIL: MCP CLI tool invoke failed"; echo "${OUTPUT}"; }
```

### Test 6.4: Verify search returns results

```bash
CLI_JAR="apps/wanaku-cli/target/quarkus-app/quarkus-run.jar"
OUTPUT=$(java -jar ${CLI_JAR} mcp tool \
  --uri "${MCP_SERVER_URI}" \
  --name "${ROUTE_ID}" \
  --param "wanaku_body=vector database" 2>&1)

# The response should contain some content (not empty) and no errors
if [ -n "${OUTPUT}" ] && echo "${OUTPUT}" | grep -qiv "error\|exception\|fail"; then
  echo "PASS: search returned a non-empty, non-error response"
  if echo "${OUTPUT}" | grep -qi "camel\|qdrant\|wanaku\|integration\|vector\|search"; then
    echo "PASS: response contains payload content from seeded data"
  else
    echo "INFO: response returned but payload content not detected (may need embedding pipeline)"
    echo "  Response: ${OUTPUT}"
  fi
else
  echo "FAIL: search returned empty or error response"
  echo "${OUTPUT}"
fi
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

### Test 7.3: Validate route uses the qdrant Camel component

Verify the Camel route properly references the `qdrant` component URI. The component URI
pattern is `qdrant:<collectionName>`.

```bash
ROUTE_FILE="${TEMPLATE_BASE_DIR}/${TEMPLATE_NAME}/${SYSTEM_NAME}/${SYSTEM_NAME}.camel.yaml"

grep -q 'qdrant:' "${ROUTE_FILE}" \
  && echo "PASS: route uses qdrant component URI" \
  || echo "FAIL: route does not use qdrant component URI"
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

### Test 7.6: Verify CamelQdrantWithPayload header is set

The route should set `CamelQdrantWithPayload` to `true` so that search results include the
document payloads, not just vector IDs and scores. Note: the Camel header name is
`CamelQdrantWithPayload` (not `CamelQdrantIncludePayload`).

```bash
ROUTE_FILE="${TEMPLATE_BASE_DIR}/${TEMPLATE_NAME}/${SYSTEM_NAME}/${SYSTEM_NAME}.camel.yaml"

grep -q "CamelQdrantWithPayload" "${ROUTE_FILE}" \
  && echo "PASS: CamelQdrantWithPayload header is set" \
  || echo "FAIL: CamelQdrantWithPayload header is missing (search results will lack payloads)"
```

---

## Phase 8: Cleanup

### Test 8.1: Remove instantiated catalogs

```bash
curl -sf -X DELETE "${WANAKU_HOST}/api/v1/service-catalog/remove?name=${TEMPLATE_NAME}" > /dev/null 2>&1 \
  && echo "PASS: default catalog removed" \
  || echo "INFO: default catalog may not exist"

curl -sf -X DELETE "${WANAKU_HOST}/api/v1/service-catalog/remove?name=my-custom-qdrant" > /dev/null 2>&1 \
  && echo "PASS: custom catalog removed" \
  || echo "INFO: custom catalog may not exist"
```

### Test 8.2: Verify cleanup

```bash
RESPONSE=$(curl -sf "${WANAKU_HOST}/api/v1/service-catalog" | jq '.')

echo "${RESPONSE}" | jq -e '.data[] | select(.name | test("qdrant"))' > /dev/null 2>&1 \
  && echo "FAIL: qdrant catalog still present after cleanup" \
  || echo "PASS: no qdrant catalogs remain"
```

### Test 8.3: Remove Qdrant test collection

```bash
curl -sf -X DELETE "http://${QDRANT_HOST}:${QDRANT_REST_PORT}/collections/${QDRANT_COLLECTION}" \
  ${QDRANT_API_KEY:+-H "api-key: ${QDRANT_API_KEY}"} > /dev/null 2>&1 \
  && echo "PASS: test collection ${QDRANT_COLLECTION} deleted" \
  || echo "INFO: test collection may not exist"
```

### Test 8.4: Stop Qdrant container

```bash
${CONTAINER_RUNTIME} stop qdrant-test > /dev/null 2>&1 && ${CONTAINER_RUNTIME} rm qdrant-test > /dev/null 2>&1 \
  && echo "PASS: Qdrant container stopped and removed" \
  || echo "INFO: Qdrant container may not exist (external instance?)"
```

### Test 8.5: Stop the router

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
| 1 | 1.3 | Camel route has required elements (qdrant, SIMILARITY_SEARCH) | P0 |
| 1 | 1.4 | rules.yaml exposes MCP tool | P0 |
| 1 | 1.5 | Dependencies include camel-qdrant | P0 |
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
| 6 | 6.0 | Pre-flight check (Qdrant + CIC JAR) | P0 |
| 6 | 6.1 | Launch Camel Integration Capability with catalog | P0 |
| 6 | 6.2 | Verify capability registered with router | P0 |
| 6 | 6.3 | Invoke tool via MCP CLI | P1 |
| 6 | 6.4 | Verify search returns results | P1 |
| 6 | 6.5 | Stop the Camel Integration Capability | P1 |
| 7 | 7.1 | Instantiate with empty properties | P2 |
| 7 | 7.2 | Template download returns valid ZIP | P2 |
| 7 | 7.3 | Route uses qdrant component URI | P1 |
| 7 | 7.4 | No naming collisions with existing templates | P1 |
| 7 | 7.5 | Unrelated search excludes this template | P2 |
| 7 | 7.6 | CamelQdrantWithPayload header is set | P1 |
| 8 | 8.1 | Remove instantiated catalogs | P2 |
| 8 | 8.2 | Verify cleanup | P2 |
| 8 | 8.3 | Remove Qdrant test collection | P2 |
| 8 | 8.4 | Stop Qdrant container | P2 |
| 8 | 8.5 | Stop the router | P2 |
