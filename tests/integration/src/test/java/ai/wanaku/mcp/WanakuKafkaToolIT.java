package ai.wanaku.mcp;

import io.quarkus.test.junit.QuarkusTest;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static ai.wanaku.mcp.CLIHelper.executeWanakuCliCommand;
import static org.awaitility.Awaitility.await;

@QuarkusTest
@Testcontainers
public class WanakuKafkaToolIT extends WanakuIntegrationBase {

    @Container
    static KafkaContainer kafkaContainer = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"))
            .withNetwork(router.getNetwork())
            .withListener("kafka:19092")
            .withNetworkAliases("kafka");

    private static String bootStrapHost;

    private static Thread responderThread;

    private static final AtomicBoolean keepRunning = new AtomicBoolean(true);

    @TempDir
    static Path tempDir;

    @BeforeEach
    void startKafka() throws Exception {
        bootStrapHost = kafkaContainer.getBootstrapServers();
        createKafkaTopics();
        createCapabilityFile();
    }

    /**
     * Starts a mock Kafka responder for request–reply.
     * It listens on the 'request-topic' and responds on 'response-topic'.
     */
    private static void startMockKafkaResponder() {
        responderThread = new Thread(() -> {
            try (
                    KafkaConsumer<String, String> consumer = createConsumer(bootStrapHost);
                    KafkaProducer<String, String> producer = createProducer(bootStrapHost)
            ) {
                consumer.subscribe(List.of("request-topic"));

                while (keepRunning.get()) {
                    var records = consumer.poll(Duration.ofMillis(500));
                    for (var record : records) {
                        String response = "Processed: " + record.value();
                        Thread.sleep(2000);
                        producer.send(new ProducerRecord<>("response-topic", response));
                        producer.flush();
                    }
                }
            } catch (Exception e) {
                System.out.println("Kafka responder error: " + e.getMessage());
            }
        }, "mock-kafka-responder");

        responderThread.start();
    }

    private static KafkaProducer<String, String> createProducer(String broker) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, broker);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        return new KafkaProducer<>(props);
    }

    private static KafkaConsumer<String, String> createConsumer(String broker) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new KafkaConsumer<>(props);
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
            )).all().get();
        }
    }

    @Test
    void kafkaToolAdditionAndVerification() throws Exception {
        // Ensure tools list is initially empty
        client.when().toolsList()
                .withAssert(toolsPage -> Assertions.assertThat(toolsPage.tools()).isEmpty())
                .send();

        // Add Kafka tool via CLI
        String host = String.format("http://localhost:%d", router.getMappedPort(8080));

        int addToolExit = executeWanakuCliCommand(List.of(
                "wanaku", "tools", "add",
                "-n", "sushi-request",
                "--description", "Orders the delivery of authentic Japanese sushi",
                "--uri", "",
                "--type", "kafka",
                "--configuration-from-file=" + tempDir.resolve("capabilities.properties")
        ), host);

        Assertions.assertThat(addToolExit)
                .as("Failed to add Kafka tool via CLI")
                .isEqualTo(0);

        // Verify tool is registered
        client.when().toolsList()
                .withAssert(toolsPage -> {
                    Assertions.assertThat(toolsPage.tools())
                            .as("Tool list should contain exactly one tool")
                            .hasSize(1);
                    Assertions.assertThat(toolsPage.tools().getFirst().name())
                            .as("Registered tool should have the expected name")
                            .isEqualTo("sushi-request");
                })
                .send();

        // Call the tool and validate response
        String orderMessage = "Order 1 sushi roll";

        try {
            if (responderThread == null || !responderThread.isAlive()) {
                keepRunning.set(true);
                startMockKafkaResponder();
            }

            await().atMost(20, TimeUnit.SECONDS)
                    .pollInterval(200, TimeUnit.MILLISECONDS)
                    .untilAsserted(() -> client.when().toolsCall("sushi-request")
                            .withArguments(Map.of("wanaku_body", orderMessage))
                            .withAssert(toolResponse -> {
                                Assertions.assertThat(toolResponse.isError()).isFalse();
                                Assertions.assertThat(toolResponse.content().getFirst().asText()
                                        .text().equalsIgnoreCase("Processed: Order 1 sushi roll")).isTrue();
                            })
                            .send());
        } finally {
            // Stop the responder
            keepRunning.set(false);
            if (responderThread != null) {
                responderThread.join(1000L);
                responderThread = null;
            }
        }
    }

    @Override
    public List<WanakuContainerDownstreamService> activeWanakuDownstreamServices() {
        return List.of(WanakuContainerDownstreamService.KAFKA);
    }
}
