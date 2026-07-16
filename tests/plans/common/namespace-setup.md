# Common: Namespace Setup

Reusable steps for creating and configuring the test namespace on OpenShift.
Use this shared setup from each OpenShift plan instead of re-defining the namespace derivation inline.

## Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `WANAKU_NAMESPACE_PREFIX` | Prefix used to derive a per-run namespace when one is not provided | `wanaku-test` |
| `WANAKU_TEST_RUN_ID` | Unique suffix for the namespace when one is not provided | `20260716-043000` |
| `WANAKU_NAMESPACE` | Target namespace for all Wanaku resources | `wanaku-test-<run-id>` |

## Steps

### 1. Export the namespace variable

```bash
export WANAKU_TEST_RUN_ID="${WANAKU_TEST_RUN_ID:-$(date +%Y%m%d-%H%M%S)-$$}"
export WANAKU_NAMESPACE_PREFIX="${WANAKU_NAMESPACE_PREFIX:-wanaku-test}"
export WANAKU_NAMESPACE="${WANAKU_NAMESPACE:-${WANAKU_NAMESPACE_PREFIX}-${WANAKU_TEST_RUN_ID}}"
```

This keeps each test run isolated by default while still allowing callers to override `WANAKU_NAMESPACE` when they intentionally want to reuse a namespace.

### 2. Create the namespace (project)

```bash
oc new-project "${WANAKU_NAMESPACE}" --display-name="Wanaku Operator Test" 2>/dev/null \
  || oc project "${WANAKU_NAMESPACE}"
```

**Verification:**

```bash
oc project -q | grep -q "${WANAKU_NAMESPACE}"
echo "exit_code=$?"
# Expected: exit_code=0
```

### 3. Verify current context targets the correct namespace

```bash
CURRENT_NS=$(oc project -q)
if [ "${CURRENT_NS}" != "${WANAKU_NAMESPACE}" ]; then
  echo "FAIL: current namespace is '${CURRENT_NS}', expected '${WANAKU_NAMESPACE}'"
  exit 1
fi
echo "PASS: namespace is ${WANAKU_NAMESPACE}"
```

### 4. Verify the user has sufficient permissions

```bash
oc auth can-i create deployments -n "${WANAKU_NAMESPACE}" | grep -q "yes"
echo "can-create-deployments=$?"

oc auth can-i create services -n "${WANAKU_NAMESPACE}" | grep -q "yes"
echo "can-create-services=$?"

oc auth can-i create persistentvolumeclaims -n "${WANAKU_NAMESPACE}" | grep -q "yes"
echo "can-create-pvcs=$?"

oc auth can-i create routes -n "${WANAKU_NAMESPACE}" | grep -q "yes"
echo "can-create-routes=$?"

# Expected: all values should be 0
```
