#!/bin/bash
set -euo pipefail

# Deploy Wanaku to a development OpenShift environment.
# Usage: WANAKU_ADMIN_USERNAME=admin WANAKU_ADMIN_PASSWORD="the-password" ./deploy-to-dev-env.sh <namespace>
#
# Prerequisites: oc, helm, wanaku CLI, and active cluster login.
# Cluster access is restricted to Wanaku Core Committers.

OPENSHIFT_NAMESPACE="${1:-}"

log_info()  { echo "[INFO]  $*"; }
log_error() { echo "[ERROR] $*" >&2; }
log_step()  { echo ""; echo "==> $*"; }

# --- Validate inputs ---
if [[ -z "${OPENSHIFT_NAMESPACE}" ]]; then
    log_error "Usage: $0 <namespace>"
    exit 1
fi

if [[ -z "${WANAKU_ADMIN_USERNAME:-}" ]]; then
    log_error "WANAKU_ADMIN_USERNAME environment variable is not set"
    exit 1
fi

if [[ -z "${WANAKU_ADMIN_PASSWORD:-}" ]]; then
    log_error "WANAKU_ADMIN_PASSWORD environment variable is not set"
    exit 1
fi

# --- Check prerequisites ---
for cmd in oc helm wanaku; do
    if ! command -v "${cmd}" &> /dev/null; then
        log_error "Required command '${cmd}' not found in PATH"
        exit 1
    fi
done

# --- Resolve OIDC configuration ---
log_step "Resolving OIDC configuration"

KEYCLOAK_HOST=$(oc get route keycloak -o jsonpath='{.spec.host}') || {
    log_error "Failed to get Keycloak route. Is Keycloak deployed?"
    exit 1
}
QUARKUS_OIDC_CLIENT_AUTH_SERVER="http://${KEYCLOAK_HOST}"
log_info "Keycloak URL: ${QUARKUS_OIDC_CLIENT_AUTH_SERVER}"

QUARKUS_OIDC_CLIENT_CREDENTIALS_SECRET=$(wanaku admin credentials show \
    --keycloak-url "${QUARKUS_OIDC_CLIENT_AUTH_SERVER}" \
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
log_step "Switching to namespace '${OPENSHIFT_NAMESPACE}'"
oc project "${OPENSHIFT_NAMESPACE}" || {
    log_error "Failed to switch to namespace '${OPENSHIFT_NAMESPACE}'"
    exit 1
}

# --- Determine script directory for relative paths ---
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# --- Deploy operator ---
log_step "Uninstalling old operator (if present)"
helm uninstall wanaku-operator --namespace "${OPENSHIFT_NAMESPACE}" --ignore-not-found
log_info "Old operator uninstalled"

log_step "Installing new operator"
helm install wanaku-operator \
    "${REPO_ROOT}/apps/wanaku-operator/deploy/helm/wanaku-operator" \
    --namespace "${OPENSHIFT_NAMESPACE}" \
    --set operatorNamespace="${OPENSHIFT_NAMESPACE}" || {
    log_error "Helm install of wanaku-operator failed"
    exit 1
}
log_info "Operator installed successfully"

# --- Undeploy existing HTTP capability (before router) ---
log_step "Undeploying existing HTTP capability (if present)"
oc delete wanakucapabilities/wanaku-dev-capabilities --ignore-not-found --timeout=60s || {
    log_error "Failed to delete existing HTTP capability"
    exit 1
}
log_info "Existing HTTP capability removed"

# --- Undeploy existing router ---
log_step "Undeploying existing router (if present)"
oc delete wanakurouter/wanaku-ci-dev --ignore-not-found --timeout=60s || {
    log_error "Failed to delete existing router"
    exit 1
}
log_info "Existing router removed"

# --- Deploy router ---
log_step "Deploying the router"
sed -e "s|oidc-url-replace|${QUARKUS_OIDC_CLIENT_AUTH_SERVER}|g" \
    -e "s|replace-me-with-the-client-credentials-secret|${QUARKUS_OIDC_CLIENT_CREDENTIALS_SECRET}|g" \
    "${REPO_ROOT}/deploy/openshift/wanaku-router.yaml" | oc apply -f - || {
    log_error "Failed to apply wanaku-router.yaml"
    exit 1
}

log_info "Waiting for router to become ready..."
oc wait wanakurouter/wanaku-ci-dev --for=condition=Ready --timeout=120s || {
    log_error "Router did not become ready within 120s"
    exit 1
}
log_info "Router is ready"

# --- Deploy HTTP capability ---
log_step "Deploying the HTTP capability"
sed -e "s|oidc-url-replace|${QUARKUS_OIDC_CLIENT_AUTH_SERVER}|g" \
    -e "s|replace-me-with-the-client-credentials-secret|${QUARKUS_OIDC_CLIENT_CREDENTIALS_SECRET}|g" \
    "${REPO_ROOT}/deploy/openshift/wanaku-capabilities.yaml" | oc apply -f - || {
    log_error "Failed to apply wanaku-capabilities.yaml"
    exit 1
}

log_info "Waiting for capabilities to become ready..."
oc wait wanakucapabilities/wanaku-dev-capabilities --for=condition=Ready --timeout=120s || {
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

ROUTER_HOST=$(oc get route wanaku-ci-dev -o jsonpath='{.spec.host}' 2>/dev/null) || true
log_info "Keycloak URL:  ${QUARKUS_OIDC_CLIENT_AUTH_SERVER}"
if [[ -n "${ROUTER_HOST:-}" ]]; then
    log_info "Router URL:    http://${ROUTER_HOST}"
else
    log_info "Router URL:    (no route found)"
fi
