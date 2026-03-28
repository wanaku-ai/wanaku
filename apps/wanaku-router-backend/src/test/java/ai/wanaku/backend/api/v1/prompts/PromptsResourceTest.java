package ai.wanaku.backend.api.v1.prompts;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response.Status;

import java.util.List;
import org.jboss.logging.Logger;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import ai.wanaku.backend.support.TestIndexHelper;
import ai.wanaku.backend.support.WanakuKeycloakTestResource;
import ai.wanaku.backend.support.WanakuRouterTest;
import ai.wanaku.capabilities.sdk.api.types.PromptMessage;
import ai.wanaku.capabilities.sdk.api.types.PromptReference;
import ai.wanaku.core.util.support.PromptsHelper;

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
public class PromptsResourceTest extends WanakuRouterTest {
    private static final Logger LOG = Logger.getLogger(PromptsResourceTest.class);

    private static String createdName;

    @BeforeAll
    static void setup() {
        TestIndexHelper.clearAllCaches();
    }

    @Order(1)
    @Test
    public void testAddPromptSuccessfully() {
        List<PromptMessage> messages = List.of(
                PromptsHelper.createPromptMessage("user", "Write a test for {{test_subject}}"),
                PromptsHelper.createPromptMessage("assistant", "I'll help you write a test for {{test_subject}}."));

        PromptReference promptReference =
                PromptsHelper.createPromptReference("test-prompt-1", "A test prompt for writing tests", messages);

        final Response response = given().header("Content-Type", MediaType.APPLICATION_JSON)
                .body(promptReference)
                .when()
                .post("/api/v1/prompts");

        LOG.infof("Response: %s", response.getBody().asString());

        assertHttpStatus(response, Status.OK.getStatusCode());
        createdName = response.then().extract().path("data.name");
    }

    @Order(2)
    @Test
    void testList() {
        Response response = given().when().get("/api/v1/prompts");
        assertHttpStatus(response, Status.OK.getStatusCode());
        response.then()
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
        Response response = given().when().get("/api/v1/prompts/" + createdName);
        assertHttpStatus(response, Status.OK.getStatusCode());
        response.then()
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

        Response updateResponse = given().header("Content-Type", MediaType.APPLICATION_JSON)
                .body(updatedPrompt)
                .when()
                .put("/api/v1/prompts");
        assertHttpStatus(updateResponse, Status.OK.getStatusCode());

        Response getResponse = given().when().get("/api/v1/prompts/" + createdName);
        assertHttpStatus(getResponse, Status.OK.getStatusCode());
        getResponse.then().body("data.description", is("An updated test prompt for writing tests"));
    }

    @Order(5)
    @Test
    void testRemove() {
        Response deleteResponse = given().when().delete("/api/v1/prompts?prompt=" + createdName);
        assertHttpStatus(deleteResponse, Status.OK.getStatusCode());

        Response listResponse = given().when().get("/api/v1/prompts");
        assertHttpStatus(listResponse, Status.OK.getStatusCode());
        listResponse.then().body("data.size()", is(0));
    }

    @Order(6)
    @Test
    void testAddAfterRemove() {
        List<PromptMessage> messages = List.of(
                PromptsHelper.createPromptMessage("user", "Debug {{code}}"),
                PromptsHelper.createPromptMessage("assistant", "I'll help you debug the code."));

        PromptReference promptReference =
                PromptsHelper.createPromptReference("test-prompt-3", "A prompt for debugging code", messages);

        Response createResponse = given().header("Content-Type", MediaType.APPLICATION_JSON)
                .body(promptReference)
                .when()
                .post("/api/v1/prompts");
        assertHttpStatus(createResponse, Status.OK.getStatusCode());

        Response listResponse = given().when().get("/api/v1/prompts");
        assertHttpStatus(listResponse, Status.OK.getStatusCode());
        listResponse.then().body("data.size()", is(1), "data[0].name", is("test-prompt-3"));
    }
}
