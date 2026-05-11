docker run -d \
  -e REGISTRATION_URL=<registration-url> \
  -e REGISTRATION_ANNOUNCE_ADDRESS=auto \
  -e GRPC_PORT=9190 \
  -e SERVICE_NAME={{systemName}} \
  -e SERVICE_CATALOG={{catalogName}} \
  -e SERVICE_CATALOG_SYSTEM={{systemName}} \
  -e TOKEN_ENDPOINT=<token-endpoint> \
  -e CLIENT_ID=wanaku-service \
  -e CLIENT_SECRET=<client-secret> \
  -p 9190:9190 \
  quay.io/wanaku/camel-integration-capability:latest