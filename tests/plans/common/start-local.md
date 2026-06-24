# Common: Start Wanaku Locally

Reusable steps for building and starting a Wanaku stack locally (no authentication, no Kubernetes).

## Prerequisites

- Java 21+
- Maven 3.9+
- The Wanaku repository checked out and at the repo root

## Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `WANAKU_REPO_ROOT` | Path to the Wanaku repository root | `.` |

## Output variables

| Variable | Description |
|----------|-------------|
| `VERSION` | Wanaku version string from the build |
| `CLI_JAR` | Path to the CLI JAR |
| `WANAKU_ROUTER_URL` | Router base URL (`http://localhost:8080`) |
| `WANAKU_PID` | PID of the background Wanaku process |

## Steps

### 1. Build the distribution

```bash
cd "${WANAKU_REPO_ROOT:-.}"
mvn -DskipTests -Pdist clean package
```

**Verification:**

```bash
VERSION=$(cat core/core-util/target/classes/version.txt)
CLI_JAR="apps/wanaku-cli/target/quarkus-app/quarkus-run.jar"
ROUTER_DIST="apps/wanaku-router-backend/target/distributions/wanaku-router-backend-${VERSION}.zip"
HTTP_TOOL_DIST="capabilities/tools/wanaku-tool-service-http/target/distributions/wanaku-tool-service-http-${VERSION}.zip"

for FILE in "${CLI_JAR}" "${ROUTER_DIST}" "${HTTP_TOOL_DIST}"; do
  if [ ! -f "${FILE}" ]; then
    echo "FAIL: ${FILE} not found"
    exit 1
  fi
  echo "PASS: ${FILE} exists"
done
```

### 2. Start the local stack

```bash
java -jar "${CLI_JAR}" start local \
  --local-dist "${ROUTER_DIST}" \
  --local-dist "${HTTP_TOOL_DIST}" &
WANAKU_PID=$!
echo "Wanaku started with PID ${WANAKU_PID}"
```

### 3. Wait for health

```bash
export WANAKU_ROUTER_URL="http://localhost:8080"

MAX_RETRIES=30
RETRY_INTERVAL=5
for i in $(seq 1 ${MAX_RETRIES}); do
  HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "${WANAKU_ROUTER_URL}/q/health/ready" 2>/dev/null || echo "000")
  if [ "${HTTP_CODE}" = "200" ]; then
    echo "PASS: router is healthy (attempt ${i})"
    break
  fi
  if [ "${i}" -eq "${MAX_RETRIES}" ]; then
    echo "FAIL: router not healthy after ${MAX_RETRIES} attempts (last HTTP ${HTTP_CODE})"
    kill "${WANAKU_PID}" 2>/dev/null || true
    exit 1
  fi
  sleep ${RETRY_INTERVAL}
done
```

### 4. Verify the CLI can connect

```bash
wanaku tools list --host "${WANAKU_ROUTER_URL}" --plain
EXIT_CODE=$?
if [ "${EXIT_CODE}" -eq 0 ]; then
  echo "PASS: CLI can connect to router"
else
  echo "FAIL: CLI cannot connect to router (exit code ${EXIT_CODE})"
  exit 1
fi
```

## Shutdown

```bash
if [ -n "${WANAKU_PID}" ]; then
  kill "${WANAKU_PID}" 2>/dev/null || true
  wait "${WANAKU_PID}" 2>/dev/null || true
  echo "PASS: Wanaku process stopped"
fi
```
