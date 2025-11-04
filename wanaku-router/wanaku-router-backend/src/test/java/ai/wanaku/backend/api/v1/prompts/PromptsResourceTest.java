package ai.wanaku.backend.api.v1.prompts;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

import ai.wanaku.api.types.PromptMessage;
import ai.wanaku.api.types.PromptReference;
import ai.wanaku.backend.support.TestIndexHelper;
import ai.wanaku.backend.support.WanakuKeycloakTestResource;
import ai.wanaku.backend.support.WanakuRouterTest;
import ai.wanaku.core.util.support.PromptsHelper;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.util.List;
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
public class PromptsResourceTest extends WanakuRouterTest {
    private static final Logger LOG = Logger.getLogger(PromptsResourceTest.class);

    private static String createdName;

    @BeforeAll
    static void setup() throws IOException {
        TestIndexHelper.deleteRecursively("target/wanaku/router");
    }

    @Order(1)
    @Test
    public void testAddPromptSuccessfully() {
        List<PromptMessage> messages = List.of(
                PromptsHelper.createPromptMessage("user", "Write a test for {{test_subject}}"),
                PromptsHelper.createPromptMessage("assistant", "I'll help you write a test for {{test_subject}}."));

        PromptReference promptReference =
                PromptsHelper.createPromptReference("test-prompt-1", "A test prompt for writing tests", messages);

        final var response = given().header("Content-Type", MediaType.APPLICATION_JSON)
                .body(promptReference)
                .when()
                .post("/api/v1/prompts");

        LOG.infof("Response: %s", response.getBody().asString());

        createdName = response.then().statusCode(200).extract().path("data.name");
    }

    @Order(2)
    @Test
    void testList() {
        given().when()
                .get("/api/v1/prompts")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body(
                        "data.size()",
                        is(1),
                        "data[0].name",
                        is("test-prompt-1"),
                        "data[0].description",
                        is("A test prompt for writing tests"));
    }

    @Order(3)
    @Test
    void testGetByName() {
        given().when()
                .get("/api/v1/prompts/" + createdName)
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body(
                        "data.name",
                        is("test-prompt-1"),
                        "data.description",
                        is("A test prompt for writing tests"),
                        "data.messages.size()",
                        is(2));
    }

    @Order(4)
    @Test
    void testUpdate() {
        List<PromptMessage> updatedMessages = List.of(
                PromptsHelper.createPromptMessage("user", "Write an updated test for {{test_subject}}"),
                PromptsHelper.createPromptMessage(
                        "assistant", "I'll help you write an updated test for {{test_subject}}."));

        PromptReference updatedPrompt = PromptsHelper.createPromptReference(
                "test-prompt-1", "An updated test prompt for writing tests", updatedMessages);

        given().header("Content-Type", MediaType.APPLICATION_JSON)
                .body(updatedPrompt)
                .when()
                .put("/api/v1/prompts")
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        given().when()
                .get("/api/v1/prompts/" + createdName)
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data.description", is("An updated test prompt for writing tests"));
    }

    @Order(5)
    @Test
    void testRemove() {
        given().when()
                .delete("/api/v1/prompts?prompt=" + createdName)
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        given().when()
                .get("/api/v1/prompts")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data.size()", is(0));
    }

    @Order(6)
    @Test
    void testAddAfterRemove() {
        List<PromptMessage> messages = List.of(
                PromptsHelper.createPromptMessage("user", "Debug {{code}}"),
                PromptsHelper.createPromptMessage("assistant", "I'll help you debug the code."));

        PromptReference promptReference =
                PromptsHelper.createPromptReference("test-prompt-3", "A prompt for debugging code", messages);

        given().header("Content-Type", MediaType.APPLICATION_JSON)
                .body(promptReference)
                .when()
                .post("/api/v1/prompts")
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        given().when()
                .get("/api/v1/prompts")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("data.size()", is(1), "data[0].name", is("test-prompt-3"));
    }
}
