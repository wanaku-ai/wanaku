package ai.wanaku.routers;

import ai.wanaku.api.types.InputSchema;
import ai.wanaku.core.mcp.client.ClientUtil;
import java.io.File;
import java.util.Collections;
import java.util.List;

import ai.wanaku.api.types.ToolReference;
import ai.wanaku.core.util.support.ToolsHelper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIf;
import org.testcontainers.DockerClientFactory;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTestResource(ValkeyResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@QuarkusTest
@EnabledIf(value = "dockerCheck", disabledReason = "Docker environment is not available")
@Disabled("Does not work")
public class WanakuRouterMainTest {
    private static final String DEFAULT_TOOLS_INDEX_FILE_NAME = "tools.json";

    static boolean dockerCheck() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    @AfterAll
    static void cleanData() {
        File indexFile = new File("target/test-data/", DEFAULT_TOOLS_INDEX_FILE_NAME);
        if (indexFile.exists()) {
            indexFile.delete();
        }
    }

    private static McpClient createClient() {
        return ClientUtil.createClient(String.format("http://localhost:%d/mcp/sse", RestAssured.port));
    }

    @Order(1)
    @Test
    void testListEmpty() throws Exception {
        try (McpClient mcpClient = createClient()) {

            List<ToolSpecification> toolSpecifications = mcpClient.listTools();
            Assertions.assertNotNull(toolSpecifications);
            Assertions.assertEquals(0, toolSpecifications.size());
        }
    }

    @Order(2)
    @Test
    public void testExposeResourceSuccessfully() throws Exception {
        InputSchema inputSchema1 = ToolsHelper.createInputSchema(
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

        try (McpClient mcpClient = createClient()) {
            List<ToolSpecification> toolSpecifications = mcpClient.listTools();
            Assertions.assertNotNull(toolSpecifications);
            Assertions.assertEquals(1, toolSpecifications.size());
        }

    }

    @Order(3)
    @Test
    void testRemove() throws Exception {
        given()
                .when().put("/api/v1/tools/remove?tool=test-tool-3")
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        given()
                .when().get("/api/v1/tools/list")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data.size()", is(0));


        try (McpClient mcpClient = createClient()) {
            List<ToolSpecification> toolSpecifications = mcpClient.listTools();
            Assertions.assertNotNull(toolSpecifications);
            Assertions.assertEquals(0, toolSpecifications.size());
        }
    }
}
