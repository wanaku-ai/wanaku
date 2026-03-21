package ai.wanaku.backend.api.v1.capabilities;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jboss.logging.Logger;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import ai.wanaku.backend.support.TestIndexHelper;
import ai.wanaku.backend.support.WanakuKeycloakTestResource;
import ai.wanaku.backend.support.WanakuRouterTest;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceType;
import io.quarkus.test.keycloak.client.KeycloakTestClient;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.greaterThan;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.DisabledIf;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@QuarkusTest
@QuarkusTestResource(value = WanakuKeycloakTestResource.class, restrictToAnnotatedClass = true)
@DisabledIf(value = "isUnsupportedOSOnGithub", disabledReason = "Does not run on macOS or Windows on GitHub")
public class CapabilitiesResourceIT extends WanakuRouterTest {
    private static final Logger LOG = Logger.getLogger(CapabilitiesResourceIT.class);

    private static final String TOOL_SERVICE_NAME = "capabilities-tool-service";
    private static final String RESOURCE_SERVICE_NAME = "capabilities-resource-service";
    private static final String EXTRA_SERVICE_NAME = "capabilities-extra-service";

    private static String toolServiceId;
    private static String resourceServiceId;
    private static String extraServiceId;

    private static KeycloakTestClient keycloakClient;

    private String getAccessToken() {
        return keycloakClient.getRealmClientAccessToken("wanaku", "wanaku-service", "secret");
    }

    @BeforeAll
    static void setup() {
        TestIndexHelper.clearAllCaches();
        keycloakClient = new KeycloakTestClient();
    }

    @Order(1)
    @Test
    void testGetCapabilities_ReturnsValidStructure() {
        given().when()
                .get("/api/v1/capabilities")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data", notNullValue());
    }

    @Order(2)
    @Test
    void testGetCapabilities_IncludesRegisteredToolService() {
        String accessToken = getAccessToken();

        ServiceTarget serviceTarget = new ServiceTarget(
                null,
                TOOL_SERVICE_NAME,
                "localhost",
                9101,
                ServiceType.TOOL_INVOKER.asValue(),
                "mcp",
                null,
                null,
                null);

        toolServiceId = given().header("Content-Type", MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(serviceTarget)
                .when()
                .post("/api/v1/management/discovery")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data.id", notNullValue())
                .extract()
                .path("data.id");

        given().when()
                .get("/api/v1/capabilities")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data.find { it.id == '" + toolServiceId + "' }.serviceName", equalTo(TOOL_SERVICE_NAME))
                .body(
                        "data.find { it.id == '" + toolServiceId + "' }.serviceType",
                        equalTo(ServiceType.TOOL_INVOKER.asValue()));

        LOG.infof("Registered tool capability with id %s", toolServiceId);
    }

    @Order(3)
    @Test
    void testGetCapabilities_IncludesRegisteredResourceService() {
        String accessToken = getAccessToken();

        ServiceTarget serviceTarget = new ServiceTarget(
                null,
                RESOURCE_SERVICE_NAME,
                "localhost",
                9102,
                ServiceType.RESOURCE_PROVIDER.asValue(),
                "mcp",
                null,
                null,
                null);

        resourceServiceId = given().header("Content-Type", MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(serviceTarget)
                .when()
                .post("/api/v1/management/discovery")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data.id", notNullValue())
                .extract()
                .path("data.id");

        given().when()
                .get("/api/v1/capabilities")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data.find { it.id == '" + resourceServiceId + "' }.serviceName", equalTo(RESOURCE_SERVICE_NAME))
                .body(
                        "data.find { it.id == '" + resourceServiceId + "' }.serviceType",
                        equalTo(ServiceType.RESOURCE_PROVIDER.asValue()));

        LOG.infof("Registered resource capability with id %s", resourceServiceId);
    }

    @Order(4)
    @Test
    void testGetCapabilities_ShowsMultipleServiceTypes() {
        given().when()
                .get("/api/v1/capabilities")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body(
                        "data.find { it.id == '" + toolServiceId + "' }.serviceType",
                        equalTo(ServiceType.TOOL_INVOKER.asValue()))
                .body(
                        "data.find { it.id == '" + resourceServiceId + "' }.serviceType",
                        equalTo(ServiceType.RESOURCE_PROVIDER.asValue()));
    }

    @Order(5)
    @Test
    void testGetCapabilities_UpdatesWhenServiceAdded() {
        String accessToken = getAccessToken();

        int initialCount = given().when()
                .get("/api/v1/capabilities")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .jsonPath()
                .getList("data")
                .size();

        ServiceTarget serviceTarget = new ServiceTarget(
                null,
                EXTRA_SERVICE_NAME,
                "localhost",
                9103,
                ServiceType.TOOL_INVOKER.asValue(),
                "mcp",
                null,
                null,
                null);

        extraServiceId = given().header("Content-Type", MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(serviceTarget)
                .when()
                .post("/api/v1/management/discovery")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .path("data.id");

        given().when()
                .get("/api/v1/capabilities")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data.size()", greaterThan(initialCount))
                .body("data.find { it.id == '" + extraServiceId + "' }", notNullValue());
    }

    @Order(6)
    @Test
    void testGetCapabilities_UpdatesWhenServiceRemoved() {
        String accessToken = getAccessToken();

        ServiceTarget toolToRemove = new ServiceTarget(
                toolServiceId,
                TOOL_SERVICE_NAME,
                "localhost",
                9101,
                ServiceType.TOOL_INVOKER.asValue(),
                "mcp",
                null,
                null,
                null);

        given().header("Content-Type", MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(toolToRemove)
                .when()
                .delete("/api/v1/management/discovery")
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        ServiceTarget resourceToRemove = new ServiceTarget(
                resourceServiceId,
                RESOURCE_SERVICE_NAME,
                "localhost",
                9102,
                ServiceType.RESOURCE_PROVIDER.asValue(),
                "mcp",
                null,
                null,
                null);

        given().header("Content-Type", MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(resourceToRemove)
                .when()
                .delete("/api/v1/management/discovery")
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        ServiceTarget extraToRemove = new ServiceTarget(
                extraServiceId,
                EXTRA_SERVICE_NAME,
                "localhost",
                9103,
                ServiceType.TOOL_INVOKER.asValue(),
                "mcp",
                null,
                null,
                null);

        given().header("Content-Type", MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(extraToRemove)
                .when()
                .delete("/api/v1/management/discovery")
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        given().when()
                .get("/api/v1/capabilities")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data.find { it.id == '" + toolServiceId + "' }", nullValue())
                .body("data.find { it.id == '" + resourceServiceId + "' }", nullValue())
                .body("data.find { it.id == '" + extraServiceId + "' }", nullValue());
    }
}
