package ai.wanaku.server.quarkus.api.v1.tools;

import ai.wanaku.api.types.ToolReference;
import ai.wanaku.core.mcp.ToolReferenceEntity;
import ai.wanaku.core.util.support.ToolsHelper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoDatabase;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.bson.Document;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.wanaku.server.quarkus.support.MongoDBResource;

import java.util.Collections;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTestResource(MongoDBResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@QuarkusTest
public class ToolsResourceTest {

    @BeforeAll
    public static void beforeAll() throws JsonProcessingException {
        MongoDatabase mongoDatabase = MongoDBResource.getDatabase();

        mongoDatabase.createCollection(ToolReferenceEntity.COLLECTION_NAME);

        ObjectMapper mapper = new ObjectMapper();
        for (ToolReference toolReference : ToolsHelper.testFixtures()) {
            Document d = Document.parse(mapper.writeValueAsString(toolReference));
            d.append("_id", toolReference.getName());

            mongoDatabase.getCollection(ToolReferenceEntity.COLLECTION_NAME).insertOne(d);
        }
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
    void testRemoveNonExistentTool() {
        given()
                .when().put("/api/v1/tools/remove?tool=test-tool-non-existent-tool")
                .then()
                .statusCode(Response.Status.NOT_FOUND.getStatusCode());
    }
}