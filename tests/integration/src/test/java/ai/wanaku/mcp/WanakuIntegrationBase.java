package ai.wanaku.mcp;

import ai.wanaku.cli.main.CliMain;
import io.quarkiverse.mcp.server.test.McpAssured;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    /**
     * The main Wanaku router container.
     */
    protected static final GenericContainer<?> router = new GenericContainer<>("quay.io/wanaku/wanaku-router")
            .withExposedPorts(8080)
            .withNetwork(network)
            .withNetworkAliases("wanaku-router");

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

    /**
     * Starts the main router container before any tests are run.
     */
    @BeforeAll
    static void startContainers() throws URISyntaxException {
        router.start();
        router.followOutput(logConsumer);

        client = McpAssured.newSseClient()
                .setBaseUri(new URI("http://localhost:" + router.getMappedPort(8080) + "/"))
                .setSsePath("mcp/sse")
                .build();

        client.connect();
    }

    /**
     * Starts the specific downstream services required by a test class.
     * It ensures that each service is started only once and connects it to the shared network.
     * Crucially, it maps the local `src/test/resources` directory to {@link #CONTAINER_RESOURCES_FOLDER}
     * inside the container, making test files available.
     */
    @BeforeEach
    public void startServices() {
        activeWanakuDownstreamServices().stream().forEach(service -> {
            services.add(service.getContainer());
            if (!service.getContainer().isRunning()) {
                service.getContainer()
                        .withNetwork(network)
                        .withExposedPorts(8080)
                        .waitingFor(Wait.forLogMessage(".*Using registration service.*", 1))
                        .withFileSystemBind(
                                this.getClass().getClassLoader().getResource("").getPath(),
                                CONTAINER_RESOURCES_FOLDER,
                                BindMode.READ_ONLY)
                        .withEnv("WANAKU_SERVICE_REGISTRATION_URI", "http://wanaku-router:8080")
                        .start();

                service.getContainer().followOutput(logConsumer);
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

    /**
     * Stops all running containers after all tests have completed.
     */
    @AfterAll
    static void stopContainers() {
        router.stop();
        services.stream().forEach(GenericContainer::stop);
        services = new HashSet<>();
    }
}
