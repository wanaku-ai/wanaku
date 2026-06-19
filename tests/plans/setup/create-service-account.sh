#!/bin/bash
set -e

WANAKU_NAMESPACE="${WANAKU_NAMESPACE:-wanaku-test}"
TOKEN_DURATION="${TOKEN_DURATION:-8760h}"

echo "=== Wanaku Test Service Account Setup ==="
echo "Namespace: ${WANAKU_NAMESPACE}"

# 1. Create namespace if it doesn't exist
if oc get namespace "${WANAKU_NAMESPACE}" > /dev/null 2>&1; then
  echo "PASS: namespace ${WANAKU_NAMESPACE} already exists"
else
  oc new-project "${WANAKU_NAMESPACE}" --display-name="Wanaku Operator Test" 2>/dev/null \
    || oc create namespace "${WANAKU_NAMESPACE}"
  echo "PASS: namespace ${WANAKU_NAMESPACE} created"
fi

# 2. Create the ServiceAccount
if oc get serviceaccount wanaku-test-runner -n "${WANAKU_NAMESPACE}" > /dev/null 2>&1; then
  echo "PASS: serviceaccount wanaku-test-runner already exists"
else
  oc create serviceaccount wanaku-test-runner -n "${WANAKU_NAMESPACE}"
  echo "PASS: serviceaccount wanaku-test-runner created"
fi

# 3. Create the ClusterRole with minimum required permissions
cat <<EOF | oc apply -f -
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: wanaku-test-runner
  labels:
    app.kubernetes.io/part-of: wanaku-test
rules:
  # Namespace/project management
  - apiGroups: ["", "project.openshift.io"]
    resources: ["namespaces", "projects", "projectrequests"]
    verbs: ["get", "list", "watch", "create", "delete"]

  # CRDs (Helm install/uninstall)
  - apiGroups: ["apiextensions.k8s.io"]
    resources: ["customresourcedefinitions"]
    verbs: ["get", "list", "watch", "create", "update", "patch", "delete"]

  # RBAC (Helm creates ClusterRoles/Bindings for the operator)
  - apiGroups: ["rbac.authorization.k8s.io"]
    resources: ["clusterroles", "clusterrolebindings", "roles", "rolebindings"]
    verbs: ["get", "list", "watch", "create", "update", "patch", "delete"]

  # Core resources
  - apiGroups: [""]
    resources: ["pods", "pods/log", "pods/exec", "services", "persistentvolumeclaims",
                 "configmaps", "secrets", "serviceaccounts", "events"]
    verbs: ["get", "list", "watch", "create", "update", "patch", "delete"]

  # Deployments and ReplicaSets
  - apiGroups: ["apps"]
    resources: ["deployments", "replicasets"]
    verbs: ["get", "list", "watch", "create", "update", "patch", "delete"]

  # OpenShift Routes
  - apiGroups: ["route.openshift.io"]
    resources: ["routes"]
    verbs: ["get", "list", "watch", "create", "update", "patch", "delete"]

  # Ingresses
  - apiGroups: ["networking.k8s.io"]
    resources: ["ingresses"]
    verbs: ["get", "list", "watch", "create", "update", "patch", "delete"]

  # Wanaku custom resources
  - apiGroups: ["wanaku.ai"]
    resources: ["wanakurouters", "wanakurouters/status", "wanakurouters/finalizers",
                 "wanakucapabilities", "wanakucapabilities/status", "wanakucapabilities/finalizers",
                 "wanakuservicecatalogs", "wanakuservicecatalogs/status", "wanakuservicecatalogs/finalizers"]
    verbs: ["get", "list", "watch", "create", "update", "patch", "delete"]

  # Self subject access review (oc auth can-i)
  - apiGroups: ["authorization.k8s.io"]
    resources: ["selfsubjectaccessreviews"]
    verbs: ["create"]
EOF
echo "PASS: clusterrole wanaku-test-runner applied"

# 4. Bind the ClusterRole to the ServiceAccount
if oc get clusterrolebinding wanaku-test-runner > /dev/null 2>&1; then
  echo "PASS: clusterrolebinding wanaku-test-runner already exists"
else
  oc create clusterrolebinding wanaku-test-runner \
    --clusterrole=wanaku-test-runner \
    --serviceaccount="${WANAKU_NAMESPACE}:wanaku-test-runner"
  echo "PASS: clusterrolebinding wanaku-test-runner created"
fi

# 5. Generate a token
echo ""
echo "=== Service Account Token ==="
TOKEN=$(oc create token wanaku-test-runner -n "${WANAKU_NAMESPACE}" --duration="${TOKEN_DURATION}")
echo "${TOKEN}"
echo ""
echo "To log in as this service account:"
echo "  oc login <cluster-api-url> --token=<token-above>"
echo ""
echo "To verify:"
echo "  oc whoami"
echo "  # Expected: system:serviceaccount:${WANAKU_NAMESPACE}:wanaku-test-runner"
