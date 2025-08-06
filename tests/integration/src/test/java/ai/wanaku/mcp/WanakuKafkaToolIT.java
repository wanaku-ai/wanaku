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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static ai.wanaku.mcp.CLIHelper.executeWanakuCliCommand;
import static java.lang.Thread.sleep;


@QuarkusTest
public class WanakuKafkaToolIT extends WanakuIntegrationBase {

    private static ConfluentKafkaContainer kafkaContainer;
    private static String bootstrapHost;
    private static Thread responderThread;

    @TempDir
    static Path tempDir;

    @BeforeAll
    static void startKafka() throws Exception {
        kafkaContainer = new ConfluentKafkaContainer(
                DockerImageName.parse("confluentinc/cp-kafka:7.6.1"))
                .withNetwork(router.getNetwork())
                .withListener("kafka:19092")
                .withNetworkAliases("kafka");

        kafkaContainer.start();
        bootstrapHost = kafkaContainer.getBootstrapServers();

        createKafkaTopics();
        createCapabilityFile();
        startMockKafkaResponder();
    }

    private static void createCapabilityFile() throws Exception {
        String kafkaBootstrap = "kafka:19092";
        String content = String.format("""
                bootstrapHost=%s
                requestTopic=request-topic
                replyToTopic=response-topic
                """, kafkaBootstrap);

        Files.writeString(tempDir.resolve("capabilities.properties"), content);
        System.out.println("Capabilities file created with bootstrapHost=" + kafkaBootstrap);
    }


    private static void createKafkaTopics() throws Exception {
        try (AdminClient admin = AdminClient.create(
                Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapHost))) {
            admin.createTopics(List.of(
                    new NewTopic("request-topic", 1, (short) 1),
                    new NewTopic("response-topic", 1, (short) 1)
            )).all().get(10, TimeUnit.SECONDS);
        }
    }

    /**
     * Mock responder for request–reply
     */
    private static void startMockKafkaResponder() {
        responderThread = new Thread(() -> {
            try (KafkaConsumer<String, String> consumer = createConsumer(bootstrapHost);
                 KafkaProducer<String, String> producer = createProducer(bootstrapHost)) {

                consumer.subscribe(List.of("request-topic"));
                while (true) {
                    var records = consumer.poll(Duration.ofMillis(500));
                    for (var record : records) {
                        String response = "Processed: " + record.value();
                        sleep(2000);
                        producer.send(new ProducerRecord<>("response-topic", response));
                        producer.flush();
                    }
                }
            } catch (Exception e) {
                System.out.println(e.getMessage());
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

    @AfterAll
    static void stopKafka() throws Exception {
        // Stop responder thread cleanly
        if (responderThread != null) {
            responderThread.join(1000L);
        }
        if (kafkaContainer != null) {
            kafkaContainer.stop();
            System.out.println("Kafka container stopped.");
        }
    }
}
