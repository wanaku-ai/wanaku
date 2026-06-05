#!/bin/bash
set -euo pipefail

# enable to debug the script
# set -x

wanaku_service_client_id=wanaku-service
wanaku_service_client_uuid=102929ec-264a-4acb-8111-970d62a112fb

WANAKU_KEYCLOAK_HOST=${WANAKU_KEYCLOAK_HOST:-}
WANAKU_KEYCLOAK_PASS=${WANAKU_KEYCLOAK_PASS:-admin}

pushd deploy/auth > /dev/null

if [ -z "${WANAKU_KEYCLOAK_HOST}" ]; then
  # detect if connected to openshift
  # if grep returns 0, grep command returns 1 (fails), toa void the script failing, set the the ||true
  nr_openshift="$(kubectl api-resources|grep -c openshift || true)"
  if [[ "${nr_openshift}" -eq 0 ]]; then
      # not connected to openshift
      WANAKU_KEYCLOAK_HOST=$(kubectl get ingress keycloak -o jsonpath='{.spec.rules[0].host}')
  else
      WANAKU_KEYCLOAK_HOST=$(kubectl get route keycloak -o jsonpath='{.spec.host}')
  fi

  # detect if using https
  if curl -k -s -f -o /dev/null "https://${WANAKU_KEYCLOAK_HOST}"; then
      WANAKU_KEYCLOAK_HOST=https://${WANAKU_KEYCLOAK_HOST}
  else
      WANAKU_KEYCLOAK_HOST=http://${WANAKU_KEYCLOAK_HOST}
  fi
fi

# Configurable realm name (defaults to wanaku)
WANAKU_KEYCLOAK_REALM=${WANAKU_KEYCLOAK_REALM:-wanaku}

# Validate that the imported realm JSON matches the configured realm name
JSON_REALM=$(jq -r '.realm' wanaku-config.json)
if [ "${JSON_REALM}" != "${WANAKU_KEYCLOAK_REALM}" ]; then
  echo "WARNING: wanaku-config.json defines realm '${JSON_REALM}' but WANAKU_KEYCLOAK_REALM is '${WANAKU_KEYCLOAK_REALM}'."
  echo "Update the 'realm' field in wanaku-config.json (and all internal references) to match WANAKU_KEYCLOAK_REALM."
  exit 1
fi

# Get the admin token
# -L follow redirects
# -s silent
# -k skip SSL verification
TOKEN=$(curl -s -L -k -d 'client_id=admin-cli' -d 'username=admin' -d "password=$WANAKU_KEYCLOAK_PASS" -d 'grant_type=password' \
  "${WANAKU_KEYCLOAK_HOST}/realms/master/protocol/openid-connect/token" | jq -r '.access_token')

echo "Creating the realm (may present a warning if it already exists - safe to ignore)"
curl -k -L -X POST "${WANAKU_KEYCLOAK_HOST}/admin/realms" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d @wanaku-config.json
echo ""

echo "Regenerating secret"
NEW_SECRET_REPLY=$(curl -k -L -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" "${WANAKU_KEYCLOAK_HOST}/admin/realms/${WANAKU_KEYCLOAK_REALM}/clients/${wanaku_service_client_uuid}/client-secret")
if [ -z "${NEW_SECRET_REPLY}" ] ; then
  echo "No secret was received"
  exit 1
fi

popd > /dev/null

NEW_SECRET=$(echo $NEW_SECRET_REPLY | jq -r .value)
echo "New client secret for capabilities: ${NEW_SECRET}"