# Testing Wanaku Start Local

Build for distribution:

```shell
mvn -DskipTests -Pdist clean package
```

Run the CLI with local distributions:

```shell
java -jar apps/wanaku-cli/target/quarkus-app/quarkus-run.jar start local --local-dist apps/wanaku-router-backend/target/distributions/wanaku-router-backend-0.1.1-SNAPSHOT.zip --local-dist capabilities/tools/wanaku-tool-service-http/target/distributions/wanaku-tool-service-http-0.1.1-SNAPSHOT.zip
```

Then, access <http://localhost:8080/admin>. Wanaku should be available at that address. No authentication should be required.
