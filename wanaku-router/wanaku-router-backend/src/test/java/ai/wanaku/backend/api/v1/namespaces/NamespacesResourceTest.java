package ai.wanaku.backend.api.v1.namespaces;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.jboss.logging.Logger;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import ai.wanaku.backend.support.TestIndexHelper;
import ai.wanaku.backend.support.WanakuKeycloakTestResource;
import ai.wanaku.backend.support.WanakuRouterTest;
import ai.wanaku.capabilities.sdk.api.types.Namespace;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

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
public class NamespacesResourceTest extends WanakuRouterTest {
    private static final Logger LOG = Logger.getLogger(NamespacesResourceTest.class);
    private static final String TEST_LABEL_KEY = "testsuite";
    private static final String TEST_LABEL_VALUE = "namespaces";

    private static String createdId;
    private static String createdPath;
    private static String staleId;
    private static String stalePath;

    @BeforeAll
    static void setup() {
        TestIndexHelper.clearAllCaches();
    }

    @Order(1)
    @Test
    public void testCreateNamespace() {
        Namespace namespace = new Namespace();
        createdPath = "test-ns-" + System.currentTimeMillis();
        namespace.setPath(createdPath);
        Map<String, String> labels = new HashMap<>();
        labels.put(TEST_LABEL_KEY, TEST_LABEL_VALUE);
        namespace.setLabels(labels);

        final io.restassured.response.Response response = given().header("Content-Type", MediaType.APPLICATION_JSON)
                .body(namespace)
                .when()
                .post("/api/v1/namespaces");

        LOG.infof("Response: %s", response.getBody().asString());

        createdId = response.then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data.path", is(createdPath))
                .body("data.labels." + TEST_LABEL_KEY, is(TEST_LABEL_VALUE))
                .body("data.labels.'wanaku.io/preallocated'", is("true"))
                .extract()
                .path("data.id");
    }

    @Order(2)
    @Test
    public void testListNamespacesByLabel() {
        given().queryParam("labelFilter", TEST_LABEL_KEY + "=" + TEST_LABEL_VALUE)
                .when()
                .get("/api/v1/namespaces")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body(String.format("data.find { it.id == '%s' }.path", createdId), is(createdPath));
    }

    @Order(3)
    @Test
    public void testGetNamespaceById() {
        given().when()
                .get("/api/v1/namespaces/" + createdId)
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data.id", is(createdId))
                .body("data.path", is(createdPath));
    }

    @Order(4)
    @Test
    public void testUpdateNamespace() {
        Namespace namespace = new Namespace();
        namespace.setId(createdId);
        namespace.setPath(createdPath);
        namespace.setName("allocated");
        Map<String, String> labels = new HashMap<>();
        labels.put(TEST_LABEL_KEY, TEST_LABEL_VALUE);
        labels.put("wanaku.io/preallocated", "true");
        namespace.setLabels(labels);

        given().header("Content-Type", MediaType.APPLICATION_JSON)
                .body(namespace)
                .when()
                .put("/api/v1/namespaces/" + createdId)
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        given().when()
                .get("/api/v1/namespaces/" + createdId)
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data.name", is("allocated"));
    }

    @Order(5)
    @Test
    public void testDeleteNamespace() {
        given().when().delete("/api/v1/namespaces/" + createdId).then().statusCode(Response.Status.OK.getStatusCode());

        given().queryParam("labelFilter", TEST_LABEL_KEY + "=" + TEST_LABEL_VALUE)
                .when()
                .get("/api/v1/namespaces")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data.size()", is(0));
    }

    @Order(6)
    @Test
    public void testStaleNamespaceCleanup() {
        Namespace namespace = new Namespace();
        stalePath = "test-stale-ns-" + System.currentTimeMillis();
        namespace.setPath(stalePath);
        Map<String, String> labels = new HashMap<>();
        labels.put("testsuite", "stale");
        labels.put("wanaku.io/expires-at", String.valueOf(Instant.now().getEpochSecond() - 10));
        namespace.setLabels(labels);

        final io.restassured.response.Response response = given().header("Content-Type", MediaType.APPLICATION_JSON)
                .body(namespace)
                .when()
                .post("/api/v1/namespaces");

        staleId = response.then()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .path("data.id");

        given().queryParam("maxAgeSeconds", 604800)
                .when()
                .get("/api/v1/namespaces/stale")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body(String.format("data.find { it.id == '%s' }.path", staleId), is(stalePath));

        given().queryParam("maxAgeSeconds", 0)
                .when()
                .delete("/api/v1/namespaces/stale")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data", is(1));

        given().queryParam("maxAgeSeconds", 604800)
                .when()
                .get("/api/v1/namespaces/stale")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body(String.format("data.find { it.id == '%s' }", staleId), is(null));
    }
}
