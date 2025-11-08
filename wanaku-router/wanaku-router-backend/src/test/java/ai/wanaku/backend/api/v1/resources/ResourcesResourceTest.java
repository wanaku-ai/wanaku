package ai.wanaku.backend.api.v1.resources;

import static ai.wanaku.core.util.support.ResourcesHelper.createResource;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

import ai.wanaku.api.types.ResourceReference;
import ai.wanaku.backend.support.TestIndexHelper;
import ai.wanaku.backend.support.WanakuKeycloakTestResource;
import ai.wanaku.backend.support.WanakuRouterTest;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ValidatableResponse;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import org.jboss.logging.Logger;
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
public class ResourcesResourceTest extends WanakuRouterTest {
    private static final Logger LOG = Logger.getLogger(ResourcesResourceTest.class);

    private static String createdName;

    @BeforeAll
    static void setup() throws IOException {
        TestIndexHelper.deleteRecursively("target/wanaku/router");
    }

    @Order(1)
    @Test
    public void testExposeResourceSuccessfully() {
        ResourceReference resource = createResource("/tmp/resource3.jpg", "image/jpeg", "resource3.jpg");

        final io.restassured.response.Response response = given().header("Content-Type", MediaType.APPLICATION_JSON)
                .body(resource)
                .when()
                .post("/api/v1/resources");

        LOG.infof("Response: %s", response.getBody().asString());

        createdName = response.then()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .path("data.name");

        LOG.infof("Created record with name %s", createdName);
    }

    @Order(2)
    @Test
    public void testListResourcesSuccessfully() {
        given().when()
                .get("/api/v1/resources")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body(
                        "data.size()",
                        is(1),
                        "data[0].name",
                        is("resource3.jpg"),
                        "data[0].type",
                        is("image/jpeg"),
                        "data[0].description",
                        is("A sample image resource"));
    }

    @Order(3)
    @Test
    void testRemove() {
        given().when().delete("/api/v1/resources/" + createdName).then().statusCode(Response.Status.OK.getStatusCode());

        given().when()
                .get("/api/v1/resources")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data.size()", is(0));
    }

    @Order(4)
    @Test
    void testDeleteNonExistentResource() {
        given().when()
                .delete("/api/v1/resources/non-existent-resource")
                .then()
                .statusCode(Response.Status.NOT_FOUND.getStatusCode());
    }

    @Order(5)
    @Test
    void testUpdateResource() {
        // First create a resource
        ResourceReference resource = createResource("/tmp/original.jpg", "image/jpeg", "original.jpg");

        ValidatableResponse createResponse = given().header("Content-Type", MediaType.APPLICATION_JSON)
                .body(resource)
                .when()
                .post("/api/v1/resources")
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        // Extract the created resource name
        String createdName = createResponse.extract().jsonPath().getString("data.name");

        // Now update the resource
        ResourceReference updatedResource = createResource("/tmp/updated.jpg", "image/png", createdName);
        updatedResource.setDescription("Updated description");

        given().header("Content-Type", MediaType.APPLICATION_JSON)
                .body(updatedResource)
                .when()
                .put("/api/v1/resources/" + createdName)
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        // Verify the update
        given().when()
                .get("/api/v1/resources")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data.size()", is(1))
                .body("data[0].location", is("/tmp/updated.jpg"))
                .body("data[0].type", is("image/png"))
                .body("data[0].description", is("Updated description"));
    }

    @Order(6)
    @Test
    void testUpdateNonExistentResource() {
        ResourceReference resource = createResource("/tmp/test.jpg", "image/jpeg", "non-existent");

        given().header("Content-Type", MediaType.APPLICATION_JSON)
                .body(resource)
                .when()
                .put("/api/v1/resources/non-existent")
                .then()
                .statusCode(Response.Status.NOT_FOUND.getStatusCode());
    }

    @Order(7)
    @Test
    void testUpdateResourceWithMismatchedName() {
        // First create a resource
        ResourceReference resource = createResource("/tmp/original.jpg", "image/jpeg", "original.jpg");

        ValidatableResponse createResponse = given().header("Content-Type", MediaType.APPLICATION_JSON)
                .body(resource)
                .when()
                .post("/api/v1/resources")
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        String createdName = createResponse.extract().jsonPath().getString("data.name");

        // Try to update with mismatched name in payload
        ResourceReference mismatchedResource = createResource("/tmp/updated.jpg", "image/png", "different-name");

        given().header("Content-Type", MediaType.APPLICATION_JSON)
                .body(mismatchedResource)
                .when()
                .put("/api/v1/resources/" + createdName)
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode());
    }

    @Order(8)
    @Test
    void testAddAfterRemove() {
        ResourceReference resource = createResource("/tmp/resource1.jpg", "image/jpeg", "resource1.jpg");

        given().header("Content-Type", MediaType.APPLICATION_JSON)
                .body(resource)
                .when()
                .post("/api/v1/resources")
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        given().when()
                .get("/api/v1/resources")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body(
                        "data.size()",
                        is(1),
                        "data[0].name",
                        is("resource1.jpg"),
                        "data[0].type",
                        is("image/jpeg"),
                        "data[0].location",
                        is("/tmp/resource1.jpg"));
    }
}
