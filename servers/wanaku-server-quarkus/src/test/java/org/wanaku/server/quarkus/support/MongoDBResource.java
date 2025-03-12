package org.wanaku.server.quarkus.support;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.eclipse.microprofile.config.ConfigProvider;
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
                "quarkus.mongodb.connection-string", String.format("mongodb://%s:%s", container.getHost(),
                        container.getMappedPort(27017).toString()),
                "quarkus.mongodb.database", "wanaku-test"
        );
    }

    public static MongoDatabase getDatabase() {
        MongoClient mongoClient = MongoClients.create(
                MongoClientSettings.builder()
                        .applyConnectionString(new ConnectionString(ConfigProvider.getConfig().getValue("quarkus.mongodb.connection-string", String.class)))
                        .build()
        );

        MongoDatabase mongoDatabase = mongoClient.getDatabase(ConfigProvider.getConfig().getValue("quarkus.mongodb.database", String.class));

        return mongoDatabase;
    }

    @Override
    public void stop() {
        container.stop();
    }
}
