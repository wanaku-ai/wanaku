package ai.wanaku.backend.api.v1.e2e;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
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
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceType;

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
 * End-to-end test for MCP resource capability dispatch.
 * Verifies the full chain: MCP Streamable HTTP -> router resolver -> gRPC transport -> mock capability.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@QuarkusIntegrationTest
@TestProfile(E2ETestProfile.class)
@QuarkusTestResource(value = WanakuKeycloakTestResource.class, restrictToAnnotatedClass = true)
@DisabledIf(value = "isUnsupportedOSOnGithub", disabledReason = "Does not run on macOS or Windows on GitHub")
public class ResourceCapabilityE2EIT extends WanakuRouterTest {
    private static final Logger LOG = Logger.getLogger(ResourceCapabilityE2EIT.class);

    private static final String EXPECTED_CONTENT = "test-resource-content-from-mock-capability";
    private static final String SERVICE_NAME = "test-resource";
    private static final String RESOURCE_NAME = "sample-resource.test";

    private static MockGrpcCapabilityServer mockServer;
    private static int mockPort;
    private static KeycloakTestClient keycloakClient;

    private String getAccessToken() {
        return keycloakClient.getRealmClientAccessToken("wanaku", "wanaku-service", "secret");
    }

    @BeforeAll
    static void setup() throws IOException {
        TestIndexHelper.clearAllCaches();
        keycloakClient = new KeycloakTestClient();

        mockServer = new MockGrpcCapabilityServer(List.of(EXPECTED_CONTENT));
        mockPort = mockServer.start();
        LOG.infof("Mock resource capability server started on port %d", mockPort);
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
                null,
                SERVICE_NAME,
                "localhost",
                mockPort,
                ServiceType.RESOURCE_PROVIDER.asValue(),
                "mcp",
                null,
                null,
                null);

        given().header("Content-Type", MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(serviceTarget)
                .when()
                .post("/api/v1/management/discovery")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data.id", notNullValue());

        LOG.info("Mock resource capability registered with router");
    }

    @Order(2)
    @Test
    void testExposeResource() {
        String accessToken = getAccessToken();

        ResourceReference resource = new ResourceReference();
        resource.setLocation("test-resource://sample-resource.test");
        resource.setType(SERVICE_NAME);
        resource.setName(RESOURCE_NAME);
        resource.setDescription("A test resource for e2e testing");
        resource.setMimeType("text/plain");
        resource.setNamespace("public");

        given().header("Content-Type", MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(resource)
                .when()
                .post("/api/v1/resources")
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        given().header("Authorization", "Bearer " + accessToken)
                .when()
                .get("/api/v1/resources")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data.size()", is(1), "data[0].name", is(RESOURCE_NAME), "data[0].type", is(SERVICE_NAME));

        LOG.info("Resource exposed successfully");
    }

    @Order(3)
    @Test
    @Disabled("SDK HttpClientStreamableHttpTransport session management issue — "
            + "validated by wanaku-tests integration suite instead")
    void testReadResourceViaMcp() {
        int httpPort = io.restassured.RestAssured.port;
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(
                        "http://localhost:" + httpPort + "/public/mcp")
                .build();

        try (McpSyncClient client =
                McpClient.sync(transport).requestTimeout(Duration.ofSeconds(10)).build()) {
            client.initialize();

            McpSchema.ReadResourceResult result = client.readResource(
                    new McpSchema.ReadResourceRequest("test-resource://sample-resource.test", null));

            org.junit.jupiter.api.Assertions.assertFalse(
                    result.contents().isEmpty(), "Resource contents should not be empty");
            org.junit.jupiter.api.Assertions.assertEquals(
                    EXPECTED_CONTENT,
                    ((McpSchema.TextResourceContents) result.contents().getFirst()).text());
        }

        LOG.info("Resource read via MCP successfully - content matches expected value");
    }
}
