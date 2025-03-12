package ai.wanaku.routers;

import ai.wanaku.api.types.ToolReference;
import ai.wanaku.core.mcp.common.resolvers.Resolver;
import ai.wanaku.core.util.support.ToolsHelper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.File;
import java.util.Collections;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTestResource(MongoDBResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@QuarkusTest
public class WanakuRouterMainTest {

    @AfterAll
    static void cleanData() {
        File indexFile = new File("target/test-data/", Resolver.DEFAULT_TOOLS_INDEX_FILE_NAME);
        if (indexFile.exists()) {
            indexFile.delete();
        }
    }

    private static McpClient createClient() {
        McpTransport transport = new HttpMcpTransport.Builder()
                .sseUrl(String.format("http://localhost:%d/mcp/sse", RestAssured.port))
                .logRequests(true)
                .logResponses(true)
                .build();

        return new DefaultMcpClient.Builder()
                .transport(transport)
                .build();
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
