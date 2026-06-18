# Common: Keycloak Setup on OpenShift

Reusable steps for deploying and configuring Keycloak as the OIDC provider for Wanaku on OpenShift.

## Prerequisites

- Namespace created (see [namespace-setup.md](namespace-setup.md))
- `WANAKU_NAMESPACE` environment variable set
- `WANAKU_REPO_ROOT` environment variable set (path to the wanaku repository root)
- `jq` and `curl` available
- `wanaku` CLI available on `PATH`, **or** the project built with `mvn verify` (to use the jar directly)

## Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `WANAKU_NAMESPACE` | Target namespace | `wanaku-test` |
| `WANAKU_REPO_ROOT` | Path to the wanaku repository root | `.` |
| `WANAKU_CLI` | Command to invoke the Wanaku CLI (see step 1) | `wanaku` |
| `KEYCLOAK_ADMIN_USER` | Keycloak admin username | `admin` |
| `KEYCLOAK_ADMIN_PASS` | Keycloak admin password | `admin` |
| `KEYCLOAK_IMAGE` | Keycloak container image | `quay.io/keycloak/keycloak:26.6.1` |

## Steps

### 1. Set variables

```bash
export KEYCLOAK_ADMIN_USER="${KEYCLOAK_ADMIN_USER:-admin}"
export KEYCLOAK_ADMIN_PASS="${KEYCLOAK_ADMIN_PASS:-admin}"
export KEYCLOAK_IMAGE="${KEYCLOAK_IMAGE:-quay.io/keycloak/keycloak:26.6.1}"
export WANAKU_REPO_ROOT="${WANAKU_REPO_ROOT:-.}"

# Option A: use the installed CLI
export WANAKU_CLI="wanaku"

# Option B: use the jar from a local build (after running `mvn verify` from the repo root)
# export WANAKU_CLI="java -jar ${WANAKU_REPO_ROOT}/apps/wanaku-cli/target/quarkus-app/quarkus-run.jar"
```

### 2. Deploy Keycloak

```bash
cat <<'KEYCLOAK_EOF' | sed "s|__KEYCLOAK_IMAGE__|${KEYCLOAK_IMAGE}|g; s|__ADMIN_USER__|${KEYCLOAK_ADMIN_USER}|g; s|__ADMIN_PASS__|${KEYCLOAK_ADMIN_PASS}|g" | oc apply -n "${WANAKU_NAMESPACE}" -f -
apiVersion: apps/v1
kind: Deployment
metadata:
  labels:
    app: keycloak
  name: keycloak
spec:
  replicas: 1
  selector:
    matchLabels:
      app: keycloak
  template:
    metadata:
      labels:
        app: keycloak
    spec:
      containers:
        - name: keycloak
          image: __KEYCLOAK_IMAGE__
          imagePullPolicy: Always
          ports:
            - containerPort: 8080
              protocol: TCP
          env:
            - name: KC_BOOTSTRAP_ADMIN_USERNAME
              value: "__ADMIN_USER__"
            - name: KC_BOOTSTRAP_ADMIN_PASSWORD
              value: "__ADMIN_PASS__"
          args:
            - "start-dev"
          volumeMounts:
            - name: keycloak-data
              mountPath: /opt/keycloak/data
      volumes:
        - name: keycloak-data
          persistentVolumeClaim:
            claimName: keycloak-data-pvc
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: keycloak-data-pvc
  labels:
    app: keycloak
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 1Gi
---
apiVersion: v1
kind: Service
metadata:
  labels:
    app: keycloak
  name: keycloak
spec:
  ports:
    - name: 8080-tcp
      protocol: TCP
      port: 8080
      targetPort: 8080
  selector:
    app: keycloak
  sessionAffinity: None
  type: ClusterIP
---
apiVersion: route.openshift.io/v1
kind: Route
metadata:
  labels:
    app: keycloak
  name: keycloak
spec:
  port:
    targetPort: 8080-tcp
  to:
    kind: Service
    name: keycloak
    weight: 100
  wildcardPolicy: None
KEYCLOAK_EOF
```

### 3. Wait for Keycloak to be ready

```bash
oc wait --for=condition=ready pod -l app=keycloak \
  --timeout=300s \
  -n "${WANAKU_NAMESPACE}"
```

**Expected output:** `pod/keycloak-... condition met`

### 4. Retrieve the Keycloak external host

```bash
export KEYCLOAK_HOST=$(oc get route keycloak -n "${WANAKU_NAMESPACE}" -o jsonpath='{.spec.host}')
export KEYCLOAK_URL="http://${KEYCLOAK_HOST}"
echo "Keycloak URL: ${KEYCLOAK_URL}"
```

**Verification:**

```bash
if [ -z "${KEYCLOAK_HOST}" ]; then
  echo "FAIL: could not retrieve Keycloak route host"
  exit 1
fi
echo "PASS: Keycloak host is ${KEYCLOAK_HOST}"
```

### 5. Wait for Keycloak to respond

