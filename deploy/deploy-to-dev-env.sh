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

if ! command -v "wanaku" &> /dev/null; then
    echo "Aliasing the wanaku cli to apps/wanaku-cli/target/quarkus-app/quarkus-run.jar"
    WANAKU_CLI='java -jar apps/wanaku-cli/target/quarkus-app/quarkus-run.jar'
fi

image=$(grep image: apps/wanaku-operator/deploy/helm/wanaku-operator/values.yaml |awk '{print $2}')
WANAKU_IMAGE="${WANAKU_IMAGE:-${image}}"

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

# detect if connected to openshift
# if grep returns 0, grep command returns 1 (fails), to avoid the script failing, set the the ||true
nr_openshift="$(kubectl api-resources|grep -c openshift || true)"
KEYCLOAK_HOST=""
if [[ "${nr_openshift}" -eq 0 ]]; then
    KEYCLOAK_HOST=$(kubectl get ingress keycloak -o jsonpath='{.spec.rules[0].host}') || {
        log_error "Failed to get Keycloak route. Is Keycloak deployed?"
        exit 1
    }
else
    KEYCLOAK_HOST=$(kubectl get route keycloak -o jsonpath='{.spec.host}') || {
        log_error "Failed to get Keycloak route. Is Keycloak deployed?"
        exit 1
    }
fi

QUARKUS_OIDC_CLIENT_AUTH_SERVER="http://${KEYCLOAK_HOST}"
# detect if using https
if curl -k -s -f -o /dev/null "https://${KEYCLOAK_HOST}"; then
    QUARKUS_OIDC_CLIENT_AUTH_SERVER="https://${KEYCLOAK_HOST}"
fi
log_info "Keycloak URL: ${QUARKUS_OIDC_CLIENT_AUTH_SERVER}"

QUARKUS_OIDC_CLIENT_CREDENTIALS_SECRET=$($WANAKU_CLI admin credentials show --verbose \
    --keycloak-url "${QUARKUS_OIDC_CLIENT_AUTH_SERVER}" \
    --admin-username "${WANAKU_ADMIN_USERNAME}" \
    --admin-password "${WANAKU_ADMIN_PASSWORD}" \
    --client-id wanaku-service --show-secret --plain | cut -d ' ' -f 3) || {
    log_error "Failed to retrieve OIDC client credentials secret"
    exit 1
}

if [[ -z "${QUARKUS_OIDC_CLIENT_CREDENTIALS_SECRET}" ]]; then
    log_error "OIDC client credentials secret is empty"
    exit 1
fi
log_info "OIDC client secret retrieved successfully"

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
    --set app.image="${WANAKU_IMAGE}" || {
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
sed -e "s|oidc-url-replace|${QUARKUS_OIDC_CLIENT_AUTH_SERVER}|g" \
    -e "s|wanaku-image-replace|${WANAKU_IMAGE}|g" \
    "${REPO_ROOT}/deploy/openshift/wanaku-router.yaml" | kubectl apply -f - || {
    log_error "Failed to apply wanaku-router.yaml"
    exit 1
}

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
    "${REPO_ROOT}/deploy/openshift/wanaku-capabilities.yaml" | kubectl apply -f - || {
    log_error "Failed to apply wanaku-capabilities.yaml"
    exit 1
}

log_info "Waiting for capabilities to become ready..."
kubectl wait wanakucapabilities/wanaku-dev-capabilities --for=condition=Ready --timeout=120s || {
    log_error "Capabilities did not become ready within 120s"
    exit 1
}
log_info "Capabilities are ready"

# --- Deploy Camel Code Execution Engine ---
log_step "Deploying the Camel Code Execution Engine"
oc apply -f "${REPO_ROOT}/apps/wanaku-operator/samples/camel-code-execution-engine.yaml" || {
    log_error "Failed to apply camel-code-execution-engine.yaml"
    exit 1
}

log_info "Waiting for Camel Code Execution Engine to become ready..."
oc wait camelcodeexecutionengines/camel-code-execution-engine --for=condition=Ready --timeout=120s || {
    log_error "Camel Code Execution Engine did not become ready within 120s"
    exit 1
}
log_info "Camel Code Execution Engine is ready"

log_step "Deployment completed successfully"

ROUTER_HOST=$(kubectl get route wanaku-ci-dev -o jsonpath='{.spec.host}' 2>/dev/null) || true
log_info "Keycloak URL:  ${QUARKUS_OIDC_CLIENT_AUTH_SERVER}"
if [[ -n "${ROUTER_HOST:-}" ]]; then
    log_info "Router URL:    http://${ROUTER_HOST}"
else
    log_info "Router URL:    (no route found)"
fi
