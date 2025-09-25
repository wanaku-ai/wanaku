wanaku_service_client_id=wanaku-service
wanaku_service_client_uuid=102929ec-264a-4acb-8111-970d62a112fb

WANAKU_KEYCLOAK_HOST=$(oc get routes keycloak -o json  | jq -r .spec.host)

# Get the admin token
TOKEN=$(curl -s -d 'client_id=admin-cli' -d 'username=admin' -d "password=$WANAKU_KEYCLOAK_PASS" -d 'grant_type=password' "http://${WANAKU_KEYCLOAK_HOST}/realms/master/protocol/openid-connect/token" | jq -r '.access_token')

echo "Creating the realm"
curl -X POST "http://${WANAKU_KEYCLOAK_HOST}/admin/realms" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d @wanaku-config.json

echo "Regenerating secret"
NEW_SECRET=$(curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" "http://${WANAKU_KEYCLOAK_HOST}/admin/realms/wanaku/clients/${wanaku_service_client_uuid}/client-secret")

echo $NEW_SECRET | jq -r .value