package ai.wanaku.backend.api.v1.resources;

import jakarta.ws.rs.core.Response.Status;

import org.jboss.logging.Logger;
import io.restassured.response.Response;
import ai.wanaku.backend.support.TestIndexHelper;
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

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public abstract class AbstractResourcesResourceTest extends WanakuRouterTest {
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

        final Response response =
                given().headers(getHeaders()).body(resource).when().post("/api/v1/resources");

        LOG.infof("Response: %s", response.getBody().asString());

        assertHttpStatus(response, Status.OK.getStatusCode());
        createdName = response.then().extract().path("data.name");

        LOG.infof("Created record with name %s", createdName);
    }

    @Order(2)
    @Test
    public void testListResourcesSuccessfully() {
        Response response = given().headers(getHeaders()).when().get("/api/v1/resources");
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
        Response deleteResponse = given().headers(getHeaders()).when().delete("/api/v1/resources/" + createdName);
        assertHttpStatus(deleteResponse, Status.OK.getStatusCode());

        Response listResponse = given().headers(getHeaders()).when().get("/api/v1/resources");
        assertHttpStatus(listResponse, Status.OK.getStatusCode());
        listResponse.then().body("data.size()", is(0));
    }

    @Order(4)
    @Test
    void testAddAfterRemove() {
        ResourceReference resource = createResource("/tmp/resource1.jpg", "image/jpeg", "resource1.jpg");

        Response createResponse =
                given().headers(getHeaders()).body(resource).when().post("/api/v1/resources");
        assertHttpStatus(createResponse, Status.OK.getStatusCode());

        Response listResponse = given().headers(getHeaders()).when().get("/api/v1/resources");
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
        Response response = given().headers(getHeaders())
                .body("{\"configurationData\":\"token=123\"}")
                .when()
                .post("/api/v1/resources/payloads");
        assertHttpStatus(response, Status.INTERNAL_SERVER_ERROR.getStatusCode());
        response.then().body("error.message", containsString("The 'payload' is required for this request"));
    }

    @Order(6)
    @Test
    void testExposeWithPayloadRejectsMissingPayloadName() {
        Response response = given().headers(getHeaders())
                .body("{\"payload\":{\"location\":\"/tmp/nameless.txt\",\"type\":\"text/plain\"}}")
                .when()
                .post("/api/v1/resources/payloads");
        assertHttpStatus(response, Status.INTERNAL_SERVER_ERROR.getStatusCode());
        response.then().body("error.message", containsString("The 'payload.name' is required for this request"));
    }
}
