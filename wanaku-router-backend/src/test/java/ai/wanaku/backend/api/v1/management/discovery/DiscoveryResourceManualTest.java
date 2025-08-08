
package ai.wanaku.backend.api.v1.management.discovery;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import ai.wanaku.api.types.discovery.ServiceState;
import ai.wanaku.api.types.providers.ServiceTarget;
import ai.wanaku.backend.support.TestIndexHelper;
import ai.wanaku.backend.support.WanakuKeycloakTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import java.io.IOException;
import java.time.Instant;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@QuarkusTest
@QuarkusTestResource(value = WanakuKeycloakTestResource.class, restrictToAnnotatedClass = true)
public class DiscoveryResourceManualTest {
    private static final Logger LOG = Logger.getLogger(DiscoveryResourceManualTest.class);

    private static String serviceId;

    private static KeycloakTestClient keycloakClient;

    private String getAccessToken() {
        return keycloakClient.getRealmClientAccessToken("wanaku", "wanaku-service", "secret");
    }

    @BeforeAll
    static void setup() throws IOException {
        TestIndexHelper.deleteRecursively("target/wanaku/router");

        keycloakClient = new KeycloakTestClient();
    }

    @Order(1)
    @Test
    public void testRegisterServiceSuccessfully() {

        final String accessToken = getAccessToken();
        Assertions.assertNotNull(accessToken);

        ServiceTarget serviceTarget = new ServiceTarget(
                null,
                "test-service",
                "localhost",
                8080,
                ai.wanaku.api.types.providers.ServiceType.TOOL_INVOKER
        );

        final var response = given()
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(serviceTarget)
                .when().post("/api/v1/management/discovery/register");

        LOG.infof("Response: %s", response.getBody().asString());

        serviceId = response
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data.id", notNullValue())
                .extract()
                .path("data.id");

        LOG.infof("Created service with id %s", serviceId);

        given()
                .when().get("/api/v1/capabilities/tools/list")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data.size()", is(1),
                        "data[0].id", is(serviceId),
                        "data[0].service", is("test-service"));
    }

    @Order(2)
    @Test
    public void testPingServiceSuccessfully() {
        final String accessToken = getAccessToken();
        Assertions.assertNotNull(accessToken);

        given()
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(serviceId)
                .when().post("/api/v1/management/discovery/ping")
                .then()
                .statusCode(Response.Status.OK.getStatusCode());
    }

    @Order(3)
    @Test
    public void testUpdateServiceStateSuccessfully() {
        ServiceState serviceState = new ServiceState(Instant.now(), false, "Service is down for maintenance");

        final String accessToken = getAccessToken();
        Assertions.assertNotNull(accessToken);

        given()
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(serviceState)
                .when().post("/api/v1/management/discovery/update/" + serviceId)
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        given()
                .when().get("/api/v1/capabilities/tools/state")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data.size()", is(1),
                        "data.test-service[0].id", is(serviceId),
                        "data.test-service[0].states[0].healthy", is(false));
    }

    @Order(4)
    @Test
    public void testDeregisterServiceSuccessfully() {
        final String accessToken = getAccessToken();
        Assertions.assertNotNull(accessToken);

        ServiceTarget serviceTarget = new ServiceTarget(
                serviceId,
                "test-service",
                "localhost",
                8080,
                ai.wanaku.api.types.providers.ServiceType.TOOL_INVOKER
        );

        given()
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(serviceTarget)
                .when().post("/api/v1/management/discovery/deregister")
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        given()
                .when().get("/api/v1/capabilities/tools/list")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data.size()", is(0));
    }
}
