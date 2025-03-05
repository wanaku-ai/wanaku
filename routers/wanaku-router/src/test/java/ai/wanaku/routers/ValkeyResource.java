package ai.wanaku.routers;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Map;
import org.testcontainers.containers.GenericContainer;

public class ValkeyResource implements QuarkusTestResourceLifecycleManager {
    private GenericContainer container;

    @Override
    public Map<String, String> start() {
        container = new GenericContainer("valkey/valkey")
                .withExposedPorts(6379);

        container.start();


        return Map.of(
                "valkey.host", container.getHost(),
                "valkey.port", container.getFirstMappedPort().toString()
        );
    }

    @Override
    public void stop() {
        container.stop();
    }
}
