package ai.wanaku.server.quarkus.api.v1.resources;

import ai.wanaku.api.types.ResourceReference;
import ai.wanaku.core.mcp.ResourceReferenceEntity;
import ai.wanaku.core.util.support.ResourcesHelper;
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

import static ai.wanaku.core.util.support.ResourcesHelper.createResource;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTestResource(MongoDBResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@QuarkusTest
public class ResourcesResourceTest {

    @BeforeAll
    public static void beforeAll() throws JsonProcessingException {
        MongoDatabase mongoDatabase = MongoDBResource.getDatabase();

        mongoDatabase.createCollection(ResourceReferenceEntity.COLLECTION_NAME);

        ObjectMapper mapper = new ObjectMapper();
        for (ResourceReference resourceReference : ResourcesHelper.testFixtures()) {
            Document d = Document.parse(mapper.writeValueAsString(resourceReference));
            d.append("_id", resourceReference.getName());

            mongoDatabase.getCollection(ResourceReferenceEntity.COLLECTION_NAME).insertOne(d);
        }
    }

    @Order(1)
    @Test
    public void testExposeResourceSuccessfully() {
        ResourceReference resource = createResource("/tmp/resource3.jpg", "image/jpeg", "resource3.jpg");

        given()
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .body(resource)
                .when().post("/api/v1/resources/expose")
                .then()
                .statusCode(Response.Status.OK.getStatusCode());
    }

    @Order(2)
    @Test
    public void testListResourcesSuccessfully() {
        given()
                .when().get("/api/v1/resources/list")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data.size()", is(3),
                        "data[0].name", is("resource1.jpg"),
                        "data[0].type", is("image/jpeg"),
                        "data[0].description", is("A sample image resource"));
    }

    @Order(3)
    @Test
    void testRemove() {
        given()
                .when().put("/api/v1/resources/remove?resource=resource3.jpg")
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        given()
                .when().get("/api/v1/resources/list")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data.size()", is(2),
                        "data[0].name", is("resource1.jpg"),
                        "data[0].type", is("image/jpeg"),
                        "data[0].description", is("A sample image resource"));
    }
}