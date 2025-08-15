package ai.wanaku.backend.api.v1.tools;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

import ai.wanaku.api.types.InputSchema;
import ai.wanaku.api.types.ToolReference;
import ai.wanaku.backend.support.TestIndexHelper;
import ai.wanaku.core.util.support.ToolsHelper;
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

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@QuarkusTest
public class ToolsResourceTest {
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
                .post("/api/v1/tools/add");

        LOG.infof("Response: %s", response.getBody().asString());

        createdName = response.then().statusCode(200).extract().path("data.name");
    }

    @Order(2)
    @Test
    void testList() {
        given().when()
                .get("/api/v1/tools/list")
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
        given().when()
                .put("/api/v1/tools/remove?tool=" + createdName)
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        given().when()
                .get("/api/v1/tools/list")
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
                .post("/api/v1/tools/add")
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        given().when()
                .get("/api/v1/tools/list")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data.size()", is(1), "data[0].name", is("test-tool-3"), "data[0].type", is("http"));
    }
}
