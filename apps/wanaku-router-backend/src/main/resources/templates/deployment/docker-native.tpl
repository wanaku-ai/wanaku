docker run -d \
  -e AUTH_SERVER=<auth-server> \
  -e WANAKU_SERVICE_REGISTRATION_URI=<registration-url> \
  -e QUARKUS_OIDC_CLIENT_CREDENTIALS_SECRET=<client-secret> \
  -p 9000:9000 \
  quay.io/wanaku/wanaku-tool-service-{{systemName}}:latest