```bash
MAX_RETRIES=30
RETRY_INTERVAL=10
for i in $(seq 1 ${MAX_RETRIES}); do
  HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "${KEYCLOAK_URL}/realms/master" 2>/dev/null || echo "000")
  if [ "${HTTP_CODE}" = "200" ]; then
    echo "PASS: Keycloak is responding (attempt ${i})"
    break
  fi
  if [ "${i}" -eq "${MAX_RETRIES}" ]; then
    echo "FAIL: Keycloak not responding after ${MAX_RETRIES} attempts"
    exit 1
  fi
  echo "Waiting for Keycloak... (attempt ${i}, HTTP ${HTTP_CODE})"
  sleep ${RETRY_INTERVAL}
done
```

### 6. Import the Wanaku realm using the CLI

The Wanaku CLI imports a full realm configuration that includes:
- The `wanaku` realm with all settings
- The `wanaku-service` client (confidential, service account enabled)
- The `wanaku-mcp-router` client (public, for the router)
- The `mcp-client` client (public, for MCP clients)
- All required roles, scopes, and service accounts

```bash
${WANAKU_CLI} admin realm create \
  --keycloak-url "${KEYCLOAK_URL}" \
  --admin-username "${KEYCLOAK_ADMIN_USER}" \
  --admin-password "${KEYCLOAK_ADMIN_PASS}" \
  --config "${WANAKU_REPO_ROOT}/deploy/auth/wanaku-config.json"
```

**Expected output:** `Realm imported successfully from <path>/deploy/auth/wanaku-config.json`

**Verification:**

```bash
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "${KEYCLOAK_URL}/realms/wanaku" 2>/dev/null || echo "000")
if [ "${HTTP_CODE}" != "200" ]; then
  echo "FAIL: wanaku realm not accessible (HTTP ${HTTP_CODE})"
  exit 1
fi
echo "PASS: wanaku realm is accessible"
```

### 7. Retrieve the OIDC client secret for wanaku-service

The realm configuration sets the `wanaku-service` client secret via the Keycloak variable `${WANAKU_SERVICE_SECRET:mypasswd}`. By default this resolves to `mypasswd` unless the `WANAKU_SERVICE_SECRET` environment variable was set on the Keycloak container.

To retrieve the actual secret from Keycloak, obtain an admin token and query the API:

```bash
# Obtain an admin token
KEYCLOAK_ADMIN_TOKEN=$(curl -s \
  -d "client_id=admin-cli" \
  -d "username=${KEYCLOAK_ADMIN_USER}" \
  -d "password=${KEYCLOAK_ADMIN_PASS}" \
  -d "grant_type=password" \
  "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" | jq -r '.access_token')

if [ -z "${KEYCLOAK_ADMIN_TOKEN}" ] || [ "${KEYCLOAK_ADMIN_TOKEN}" = "null" ]; then
  echo "FAIL: could not obtain Keycloak admin token"
  exit 1
fi

# Get the internal UUID of the wanaku-service client
WANAKU_SERVICE_UUID=$(curl -s \
  -H "Authorization: Bearer ${KEYCLOAK_ADMIN_TOKEN}" \
  "${KEYCLOAK_URL}/admin/realms/wanaku/clients?clientId=wanaku-service" \
  | jq -r '.[0].id')

if [ -z "${WANAKU_SERVICE_UUID}" ] || [ "${WANAKU_SERVICE_UUID}" = "null" ]; then
  echo "FAIL: could not find UUID for client wanaku-service"
  exit 1
fi

# Retrieve the secret
export WANAKU_OIDC_SECRET=$(curl -s \
  -H "Authorization: Bearer ${KEYCLOAK_ADMIN_TOKEN}" \
  "${KEYCLOAK_URL}/admin/realms/wanaku/clients/${WANAKU_SERVICE_UUID}/client-secret" \
  | jq -r '.value')

if [ -z "${WANAKU_OIDC_SECRET}" ] || [ "${WANAKU_OIDC_SECRET}" = "null" ]; then
  echo "FAIL: could not retrieve OIDC secret"
  exit 1
fi
echo "PASS: OIDC secret retrieved (length: ${#WANAKU_OIDC_SECRET})"
```

### 8. Verify the OIDC token endpoint works

```bash
TOKEN_RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" \
  -d "client_id=wanaku-service" \
  -d "client_secret=${WANAKU_OIDC_SECRET}" \
  -d "grant_type=client_credentials" \
  "${KEYCLOAK_URL}/realms/wanaku/protocol/openid-connect/token")

if [ "${TOKEN_RESPONSE}" != "200" ]; then
  echo "FAIL: token endpoint returned HTTP ${TOKEN_RESPONSE}"
  exit 1
fi
echo "PASS: OIDC token endpoint works"
```

## Output Variables

After completing this procedure, the following variables are set and available for subsequent steps:

| Variable | Description |
|----------|-------------|
| `KEYCLOAK_HOST` | External hostname of Keycloak (from OpenShift Route) |
| `KEYCLOAK_URL` | Full URL (`http://<host>`) |
| `WANAKU_OIDC_SECRET` | Client secret for the `wanaku-service` client |
