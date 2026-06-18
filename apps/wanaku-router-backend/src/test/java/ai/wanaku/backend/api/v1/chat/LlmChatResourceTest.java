package ai.wanaku.backend.api.v1.chat;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import ai.wanaku.backend.support.NoOidcTestProfile;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.StringReader;
import java.util.List;

import static ai.wanaku.test.assertions.WanakuAssertions.assertHttpStatus;
import static ai.wanaku.test.assertions.WanakuAssertions.assertHttpSuccess;
import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestProfile(NoOidcTestProfile.class)
public class LlmChatResourceTest extends AbstractLlmChatResourceTest {

    private static final String SUPPORTED_LLM = "Mistral";
    private static final String SUPPORTED_LLM_MODEL = "mistral-small-latest";

    @InjectMock
    LlmSupport llmSupport;

    @BeforeEach
    void setUp() {
        Mockito.when(llmSupport.getSupportedLlms()).thenReturn(List.of(SUPPORTED_LLM));
        Mockito.when(llmSupport.getModelSuggestions(SUPPORTED_LLM)).thenReturn(List.of(SUPPORTED_LLM_MODEL));
    }

    @Test
    void testAllowedLlms() {
        var response = given().headers(getHeaders()).when().get("/api/v1/chat/llms");
        assertHttpSuccess(response);
    }

    @Test
    void testAllowedLlmsFormat() {
        var response = given().headers(getHeaders()).when().get("/api/v1/chat/llms");
        JsonArray expected = Json.createArrayBuilder().add(SUPPORTED_LLM).build();
        JsonArray result =
                Json.createReader(new StringReader(response.getBody().print())).readArray();
        assertEquals(expected, result);
    }

    @Test
    void testModelSuggestions() {
        var response =
                given().headers(getHeaders()).when().get("/api/v1/chat/" + SUPPORTED_LLM.toLowerCase() + "/models");
        assertHttpSuccess(response);
    }

    @Test
    void testModelSuggestionsFormat() {
        var response =
                given().headers(getHeaders()).when().get("/api/v1/chat/" + SUPPORTED_LLM.toLowerCase() + "/models");
        JsonArray expected = Json.createArrayBuilder().add(SUPPORTED_LLM_MODEL).build();
        JsonArray result =
                Json.createReader(new StringReader(response.getBody().print())).readArray();
        assertEquals(expected, result);
    }

    @Test
    void testUnsupportedModelSuggestions() {
        var response = given().headers(getHeaders()).when().get("/api/v1/chat/foobar/models");
        assertHttpStatus(response, NOT_FOUND.getStatusCode());
    }

}