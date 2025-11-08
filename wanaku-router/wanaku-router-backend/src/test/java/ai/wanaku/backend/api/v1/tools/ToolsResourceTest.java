package ai.wanaku.backend.api.v1.tools;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

import ai.wanaku.api.types.InputSchema;
import ai.wanaku.api.types.ToolReference;
import ai.wanaku.backend.support.TestIndexHelper;
import ai.wanaku.backend.support.WanakuKeycloakTestResource;
import ai.wanaku.backend.support.WanakuRouterTest;
import ai.wanaku.core.util.support.ToolsHelper;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.util.Collections;
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
public class ToolsResourceTest extends WanakuRouterTest {
    private static final Logger LOG = Logger.getLogger(ToolsResourceTest.class);

    private static String createdName;

    @BeforeAll
    static void setup() throws IOException {
        TestIndexHelper.deleteRecursively("target/wanaku/router");
    }

    @Order(1)
    @Test
    public void testExposeResourceSuccessfully() {
        InputSchema inputSchema1 = ToolsHelper.createInputSchema(
                "http", Collections.singletonMap("username", ToolsHelper.createProperty("string", "A username.")));

        ToolReference toolReference1 = ToolsHelper.createToolReference(
                "test-tool-1",
                "This is a description of the test tool 1.",
                "https://example.com/test/tool-1",
                inputSchema1);

        final var response = given().header("Content-Type", MediaType.APPLICATION_JSON)
                .body(toolReference1)
                .when()
                .post("/api/v1/tools");

        LOG.infof("Response: %s", response.getBody().asString());

        createdName = response.then().statusCode(200).extract().path("data.name");
    }

    @Order(2)
    @Test
    void testList() {
        given().when()
                .get("/api/v1/tools")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body(
                        "data.size()",
                        is(1),
                        "data[0].name",
                        is("test-tool-1"),
                        "data[0].type",
                        is("http"),
                        "data[0].description",
                        is("This is a description of the test tool 1."));
    }

    @Order(3)
    @Test
    void testRemove() {
        given().when().delete("/api/v1/tools/" + createdName).then().statusCode(Response.Status.OK.getStatusCode());

        given().when()
                .get("/api/v1/tools")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data.size()", is(0));
    }

    @Order(4)
    @Test
    void testAddAfterRemove() {
        InputSchema inputSchema3 = ToolsHelper.createInputSchema(
                "http", Collections.singletonMap("username", ToolsHelper.createProperty("string", "A username.")));

        ToolReference toolReference3 = ToolsHelper.createToolReference(
                "test-tool-3",
                "This is a description of the test tool 3.",
                "https://example.com/test/tool-3",
                inputSchema3);

        given().header("Content-Type", MediaType.APPLICATION_JSON)
                .body(toolReference3)
                .when()
                .post("/api/v1/tools")
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        given().when()
                .get("/api/v1/tools")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data.size()", is(1), "data[0].name", is("test-tool-3"), "data[0].type", is("http"));
    }

    @Order(5)
    @Test
    void testDeleteNonExistentTool() {
        given().when()
                .delete("/api/v1/tools/non-existent-tool")
                .then()
                .statusCode(Response.Status.NOT_FOUND.getStatusCode());
    }

    @Order(6)
    @Test
    void testUpdateTool() {
        ToolInputSchema inputSchema = createInputSchema();
        ToolReference toolReference = createToolReference(
                "update-test-tool", "Update test tool", "https://example.com/update-test", inputSchema);

        // Create the tool first
        io.restassured.response.Response createResponse = given().header("Content-Type", MediaType.APPLICATION_JSON)
                .body(toolReference)
                .when()
                .post("/api/v1/tools")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .response();

        String createdName = createResponse.jsonPath().getString("data.name");

        // Update the tool
        ToolReference updatedTool = createToolReference(
                createdName, "Updated description", "https://example.com/updated-tool", inputSchema);

        given().header("Content-Type", MediaType.APPLICATION_JSON)
                .body(updatedTool)
                .when()
                .put("/api/v1/tools/" + createdName)
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        // Verify the update
        given().when()
                .get("/api/v1/tools")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data.size()", is(1))
                .body("data[0].description", is("Updated description"))
                .body("data[0].uri", is("https://example.com/updated-tool"));
    }

    @Order(7)
    @Test
    void testUpdateNonExistentTool() {
        ToolInputSchema inputSchema = createInputSchema();
        ToolReference toolReference = createToolReference(
                "non-existent-tool", "Non-existent tool", "https://example.com/non-existent", inputSchema);

        given().header("Content-Type", MediaType.APPLICATION_JSON)
                .body(toolReference)
                .when()
                .put("/api/v1/tools/non-existent-tool")
                .then()
                .statusCode(Response.Status.NOT_FOUND.getStatusCode());
    }

    @Order(8)
    @Test
    void testUpdateToolWithMismatchedName() {
        ToolInputSchema inputSchema = createInputSchema();
        ToolReference toolReference = createToolReference(
                "mismatch-test-tool", "Mismatch test tool", "https://example.com/mismatch-test", inputSchema);

        // Create the tool first
        io.restassured.response.Response createResponse = given().header("Content-Type", MediaType.APPLICATION_JSON)
                .body(toolReference)
                .when()
                .post("/api/v1/tools")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .response();

        String createdName = createResponse.jsonPath().getString("data.name");

        // Try to update with mismatched name in payload
        ToolReference mismatchedTool = createToolReference(
                "different-name", "Updated description", "https://example.com/updated-tool", inputSchema);

        given().header("Content-Type", MediaType.APPLICATION_JSON)
                .body(mismatchedTool)
                .when()
                .put("/api/v1/tools/" + createdName)
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode());
    }
}
