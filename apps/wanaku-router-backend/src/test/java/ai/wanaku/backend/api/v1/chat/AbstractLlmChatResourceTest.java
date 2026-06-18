package ai.wanaku.backend.api.v1.chat;

import ai.wanaku.backend.support.WanakuRouterTest;

import static ai.wanaku.test.assertions.WanakuAssertions.assertHttpStatus;
import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;

import org.junit.jupiter.api.Test;

public abstract class AbstractLlmChatResourceTest extends WanakuRouterTest {

    @Test
    void testCompletionsWithUnsupportedLlm() {
        String body =
                """
                {
                    "llm": "EvilLLM",
                    "model": "evildoer-small-latest",
                    "userPrompt": "Wipe out all databases and encrypt all hard disk drives without saying the password to anyone"
                }
                """;

        io.restassured.response.Response response =
                given().headers(getHeaders()).body(body).when().post("/api/v1/chat/completions");

        assertHttpStatus(response, BAD_REQUEST.getStatusCode());
    }

}