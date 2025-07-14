package ai.wanaku.mcp;

import org.testcontainers.containers.GenericContainer;

/**
 * Enum representing the downstream services that can be run as Docker containers for integration tests.
 * Each enum constant holds a {@link GenericContainer} instance configured with the respective service image.
 */
public enum WanakuContainerDownstreamService {

    /**
     * The Tavily tool service.
     */
    TAVILY(new GenericContainer<>("quay.io/wanaku/wanaku-tool-service-tavily")),
    /**
     * The YAML route tool service.
     */
    YAML_ROUTE(new GenericContainer<>("quay.io/wanaku/wanaku-tool-service-yaml-route")),
    /**
     * The Kafka tool service.
     */
    KAFKA(new GenericContainer<>("quay.io/wanaku/wanaku-tool-service-kafka")),
    /**
     * The HTTP tool service.
     */
    HTTP(new GenericContainer<>("quay.io/wanaku/wanaku-tool-service-http")),
    /**
     * The S3 resource provider.
     */
    PROVIDER_S3(new GenericContainer<>("quay.io/wanaku/wanaku-provider-s3")),
    /**
     * The FTP resource provider.
     */
    PROVIDER_FTP(new GenericContainer<>("quay.io/wanaku/wanaku-provider-ftp")),
    /**
     * The File resource provider.
     */
    PROVIDER_FILE(new GenericContainer<>("quay.io/wanaku/wanaku-provider-file"));

    private final GenericContainer value;

    WanakuContainerDownstreamService(GenericContainer value) {
        this.value = value;
    }

    /**
     * @return The {@link GenericContainer} for the service.
     */
    public GenericContainer getContainer() {
        return value;
    }
}
