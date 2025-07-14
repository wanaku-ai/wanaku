# Wanaku Integration Tests

This project contains integration tests for the Wanaku platform. These tests are designed to verify the end-to-end functionality of the Wanaku ecosystem, ensuring that all components work together as expected.

## Prerequisites

Before running the integration tests, you need to have the following installed:

*   Java 17+
*   Maven 3.6+
*   Docker

## Running the Tests

To run the integration tests, you first need to build the project and create the necessary Docker images. This can be done by running the following command from the root of the `wanaku` project:

```bash
mvn -B clean package -Dquarkus.container-image.build=true
```

Once the build is complete, you can run the integration tests from this directory using the following command:

```bash
mvn clean verify
```

## Architecture

The integration tests use [Testcontainers](https://www.testcontainers.org/) to manage the lifecycle of the Docker containers required for the tests. The following containers are started before the tests are executed:

*   `wanaku-router`: The central component that routes requests to the appropriate services.
*   `wanaku-tool-service-tavily`: A service that provides access to the Tavily search API.
*   `wanaku-tool-service-yaml-route`: A service that allows defining routes using YAML.
*   `wanaku-tool-service-kafka`: A service for interacting with Kafka topics.
*   `wanaku-tool-service-http`: A service for making HTTP requests.
*   `wanaku-provider-s3`: A resource provider for S3.
*   `wanaku-provider-ftp`: A resource provider for FTP.
*   `wanaku-provider-file`: A resource provider for the local filesystem.

The `WanakuIntegrationBase` class is the base class for all integration tests. It is responsible for starting and stopping the Docker containers.

### Test Resources

For testing purposes, the `src/test/resources` directory of this project is mapped as a read-only volume to the `/app/resources` directory inside the running service containers. This allows tests to access and interact with files (e.g., for file-based resource providers) in a consistent manner.

