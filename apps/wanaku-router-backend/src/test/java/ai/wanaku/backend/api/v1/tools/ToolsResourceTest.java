package ai.wanaku.backend.api.v1.tools;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response.Status;

import java.util.Collections;
import org.jboss.logging.Logger;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import ai.wanaku.backend.support.TestIndexHelper;
import ai.wanaku.backend.support.WanakuKeycloakTestResource;
import ai.wanaku.backend.support.WanakuRouterTest;
import ai.wanaku.capabilities.sdk.api.types.InputSchema;
import ai.wanaku.capabilities.sdk.api.types.ToolReference;
import ai.wanaku.core.util.support.ToolsHelper;

import static ai.wanaku.test.assertions.WanakuAssertions.assertHttpStatus;
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
public class ToolsResourceTest extends WanakuRouterTest {
    private static final Logger LOG = Logger.getLogger(ToolsResourceTest.class);

    private static String createdName;

    @BeforeAll
    static void setup() {
        TestIndexHelper.clearAllCaches();
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

        final Response response = given().header("Content-Type", MediaType.APPLICATION_JSON)
                .body(toolReference1)
                .when()
                .post("/api/v1/tools");

        LOG.infof("Response: %s", response.getBody().asString());

        assertHttpStatus(response, Status.OK.getStatusCode());
        createdName = response.then().extract().path("data.name");
    }

    @Order(2)
    @Test
    void testList() {
        Response response = given().when().get("/api/v1/tools");
        assertHttpStatus(response, Status.OK.getStatusCode());
        response.then()
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
        Response deleteResponse = given().when().delete("/api/v1/tools/" + createdName);
        assertHttpStatus(deleteResponse, Status.OK.getStatusCode());

        Response listResponse = given().when().get("/api/v1/tools");
        assertHttpStatus(listResponse, Status.OK.getStatusCode());
        listResponse.then().body("data.size()", is(0));
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

        Response createResponse = given().header("Content-Type", MediaType.APPLICATION_JSON)
                .body(toolReference3)
                .when()
                .post("/api/v1/tools");
        assertHttpStatus(createResponse, Status.OK.getStatusCode());

        Response listResponse = given().when().get("/api/v1/tools");
        assertHttpStatus(listResponse, Status.OK.getStatusCode());
        listResponse.then().body("data.size()", is(1), "data[0].name", is("test-tool-3"), "data[0].type", is("http"));
    }
}
