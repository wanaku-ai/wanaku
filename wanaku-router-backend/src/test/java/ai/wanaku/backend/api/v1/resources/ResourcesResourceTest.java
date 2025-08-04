package ai.wanaku.backend.api.v1.resources;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import ai.wanaku.api.types.ResourceReference;
import ai.wanaku.backend.support.TestIndexHelper;
import io.quarkus.test.junit.QuarkusTest;
import java.io.IOException;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static ai.wanaku.core.util.support.ResourcesHelper.createResource;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@QuarkusTest
public class ResourcesResourceTest {
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

        final io.restassured.response.Response response = given()
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .body(resource)
                .when().post("/api/v1/resources/expose");

        LOG.infof("Response: %s", response.getBody().asString());

        createdName = response
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .path("data.name");

        LOG.infof("Created record with name %s", createdName);
    }

    @Order(2)
    @Test
    public void testListResourcesSuccessfully() {
        given()
                .when().get("/api/v1/resources/list")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data.size()", is(1),
                        "data[0].name", is("resource3.jpg"),
                        "data[0].type", is("image/jpeg"),
                        "data[0].description", is("A sample image resource"));
    }

    @Order(3)
    @Test
    void testRemove() {
        given()
                .when().put("/api/v1/resources/remove?resource=" + createdName)
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        given()
                .when().get("/api/v1/resources/list")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data.size()", is(0));
    }

    @Order(4)
    @Test
    void testAddAfterRemove() {
        ResourceReference resource = createResource("/tmp/resource1.jpg", "image/jpeg", "resource1.jpg");

        given()
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .body(resource)
                .when().post("/api/v1/resources/expose")
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        given()
                .when().get("/api/v1/resources/list")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data.size()", is(1),
                        "data[0].name", is("resource1.jpg"),
                        "data[0].type", is("image/jpeg"),
                        "data[0].location", is("/tmp/resource1.jpg"));
    }
}