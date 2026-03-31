package ai.wanaku.backend.api.v1.datastores;

import jakarta.ws.rs.core.MediaType;

import java.io.IOException;
import java.util.List;
import org.jboss.logging.Logger;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import io.restassured.response.Response;
import ai.wanaku.backend.support.WanakuKeycloakTestResource;
import ai.wanaku.backend.support.WanakuRouterTest;
import ai.wanaku.capabilities.sdk.api.types.DataStore;

import static ai.wanaku.test.assertions.WanakuAssertions.assertHttpStatus;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.DisabledIf;

/**
 * Test class for DataStoresResource REST API.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@QuarkusTest
@QuarkusTestResource(value = WanakuKeycloakTestResource.class, restrictToAnnotatedClass = true)
@DisabledIf(value = "isUnsupportedOSOnGithub", disabledReason = "Does not run on macOS or Windows on GitHub")
public class DataStoresResourceTest extends WanakuRouterTest {
    private static final Logger LOG = Logger.getLogger(DataStoresResourceTest.class);

    private static String testId;
    private static final String TEST_NAME = "test-datastore";
    private static final String TEST_DATA = "Sample test data for REST API";

    private static KeycloakTestClient keycloakClient;

    @BeforeAll
    static void setup() throws IOException {
        keycloakClient = new KeycloakTestClient();
    }

    private String getAccessToken() {
        return keycloakClient.getRealmClientAccessToken("wanaku", "wanaku-service", "secret");
    }

    @Order(1)
    @Test
    public void testAdd() {
        final String accessToken = getAccessToken();
        Assertions.assertNotNull(accessToken);

        DataStore dataStore = new DataStore();
        dataStore.setName(TEST_NAME);
        dataStore.setData(TEST_DATA);

        Response response = given().header("Content-Type", MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(dataStore)
                .when()
                .post("/api/v1/data-store");
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
        final String accessToken = getAccessToken();
        Assertions.assertNotNull(accessToken);

        Response response =
                given().when().header("Authorization", "Bearer " + accessToken).get("/api/v1/data-store");
        assertHttpStatus(response, 200);
        response.then().body("data", notNullValue()).body("data.size()", greaterThanOrEqualTo(1));
    }

    @Order(3)
    @Test
    void testGetById() {
        final String accessToken = getAccessToken();
        Assertions.assertNotNull(accessToken);

        Response response =
                given().when().header("Authorization", "Bearer " + accessToken).get("/api/v1/data-store/" + testId);
        assertHttpStatus(response, 200);
        response.then()
                .body("data.id", equalTo(testId))
                .body("data.name", equalTo(TEST_NAME))
                .body("data.data", equalTo(TEST_DATA));
    }

    @Order(4)
    @Test
    void testGetByName() {
        final String accessToken = getAccessToken();
        Assertions.assertNotNull(accessToken);

        Response response = given().queryParam("name", TEST_NAME)
                .when()
                .header("Authorization", "Bearer " + accessToken)
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
        final String accessToken = getAccessToken();
        Assertions.assertNotNull(accessToken);

        DataStore dataStore = new DataStore();
        dataStore.setId(testId);
        dataStore.setName(TEST_NAME);
        dataStore.setData("Updated test data content");

        Response updateResponse = given().header("Content-Type", MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(dataStore)
                .when()
                .put("/api/v1/data-store");
        assertHttpStatus(updateResponse, 200);

        // Verify the update by fetching the record
        Response getResponse =
                given().when().header("Authorization", "Bearer " + accessToken).get("/api/v1/data-store/" + testId);
        assertHttpStatus(getResponse, 200);
        getResponse.then().body("data.id", equalTo(testId)).body("data.data", equalTo("Updated test data content"));
    }

    @Order(6)
    @Test
    void testListAllWithoutQueryParameters() {
        final String accessToken = getAccessToken();
        Assertions.assertNotNull(accessToken);

        Response response =
                given().when().header("Authorization", "Bearer " + accessToken).get("/api/v1/data-store");
        assertHttpStatus(response, 200);
        response.then()
                .body("data", notNullValue())
                .body("data", instanceOf(List.class))
                .body("data.size()", greaterThanOrEqualTo(1));
    }

    @Order(7)
    @Test
    void testGetByIdNotFound() {
        final String accessToken = getAccessToken();
        Assertions.assertNotNull(accessToken);

        Response response = given().when()
                .header("Authorization", "Bearer " + accessToken)
                .get("/api/v1/data-store/non-existent-id");
        assertHttpStatus(response, 404); // Should throw WanakuException for not found
    }

    @Order(8)
    @Test
    void testRemoveById() {
        final String accessToken = getAccessToken();
        Assertions.assertNotNull(accessToken);

        Response deleteResponse =
                given().when().header("Authorization", "Bearer " + accessToken).delete("/api/v1/data-store/" + testId);
        assertHttpStatus(deleteResponse, 200);

        // Verify deletion
        Response getResponse =
                given().when().header("Authorization", "Bearer " + accessToken).get("/api/v1/data-store/" + testId);
        assertHttpStatus(getResponse, 404); // Should fail to find
    }

    @Order(9)
    @Test
    void testAddMultipleWithSameName() {
        final String accessToken = getAccessToken();
        Assertions.assertNotNull(accessToken);

        // Add first entry
        DataStore dataStore1 = new DataStore();
        dataStore1.setName("duplicate-test");
        dataStore1.setData("First entry");

        Response createResponse1 = given().header("Content-Type", MediaType.APPLICATION_JSON)
                .body(dataStore1)
                .when()
                .header("Authorization", "Bearer " + accessToken)
                .post("/api/v1/data-store");
        assertHttpStatus(createResponse1, 200);

        // Add second entry with same name — should be rejected as duplicate
        DataStore dataStore2 = new DataStore();
        dataStore2.setName("duplicate-test");
        dataStore2.setData("Second entry");

        Response createResponse2 = given().header("Content-Type", MediaType.APPLICATION_JSON)
                .body(dataStore2)
                .when()
                .header("Authorization", "Bearer " + accessToken)
                .post("/api/v1/data-store");
        assertHttpStatus(createResponse2, 409);

        // Get by name should return only the first entry
        Response listResponse = given().queryParam("name", "duplicate-test")
                .when()
                .header("Authorization", "Bearer " + accessToken)
                .get("/api/v1/data-store");
        assertHttpStatus(listResponse, 200);
        listResponse.then().body("data.size()", equalTo(1)).body("data[0].data", equalTo("First entry"));
    }

    @Order(10)
    @Test
    void testRemoveByName() {
        final String accessToken = getAccessToken();
        Assertions.assertNotNull(accessToken);

        Response deleteResponse = given().queryParam("name", "duplicate-test")
                .when()
                .header("Authorization", "Bearer " + accessToken)
                .delete("/api/v1/data-store");
        assertHttpStatus(deleteResponse, 200);

        // Verify deletion
        Response getResponse = given().queryParam("name", "duplicate-test")
                .when()
                .header("Authorization", "Bearer " + accessToken)
                .get("/api/v1/data-store");
        assertHttpStatus(getResponse, 404);
    }

    @Order(11)
    @Test
    void testRemoveNotFound() {
        final String accessToken = getAccessToken();
        Assertions.assertNotNull(accessToken);

        Response response = given().when()
                .header("Authorization", "Bearer " + accessToken)
                .delete("/api/v1/data-store/non-existent-id");
        assertHttpStatus(response, 404); // Not found
    }
}
