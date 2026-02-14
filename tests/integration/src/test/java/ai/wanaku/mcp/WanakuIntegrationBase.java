package ai.wanaku.mcp;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import io.quarkiverse.mcp.server.test.McpAssured;
import ai.wanaku.cli.main.CliMain;
import ai.wanaku.mcp.utils.WanakuKeycloakContainer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

/**
 * Base class for Wanaku integration tests.
 * This class sets up the necessary Testcontainers environment, including a shared network,
 * a central router, and required downstream services. It also provides utility methods
 * for interacting with the system, such as executing CLI commands.
 */
public abstract class WanakuIntegrationBase {
    private static final Logger LOG = LoggerFactory.getLogger(WanakuIntegrationBase.class);

    /**
     * The shared Docker network for all containers.
     */
    private static final Network network = Network.newNetwork();

    protected static final WanakuKeycloakContainer keycloak;

    /**
     * The main Wanaku router container.
     */
    protected static final GenericContainer<?> router;

    /**
     * Extension for interacting with the Model Context Protocol.
     */
    protected static McpAssured.McpSseTestClient client;

    /**
     * The main entry point for the Wanaku CLI.
     */
    protected static CliMain cliMain = new CliMain();

    /**
     * A set of all active downstream service containers.
     */
    protected static Set<GenericContainer<?>> services = new HashSet<>();

    private static final Slf4jLogConsumer logConsumer = new Slf4jLogConsumer(LOG);

    /**
     * Defines the path inside the containers where the local `src/test/resources` folder is mapped.
     * This allows tests to access test files as if they were on the container's filesystem.
     */
    protected static final String CONTAINER_RESOURCES_FOLDER = "/app/resources";

    static {
        keycloak = new WanakuKeycloakContainer();

        keycloak.withNetwork(network).withNetworkAliases("keycloak");

        keycloak.start();
        LOG.info("Creating Wanaku Realm on keycloak");
        keycloak.createRealm();

        router = new GenericContainer<>("quay.io/wanaku/wanaku-router-backend:latest")
                .withExposedPorts(8080)
                .withNetwork(network)
                .withEnv("QUARKUS_OIDC_AUTH_SERVER_URL", "http://keycloak:8080/realms/wanaku")
                .waitingFor(Wait.forLogMessage(".*MCP.*HTTP transport endpoints.*", 1))
                .withNetworkAliases("wanaku-router");
    }

    /**
     * Starts the main router container before any tests are run.
     */
    @BeforeAll
    public static void startContainers() throws URISyntaxException {
        LOG.info("Starting Wanaku Router Container");
        try {
            router.start();
        } catch (Exception e) {
            LOG.error("Unable to initialize the router: {}", e.getMessage(), e);
            throw e;
        }
        LOG.info("Wanaku Router Container started");
        router.followOutput(logConsumer);

        client = McpAssured.newSseClient()
                .setBaseUri(new URI("http://localhost:" + router.getMappedPort(8080) + "/"))
                .setSsePath("mcp/sse")
                .build();

        client.connect();
        LOG.info("Client connected successfully");
    }

    /**
     * Starts the specific downstream services required by a test class.
     * It ensures that each service is started only once and connects it to the shared network.
     * Crucially, it maps the local `src/test/resources` directory to {@link #CONTAINER_RESOURCES_FOLDER}
     * inside the container, making test files available.
     */
    @BeforeEach
    public void startServices() {
        LOG.info("Launching Wanaku services");

        activeWanakuDownstreamServices().forEach(service -> {
            services.add(service.getContainer());
            String accessToken = keycloak.getAccessToken();
            LOG.info("Launching container {}", service.name());
            if (!service.getContainer().isRunning()) {
                GenericContainer<?> container = service.getContainer();
                container
                        .withNetwork(network)
                        .withExposedPorts(8080)
                        .withFileSystemBind(
                                this.getClass().getClassLoader().getResource("").getPath(),
                                CONTAINER_RESOURCES_FOLDER,
                                BindMode.READ_ONLY)
                        .withEnv("WANAKU_SERVICE_REGISTRATION_URI", "http://wanaku-router:8080")
                        .withEnv("QUARKUS_OIDC_CLIENT_AUTH_SERVER_URL", "http://keycloak:8080/realms/wanaku")
                        .withEnv("QUARKUS_OIDC_CLIENT_CREDENTIALS_SECRET", WanakuKeycloakContainer.CLIENT_SECRET)
                        .withEnv("QUARKUS_OIDC_CLIENT_CLIENT_ID", WanakuKeycloakContainer.CLIENT_ID)
                        .waitingFor(Wait.forLogMessage(".*Using announce address auto.*", 1))
                        .start();
                container.followOutput(logConsumer);
                // temporary fix for service registration failures
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    /**
     * Abstract method to be implemented by subclasses to specify which downstream services
     * are required for the tests in that class.
     *
     * @return A list of {@link WanakuContainerDownstreamService}s to be started.
     */
    public abstract List<WanakuContainerDownstreamService> activeWanakuDownstreamServices();

    static void stopContainerSilently(GenericContainer<?> container) {
        try {
            container.stop();
        } catch (Exception e) {
            LOG.error("Unable to stop container {}: {}", container.getContainerName(), e.getMessage(), e);
        }
    }

    /**
     * Stops all running containers after all tests have completed.
     */
    @AfterAll
    static void stopContainers() {
        stopContainerSilently(router);
        services.stream().forEach(WanakuIntegrationBase::stopContainerSilently);
        services.clear();
        stopContainerSilently(keycloak);
    }
}
