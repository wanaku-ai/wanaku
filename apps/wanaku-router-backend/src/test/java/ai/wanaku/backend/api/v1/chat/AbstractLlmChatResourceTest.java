package ai.wanaku.backend.api.v1.chat;

import jakarta.ws.rs.core.Response.Status;

import ai.wanaku.backend.support.WanakuRouterTest;

import static ai.wanaku.test.assertions.WanakuAssertions.assertHttpStatus;
import static io.restassured.RestAssured.given;

import org.junit.jupiter.api.Test;

public abstract class AbstractLlmChatResourceTest extends WanakuRouterTest {

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
