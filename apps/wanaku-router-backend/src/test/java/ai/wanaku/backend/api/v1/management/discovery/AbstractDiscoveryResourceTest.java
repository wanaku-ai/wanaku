package ai.wanaku.backend.api.v1.management.discovery;

import jakarta.ws.rs.core.Response;

import org.jboss.logging.Logger;
import ai.wanaku.backend.support.TestIndexHelper;
import ai.wanaku.backend.support.WanakuRouterTest;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceType;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public abstract class AbstractDiscoveryResourceTest extends WanakuRouterTest {
    private static final Logger LOG = Logger.getLogger(DiscoveryResourceTest.class);

    private static String serviceId;

    @BeforeAll
    static void setup() {
        TestIndexHelper.clearAllCaches();
    }

    @Order(1)
    @Test
    public void testRegisterServiceSuccessfully() {
        ServiceTarget serviceTarget =
                new ServiceTarget(null, "test-service", "localhost", 8080, "tool-invoker", "mcp", null, null, null);

        final var response =
                given().headers(getHeaders()).body(serviceTarget).when().post("/api/v1/management/discovery");

        LOG.infof("Response: %s", response.getBody().asString());

        serviceId = response.then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data.id", notNullValue())
                .extract()
                .path("data.id");

        LOG.infof("Created service with id %s", serviceId);

        given().when()
                .get("/api/v1/capabilities/")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data.size()", is(1), "data[0].id", is(serviceId), "data[0].serviceName", is("test-service"));
    }

    @Order(2)
    @Test
    public void testDeregisterServiceSuccessfully() {
        ServiceTarget serviceTarget = new ServiceTarget(
                serviceId,
                "test-service",
                "localhost",
                8080,
                ServiceType.TOOL_INVOKER.asValue(),
                "mcp",
                null,
                null,
                null);

        given().headers(getHeaders())
                .body(serviceTarget)
                .when()
                .delete("/api/v1/management/discovery")
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        given().when()
                .get("/api/v1/capabilities/")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data.size()", is(0));
    }
}
