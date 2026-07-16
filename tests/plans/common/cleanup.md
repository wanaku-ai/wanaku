# Common: Cleanup and Teardown

Reusable steps for cleaning up all Wanaku test resources from OpenShift.

## Prerequisites

- `WANAKU_NAMESPACE` environment variable set to the namespace created for this test run

## Steps

### 1. Delete WanakuServiceCatalog resources

```bash
oc delete wanakuservicecatalog --all -n "${WANAKU_NAMESPACE}" --ignore-not-found=true
echo "service-catalogs-deleted=$?"
# Expected: service-catalogs-deleted=0
```

### 2. Delete WanakuCapability resources

```bash
oc delete wanakucapability --all -n "${WANAKU_NAMESPACE}" --ignore-not-found=true
echo "capabilities-deleted=$?"
# Expected: capabilities-deleted=0
```

### 3. Delete WanakuRouter resources

```bash
oc delete wanakurouter --all -n "${WANAKU_NAMESPACE}" --ignore-not-found=true
echo "routers-deleted=$?"
# Expected: routers-deleted=0
```

### 4. Wait for operator-managed resources to be cleaned up

```bash
# Wait for operator-managed deployments to be garbage-collected (excludes the operator itself and keycloak)
oc wait --for=delete deployment -l component=wanaku-router-backend -n "${WANAKU_NAMESPACE}" --timeout=60s 2>/dev/null || true
oc wait --for=delete deployment -l component=wanaku-capability -n "${WANAKU_NAMESPACE}" --timeout=60s 2>/dev/null || true

# Verify no operator-managed deployments remain (except the operator itself and keycloak)
REMAINING=$(oc get deployments -n "${WANAKU_NAMESPACE}" \
  -o jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}' \
  | grep -v -E "^(wanaku-operator|keycloak)$" | wc -l | tr -d ' ')

if [ "${REMAINING}" != "0" ]; then
  echo "WARN: ${REMAINING} non-operator deployments still present"
  oc get deployments -n "${WANAKU_NAMESPACE}" -o name
else
  echo "PASS: operator-managed deployments cleaned up"
fi
```

### 5. Delete test ConfigMaps

```bash
oc delete configmap -l wanaku-test=true -n "${WANAKU_NAMESPACE}" --ignore-not-found=true 2>/dev/null || true
echo "PASS: test ConfigMaps cleaned up"
```

### 6. Delete Keycloak

```bash
oc delete deployment keycloak -n "${WANAKU_NAMESPACE}" --ignore-not-found=true
oc delete service keycloak -n "${WANAKU_NAMESPACE}" --ignore-not-found=true
oc delete route keycloak -n "${WANAKU_NAMESPACE}" --ignore-not-found=true
oc delete pvc keycloak-data-pvc -n "${WANAKU_NAMESPACE}" --ignore-not-found=true
echo "PASS: Keycloak resources deleted"
```

### 7. Uninstall the operator Helm release

```bash
helm uninstall wanaku-operator --namespace "${WANAKU_NAMESPACE}" 2>/dev/null || true
echo "PASS: Helm release uninstalled"
```

### 8. Delete CRDs (optional, only if full cleanup is desired)

```bash
# WARNING: This removes CRDs cluster-wide. Only do this if no other namespace uses them.
# Uncomment the following lines for a full cleanup:
#
# oc delete crd wanakurouters.wanaku.ai --ignore-not-found=true
# oc delete crd wanakucapabilities.wanaku.ai --ignore-not-found=true
# oc delete crd wanakuservicecatalogs.wanaku.ai --ignore-not-found=true
# echo "PASS: CRDs deleted"

echo "SKIP: CRD deletion (uncomment to enable)"
```

### 9. Delete the namespace (optional)

The test service account lives in `wanaku-test-infra`, not in `${WANAKU_NAMESPACE}`, so deleting the test namespace does not affect the shared test infrastructure. Only delete the namespace when it was created for this specific run.

```bash
# WARNING: This deletes everything in the namespace.
# Uncomment the following line for a full cleanup:
#
# oc delete project "${WANAKU_NAMESPACE}" --ignore-not-found=true
# echo "PASS: namespace ${WANAKU_NAMESPACE} deleted"

echo "SKIP: namespace deletion (uncomment to enable)"
```

### 10. Verify cleanup

```bash
echo "--- Remaining resources in ${WANAKU_NAMESPACE} ---"
oc get all -n "${WANAKU_NAMESPACE}" 2>/dev/null || echo "(namespace may have been deleted)"
echo "--- Wanaku CRDs ---"
oc get crd | grep wanaku || echo "(no Wanaku CRDs)"
echo "--- Helm releases ---"
helm list --namespace "${WANAKU_NAMESPACE}" 2>/dev/null || echo "(namespace may have been deleted)"
```
