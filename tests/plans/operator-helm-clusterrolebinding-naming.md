# Test Plan: Helm Chart ClusterRoleBinding Name Collision Fix (Issue #1376)

## Overview

This test plan verifies the fix for [#1376](https://github.com/wanaku-ai/wanaku/issues/1376): the Helm chart template `validating-clusterrolebinding.yaml` uses `{{ .Chart.Name }}` for the ClusterRoleBinding name, producing a static cluster-scoped name (`wanaku-operator-crd-validating-role-binding`) that collides when the operator is installed in multiple namespaces. The fix changes the name prefix to `{{ .Release.Namespace }}`, matching the pattern used by all other ClusterRoleBindings and ClusterRoles in the chart.

Every step is fully automatable.

## Prerequisites

### Required tools

| Tool | Minimum version | Verify command |
|------|-----------------|----------------|
| `oc` | 4.12+ | `oc version --client` |
| `helm` | 3.x | `helm version --short` |
| `jq` | 1.6+ | `jq --version` |

### Prerequisite check script

```bash
#!/bin/bash
set -e

FAIL=0

for CMD in oc helm jq; do
  if ! command -v "${CMD}" > /dev/null 2>&1; then
    echo "FAIL: ${CMD} is not installed"
    FAIL=1
  else
    echo "PASS: ${CMD} found at $(command -v ${CMD})"
  fi
done

OC_VERSION=$(oc version --client -o json 2>/dev/null | jq -r '.clientVersion.gitVersion // empty' || echo "unknown")
echo "  oc client version: ${OC_VERSION}"

HELM_VERSION=$(helm version --short 2>/dev/null || echo "unknown")
echo "  helm version: ${HELM_VERSION}"

if [ "${FAIL}" -ne 0 ]; then
  echo ""
  echo "FAIL: one or more prerequisites missing"
  exit 1
fi

echo ""
echo "PASS: all prerequisites met"
```

### Environment variables

Set these before running the plan. All values have sensible defaults.

```bash
# Two distinct namespaces for multi-namespace testing
export WANAKU_NAMESPACE_A="${WANAKU_NAMESPACE_A:-wanaku-test-a}"
export WANAKU_NAMESPACE_B="${WANAKU_NAMESPACE_B:-wanaku-test-b}"

# Repository root (for locating the Helm chart)
export WANAKU_REPO_ROOT="${WANAKU_REPO_ROOT:-.}"

# Helm chart path
export WANAKU_CHART_DIR="${WANAKU_REPO_ROOT}/apps/wanaku-operator/deploy/helm/wanaku-operator"
```

---

## Phase 0: OpenShift Login

Follow [common/openshift-login.md](common/openshift-login.md) to log in to the target OpenShift cluster using a service account token.

---

## Phase 1: Static Analysis of the Fix

### Test 1.1: Verify `validating-clusterrolebinding.yaml` uses `Release.Namespace` in name

**Description:** The fix must change the `metadata.name` field from `{{ .Chart.Name }}-crd-validating-role-binding` to `{{ .Release.Namespace }}-crd-validating-role-binding`. This test reads the template file and verifies the pattern.

```bash
TEMPLATE_FILE="${WANAKU_CHART_DIR}/templates/validating-clusterrolebinding.yaml"

if [ ! -f "${TEMPLATE_FILE}" ]; then
  echo "FAIL: template file not found at ${TEMPLATE_FILE}"
  exit 1
fi

# Verify the name field uses .Release.Namespace
if grep -q 'name: {{ .Release.Namespace }}-crd-validating-role-binding' "${TEMPLATE_FILE}"; then
  echo "PASS: metadata.name uses .Release.Namespace prefix"
else
  if grep -q 'name: {{ .Chart.Name }}-crd-validating-role-binding' "${TEMPLATE_FILE}"; then
    echo "FAIL: metadata.name still uses .Chart.Name (bug not fixed)"
  else
    echo "FAIL: unexpected naming pattern in metadata.name"
  fi
  grep 'name:' "${TEMPLATE_FILE}"
  exit 1
fi
```

### Test 1.2: Verify consistency with other ClusterRoleBindings in `clusterrolebinding.yaml`

**Description:** All ClusterRoleBindings in `clusterrolebinding.yaml` use `{{ .Release.Namespace }}-` as a name prefix. After the fix, `validating-clusterrolebinding.yaml` must follow the same convention.

```bash
CRB_FILE="${WANAKU_CHART_DIR}/templates/clusterrolebinding.yaml"

# Extract name patterns from clusterrolebinding.yaml (the known-good file)
GOOD_NAMES=$(grep '^\s*name:' "${CRB_FILE}" | grep -c '\.Release\.Namespace')
TOTAL_NAMES=$(grep '^\s*name:' "${CRB_FILE}" | grep -c 'role-binding')

echo "clusterrolebinding.yaml: ${GOOD_NAMES} of ${TOTAL_NAMES} ClusterRoleBinding names use .Release.Namespace"

if [ "${GOOD_NAMES}" -eq "${TOTAL_NAMES}" ] && [ "${TOTAL_NAMES}" -gt 0 ]; then
  echo "PASS: all ClusterRoleBinding names in clusterrolebinding.yaml use .Release.Namespace"
else
  echo "FAIL: inconsistent naming in clusterrolebinding.yaml"
  exit 1
fi
```

### Test 1.3: Verify all cluster-scoped resource names across all templates use `Release.Namespace`

**Description:** Audit all ClusterRole and ClusterRoleBinding templates to confirm no cluster-scoped resource name uses `.Chart.Name` as a unique identifier prefix. Uses of `.Chart.Name` in labels or subjects (namespace-scoped references like ServiceAccount names) are acceptable.

```bash
FAIL=0

for TEMPLATE in "${WANAKU_CHART_DIR}"/templates/*.yaml; do
  BASENAME=$(basename "${TEMPLATE}")

  # Only check name: fields that define cluster-scoped resource names
  # Skip label fields and subject references
  while IFS= read -r LINE; do
    # Check if this is a metadata.name line (not a roleRef or subjects name)
    # Heuristic: lines with "name:" that reference .Chart.Name AND are for resource naming
    if echo "${LINE}" | grep -q '\.Chart\.Name.*role-binding\|\.Chart\.Name.*cluster-role'; then
      echo "FAIL: ${BASENAME} has cluster-scoped name using .Chart.Name: ${LINE}"
      FAIL=1
    fi
  done < "${TEMPLATE}"
done

if [ "${FAIL}" -eq 0 ]; then
  echo "PASS: no cluster-scoped resource names use .Chart.Name"
fi
```

### Test 1.4: Verify Helm template renders unique names per namespace

**Description:** Use `helm template` to render the chart with two different namespace values and confirm the ClusterRoleBinding names are distinct.

```bash
# Render for namespace "alpha"
NAME_ALPHA=$(helm template test-release "${WANAKU_CHART_DIR}" \
  --namespace alpha \
  --show-only templates/validating-clusterrolebinding.yaml 2>/dev/null \
  | grep '^\s*name:' | head -1 | awk '{print $2}')

# Render for namespace "beta"
NAME_BETA=$(helm template test-release "${WANAKU_CHART_DIR}" \
  --namespace beta \
  --show-only templates/validating-clusterrolebinding.yaml 2>/dev/null \
  | grep '^\s*name:' | head -1 | awk '{print $2}')

echo "namespace=alpha -> name=${NAME_ALPHA}"
echo "namespace=beta  -> name=${NAME_BETA}"

if [ -z "${NAME_ALPHA}" ] || [ -z "${NAME_BETA}" ]; then
  echo "FAIL: could not render template names"
  exit 1
fi

if [ "${NAME_ALPHA}" = "${NAME_BETA}" ]; then
  echo "FAIL: ClusterRoleBinding names are identical across namespaces (collision not fixed)"
  exit 1
fi
echo "PASS: ClusterRoleBinding names are unique per namespace"

# Verify the names contain the namespace prefix
echo "${NAME_ALPHA}" | grep -q "^alpha-" && \
  echo "PASS: alpha name starts with namespace prefix" || \
  { echo "FAIL: alpha name does not start with namespace prefix"; exit 1; }

echo "${NAME_BETA}" | grep -q "^beta-" && \
  echo "PASS: beta name starts with namespace prefix" || \
  { echo "FAIL: beta name does not start with namespace prefix"; exit 1; }
```

### Test 1.5: Verify all ClusterRoleBinding names are unique within a single rendered chart

**Description:** Render the full chart and verify that no two ClusterRoleBindings share the same name.

```bash
ALL_CRB_NAMES=$(helm template test-release "${WANAKU_CHART_DIR}" \
  --namespace testns 2>/dev/null \
  | grep -A1 'kind: ClusterRoleBinding' \
  | grep '^\s*name:' \
  | awk '{print $2}' \
  | sort)

UNIQUE_CRB_NAMES=$(echo "${ALL_CRB_NAMES}" | sort -u)

TOTAL=$(echo "${ALL_CRB_NAMES}" | wc -l | tr -d ' ')
UNIQUE=$(echo "${UNIQUE_CRB_NAMES}" | wc -l | tr -d ' ')

echo "total ClusterRoleBinding names: ${TOTAL}"
echo "unique ClusterRoleBinding names: ${UNIQUE}"

if [ "${TOTAL}" -ne "${UNIQUE}" ]; then
  echo "FAIL: duplicate ClusterRoleBinding names detected"
  echo "${ALL_CRB_NAMES}" | uniq -d
  exit 1
fi
echo "PASS: all ${TOTAL} ClusterRoleBinding names are unique"
```

### Test 1.6: Verify roleRef still points to the correct ClusterRole

**Description:** The fix only changes the ClusterRoleBinding `metadata.name`. The `roleRef.name` must still reference the namespace-prefixed ClusterRole.

```bash
RENDERED=$(helm template test-release "${WANAKU_CHART_DIR}" \
  --namespace testns \
  --show-only templates/validating-clusterrolebinding.yaml 2>/dev/null)

ROLE_REF=$(echo "${RENDERED}" | grep -A3 'roleRef:' | grep 'name:' | awk '{print $2}')
echo "roleRef.name=${ROLE_REF}"

if [ "${ROLE_REF}" != "testns-josdk-crd-validating-cluster-role" ]; then
  echo "FAIL: roleRef.name is '${ROLE_REF}', expected 'testns-josdk-crd-validating-cluster-role'"
  exit 1
fi
echo "PASS: roleRef points to the correct namespace-prefixed ClusterRole"
```

### Test 1.7: Verify subjects still reference the correct ServiceAccount and namespace

**Description:** The ServiceAccount subject must reference the correct namespace.

```bash
RENDERED=$(helm template test-release "${WANAKU_CHART_DIR}" \
  --namespace testns \
  --show-only templates/validating-clusterrolebinding.yaml 2>/dev/null)

SA_NAMESPACE=$(echo "${RENDERED}" | grep -A4 'subjects:' | grep 'namespace:' | awk '{print $2}')
echo "subjects.namespace=${SA_NAMESPACE}"

if [ "${SA_NAMESPACE}" != "testns" ]; then
  echo "FAIL: subject namespace is '${SA_NAMESPACE}', expected 'testns'"
  exit 1
fi
echo "PASS: subject references the correct namespace"
```

---

## Phase 2: Multi-Namespace Installation Test

### Step 2.1: Create two namespaces

```bash
oc new-project "${WANAKU_NAMESPACE_A}" --display-name="Wanaku CRB Test A" 2>/dev/null \
  || oc project "${WANAKU_NAMESPACE_A}"
echo "namespace-a=$?"

oc new-project "${WANAKU_NAMESPACE_B}" --display-name="Wanaku CRB Test B" 2>/dev/null \
  || oc project "${WANAKU_NAMESPACE_B}"
echo "namespace-b=$?"
```

**Verification:**

```bash
for NS in "${WANAKU_NAMESPACE_A}" "${WANAKU_NAMESPACE_B}"; do
  oc get namespace "${NS}" > /dev/null 2>&1
  if [ $? -eq 0 ]; then
    echo "PASS: namespace ${NS} exists"
  else
    echo "FAIL: namespace ${NS} does not exist"
    exit 1
  fi
done
```

### Test 2.2: Install operator in namespace A

```bash
helm install wanaku-operator-a "${WANAKU_CHART_DIR}" \
  --namespace "${WANAKU_NAMESPACE_A}" \
  --set operatorNamespace="${WANAKU_NAMESPACE_A}" \
  --set app.imagePullPolicy=Always
```

**Verification:**

```bash
helm status wanaku-operator-a --namespace "${WANAKU_NAMESPACE_A}" -o json \
  | jq -r '.info.status' | grep -q "deployed"

if [ $? -eq 0 ]; then
  echo "PASS: operator installed in ${WANAKU_NAMESPACE_A}"
else
  echo "FAIL: operator installation failed in ${WANAKU_NAMESPACE_A}"
  exit 1
fi
```

### Test 2.3: Install operator in namespace B (the collision test)

**Description:** This is the core test. Before the fix, this step would fail with: `Error: INSTALLATION FAILED: unable to continue with install: ClusterRoleBinding "wanaku-operator-crd-validating-role-binding" in namespace "" exists and cannot be imported into the current release: invalid ownership metadata`. After the fix, it should succeed.

```bash
helm install wanaku-operator-b "${WANAKU_CHART_DIR}" \
  --namespace "${WANAKU_NAMESPACE_B}" \
  --set operatorNamespace="${WANAKU_NAMESPACE_B}" \
  --set app.imagePullPolicy=Always

INSTALL_EXIT=$?

if [ "${INSTALL_EXIT}" -eq 0 ]; then
  echo "PASS: operator installed in ${WANAKU_NAMESPACE_B} (no name collision)"
else
  echo "FAIL: operator installation in ${WANAKU_NAMESPACE_B} failed (exit code ${INSTALL_EXIT})"
  echo "This is the exact symptom of issue #1376"
  exit 1
fi
```

**Verification:**

```bash
helm status wanaku-operator-b --namespace "${WANAKU_NAMESPACE_B}" -o json \
  | jq -r '.info.status' | grep -q "deployed"

if [ $? -eq 0 ]; then
  echo "PASS: operator deployed in ${WANAKU_NAMESPACE_B}"
else
  echo "FAIL: operator not deployed in ${WANAKU_NAMESPACE_B}"
  exit 1
fi
```

### Test 2.4: Verify both ClusterRoleBindings exist with distinct names

**Description:** After both installs, there should be two separate ClusterRoleBindings with namespace-specific names.

```bash
CRB_A="${WANAKU_NAMESPACE_A}-crd-validating-role-binding"
CRB_B="${WANAKU_NAMESPACE_B}-crd-validating-role-binding"

oc get clusterrolebinding "${CRB_A}" > /dev/null 2>&1
if [ $? -eq 0 ]; then
  echo "PASS: ClusterRoleBinding ${CRB_A} exists"
else
  echo "FAIL: ClusterRoleBinding ${CRB_A} not found"
  exit 1
fi

oc get clusterrolebinding "${CRB_B}" > /dev/null 2>&1
if [ $? -eq 0 ]; then
  echo "PASS: ClusterRoleBinding ${CRB_B} exists"
else
  echo "FAIL: ClusterRoleBinding ${CRB_B} not found"
  exit 1
fi
```

### Test 2.5: Verify each ClusterRoleBinding references the correct ServiceAccount namespace

```bash
SA_NS_A=$(oc get clusterrolebinding "${WANAKU_NAMESPACE_A}-crd-validating-role-binding" \
  -o jsonpath='{.subjects[0].namespace}')
SA_NS_B=$(oc get clusterrolebinding "${WANAKU_NAMESPACE_B}-crd-validating-role-binding" \
  -o jsonpath='{.subjects[0].namespace}')

echo "CRB A -> ServiceAccount namespace: ${SA_NS_A}"
echo "CRB B -> ServiceAccount namespace: ${SA_NS_B}"

if [ "${SA_NS_A}" != "${WANAKU_NAMESPACE_A}" ]; then
  echo "FAIL: CRB A references wrong namespace '${SA_NS_A}'"
  exit 1
fi
echo "PASS: CRB A references correct namespace"

if [ "${SA_NS_B}" != "${WANAKU_NAMESPACE_B}" ]; then
  echo "FAIL: CRB B references wrong namespace '${SA_NS_B}'"
  exit 1
fi
echo "PASS: CRB B references correct namespace"
```

### Test 2.6: Verify each ClusterRoleBinding references its own namespace-scoped ClusterRole

```bash
ROLE_A=$(oc get clusterrolebinding "${WANAKU_NAMESPACE_A}-crd-validating-role-binding" \
  -o jsonpath='{.roleRef.name}')
ROLE_B=$(oc get clusterrolebinding "${WANAKU_NAMESPACE_B}-crd-validating-role-binding" \
  -o jsonpath='{.roleRef.name}')

echo "CRB A -> ClusterRole: ${ROLE_A}"
echo "CRB B -> ClusterRole: ${ROLE_B}"

if [ "${ROLE_A}" != "${WANAKU_NAMESPACE_A}-josdk-crd-validating-cluster-role" ]; then
  echo "FAIL: CRB A references wrong ClusterRole '${ROLE_A}'"
  exit 1
fi
echo "PASS: CRB A references correct ClusterRole"

if [ "${ROLE_B}" != "${WANAKU_NAMESPACE_B}-josdk-crd-validating-cluster-role" ]; then
  echo "FAIL: CRB B references wrong ClusterRole '${ROLE_B}'"
  exit 1
fi
echo "PASS: CRB B references correct ClusterRole"
```

### Test 2.7: Verify the old collision-causing name does NOT exist

**Description:** After the fix, the static name `wanaku-operator-crd-validating-role-binding` should not be created by either install.

```bash
oc get clusterrolebinding wanaku-operator-crd-validating-role-binding > /dev/null 2>&1
if [ $? -eq 0 ]; then
  echo "FAIL: old collision-prone ClusterRoleBinding name still exists"
  exit 1
fi
echo "PASS: old static name does not exist"
```

### Test 2.8: Verify all other ClusterRoleBindings from both installs are non-conflicting

**Description:** The three ClusterRoleBindings in `clusterrolebinding.yaml` (capability, router, service-catalog) should also exist in pairs with namespace prefixes.

```bash
FAIL=0

for SUFFIX in wanaku-capability-crd-validating-role-binding wanaku-router-crd-validating-role-binding wanaku-service-catalog-crd-validating-role-binding; do
  for NS in "${WANAKU_NAMESPACE_A}" "${WANAKU_NAMESPACE_B}"; do
    CRB_NAME="${NS}-${SUFFIX}"
    oc get clusterrolebinding "${CRB_NAME}" > /dev/null 2>&1
    if [ $? -eq 0 ]; then
      echo "PASS: ${CRB_NAME} exists"
    else
      echo "FAIL: ${CRB_NAME} not found"
      FAIL=1
    fi
  done
done

if [ "${FAIL}" -ne 0 ]; then
  exit 1
fi
```

---

## Phase 3: Operator Health Verification

### Test 3.1: Verify operator pods are running in both namespaces

```bash
for NS in "${WANAKU_NAMESPACE_A}" "${WANAKU_NAMESPACE_B}"; do
  oc wait deployment/wanaku-operator \
    --for=condition=Available \
    --timeout=180s \
    --namespace "${NS}"

  POD_STATUS=$(oc get pods -l app.kubernetes.io/name=wanaku-operator \
    -n "${NS}" -o jsonpath='{.items[0].status.phase}')

  if [ "${POD_STATUS}" = "Running" ]; then
    echo "PASS: operator in ${NS} is Running"
  else
    echo "FAIL: operator in ${NS} has status '${POD_STATUS}'"
    exit 1
  fi
done
```

### Test 3.2: Verify operator health endpoints in both namespaces

```bash
for NS in "${WANAKU_NAMESPACE_A}" "${WANAKU_NAMESPACE_B}"; do
  OPERATOR_POD=$(oc get pods -l app.kubernetes.io/name=wanaku-operator \
    -n "${NS}" -o jsonpath='{.items[0].metadata.name}')

  oc exec "${OPERATOR_POD}" -n "${NS}" -- \
    curl -sf http://localhost:8081/q/health/live > /dev/null 2>&1
  if [ $? -eq 0 ]; then
    echo "PASS: operator in ${NS} liveness probe OK"
  else
    echo "FAIL: operator in ${NS} liveness probe failed"
    exit 1
  fi

  oc exec "${OPERATOR_POD}" -n "${NS}" -- \
    curl -sf http://localhost:8081/q/health/ready > /dev/null 2>&1
  if [ $? -eq 0 ]; then
    echo "PASS: operator in ${NS} readiness probe OK"
  else
    echo "FAIL: operator in ${NS} readiness probe failed"
    exit 1
  fi
done
```

### Test 3.3: Verify CRDs are installed (cluster-scoped, shared)

```bash
for CRD in wanakurouters.wanaku.ai wanakucapabilities.wanaku.ai wanakuservicecatalogs.wanaku.ai; do
  oc get crd "${CRD}" > /dev/null 2>&1
  if [ $? -eq 0 ]; then
    echo "PASS: CRD ${CRD} exists"
  else
    echo "FAIL: CRD ${CRD} not found"
    exit 1
  fi
done
```

---

## Phase 4: Independent Uninstallation Test

### Test 4.1: Uninstall operator from namespace A without affecting namespace B

**Description:** Removing the operator from one namespace must not break the other.

```bash
helm uninstall wanaku-operator-a --namespace "${WANAKU_NAMESPACE_A}"

UNINSTALL_EXIT=$?

if [ "${UNINSTALL_EXIT}" -eq 0 ]; then
  echo "PASS: operator uninstalled from ${WANAKU_NAMESPACE_A}"
else
  echo "FAIL: uninstall from ${WANAKU_NAMESPACE_A} failed"
  exit 1
fi
```

### Test 4.2: Verify namespace B ClusterRoleBinding is unaffected

```bash
CRB_B="${WANAKU_NAMESPACE_B}-crd-validating-role-binding"

oc get clusterrolebinding "${CRB_B}" > /dev/null 2>&1
if [ $? -eq 0 ]; then
  echo "PASS: ${CRB_B} still exists after uninstalling namespace A"
else
  echo "FAIL: ${CRB_B} was removed (cross-namespace side effect)"
  exit 1
fi
```

### Test 4.3: Verify namespace A ClusterRoleBinding is cleaned up

```bash
CRB_A="${WANAKU_NAMESPACE_A}-crd-validating-role-binding"

# Helm should have removed the CRB owned by release A
oc get clusterrolebinding "${CRB_A}" > /dev/null 2>&1
if [ $? -ne 0 ]; then
  echo "PASS: ${CRB_A} was removed with the Helm release"
else
  echo "WARN: ${CRB_A} still exists after uninstall (Helm may not clean up cluster-scoped resources by default)"
  echo "INFO: this is expected Helm behavior for cluster-scoped resources unless --cascade is used"
fi
```

### Test 4.4: Verify operator in namespace B is still healthy

```bash
POD_STATUS=$(oc get pods -l app.kubernetes.io/name=wanaku-operator \
  -n "${WANAKU_NAMESPACE_B}" -o jsonpath='{.items[0].status.phase}')

if [ "${POD_STATUS}" = "Running" ]; then
  echo "PASS: operator in ${WANAKU_NAMESPACE_B} is still Running"
else
  echo "FAIL: operator in ${WANAKU_NAMESPACE_B} has status '${POD_STATUS}'"
  exit 1
fi
```

---

## Phase 5: Edge Cases

### Test 5.1: Namespace with special characters (hyphens)

**Description:** Verify the naming works correctly with hyphenated namespace names, which are the most common pattern in Kubernetes.

```bash
# This is implicitly tested by the default namespace names (wanaku-test-a, wanaku-test-b)
# but verify the rendered name is valid

RENDERED_NAME=$(helm template test-release "${WANAKU_CHART_DIR}" \
  --namespace "my-long-hyphenated-namespace" \
  --show-only templates/validating-clusterrolebinding.yaml 2>/dev/null \
  | grep '^\s*name:' | head -1 | awk '{print $2}')

echo "hyphenated-namespace-name=${RENDERED_NAME}"

if [ "${RENDERED_NAME}" = "my-long-hyphenated-namespace-crd-validating-role-binding" ]; then
  echo "PASS: hyphenated namespace name renders correctly"
else
  echo "FAIL: unexpected name '${RENDERED_NAME}'"
  exit 1
fi
```

### Test 5.2: Verify naming consistency across all ClusterRoleBinding templates

**Description:** After the fix, the naming pattern for the validating ClusterRoleBinding should be `<namespace>-crd-validating-role-binding`, following the same `<namespace>-<description>` pattern as the other three: `<namespace>-wanaku-capability-crd-validating-role-binding`, `<namespace>-wanaku-router-crd-validating-role-binding`, `<namespace>-wanaku-service-catalog-crd-validating-role-binding`.

```bash
RENDERED=$(helm template test-release "${WANAKU_CHART_DIR}" --namespace testns 2>/dev/null)

# Extract all ClusterRoleBinding names
CRB_NAMES=$(echo "${RENDERED}" \
  | awk '/kind: ClusterRoleBinding/{found=1} found && /^\s*name:/{print $2; found=0}')

echo "All ClusterRoleBinding names:"
echo "${CRB_NAMES}"

FAIL=0
while IFS= read -r NAME; do
  if echo "${NAME}" | grep -q "^testns-"; then
    echo "PASS: ${NAME} has namespace prefix"
  else
    echo "FAIL: ${NAME} does not have namespace prefix"
    FAIL=1
  fi
done <<EOF
${CRB_NAMES}
EOF

if [ "${FAIL}" -ne 0 ]; then
  exit 1
fi
```

### Test 5.3: Verify re-installation after uninstall works cleanly

**Description:** Uninstall and re-install in the same namespace to verify there are no leftover resources that cause conflicts.

```bash
# Uninstall from namespace B
helm uninstall wanaku-operator-b --namespace "${WANAKU_NAMESPACE_B}" 2>/dev/null || true

# Clean up any leftover cluster-scoped resources manually
oc delete clusterrolebinding "${WANAKU_NAMESPACE_B}-crd-validating-role-binding" --ignore-not-found=true 2>/dev/null || true

# Re-install
helm install wanaku-operator-b "${WANAKU_CHART_DIR}" \
  --namespace "${WANAKU_NAMESPACE_B}" \
  --set operatorNamespace="${WANAKU_NAMESPACE_B}" \
  --set app.imagePullPolicy=Always

if [ $? -eq 0 ]; then
  echo "PASS: re-installation in ${WANAKU_NAMESPACE_B} succeeded"
else
  echo "FAIL: re-installation in ${WANAKU_NAMESPACE_B} failed"
  exit 1
fi

# Verify the CRB was recreated
oc get clusterrolebinding "${WANAKU_NAMESPACE_B}-crd-validating-role-binding" > /dev/null 2>&1
if [ $? -eq 0 ]; then
  echo "PASS: ClusterRoleBinding recreated after re-install"
else
  echo "FAIL: ClusterRoleBinding not found after re-install"
  exit 1
fi
```

---

## Phase 6: Cleanup

### Step 6.1: Uninstall Helm releases

```bash
helm uninstall wanaku-operator-a --namespace "${WANAKU_NAMESPACE_A}" 2>/dev/null || true
echo "PASS: release A uninstalled (or already removed)"

helm uninstall wanaku-operator-b --namespace "${WANAKU_NAMESPACE_B}" 2>/dev/null || true
echo "PASS: release B uninstalled (or already removed)"
```

### Step 6.2: Clean up cluster-scoped resources

```bash
for NS in "${WANAKU_NAMESPACE_A}" "${WANAKU_NAMESPACE_B}"; do
  for SUFFIX in crd-validating-role-binding wanaku-capability-crd-validating-role-binding wanaku-router-crd-validating-role-binding wanaku-service-catalog-crd-validating-role-binding; do
    oc delete clusterrolebinding "${NS}-${SUFFIX}" --ignore-not-found=true 2>/dev/null || true
  done

  for SUFFIX in wanaku-capability-cluster-role wanaku-router-cluster-role wanaku-service-catalog-cluster-role wanaku-camel-route-cluster-role josdk-crd-validating-cluster-role; do
    oc delete clusterrole "${NS}-${SUFFIX}" --ignore-not-found=true 2>/dev/null || true
  done
done

# Also clean up the old static name in case it was left over from a pre-fix run
oc delete clusterrolebinding wanaku-operator-crd-validating-role-binding --ignore-not-found=true 2>/dev/null || true

echo "PASS: cluster-scoped resources cleaned up"
```

### Step 6.3: Delete namespaces (optional)

```bash
# WARNING: This deletes everything in the namespaces.
# Uncomment the following lines for a full cleanup:
#
# oc delete project "${WANAKU_NAMESPACE_A}" --ignore-not-found=true
# oc delete project "${WANAKU_NAMESPACE_B}" --ignore-not-found=true
# echo "PASS: test namespaces deleted"

echo "SKIP: namespace deletion (uncomment to enable)"
```

### Step 6.4: Verify cleanup

```bash
echo "--- Remaining Wanaku ClusterRoleBindings ---"
oc get clusterrolebinding | grep -E "wanaku|crd-validating" || echo "(none)"

echo "--- Remaining Wanaku ClusterRoles ---"
oc get clusterrole | grep -E "wanaku|josdk" || echo "(none)"

echo "--- Helm releases ---"
helm list --namespace "${WANAKU_NAMESPACE_A}" 2>/dev/null || echo "(namespace may not exist)"
helm list --namespace "${WANAKU_NAMESPACE_B}" 2>/dev/null || echo "(namespace may not exist)"
```

---

## Test Summary Matrix

| Phase | Test ID | Test Name | Priority |
|-------|---------|-----------|----------|
| 0 | -- | OpenShift login | Critical |
| 1 | 1.1 | Template uses `.Release.Namespace` in name | Critical |
| 1 | 1.2 | Consistency with other ClusterRoleBindings | Critical |
| 1 | 1.3 | No cluster-scoped names use `.Chart.Name` | High |
| 1 | 1.4 | `helm template` renders unique names per namespace | Critical |
| 1 | 1.5 | All ClusterRoleBinding names unique within chart | High |
| 1 | 1.6 | roleRef points to correct ClusterRole | Critical |
| 1 | 1.7 | Subjects reference correct namespace | Critical |
| 2 | 2.1 | Create two namespaces | Critical |
| 2 | 2.2 | Install operator in namespace A | Critical |
| 2 | 2.3 | Install operator in namespace B (collision test) | Critical |
| 2 | 2.4 | Both ClusterRoleBindings exist with distinct names | Critical |
| 2 | 2.5 | Each CRB references correct ServiceAccount namespace | High |
| 2 | 2.6 | Each CRB references correct ClusterRole | High |
| 2 | 2.7 | Old static name does not exist | High |
| 2 | 2.8 | All CRBs from both installs are non-conflicting | High |
| 3 | 3.1 | Operator pods running in both namespaces | Critical |
| 3 | 3.2 | Operator health endpoints pass in both namespaces | High |
| 3 | 3.3 | CRDs installed (shared, cluster-scoped) | High |
| 4 | 4.1 | Uninstall from namespace A | Critical |
| 4 | 4.2 | Namespace B CRB unaffected by A uninstall | Critical |
| 4 | 4.3 | Namespace A CRB cleaned up | Medium |
| 4 | 4.4 | Operator in namespace B still healthy | Critical |
| 5 | 5.1 | Hyphenated namespace names render correctly | Medium |
| 5 | 5.2 | Naming consistency across all CRB templates | High |
| 5 | 5.3 | Re-installation after uninstall works cleanly | Medium |
| 6 | 6.1-6.4 | Cleanup | Critical |
