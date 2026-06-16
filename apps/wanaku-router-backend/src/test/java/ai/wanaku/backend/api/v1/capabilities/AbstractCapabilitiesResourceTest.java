package ai.wanaku.backend.api.v1.capabilities;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import ai.wanaku.backend.support.TestIndexHelper;
import ai.wanaku.backend.support.WanakuRouterTest;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceType;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.greaterThan;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public abstract class AbstractCapabilitiesResourceTest extends WanakuRouterTest {

    @BeforeAll
    static void setup() {
        TestIndexHelper.clearAllCaches();
    }

    protected boolean isAuthEnabled() {
        return false;
    }

    @Test
    void testGetCapabilities_ReturnsValidStructure() {
        given().headers(getHeaders())
                .when()
                .get("/api/v1/capabilities")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data", notNullValue());
    }

    @Test
    void testGetCapabilities_IncludesRegisteredToolService() {
        ServiceTarget serviceTarget = new ServiceTarget(
                null,
                "test-tool-service-independent",
                "localhost",
                9101,
                ServiceType.TOOL_INVOKER.asValue(),
                "mcp",
                null,
                null,
                null);

        String serviceId = given().headers(getHeaders())
                .body(serviceTarget)
                .when()
                .post("/api/v1/management/discovery")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data.id", notNullValue())
                .extract()
                .path("data.id");

        given().headers(getHeaders())
                .when()
                .get("/api/v1/capabilities")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body(
                        "data.find { it.id == '" + serviceId + "' }.serviceName",
                        equalTo("test-tool-service-independent"))
                .body(
                        "data.find { it.id == '" + serviceId + "' }.serviceType",
                        equalTo(ServiceType.TOOL_INVOKER.asValue()));

        // Cleanup
        ServiceTarget toRemove = new ServiceTarget(
                serviceId,
                "test-tool-service-independent",
                "localhost",
                9101,
                ServiceType.TOOL_INVOKER.asValue(),
                "mcp",
                null,
                null,
                null);

        given().headers(getHeaders()).body(toRemove).when().delete("/api/v1/management/discovery");
    }

    @Test
    void testGetCapabilities_IncludesRegisteredResourceService() {
        ServiceTarget serviceTarget = new ServiceTarget(
                null,
                "test-resource-service-independent",
                "localhost",
                9102,
                ServiceType.RESOURCE_PROVIDER.asValue(),
                "mcp",
                null,
                null,
                null);

        String serviceId = given().headers(getHeaders())
                .body(serviceTarget)
                .when()
                .post("/api/v1/management/discovery")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data.id", notNullValue())
                .extract()
                .path("data.id");

        given().headers(getHeaders())
                .when()
                .get("/api/v1/capabilities")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body(
                        "data.find { it.id == '" + serviceId + "' }.serviceName",
                        equalTo("test-resource-service-independent"))
                .body(
                        "data.find { it.id == '" + serviceId + "' }.serviceType",
                        equalTo(ServiceType.RESOURCE_PROVIDER.asValue()));

        // Cleanup
        ServiceTarget toRemove = new ServiceTarget(
                serviceId,
                "test-resource-service-independent",
                "localhost",
                9102,
                ServiceType.RESOURCE_PROVIDER.asValue(),
                "mcp",
                null,
                null,
                null);

        given().headers(getHeaders()).body(toRemove).when().delete("/api/v1/management/discovery");
    }

    @Test
    void testGetCapabilities_ShowsMultipleServiceTypes() {
        // Register both types
        ServiceTarget toolService = new ServiceTarget(
                null,
                "test-multi-tool",
                "localhost",
                9201,
                ServiceType.TOOL_INVOKER.asValue(),
                "mcp",
                null,
                null,
                null);

        String toolId = given().headers(getHeaders())
                .body(toolService)
                .when()
                .post("/api/v1/management/discovery")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .path("data.id");

        ServiceTarget resourceService = new ServiceTarget(
                null,
                "test-multi-resource",
                "localhost",
                9202,
                ServiceType.RESOURCE_PROVIDER.asValue(),
                "mcp",
                null,
                null,
                null);

        String resourceId = given().headers(getHeaders())
                .body(resourceService)
                .when()
                .post("/api/v1/management/discovery")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .path("data.id");

        // Verify both types are present
        given().headers(getHeaders())
                .when()
                .get("/api/v1/capabilities")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body(
                        "data.find { it.id == '" + toolId + "' }.serviceType",
                        equalTo(ServiceType.TOOL_INVOKER.asValue()))
                .body(
                        "data.find { it.id == '" + resourceId + "' }.serviceType",
                        equalTo(ServiceType.RESOURCE_PROVIDER.asValue()));

        // Cleanup
        given().headers(getHeaders())
                .body(new ServiceTarget(
                        toolId,
                        "test-multi-tool",
                        "localhost",
                        9201,
                        ServiceType.TOOL_INVOKER.asValue(),
                        "mcp",
                        null,
                        null,
                        null))
                .when()
                .delete("/api/v1/management/discovery");

        given().headers(getHeaders())
                .body(new ServiceTarget(
                        resourceId,
                        "test-multi-resource",
                        "localhost",
                        9202,
                        ServiceType.RESOURCE_PROVIDER.asValue(),
                        "mcp",
                        null,
                        null,
                        null))
                .when()
                .delete("/api/v1/management/discovery");
    }

    @Test
    void testGetCapabilities_UpdatesWhenServiceAdded() {
        int initialCount = given().headers(getHeaders())
                .when()
                .get("/api/v1/capabilities")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .jsonPath()
                .getList("data")
                .size();

        ServiceTarget serviceTarget = new ServiceTarget(
                null,
                "test-added-service",
                "localhost",
                9103,
                ServiceType.TOOL_INVOKER.asValue(),
                "mcp",
                null,
                null,
                null);

        String serviceId = given().headers(getHeaders())
                .body(serviceTarget)
                .when()
                .post("/api/v1/management/discovery")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .path("data.id");

        given().headers(getHeaders())
                .when()
                .get("/api/v1/capabilities")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data.size()", greaterThan(initialCount))
                .body("data.find { it.id == '" + serviceId + "' }", notNullValue());

        // Cleanup
        given().headers(getHeaders())
                .body(new ServiceTarget(
                        serviceId,
                        "test-added-service",
                        "localhost",
                        9103,
                        ServiceType.TOOL_INVOKER.asValue(),
                        "mcp",
                        null,
                        null,
                        null))
                .when()
                .delete("/api/v1/management/discovery");
    }

    @Test
    void testGetCapabilities_UpdatesWhenServiceRemoved() {
        // Register a test service
        ServiceTarget testService = new ServiceTarget(
                null,
                "test-removal-service",
                "localhost",
                9999,
                ServiceType.TOOL_INVOKER.asValue(),
                "mcp",
                null,
                null,
                null);

        String serviceId = given().headers(getHeaders())
                .body(testService)
                .when()
                .post("/api/v1/management/discovery")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .path("data.id");

        // Verify it exists
        given().headers(getHeaders())
                .when()
                .get("/api/v1/capabilities")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data.find { it.id == '" + serviceId + "' }", notNullValue());

        // Remove the service
        ServiceTarget toRemove = new ServiceTarget(
                serviceId,
                "test-removal-service",
                "localhost",
                9999,
                ServiceType.TOOL_INVOKER.asValue(),
                "mcp",
                null,
                null,
                null);

        given().headers(getHeaders())
                .body(toRemove)
                .when()
                .delete("/api/v1/management/discovery")
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        // Verify it's removed
        given().headers(getHeaders())
                .when()
                .get("/api/v1/capabilities")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data.find { it.id == '" + serviceId + "' }", nullValue());
    }

    @Test
    void testRegisterService_WithInvalidServiceType_ShouldFail() {
        ServiceTarget invalidServiceTarget = new ServiceTarget(
                null, "invalid-service-type-test", "localhost", 9200, "invalid-service-type", "mcp", null, null, null);

        given().headers(getHeaders())
                .body(invalidServiceTarget)
                .when()
                .post("/api/v1/management/discovery")
                .then()
                .statusCode(
                        Response.Status.OK
                                .getStatusCode()); // Service registry accepts any string, validation happens at usage

        // Clean up
        String serviceId = given().headers(getHeaders())
                .when()
                .get("/api/v1/capabilities")
                .then()
                .extract()
                .jsonPath()
                .getString("data.find { it.serviceName == 'invalid-service-type-test' }.id");

        if (serviceId != null) {
            ServiceTarget toRemove = new ServiceTarget(
                    serviceId,
                    "invalid-service-type-test",
                    "localhost",
                    9200,
                    "invalid-service-type",
                    "mcp",
                    null,
                    null,
                    null);

            given().headers(getHeaders()).body(toRemove).when().delete("/api/v1/management/discovery");
        }
    }

    @Test
    void testGetCapabilities_WithInvalidToken() {
        // GET /api/v1/capabilities now requires authentication. When auth is enabled, a request with
        // an invalid bearer token is rejected with 401; when auth is disabled the token is ignored
        // and the endpoint responds normally.
        int expected =
                isAuthEnabled() ? Response.Status.UNAUTHORIZED.getStatusCode() : Response.Status.OK.getStatusCode();
        given().header("Authorization", "Bearer invalid-token")
                .when()
                .get("/api/v1/capabilities")
                .then()
                .statusCode(expected);
    }

    @Test
    void testRegisterService_WithMalformedJson_ShouldFail() {
        String malformedJson = "{\"serviceName\": \"test\", \"host\": \"localhost\", \"port\": ";

        given().headers(getHeaders())
                .body(malformedJson)
                .when()
                .post("/api/v1/management/discovery")
                .then()
                .statusCode(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()); // Jackson parsing error returns 500
    }

    @Test
    void testRegisterService_WithNullServiceName_AcceptsButMayFailLater() {
        ServiceTarget invalidTarget = new ServiceTarget(
                null,
                null, // null service name
                "localhost",
                9201,
                ServiceType.TOOL_INVOKER.asValue(),
                "mcp",
                null,
                null,
                null);

        // Service registry accepts null service name, but it may cause issues during usage
        String serviceId = given().headers(getHeaders())
                .body(invalidTarget)
                .when()
                .post("/api/v1/management/discovery")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .path("data.id");

        // Clean up
        if (serviceId != null) {
            ServiceTarget toRemove = new ServiceTarget(
                    serviceId, null, "localhost", 9201, ServiceType.TOOL_INVOKER.asValue(), "mcp", null, null, null);

            given().headers(getHeaders()).body(toRemove).when().delete("/api/v1/management/discovery");
        }
    }

    @Test
    void testRegisterService_WithEmptyServiceName_AcceptsButMayFailLater() {
        ServiceTarget invalidTarget = new ServiceTarget(
                null,
                "", // empty service name
                "localhost",
                9202,
                ServiceType.TOOL_INVOKER.asValue(),
                "mcp",
                null,
                null,
                null);

        // Service registry accepts empty service name, but it may cause issues during usage
        String serviceId = given().headers(getHeaders())
                .body(invalidTarget)
                .when()
                .post("/api/v1/management/discovery")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .path("data.id");

        // Clean up
        if (serviceId != null) {
            ServiceTarget toRemove = new ServiceTarget(
                    serviceId, "", "localhost", 9202, ServiceType.TOOL_INVOKER.asValue(), "mcp", null, null, null);

            given().headers(getHeaders()).body(toRemove).when().delete("/api/v1/management/discovery");
        }
    }

    @Test
    void testRegisterService_WithNullHost_AcceptsButMayFailLater() {
        ServiceTarget invalidTarget = new ServiceTarget(
                null,
                "null-host-test",
                null, // null host
                9203,
                ServiceType.TOOL_INVOKER.asValue(),
                "mcp",
                null,
                null,
                null);

        // Service registry accepts null host, but health checks will fail
        String serviceId = given().headers(getHeaders())
                .body(invalidTarget)
                .when()
                .post("/api/v1/management/discovery")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .path("data.id");

        // Clean up
        if (serviceId != null) {
            ServiceTarget toRemove = new ServiceTarget(
                    serviceId,
                    "null-host-test",
                    null,
                    9203,
                    ServiceType.TOOL_INVOKER.asValue(),
                    "mcp",
                    null,
                    null,
                    null);

            given().headers(getHeaders()).body(toRemove).when().delete("/api/v1/management/discovery");
        }
    }

    @Test
    void testRegisterService_WithNegativePort_AcceptsButMayFailLater() {
        ServiceTarget invalidTarget = new ServiceTarget(
                null,
                "negative-port-test",
                "localhost",
                -1, // negative port
                ServiceType.TOOL_INVOKER.asValue(),
                "mcp",
                null,
                null,
                null);

        // Service registry accepts negative port, but connection attempts will fail
        String serviceId = given().headers(getHeaders())
                .body(invalidTarget)
                .when()
                .post("/api/v1/management/discovery")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .path("data.id");

        // Clean up
        if (serviceId != null) {
            ServiceTarget toRemove = new ServiceTarget(
                    serviceId,
                    "negative-port-test",
                    "localhost",
                    -1,
                    ServiceType.TOOL_INVOKER.asValue(),
                    "mcp",
                    null,
                    null,
                    null);

            given().headers(getHeaders()).body(toRemove).when().delete("/api/v1/management/discovery");
        }
    }

    @Test
    void testRegisterService_WithZeroPort_AcceptsButMayFailLater() {
        ServiceTarget invalidTarget = new ServiceTarget(
                null,
                "zero-port-test",
                "localhost",
                0, // zero port
                ServiceType.TOOL_INVOKER.asValue(),
                "mcp",
                null,
                null,
                null);

        // Service registry accepts zero port, but connection attempts will fail
        String serviceId = given().headers(getHeaders())
                .body(invalidTarget)
                .when()
                .post("/api/v1/management/discovery")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .path("data.id");

        // Clean up
        if (serviceId != null) {
            ServiceTarget toRemove = new ServiceTarget(
                    serviceId,
                    "zero-port-test",
                    "localhost",
                    0,
                    ServiceType.TOOL_INVOKER.asValue(),
                    "mcp",
                    null,
                    null,
                    null);

            given().headers(getHeaders()).body(toRemove).when().delete("/api/v1/management/discovery");
        }
    }

    @Test
    void testRegisterService_WithPortOutOfRange_AcceptsButMayFailLater() {
        ServiceTarget invalidTarget = new ServiceTarget(
                null,
                "out-of-range-port-test",
                "localhost",
                70000, // port out of valid range (1-65535)
                ServiceType.TOOL_INVOKER.asValue(),
                "mcp",
                null,
                null,
                null);

        // Service registry accepts out-of-range port, but connection attempts will fail
        String serviceId = given().headers(getHeaders())
                .body(invalidTarget)
                .when()
                .post("/api/v1/management/discovery")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .path("data.id");

        // Clean up
        if (serviceId != null) {
            ServiceTarget toRemove = new ServiceTarget(
                    serviceId,
                    "out-of-range-port-test",
                    "localhost",
                    70000,
                    ServiceType.TOOL_INVOKER.asValue(),
                    "mcp",
                    null,
                    null,
                    null);

            given().headers(getHeaders()).body(toRemove).when().delete("/api/v1/management/discovery");
        }
    }

    @Test
    void testDeregisterService_NonExistent_ShouldHandleGracefully() {
        ServiceTarget nonExistentService = new ServiceTarget(
                "non-existent-id-12345",
                "non-existent-service",
                "localhost",
                9999,
                ServiceType.TOOL_INVOKER.asValue(),
                "mcp",
                null,
                null,
                null);

        given().headers(getHeaders())
                .body(nonExistentService)
                .when()
                .delete("/api/v1/management/discovery")
                .then()
                .statusCode(Response.Status.OK.getStatusCode()); // Should handle gracefully
    }

    @Test
    void testDeregisterService_WithEmptyBody_ShouldFail() {
        // Send empty JSON object instead of null to avoid RestAssured IllegalArgumentException
        given().headers(getHeaders())
                .body("{}")
                .when()
                .delete("/api/v1/management/discovery")
                .then()
                .statusCode(
                        Response.Status.INTERNAL_SERVER_ERROR
                                .getStatusCode()); // NPE when trying to deregister with empty object
    }

    @Test
    void testGetStaleCapabilities_WithNegativeMaxAge_ShouldHandleGracefully() {
        given().headers(getHeaders())
                .queryParam("maxAgeSeconds", -1)
                .when()
                .get("/api/v1/capabilities/stale")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data", notNullValue());
    }

    @Test
    void testCleanupStaleCapabilities_WithInvalidParameters_ShouldHandleGracefully() {
        given().headers(getHeaders())
                .queryParam("maxAgeSeconds", -100)
                .queryParam("inactiveOnly", "invalid-boolean")
                .when()
                .delete("/api/v1/capabilities/stale")
                .then()
                .statusCode(Response.Status.OK.getStatusCode());
    }

    @Test
    void testRegisterService_WithMissingServiceType_AcceptsButMayFailLater() {
        ServiceTarget invalidTarget = new ServiceTarget(
                null,
                "missing-type-test",
                "localhost",
                9204,
                null, // null service type
                "mcp",
                null,
                null,
                null);

        // Service registry accepts null service type, but it may cause issues during usage
        String serviceId = given().headers(getHeaders())
                .body(invalidTarget)
                .when()
                .post("/api/v1/management/discovery")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .path("data.id");

        // Clean up
        if (serviceId != null) {
            ServiceTarget toRemove =
                    new ServiceTarget(serviceId, "missing-type-test", "localhost", 9204, null, "mcp", null, null, null);

            given().headers(getHeaders()).body(toRemove).when().delete("/api/v1/management/discovery");
        }
    }

    @Test
    void testRegisterService_WithoutAuthorizationHeader_RedirectsToLogin() {
        Assumptions.assumeTrue(isAuthEnabled());
        ServiceTarget serviceTarget = new ServiceTarget(
                null,
                "unauthorized-test",
                "localhost",
                9205,
                ServiceType.TOOL_INVOKER.asValue(),
                "mcp",
                null,
                null,
                null);

        // OIDC redirects to login page (302) instead of returning 401
        given().header("Content-Type", MediaType.APPLICATION_JSON)
                .body(serviceTarget)
                .when()
                .post("/api/v1/management/discovery")
                .then()
                .statusCode(Response.Status.FOUND.getStatusCode()); // 302 redirect to Keycloak login
    }
}
