#!/bin/bash
set -e

WANAKU_NAMESPACE="${WANAKU_NAMESPACE:-wanaku-test}"

echo "=== Wanaku Test Service Account Teardown ==="
echo "Namespace: ${WANAKU_NAMESPACE}"

oc delete clusterrolebinding wanaku-test-runner --ignore-not-found=true
echo "PASS: clusterrolebinding deleted"

oc delete clusterrole wanaku-test-runner --ignore-not-found=true
echo "PASS: clusterrole deleted"

oc delete serviceaccount wanaku-test-runner -n "${WANAKU_NAMESPACE}" --ignore-not-found=true
echo "PASS: serviceaccount deleted"

echo ""
echo "Done. The namespace ${WANAKU_NAMESPACE} was not deleted (it may contain other resources)."
