java -jar camel-integration-capability-main-*-jar-with-dependencies.jar \
  --registration-url <registration-url> \
  --registration-announce-address localhost \
  --grpc-port <grpc-port> \
  --name {{systemName}} \
  --service-catalog {{catalogName}} \
  --service-catalog-system {{systemName}} \
  --client-id wanaku-service \
  --fail-fast