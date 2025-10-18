wanaku_service_client_id=wanaku-service
wanaku_service_client_uuid=102929ec-264a-4acb-8111-970d62a112fb

if [ -z "${WANAKU_KEYCLOAK_HOST}" ]; then
  export WANAKU_KEYCLOAK_HOST=$(oc get routes keycloak -o json | jq -r .spec.host)
fi

# Get the admin token
TOKEN=$(curl -s -d 'client_id=admin-cli' -d 'username=admin' -d "password=$WANAKU_KEYCLOAK_PASS" -d 'grant_type=password' "http://${WANAKU_KEYCLOAK_HOST}/realms/master/protocol/openid-connect/token" | jq -r '.access_token')

echo "Creating the realm (may present a warning if it already exists - safe to ignore)"
curl -X POST "http://${WANAKU_KEYCLOAK_HOST}/admin/realms" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d @wanaku-config.json
echo ""

echo "Regenerating secret"
NEW_SECRET_REPLY=$(curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" "http://${WANAKU_KEYCLOAK_HOST}/admin/realms/wanaku/clients/${wanaku_service_client_uuid}/client-secret")
if [ -z "${NEW_SECRET_REPLY}" ] ; then
  echo "No secret was received"
  exit 1
fi

NEW_SECRET=$(echo $NEW_SECRET_REPLY | jq -r .value)
echo "New client secret for capabilities: ${NEW_SECRET}"