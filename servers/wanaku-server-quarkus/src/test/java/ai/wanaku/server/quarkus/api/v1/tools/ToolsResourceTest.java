package ai.wanaku.server.quarkus.api.v1.tools;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import ai.wanaku.api.types.ResourceReference;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import ai.wanaku.api.types.ToolReference;
import ai.wanaku.core.util.support.ToolsHelper;
import ai.wanaku.server.quarkus.support.TestIndexHelper;

import static ai.wanaku.core.util.support.ResourcesHelper.createResource;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@QuarkusTest
public class ToolsResourceTest {

    public static final List<ToolReference> TOOL_REFERENCES = ToolsHelper.testFixtures();

    @BeforeAll
    static void setup() throws IOException {
        File indexFile = TestIndexHelper.createToolsIndex();

        // Verify that the file exists and is not empty
        Assumptions.assumeTrue(indexFile.exists(), "Cannot test because the index file does not exist");
    }

    @Order(1)
    @Test
    public void testExposeResourceSuccessfully() {
        ToolReference.InputSchema inputSchema1 = ToolsHelper.createInputSchema(
                "http",
                Collections.singletonMap("username", ToolsHelper.createProperty("string", "A username."))
        );

        ToolReference toolReference1 = ToolsHelper.createToolReference(
                "test-tool-3",
                "This is a description of the test tool 1.",
                "https://example.com/test/tool-1",
                inputSchema1
        );

        given()
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .body(toolReference1)
                .when().post("/api/v1/tools/add")
                .then()
                .statusCode(200);
    }

    @Order(2)
    @Test
    void testList() {
        given()
                .when().get("/api/v1/tools/list")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data.size()", is(3),
                        "data[0].name", is("Tool 1"),
                        "data[0].type", is("http"),
                        "data[0].description", is("This is a description of Tool 1."));
    }

    @Order(3)
    @Test
    void testRemove() {
        given()
                .when().put("/api/v1/tools/remove?tool=test-tool-3")
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        given()
                .when().get("/api/v1/tools/list")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data.size()", is(2),
                        "data[0].name", is("Tool 1"),
                        "data[0].type", is("http"),
                        "data[0].description", is("This is a description of Tool 1."));
    }

    @Order(4)
    @Test
    void testAddAfterRemove() {
        ToolReference.InputSchema inputSchema3 = ToolsHelper.createInputSchema(
                "http",
                Collections.singletonMap("username", ToolsHelper.createProperty("string", "A username."))
        );

        ToolReference toolReference3 = ToolsHelper.createToolReference(
                "test-tool-3",
                "This is a description of the test tool 3.",
                "https://example.com/test/tool-3",
                inputSchema3
        );

        given()
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .body(toolReference3)
                .when().post("/api/v1/tools/add")
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        given()
                .when().get("/api/v1/tools/list")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data.size()", is(3),
                        "data[2].name", is("test-tool-3"),
                        "data[2].type", is("http"));
    }
}