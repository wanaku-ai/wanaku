package ai.wanaku.mcp;

import io.quarkus.test.junit.QuarkusTest;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static ai.wanaku.mcp.CLIHelper.executeWanakuCliCommand;


@QuarkusTest
@Testcontainers
public class WanakuKafkaToolIT extends WanakuIntegrationBase {

    @Container
    static KafkaContainer kafkaContainer = new KafkaContainer(
            DockerImageName.parse("apache/kafka-native:3.8.0"))
            .withNetwork(router.getNetwork())
            .withListener("kafka:19092")
            .withNetworkAliases("kafka");

    private static String bootStrapHost;

    @TempDir
    static Path tempDir;

    @BeforeAll
    static void startKafka() throws Exception {
        kafkaContainer.start();
        bootStrapHost = kafkaContainer.getBootstrapServers();
        createKafkaTopics();
        createCapabilityFile();
    }

    private static void createCapabilityFile() throws Exception {
        String content = """
                bootstrapHost=kafka:19092
                requestTopic=request-topic
                replyToTopic=response-topic
                """;

        Files.writeString(tempDir.resolve("capabilities.properties"), content);
    }


    private static void createKafkaTopics() throws Exception {
        try (AdminClient admin = AdminClient.create(
                Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootStrapHost))) {
            admin.createTopics(List.of(
                    new NewTopic("request-topic", 1, (short) 1),
                    new NewTopic("response-topic", 1, (short) 1)
            )).all().get(10, TimeUnit.SECONDS);
        }
    }


    @Test
    void kafkaToolAdditionAndVerification() {
        // Check tools list empty
        client.when().toolsList()
                .withAssert(toolsPage -> Assertions.assertThat(toolsPage.tools()).isEmpty())
                .send();

        // Add tool via CLI
        String host = String.format("http://localhost:%d", router.getMappedPort(8080));

        int addToolExit = executeWanakuCliCommand(List.of(
                "wanaku", "tools", "add", "-n", "sushi-request",
                "--description", "Orders the delivery of authentic Japanese sushi",
                "--uri", "",
                "--type", "kafka",
                "--input-schema-type", "string",
                "--property", "body:string,All the items you want in your sushi order",
                "--configuration-from-file=" + tempDir.resolve("capabilities.properties")
        ), host);

        Assertions.assertThat(addToolExit)
                .as("Failed to add Kafka tool via CLI")
                .isEqualTo(0);

        // Verify tool present
        client.when().toolsList()
                .withAssert(toolsPage ->
                        Assertions.assertThat(toolsPage.tools().getFirst().name()).isEqualTo("sushi-request"))
                .send();

        // Call tool
        String orderMessage = "Order 1 sushi roll";
        client.when().toolsCall("sushi-request")
                .withArguments(Map.of("body", orderMessage))
                .withAssert(toolResponse -> {
                    Assertions.assertThat(toolResponse.isError()).isFalse();
                    Assertions.assertThat(toolResponse.content().getFirst()).isNotNull();
                })
                .send();
    }

    @Override
    public List<WanakuContainerDownstreamService> activeWanakuDownstreamServices() {
        return List.of(WanakuContainerDownstreamService.KAFKA);
    }
}
