package ai.wanaku.backend.api.v1.datastores;

import java.util.List;
import org.jboss.logging.Logger;
import io.restassured.response.Response;
import ai.wanaku.backend.support.WanakuRouterTest;
import ai.wanaku.capabilities.sdk.api.types.DataStore;

import static ai.wanaku.test.assertions.WanakuAssertions.assertHttpStatus;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public abstract class AbstractDataStoresResourceTest extends WanakuRouterTest {
    private static final Logger LOG = Logger.getLogger(AbstractDataStoresResourceTest.class);

    private static String testId;
    private static final String TEST_NAME = "test-datastore";
    private static final String TEST_DATA = "Sample test data for REST API";

    @Order(1)
    @Test
    public void testAdd() {
        DataStore dataStore = new DataStore();
        dataStore.setName(TEST_NAME);
        dataStore.setData(TEST_DATA);

        Response response = given().headers(getHeaders()).body(dataStore).when().post("/api/v1/data-store");
        assertHttpStatus(response, 200);
        String responseId = response.then()
                .body("data.name", equalTo(TEST_NAME))
                .body("data.data", equalTo(TEST_DATA))
                .body("data.id", notNullValue())
                .extract()
                .path("data.id");

        testId = responseId;
        LOG.infof("Created data store with ID: %s", testId);
    }

    @Order(2)
    @Test
    void testList() {
        Response response = given().headers(getHeaders()).when().get("/api/v1/data-store");
        assertHttpStatus(response, 200);
        response.then().body("data", notNullValue()).body("data.size()", greaterThanOrEqualTo(1));
    }

    @Order(3)
    @Test
    void testGetById() {
        Response response = given().headers(getHeaders()).when().get("/api/v1/data-store/" + testId);
        assertHttpStatus(response, 200);
        response.then()
                .body("data.id", equalTo(testId))
                .body("data.name", equalTo(TEST_NAME))
                .body("data.data", equalTo(TEST_DATA));
    }

    @Order(4)
    @Test
    void testGetByName() {
        Response response = given().headers(getHeaders())
                .queryParam("name", TEST_NAME)
                .when()
                .get("/api/v1/data-store");
        assertHttpStatus(response, 200);
        response.then()
                .body("data", notNullValue())
                .body("data.size()", greaterThanOrEqualTo(1))
                .body("data[0].name", equalTo(TEST_NAME));
    }

    @Order(5)
    @Test
    void testUpdate() {
        DataStore dataStore = new DataStore();
        dataStore.setId(testId);
        dataStore.setName(TEST_NAME);
        dataStore.setData("Updated test data content");

        Response updateResponse =
                given().headers(getHeaders()).body(dataStore).when().put("/api/v1/data-store");
        assertHttpStatus(updateResponse, 200);

        Response getResponse = given().headers(getHeaders()).when().get("/api/v1/data-store/" + testId);
        assertHttpStatus(getResponse, 200);
        getResponse.then().body("data.id", equalTo(testId)).body("data.data", equalTo("Updated test data content"));
    }

    @Order(6)
    @Test
    void testListAllWithoutQueryParameters() {
        Response response = given().headers(getHeaders()).when().get("/api/v1/data-store");
        assertHttpStatus(response, 200);
        response.then()
                .body("data", notNullValue())
                .body("data", instanceOf(List.class))
                .body("data.size()", greaterThanOrEqualTo(1));
    }

    @Order(7)
    @Test
    void testGetByIdNotFound() {
        Response response = given().headers(getHeaders()).when().get("/api/v1/data-store/non-existent-id");
        assertHttpStatus(response, 404);
    }

    @Order(8)
    @Test
    void testRemoveById() {
        Response deleteResponse = given().headers(getHeaders()).when().delete("/api/v1/data-store/" + testId);
        assertHttpStatus(deleteResponse, 200);

        Response getResponse = given().headers(getHeaders()).when().get("/api/v1/data-store/" + testId);
        assertHttpStatus(getResponse, 404);
    }

    @Order(9)
    @Test
    void testAddMultipleWithSameName() {
        DataStore dataStore1 = new DataStore();
        dataStore1.setName("duplicate-test");
        dataStore1.setData("First entry");

        Response createResponse1 =
                given().headers(getHeaders()).body(dataStore1).when().post("/api/v1/data-store");
        assertHttpStatus(createResponse1, 200);

        DataStore dataStore2 = new DataStore();
        dataStore2.setName("duplicate-test");
        dataStore2.setData("Second entry");

        Response createResponse2 =
                given().headers(getHeaders()).body(dataStore2).when().post("/api/v1/data-store");
        assertHttpStatus(createResponse2, 409);

        Response listResponse = given().headers(getHeaders())
                .queryParam("name", "duplicate-test")
                .when()
                .get("/api/v1/data-store");
        assertHttpStatus(listResponse, 200);
        listResponse.then().body("data.size()", equalTo(1)).body("data[0].data", equalTo("First entry"));
    }

    @Order(10)
    @Test
    void testRemoveByName() {
        Response deleteResponse = given().headers(getHeaders())
                .queryParam("name", "duplicate-test")
                .when()
                .delete("/api/v1/data-store");
        assertHttpStatus(deleteResponse, 200);

        Response getResponse = given().headers(getHeaders())
                .queryParam("name", "duplicate-test")
                .when()
                .get("/api/v1/data-store");
        assertHttpStatus(getResponse, 404);
    }

    @Order(11)
    @Test
    void testRemoveNotFound() {
        Response response = given().headers(getHeaders()).when().delete("/api/v1/data-store/non-existent-id");
        assertHttpStatus(response, 404);
    }
}
