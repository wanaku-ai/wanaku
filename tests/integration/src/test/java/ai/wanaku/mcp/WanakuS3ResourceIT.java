package ai.wanaku.mcp;

import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.AssertionFailedError;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static ai.wanaku.mcp.CLIHelper.executeWanakuCliCommand;

/**
 * Integration test for Wanaku MCP Router's AWS S3 resource capabilities.
 *
 * <p>This test verifies that the Wanaku MCP Router can successfully expose and read
 * AWS S3 resources through the Model Context Protocol (MCP). The test uses LocalStack
 * to simulate AWS S3 services in a containerized environment.</p>
 *
 * <p>The test demonstrates the following Wanaku capabilities:</p>
 * <ul>
 *   <li>Exposing S3 objects as MCP resources using the {@code aws2-s3} capability</li>
 *   <li>Configuring S3 access with region, access keys, and secrets</li>
 *   <li>Reading S3 resource content through the MCP protocol</li>
 *   <li>Integration with the Wanaku CLI for resource management</li>
 * </ul>
 *
 * <p>The test setup includes:</p>
 * <ul>
 *   <li>A LocalStack container running S3 services</li>
 *   <li>Creation of a test bucket and object in S3</li>
 *   <li>Generation of capability and secret configuration files</li>
 *   <li>Activation of the S3 provider downstream service</li>
 * </ul>
 *
 * <p>This test extends {@link WanakuIntegrationBase} which provides the base
 * infrastructure for Wanaku integration testing, including the MCP router
 * container and client setup.</p>
 */
@QuarkusTest
@Testcontainers
public class WanakuS3ResourceIT extends WanakuIntegrationBase {
    private static final String BUCKET_NAME = "myBucket";
    @TempDir
    private static Path tempDir;

    @Container
    static LocalStackContainer localStack = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:s3-latest")
    ).withServices(LocalStackContainer.Service.S3);

    /**
     * Sets up the test environment by creating S3 resources and configuration files.
     *
     * <p>This method performs the following setup tasks:</p>
     * <ol>
     *   <li>Creates a test bucket in the LocalStack S3 service</li>
     *   <li>Uploads a test file ({@code test.txt}) to the bucket</li>
     *   <li>Generates a capabilities configuration file with S3 settings</li>
     *   <li>Generates a secrets configuration file with S3 credentials</li>
     * </ol>
     *
     * <p>The capabilities file contains:</p>
     * <ul>
     *   <li>{@code region} - The AWS region for S3 operations</li>
     *   <li>{@code deleteAfterRead} - Whether to delete objects after reading (set to false)</li>
     * </ul>
     *
     * <p>The secrets file contains:</p>
     * <ul>
     *   <li>{@code accessKey} - AWS access key for authentication</li>
     *   <li>{@code secretKey} - AWS secret key for authentication</li>
     * </ul>
     *
     */
    @BeforeAll
    static void addFileToS3() throws IOException, InterruptedException {
        localStack.execInContainer("awslocal", "s3api", "create-bucket", "--bucket", BUCKET_NAME);
        localStack.execInContainer("awslocal",  "s3api", "put-object", "--bucket", BUCKET_NAME,
                "--key", "test.txt", "--body", WanakuS3ResourceIT.class.getResource("/test.txt").getPath());

        Path capabilities = tempDir.resolve("capabilities.properties");
        Path secrets = tempDir.resolve("secrets.properties");

        Files.write(capabilities, """
                region=%s
                deleteAfterRead=%s
                """.formatted(localStack.getRegion(), "false")
                .getBytes(StandardCharsets.UTF_8));

        Files.write(secrets, """
                accessKey=%s
                secretKey=%s
                """.formatted(localStack.getAccessKey(), localStack.getSecretKey())
                .getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Tests the complete workflow of exposing and reading an S3 resource through Wanaku.
     *
     * <p>This test verifies the end-to-end functionality of S3 resource management
     * in the Wanaku MCP Router. It performs the following operations:</p>
     *
     * <ol>
     *   <li><strong>Resource Exposure:</strong> Uses the Wanaku CLI to expose an S3 object
     *       as an MCP resource with the {@code aws2-s3} capability type</li>
     *   <li><strong>Resource Discovery:</strong> Verifies that the exposed resource appears
     *       in the MCP resources list with the correct name</li>
     *   <li><strong>Resource Reading:</strong> Reads the content of the S3 resource through
     *       the MCP protocol and verifies the content matches expectations</li>
     * </ol>
     *
     * <p>The test uses the following Wanaku CLI command structure:</p>
     * <pre>
     * wanaku resources expose \
     *   --location=myBucket/test.txt \
     *   --description="S3 Sample test resource added via CLI" \
     *   --name=test-s3-resource \
     *   --type=aws2-s3 \
     *   --configuration-from-file=capabilities.properties \
     *   --secrets-from-file=secrets.properties
     * </pre>
     *
     * <p>The test validates:</p>
     * <ul>
     *   <li>Successful resource exposure through the CLI</li>
     *   <li>Correct resource metadata in the MCP resources list</li>
     *   <li>Ability to read the actual content of the S3 object</li>
     *   <li>Content integrity (verifies the content contains "Wanaku!!")</li>
     * </ul>
     */
    @Test
    public void readResource() throws Exception {
        String host = String.format("http://localhost:%d", router.getMappedPort(8080));

        // Reduce flakiness
        Awaitility.await()
                .ignoreException(AssertionFailedError.class)
                .timeout(Duration.ofSeconds(10))
                .until(() ->
                        executeWanakuCliCommand(List.of("wanaku",
                                "resources",
                                "expose",
                                "--location=" + BUCKET_NAME + "/test.txt",
                                "--description=\"S3 Sample test resource added via CLI\"",
                                "--name=test-s3-resource",
                                "--type=aws2-s3",
                                "--configuration-from-file=" + tempDir.resolve("capabilities.properties"),
                                "--secrets-from-file=" + tempDir.resolve("secrets.properties")), host) == 0);

        McpAssured.Snapshot snapshot = client.when().resourcesList()
                .withAssert(resourcesPage ->
                        Assertions.assertThat(resourcesPage.resources().get(0).name()).isEqualTo("test-s3-resource"))
                .send()
                .thenAssertResults();
        JsonObject response = snapshot.responses().get(snapshot.responses().size() - 1);

        String uri = response.getJsonObject("result")
                .getJsonArray("resources")
                .getJsonObject(0)
                .getString("uri");

        client.when().resourcesRead(uri)
                .withAssert(resourceResponse ->
                        Assertions.assertThat(resourceResponse.contents().get(0).asText().text()).contains("Wanaku!!"))
                .send();
    }

    @Override
    public List<WanakuContainerDownstreamService> activeWanakuDownstreamServices() {
        return List.of(WanakuContainerDownstreamService.PROVIDER_S3);
    }
}
