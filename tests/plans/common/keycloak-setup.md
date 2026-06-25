# Common: Keycloak Setup on OpenShift

Reusable steps for deploying and configuring Keycloak as the OIDC provider for Wanaku on OpenShift.

## Prerequisites

- Namespace created (see [namespace-setup.md](namespace-setup.md))
- `WANAKU_NAMESPACE` environment variable set
- `WANAKU_REPO_ROOT` environment variable set (path to the wanaku repository root)
- `jq` available
- `curl` available (only for Keycloak readiness polling — all Wanaku/Keycloak API calls use the CLI)
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

### 5. Set KC_HOSTNAME to match the external route

Setting `KC_HOSTNAME` ensures Keycloak stamps the same issuer in tokens regardless of whether
they are obtained via the external route or internally. Without this, tokens obtained externally
(e.g. `http://keycloak-wanaku-test.<cluster>/realms/wanaku`) will have a different issuer than
what the router expects (`http://keycloak:8080/realms/wanaku`), causing 401 errors.

**Important:** In Keycloak 26+, `KC_HOSTNAME` must be a **full URL** (e.g., `http://hostname`),
not a bare hostname. A bare hostname causes Keycloak to append the internal HTTP port (8080) to
the issuer URL, which is unreachable from inside the cluster via the external route and causes
the Quarkus OIDC tenants to timeout sequentially at startup.

```bash
oc set env deployment/keycloak \
  KC_HOSTNAME="${KEYCLOAK_URL}" \
  KC_HOSTNAME_STRICT=false \
  -n "${WANAKU_NAMESPACE}"
```

Wait for the rollout to complete (the env change triggers a new pod):

```bash
oc rollout status deployment/keycloak \
  --timeout=300s \
  -n "${WANAKU_NAMESPACE}"
```

**Expected output:** `deployment "keycloak" successfully rolled out`

### 6. Wait for Keycloak to respond

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

### 7. Import the Wanaku realm using the CLI

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

### 8. Retrieve the OIDC client secret for wanaku-service

The realm configuration sets the `wanaku-service` client secret via the Keycloak variable `${WANAKU_SERVICE_SECRET:mypasswd}`. By default this resolves to `mypasswd` unless the `WANAKU_SERVICE_SECRET` environment variable was set on the Keycloak container.

Retrieve the actual secret using the CLI:

```bash
CREDENTIALS_OUTPUT=$(${WANAKU_CLI} admin credentials show \
  --keycloak-url "${KEYCLOAK_URL}" \
  --admin-username "${KEYCLOAK_ADMIN_USER}" \
  --admin-password "${KEYCLOAK_ADMIN_PASS}" \
  --client-id wanaku-service \
  --show-secret \
  --plain 2>&1)

export WANAKU_OIDC_SECRET=$(echo "${CREDENTIALS_OUTPUT}" | grep "Client Secret:" | sed 's/.*Client Secret: //')

if [ -z "${WANAKU_OIDC_SECRET}" ] || [ "${WANAKU_OIDC_SECRET}" = "null" ]; then
  echo "FAIL: could not retrieve OIDC secret"
  echo "${CREDENTIALS_OUTPUT}"
  exit 1
fi
echo "PASS: OIDC secret retrieved (length: ${#WANAKU_OIDC_SECRET})"
```

### 9. Create the Kubernetes Secret for the operator

The operator Helm chart reads `WANAKU_OIDC_CLIENT_SECRET` from a Kubernetes Secret named `wanaku-oidc` (configurable via `app.oidc.secretName`). Create this Secret so the operator can authenticate with the OIDC-protected router when deploying service catalogs.

```bash
oc create secret generic wanaku-oidc \
  --from-literal=client-secret="${WANAKU_OIDC_SECRET}" \
  -n "${WANAKU_NAMESPACE}" 2>/dev/null \
  || oc get secret wanaku-oidc -n "${WANAKU_NAMESPACE}" > /dev/null 2>&1

if ! oc get secret wanaku-oidc -n "${WANAKU_NAMESPACE}" > /dev/null 2>&1; then
  echo "FAIL: could not create wanaku-oidc secret"
  exit 1
fi
echo "PASS: wanaku-oidc Kubernetes Secret created"
```

### 10. Create a test user

The realm import creates clients and roles but no regular users. Create a test user that
subsequent steps will use to interact with the router.

```bash
export WANAKU_TEST_USER="${WANAKU_TEST_USER:-alice}"
export WANAKU_TEST_PASS="${WANAKU_TEST_PASS:-secretpass}"
export WANAKU_TEST_EMAIL="${WANAKU_TEST_EMAIL:-alice@example.com}"

${WANAKU_CLI} admin users add \
  --keycloak-url "${KEYCLOAK_URL}" \
  --admin-username "${KEYCLOAK_ADMIN_USER}" \
  --admin-password "${KEYCLOAK_ADMIN_PASS}" \
  --username "${WANAKU_TEST_USER}" \
  --password "${WANAKU_TEST_PASS}" \
  --email "${WANAKU_TEST_EMAIL}"
```

**Verification:**

```bash
if [ $? -ne 0 ]; then
  echo "FAIL: could not create test user"
  exit 1
fi
echo "PASS: test user '${WANAKU_TEST_USER}' created"
```

### 11. Verify OIDC login (requires the router)

The `wanaku auth login` command authenticates via the router's OIDC proxy endpoint (`/q/oidc/...`), not directly against Keycloak. This means the WanakuRouter must be deployed and healthy before this step can run.

Follow [common/oidc-login-verification.md](oidc-login-verification.md) **after the router is created** in the test plan.

## Output Variables

After completing this procedure, the following variables are set and available for subsequent steps:

| Variable | Description |
|----------|-------------|
| `KEYCLOAK_HOST` | External hostname of Keycloak (from OpenShift Route) |
| `KEYCLOAK_URL` | Full URL (`http://<host>`) |
| `WANAKU_OIDC_SECRET` | Client secret for the `wanaku-service` client |
| `WANAKU_TEST_USER` | Username for authenticated CLI operations (default: `alice`) |
| `WANAKU_TEST_PASS` | Password for the test user (default: `secretpass`) |
| `WANAKU_TEST_EMAIL` | Email for the test user (default: `alice@example.com`) |
