package ai.wanaku.backend.api.v1.chat;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response.Status;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import ai.wanaku.backend.support.NoOidcTestProfile;
import ai.wanaku.backend.support.WanakuRouterTest;

import static ai.wanaku.test.assertions.WanakuAssertions.assertHttpStatus;
import static io.restassured.RestAssured.given;

import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(NoOidcTestProfile.class)
public class LlmChatResourceTest extends AbstractLlmChatResourceTest {}

abstract class AbstractLlmChatResourceTest extends WanakuRouterTest {

    protected java.util.Map<String, String> getHeaders() {
        return java.util.Map.of("Content-Type", MediaType.APPLICATION_JSON);
    }

    @Test
    void testCompletionsWithoutApiKey() {
        String body =
                """
                {
                    "baseUrl": "http://localhost:11434",
                    "chatParams": {
                        "model": "test",
                        "messages": [{"role": "user", "content": "hello"}]
                    }
                }
                """;

        io.restassured.response.Response response =
                given().headers(getHeaders()).body(body).when().post("/api/v1/chat/completions");

        assertHttpStatus(response, Status.INTERNAL_SERVER_ERROR.getStatusCode());
    }

    @Test
    void testCompletionsWithEmptyApiKey() {
        String body =
                """
                {
                    "baseUrl": "http://localhost:11434",
                    "apiKey": "",
                    "chatParams": {
                        "model": "test",
                        "messages": [{"role": "user", "content": "hello"}]
                    }
                }
                """;

        io.restassured.response.Response response =
                given().headers(getHeaders()).body(body).when().post("/api/v1/chat/completions");

        assertHttpStatus(response, Status.INTERNAL_SERVER_ERROR.getStatusCode());
    }

    @Test
    void testCompletionsRejectsDisallowedUrl() {
        String body =
                """
                {
                    "baseUrl": "https://evil.example.com",
                    "chatParams": {
                        "model": "test",
                        "messages": [{"role": "user", "content": "hello"}]
                    }
                }
                """;

        io.restassured.response.Response response =
                given().headers(getHeaders()).body(body).when().post("/api/v1/chat/completions");

        assertHttpStatus(response, Status.INTERNAL_SERVER_ERROR.getStatusCode());
    }
}
