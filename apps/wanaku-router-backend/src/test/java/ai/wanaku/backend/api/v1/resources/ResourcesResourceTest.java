package ai.wanaku.backend.api.v1.resources;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response.Status;

import org.jboss.logging.Logger;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import ai.wanaku.backend.support.TestIndexHelper;
import ai.wanaku.backend.support.WanakuKeycloakTestResource;
import ai.wanaku.backend.support.WanakuRouterTest;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;

import static ai.wanaku.core.util.support.ResourcesHelper.createResource;
import static ai.wanaku.test.assertions.WanakuAssertions.assertHttpStatus;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
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
public class ResourcesResourceTest extends WanakuRouterTest {
    private static final Logger LOG = Logger.getLogger(ResourcesResourceTest.class);

    private static String createdName;

    @BeforeAll
    static void setup() {
        TestIndexHelper.clearAllCaches();
    }

    @Order(1)
    @Test
    public void testExposeResourceSuccessfully() {
        ResourceReference resource = createResource("/tmp/resource3.jpg", "image/jpeg", "resource3.jpg");

        final Response response = given().header("Content-Type", MediaType.APPLICATION_JSON)
                .body(resource)
                .when()
                .post("/api/v1/resources");

        LOG.infof("Response: %s", response.getBody().asString());

        assertHttpStatus(response, Status.OK.getStatusCode());
        createdName = response.then().extract().path("data.name");

        LOG.infof("Created record with name %s", createdName);
    }

    @Order(2)
    @Test
    public void testListResourcesSuccessfully() {
        Response response = given().when().get("/api/v1/resources");
        assertHttpStatus(response, Status.OK.getStatusCode());
        response.then()
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
        Response deleteResponse = given().when().delete("/api/v1/resources/" + createdName);
        assertHttpStatus(deleteResponse, Status.OK.getStatusCode());

        Response listResponse = given().when().get("/api/v1/resources");
        assertHttpStatus(listResponse, Status.OK.getStatusCode());
        listResponse.then().body("data.size()", is(0));
    }

    @Order(4)
    @Test
    void testAddAfterRemove() {
        ResourceReference resource = createResource("/tmp/resource1.jpg", "image/jpeg", "resource1.jpg");

        Response createResponse = given().header("Content-Type", MediaType.APPLICATION_JSON)
                .body(resource)
                .when()
                .post("/api/v1/resources");
        assertHttpStatus(createResponse, Status.OK.getStatusCode());

        Response listResponse = given().when().get("/api/v1/resources");
        assertHttpStatus(listResponse, Status.OK.getStatusCode());
        listResponse
                .then()
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

    @Order(5)
    @Test
    void testExposeWithPayloadRejectsMissingPayload() {
        Response response = given().header("Content-Type", MediaType.APPLICATION_JSON)
                .body("{\"configurationData\":\"token=123\"}")
                .when()
                .post("/api/v1/resources/payloads");
        assertHttpStatus(response, Status.INTERNAL_SERVER_ERROR.getStatusCode());
        response.then().body("error.message", containsString("The 'payload' is required for this request"));
    }

    @Order(6)
    @Test
    void testExposeWithPayloadRejectsMissingPayloadName() {
        Response response = given().header("Content-Type", MediaType.APPLICATION_JSON)
                .body("{\"payload\":{\"location\":\"/tmp/nameless.txt\",\"type\":\"text/plain\"}}")
                .when()
                .post("/api/v1/resources/payloads");
        assertHttpStatus(response, Status.INTERNAL_SERVER_ERROR.getStatusCode());
        response.then().body("error.message", containsString("The 'payload.name' is required for this request"));
    }
}
