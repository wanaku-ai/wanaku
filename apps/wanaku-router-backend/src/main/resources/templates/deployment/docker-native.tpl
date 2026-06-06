docker run -d \
  {{authOptions}}-e WANAKU_SERVICE_REGISTRATION_URI=<registration-url> \
  -p 9000:9000 \
  quay.io/wanaku/wanaku-tool-service-{{systemName}}:latest
