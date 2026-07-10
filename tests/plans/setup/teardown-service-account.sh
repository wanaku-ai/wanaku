#!/bin/bash
set -e

WANAKU_SA_NAMESPACE="${WANAKU_SA_NAMESPACE:-wanaku-test-infra}"

echo "=== Wanaku Test Service Account Teardown ==="
echo "Service account namespace: ${WANAKU_SA_NAMESPACE}"

oc delete clusterrolebinding wanaku-test-runner --ignore-not-found=true
echo "PASS: clusterrolebinding deleted"

oc delete clusterrole wanaku-test-runner --ignore-not-found=true
echo "PASS: clusterrole deleted"

oc delete serviceaccount wanaku-test-runner -n "${WANAKU_SA_NAMESPACE}" --ignore-not-found=true
echo "PASS: serviceaccount deleted"

echo ""
echo "Done. The namespace ${WANAKU_SA_NAMESPACE} was not deleted (it only contains test infrastructure)."
