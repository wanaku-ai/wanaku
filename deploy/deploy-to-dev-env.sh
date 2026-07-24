#!/bin/bash
set -euo pipefail

# Deploy Wanaku to a development Kubernetes environment.
# Usage: WANAKU_ADMIN_USERNAME=admin WANAKU_ADMIN_PASSWORD="the-password" ./deploy-to-dev-env.sh [namespace]
#
# Prerequisites:
# - tools: kubectl, helm, wanaku CLI
# - Keycloak installed and configured in the cluster
# - Active cluster login.
# Cluster access is restricted to Wanaku Core Committers.

# if you are developing wanaku and don't have/want the wanaku binary cli in the bin/ directory
# then you may alias wanaku='java -jar apps/wanaku-cli/target/quarkus-app/quarkus-run.jar'

# debug the script
# set -x

NAMESPACE="${1:-wanaku}"
WANAKU_ADMIN_USERNAME="${WANAKU_ADMIN_USERNAME:-admin}"
WANAKU_ADMIN_PASSWORD="${WANAKU_ADMIN_PASSWORD:-admin}"
WANAKU_CLI=wanaku
WANAKU_INGRESS_HOST="${WANAKU_INGRESS_HOST:-}"

# kubernetes cluster detection to minikube or openshift
IS_OPENSHIFT=$([ "$(kubectl api-resources 2>/dev/null | grep -c openshift)" -gt 0 ] && echo "true" || echo "")
IS_MINIKUBE=$([ "$(minikube status 2>/dev/null | grep -i -c running)" -eq 3 ] && echo "true" || echo "")

# if deploying to openshift, then ingress is not required, as the endpoint is exposed as an openshift route CR
#   and the host is automatically set by the openshift router controller.
# if deploying to minikube, then ingress is required, as the endpoint is exposed as an ingress CR which requires a host.
if [[ -z "${WANAKU_INGRESS_HOST}" && -n "${IS_MINIKUBE}" ]]; then
    WANAKU_INGRESS_HOST="wanaku.$(minikube ip).nip.io"
fi

if ! command -v "wanaku" &> /dev/null; then
    echo "Aliasing the wanaku cli to apps/wanaku-cli/target/quarkus-app/quarkus-run.jar"
    WANAKU_CLI='java -jar apps/wanaku-cli/target/quarkus-app/quarkus-run.jar'
fi

image=$(grep image: apps/wanaku-operator/deploy/helm/wanaku-operator/values.yaml |awk '{print $2}')
WANAKU_OPERATOR_IMAGE="${WANAKU_OPERATOR_IMAGE:-${image}}"
WANAKU_ROUTER_IMAGE="${WANAKU_ROUTER_IMAGE:-quay.io/wanaku/wanaku-router-backend:latest}"

log_info()  { echo "[INFO]  $*"; }
log_error() { echo "[ERROR] $*" >&2; }
log_step()  { echo ""; echo "==> $*"; }

# --- Check prerequisites ---
for cmd in kubectl helm ; do
    if ! command -v "${cmd}" &> /dev/null; then
        log_error "Required command '${cmd}' not found in PATH"
        exit 1
    fi
done

# --- Resolve OIDC configuration ---
log_step "Resolving OIDC configuration"

EXTERNAL_KEYCLOAK_HOST=""
# the reachable host internal to kubernetes as set in svc/keycloak
INTERNAL_KEYCLOAK_HOST="http://keycloak:8080"

if [[ -n "${IS_OPENSHIFT}" ]]; then
    EXTERNAL_KEYCLOAK_HOST=$(kubectl get route keycloak -o jsonpath='{.spec.host}') || {
        log_error "Failed to get Keycloak route. Is Keycloak deployed?"
        exit 1
    }
else
    EXTERNAL_KEYCLOAK_HOST=$(kubectl get ingress keycloak -o jsonpath='{.spec.rules[0].host}') || {
        log_error "Failed to get Keycloak route. Is Keycloak deployed?"
        exit 1
    }
fi

# keycloak address visible only in the cluster
QUARKUS_OIDC_CLIENT_AUTH_SERVER="${INTERNAL_KEYCLOAK_HOST}"

# detect if using https on external keycloak server
if curl -k -s -f -o /dev/null "https://${EXTERNAL_KEYCLOAK_HOST}"; then
    EXTERNAL_KEYCLOAK_HOST="https://${EXTERNAL_KEYCLOAK_HOST}"
else
    EXTERNAL_KEYCLOAK_HOST="http://${EXTERNAL_KEYCLOAK_HOST}"
fi
log_info "Keycloak public URL: ${EXTERNAL_KEYCLOAK_HOST}"

out=$($WANAKU_CLI admin credentials show --verbose --insecure \
    --keycloak-url "${EXTERNAL_KEYCLOAK_HOST}" \
    --admin-username "${WANAKU_ADMIN_USERNAME}" \
    --admin-password "${WANAKU_ADMIN_PASSWORD}" \
    --client-id wanaku-service --show-secret --plain) || true

if [[ $out == *"Exception"* ]]; then
    log_error "Failed to retrieve OIDC client credentials secret: $out"
    exit 1
