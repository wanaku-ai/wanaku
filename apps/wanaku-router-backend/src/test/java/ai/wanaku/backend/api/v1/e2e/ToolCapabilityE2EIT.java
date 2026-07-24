package ai.wanaku.backend.api.v1.e2e;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jboss.logging.Logger;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import ai.wanaku.backend.support.E2ETestProfile;
import ai.wanaku.backend.support.MockGrpcCapabilityServer;
import ai.wanaku.backend.support.TestIndexHelper;
import ai.wanaku.backend.support.WanakuKeycloakTestResource;
import ai.wanaku.backend.support.WanakuRouterTest;
import ai.wanaku.capabilities.sdk.api.types.InputSchema;
import ai.wanaku.capabilities.sdk.api.types.ToolReference;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceType;
import ai.wanaku.core.util.support.ToolsHelper;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.DisabledIf;

/**
 * End-to-end test for MCP tool capability dispatch.
 * Verifies the full chain: MCP Streamable HTTP -> router resolver -> gRPC transport -> mock capability.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@QuarkusIntegrationTest
@TestProfile(E2ETestProfile.class)
@QuarkusTestResource(value = WanakuKeycloakTestResource.class, restrictToAnnotatedClass = true)
@DisabledIf(value = "isUnsupportedOSOnGithub", disabledReason = "Does not run on macOS or Windows on GitHub")
public class ToolCapabilityE2EIT extends WanakuRouterTest {
    private static final Logger LOG = Logger.getLogger(ToolCapabilityE2EIT.class);

    private static final String EXPECTED_CONTENT = "test-tool-result-from-mock-capability";
    private static final String SERVICE_NAME = "test-tool";
    private static final String TOOL_NAME = "test-tool-operation";

    private static MockGrpcCapabilityServer mockServer;
    private static int mockPort;
    private static KeycloakTestClient keycloakClient;
    // MCP client removed — McpAssured replaced by official SDK (Streamable HTTP)

    private String getAccessToken() {
        return keycloakClient.getRealmClientAccessToken("wanaku", "wanaku-service", "secret");
    }

    @BeforeAll
    static void setup() throws IOException {
        TestIndexHelper.clearAllCaches();
        keycloakClient = new KeycloakTestClient();

        mockServer = new MockGrpcCapabilityServer(List.of(EXPECTED_CONTENT));
        mockPort = mockServer.start();
        LOG.infof("Mock tool capability server started on port %d", mockPort);
    }

    @AfterAll
    static void tearDown() {
        if (mockServer != null) {
            mockServer.stop();
        }
    }

    @Order(1)
    @Test
    void testRegisterCapability() {
        String accessToken = getAccessToken();

        ServiceTarget serviceTarget = new ServiceTarget(
                null, SERVICE_NAME, "localhost", mockPort, ServiceType.TOOL_INVOKER.asValue(), "mcp", null, null, null);

        given().header("Content-Type", MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(serviceTarget)
                .when()
                .post("/api/v1/management/discovery")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data.id", notNullValue());

        LOG.info("Mock tool capability registered with router");
    }

    @Order(2)
    @Test
    void testAddTool() {
        String accessToken = getAccessToken();

        InputSchema inputSchema = ToolsHelper.createInputSchema(
                SERVICE_NAME,
                Collections.singletonMap("input", ToolsHelper.createProperty("string", "An input parameter.")));

        ToolReference toolReference = new ToolReference();
        toolReference.setName(TOOL_NAME);
        toolReference.setDescription("A test tool for e2e testing");
        toolReference.setUri("test-tool://operation");
        toolReference.setType(SERVICE_NAME);
        toolReference.setNamespace("public");
        toolReference.setInputSchema(inputSchema);

        given().header("Content-Type", MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(toolReference)
                .when()
                .post("/api/v1/tools")
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        given().header("Authorization", "Bearer " + accessToken)
                .when()
                .get("/api/v1/tools")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data.size()", is(1), "data[0].name", is(TOOL_NAME), "data[0].type", is(SERVICE_NAME));

        LOG.info("Tool added successfully");
    }

    @Order(3)
    @Test
    @Disabled("SDK HttpClientStreamableHttpTransport session management issue — "
            + "validated by wanaku-tests integration suite instead")
    void testCallToolViaMcp() {
        int httpPort = io.restassured.RestAssured.port;
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(
                        "http://localhost:" + httpPort + "/public/mcp")
                .build();

        try (McpSyncClient client =
                McpClient.sync(transport).requestTimeout(Duration.ofSeconds(10)).build()) {
            client.initialize();

            McpSchema.CallToolResult result =
                    client.callTool(new McpSchema.CallToolRequest(TOOL_NAME, Map.of("input", "test-value"), null));

            org.junit.jupiter.api.Assertions.assertFalse(
                    result.isError() != null && result.isError(), "Tool response should not be an error");
            org.junit.jupiter.api.Assertions.assertFalse(
                    result.content().isEmpty(), "Tool response content should not be empty");
            org.junit.jupiter.api.Assertions.assertEquals(
                    EXPECTED_CONTENT, ((McpSchema.TextContent) result.content().getFirst()).text());
        }

        LOG.info("Tool called via MCP successfully - content matches expected value");
    }
}
