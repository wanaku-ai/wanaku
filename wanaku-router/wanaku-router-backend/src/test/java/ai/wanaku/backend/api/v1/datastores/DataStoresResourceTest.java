package ai.wanaku.backend.api.v1.datastores;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import ai.wanaku.api.types.DataStore;
import ai.wanaku.backend.support.WanakuKeycloakTestResource;
import ai.wanaku.backend.support.WanakuRouterTest;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Test class for DataStoresResource REST API.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@QuarkusTest
@QuarkusTestResource(value = WanakuKeycloakTestResource.class, restrictToAnnotatedClass = true)
public class DataStoresResourceTest extends WanakuRouterTest {
    private static final Logger LOG = Logger.getLogger(DataStoresResourceTest.class);

    private static String testId;
    private static final String TEST_NAME = "test-datastore";
    private static final String TEST_DATA = "Sample test data for REST API";

    @Order(1)
    @Test
    public void testAdd() {
        DataStore dataStore = new DataStore();
        dataStore.setName(TEST_NAME);
        dataStore.setData(TEST_DATA);

        String response = given().header("Content-Type", MediaType.APPLICATION_JSON)
                .body(dataStore)
                .when()
                .post("/api/v1/data-store/add")
                .then()
                .statusCode(200)
                .body("data.name", equalTo(TEST_NAME))
                .body("data.data", equalTo(TEST_DATA))
                .body("data.id", notNullValue())
                .extract()
                .path("data.id");

        testId = response;
        LOG.infof("Created data store with ID: %s", testId);
    }

    @Order(2)
    @Test
    void testList() {
        given().when()
                .get("/api/v1/data-store/list")
                .then()
                .statusCode(200)
                .body("data", notNullValue())
                .body("data.size()", greaterThanOrEqualTo(1));
    }

    @Order(3)
    @Test
    void testGetById() {
        given().queryParam("id", testId)
                .when()
                .get("/api/v1/data-store/get")
                .then()
                .statusCode(200)
                .body("data.id", equalTo(testId))
                .body("data.name", equalTo(TEST_NAME))
                .body("data.data", equalTo(TEST_DATA));
    }

    @Order(4)
    @Test
    void testGetByName() {
        given().queryParam("name", TEST_NAME)
                .when()
                .get("/api/v1/data-store/get")
                .then()
                .statusCode(200)
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

        given().header("Content-Type", MediaType.APPLICATION_JSON)
                .body(dataStore)
                .when()
                .put("/api/v1/data-store/update")
                .then()
                .statusCode(200);

        // Verify the update by fetching the record
        given().queryParam("id", testId)
                .when()
                .get("/api/v1/data-store/get")
                .then()
                .statusCode(200)
                .body("data.id", equalTo(testId))
                .body("data.data", equalTo("Updated test data content"));
    }

    @Order(6)
    @Test
    void testGetWithoutParameters() {
        given().when().get("/api/v1/data-store/get").then().statusCode(500); // Should throw WanakuException
    }

    @Order(7)
    @Test
    void testGetByIdNotFound() {
        given().queryParam("id", "non-existent-id")
                .when()
                .get("/api/v1/data-store/get")
                .then()
                .statusCode(500); // Should throw WanakuException for not found
    }

    @Order(8)
    @Test
    void testRemoveById() {
        given().queryParam("id", testId)
                .when()
                .delete("/api/v1/data-store/remove")
                .then()
                .statusCode(200);

        // Verify deletion
        given().queryParam("id", testId)
                .when()
                .get("/api/v1/data-store/get")
                .then()
                .statusCode(500); // Should fail to find
    }

    @Order(9)
    @Test
    void testAddMultipleWithSameName() {
        // Add first entry
        DataStore dataStore1 = new DataStore();
        dataStore1.setName("duplicate-test");
        dataStore1.setData("First entry");

        given().header("Content-Type", MediaType.APPLICATION_JSON)
                .body(dataStore1)
                .when()
                .post("/api/v1/data-store/add")
                .then()
                .statusCode(200);

        // Add second entry with same name
        DataStore dataStore2 = new DataStore();
        dataStore2.setName("duplicate-test");
        dataStore2.setData("Second entry");

        given().header("Content-Type", MediaType.APPLICATION_JSON)
                .body(dataStore2)
                .when()
                .post("/api/v1/data-store/add")
                .then()
                .statusCode(200);

        // Get by name should return both
        given().queryParam("name", "duplicate-test")
                .when()
                .get("/api/v1/data-store/get")
                .then()
                .statusCode(200)
                .body("data.size()", equalTo(2));
    }

    @Order(10)
    @Test
    void testRemoveByName() {
        given().queryParam("name", "duplicate-test")
                .when()
                .delete("/api/v1/data-store/remove")
                .then()
                .statusCode(200);

        // Verify deletion
        given().queryParam("name", "duplicate-test")
                .when()
                .get("/api/v1/data-store/get")
                .then()
                .statusCode(200)
                .body("data.size()", equalTo(0));
    }

    @Order(11)
    @Test
    void testRemoveNotFound() {
        given().queryParam("id", "non-existent-id")
                .when()
                .delete("/api/v1/data-store/remove")
                .then()
                .statusCode(404); // Not found
    }
}