fi

QUARKUS_OIDC_CLIENT_CREDENTIALS_SECRET=$(echo $out | sed 's/.*Secret:\ //g')

if [[ -z "${QUARKUS_OIDC_CLIENT_CREDENTIALS_SECRET}" ]]; then
    log_error "OIDC client credentials secret is empty"
    exit 1
fi
log_info "OIDC client secret retrieved successfully"

kubectl create secret generic wanaku-oidc --from-literal=client-secret="${QUARKUS_OIDC_CLIENT_CREDENTIALS_SECRET}" 2>/dev/null \
  || kubectl get secret wanaku-oidc > /dev/null 2>&1

if ! kubectl get secret wanaku-oidc > /dev/null 2>&1; then
  log_error "FAIL: could not create wanaku-oidc secret"
  exit 1
fi
log_info "wanaku-oidc Kubernetes Secret created"


# --- Switch to target namespace ---
log_step "Switching to namespace '${NAMESPACE}'"
kubectl config set-context --current --namespace="${NAMESPACE}" || {
    log_error "Failed to switch to namespace '${NAMESPACE}'"
    exit 1
}

# --- Determine script directory for relative paths ---
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# --- Deploy operator ---
log_step "Uninstalling old operator (if present)"
helm uninstall wanaku-operator --namespace "${NAMESPACE}" --ignore-not-found
log_info "Old operator uninstalled"

log_step "Installing new operator"
helm install wanaku-operator \
    "${REPO_ROOT}/apps/wanaku-operator/deploy/helm/wanaku-operator" \
    --namespace "${NAMESPACE}" \
    --set app.envs.AUTH_SERVER="${QUARKUS_OIDC_CLIENT_AUTH_SERVER}" \
    --set app.image="${WANAKU_OPERATOR_IMAGE}" || {
    log_error "Helm install of wanaku-operator failed"
    exit 1
}
log_info "Operator installed successfully"

# --- Undeploy existing HTTP capability (before router) ---
log_step "Undeploying existing HTTP capability (if present)"
kubectl delete wanakucapabilities/wanaku-dev-capabilities --ignore-not-found --timeout=60s || {
    log_error "Failed to delete existing HTTP capability"
    exit 1
}
log_info "Existing HTTP capability removed"

# --- Undeploy existing router ---
log_step "Undeploying existing router (if present)"
kubectl delete wanakurouter/wanaku-ci-dev --ignore-not-found --timeout=60s || {
    log_error "Failed to delete existing router"
    exit 1
}
log_info "Existing router removed"

# --- Deploy router ---
log_step "Deploying the router"
if [[ -n "${IS_OPENSHIFT}" ]]; then
    sed -e "s|oidc-url-replace|${QUARKUS_OIDC_CLIENT_AUTH_SERVER}|g" \
        -e "s|wanaku-image-replace|${WANAKU_ROUTER_IMAGE}|g" \
        "${REPO_ROOT}/deploy/kubernetes/wanaku-router.yaml" | kubectl apply -f - || {
        log_error "Failed to apply wanaku-router.yaml"
        exit 1
    }
else
    sed -e "s|oidc-url-replace|${QUARKUS_OIDC_CLIENT_AUTH_SERVER}|g" \
        -e "s|replace-wanaku-ingress-host|${WANAKU_INGRESS_HOST}|g" \
        -e "s|wanaku-image-replace|${WANAKU_ROUTER_IMAGE}|g" \
        "${REPO_ROOT}/deploy/kubernetes/wanaku-router.yaml" | kubectl apply -f - || {
        log_error "Failed to apply wanaku-router.yaml"
        exit 1
    }
fi

log_info "Waiting for router to become ready..."
kubectl wait wanakurouter/wanaku-ci-dev --for=condition=Ready --timeout=120s || {
    log_error "Router did not become ready within 120s"
    exit 1
}
log_info "Router is ready"

# --- Deploy HTTP capability ---
log_step "Deploying the HTTP capability"
sed -e "s|oidc-url-replace|${QUARKUS_OIDC_CLIENT_AUTH_SERVER}|g" \
    -e "s|replace-me-with-the-client-credentials-secret|${QUARKUS_OIDC_CLIENT_CREDENTIALS_SECRET}|g" \
    "${REPO_ROOT}/deploy/kubernetes/wanaku-capabilities.yaml" | kubectl apply -f - || {
    log_error "Failed to apply wanaku-capabilities.yaml"
    exit 1
}

log_info "Waiting for capabilities to become ready..."
kubectl wait wanakucapabilities/wanaku-dev-capabilities --for=condition=Ready --timeout=120s || {
    log_error "Capabilities did not become ready within 120s"
    exit 1
}
log_info "Capabilities are ready"

log_step "Deployment completed successfully"

ROUTER_HOST=$(kubectl get wanakurouter.wanaku.ai/wanaku-ci-dev -ojsonpath='{.status.host}' 2>/dev/null) || true

log_info "Keycloak URL:  ${EXTERNAL_KEYCLOAK_HOST}"
log_info "Wanaku URL  :  ${ROUTER_HOST}"
