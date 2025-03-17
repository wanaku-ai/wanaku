package ai.wanaku.core.persistence.mongodb;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;

import java.util.Map;

public class MongoDBResource implements QuarkusTestResourceLifecycleManager {
    private GenericContainer container;

    @Override
    public Map<String, String> start() {
        container = new MongoDBContainer("mongo:8.0.5")
                .withExposedPorts(27017);

        container.start();

        return Map.of(
                "wanaku.persistence.mongodb", "true",
                "quarkus.mongodb.connection-string", String.format("mongodb://%s:%s", container.getHost(),
                        container.getMappedPort(27017).toString()),
                "quarkus.mongodb.database", "wanaku-test"
        );
    }

    @Override
    public void stop() {
        container.stop();
    }
}
