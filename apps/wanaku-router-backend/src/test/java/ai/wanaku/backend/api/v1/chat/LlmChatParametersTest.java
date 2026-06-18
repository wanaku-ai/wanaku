package ai.wanaku.backend.api.v1.chat;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import java.util.List;
import dev.langchain4j.model.chat.request.ChatRequestParameters;

import static ai.wanaku.backend.api.v1.chat.ExtraLlmParameters.MAX_TOKENS;
import static ai.wanaku.backend.api.v1.chat.LlmChatParameters.CHAT_HISTORY;
import static ai.wanaku.backend.api.v1.chat.LlmChatParameters.CONTENT;
import static ai.wanaku.backend.api.v1.chat.LlmChatParameters.EXTRA_LLM_PARAMETERS;
import static ai.wanaku.backend.api.v1.chat.LlmChatParameters.LLM;
import static ai.wanaku.backend.api.v1.chat.LlmChatParameters.MODEL_NAME;
import static ai.wanaku.backend.api.v1.chat.LlmChatParameters.ROLE;
import static ai.wanaku.backend.api.v1.chat.LlmChatParameters.SELECTED_TOOLS;
import static ai.wanaku.backend.api.v1.chat.LlmChatParameters.SYSTEM_PROMPT;
import static ai.wanaku.backend.api.v1.chat.LlmChatParameters.USER_PROMPT;
import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LlmChatParametersTest {

    private JsonObject jsonParameters;

    @BeforeEach
    public void setup() {
        jsonParameters = Json.createObjectBuilder()
                .add(LLM, "Mistral")
                .add(MODEL_NAME, "mistral-small-latest")
                .add(SYSTEM_PROMPT, "You are a helpful assistant")
                .add(USER_PROMPT, "What's the  weather in London?")
                .add(SELECTED_TOOLS, Json.createArrayBuilder().add("get_weather_conditions"))
                .add(EXTRA_LLM_PARAMETERS, Json.createObjectBuilder().add(MAX_TOKENS, 1))
                .add(
                        CHAT_HISTORY,
                        Json.createArrayBuilder()
                                .add(Json.createObjectBuilder()
                                        .add(ROLE, "user")
                                        .add(CONTENT, "Hi there! I would like to ask about weather")
                                        .build())
                                .add(Json.createObjectBuilder()
                                        .add(ROLE, "assistant")
                                        .add(CONTENT, "Sure. Ask away.")
                                        .build()))
                .build();
    }

    @Test
    public void testLlmRequired() {
        jsonParameters = Json.createObjectBuilder(jsonParameters).remove(LLM).build();
        assertThrows(
                IllegalArgumentException.class,
                () -> LlmChatParameters.fromJson(jsonParameters),
                "LLM parameter is required");
    }

    @Test
    public void testModelRequired() {
        jsonParameters =
                Json.createObjectBuilder(jsonParameters).remove(MODEL_NAME).build();
        assertThrows(
                IllegalArgumentException.class,
                () -> LlmChatParameters.fromJson(jsonParameters),
                "Model parameter is required");
    }

    @Test
    public void testUserPromptRequired() {
        jsonParameters =
                Json.createObjectBuilder(jsonParameters).remove(USER_PROMPT).build();
        assertThrows(
                IllegalArgumentException.class,
                () -> LlmChatParameters.fromJson(jsonParameters),
                "UserPrompt parameter is required");
    }

    @Test
    public void testSelectedTools() {
        var parameters = LlmChatParameters.fromJson(jsonParameters);
        assertEquals(List.of("get_weather_conditions"), parameters.selectedTools());
    }

    @Test
    public void testNoSelectedTools() {
        jsonParameters =
                Json.createObjectBuilder(jsonParameters).remove(SELECTED_TOOLS).build();
        var parameters = LlmChatParameters.fromJson(jsonParameters);
        assertEquals(List.of(), parameters.selectedTools());
    }

    @Test
    public void testChatHistory() {
        var parameters = LlmChatParameters.fromJson(jsonParameters);
        assertEquals(
                List.of(userMessage("Hi there! I would like to ask about weather"), aiMessage("Sure. Ask away.")),
                parameters.chatHistory());
    }

    @Test
    public void testNoChatHistory() {
        jsonParameters =
                Json.createObjectBuilder(jsonParameters).remove(CHAT_HISTORY).build();
        var parameters = LlmChatParameters.fromJson(jsonParameters);
        assertEquals(List.of(), parameters.chatHistory());
    }

    @Test
    public void testExtraLlmParameters() {
        var parameters = LlmChatParameters.fromJson(jsonParameters);
        assertEquals(ChatRequestParameters.builder().maxOutputTokens(1).build(), parameters.extraLlmParameters());
    }

    @Test
    public void testNoExtraLlmParameters() {
        jsonParameters = Json.createObjectBuilder(jsonParameters)
                .remove(EXTRA_LLM_PARAMETERS)
                .build();
        var parameters = LlmChatParameters.fromJson(jsonParameters);
        assertNull(parameters.extraLlmParameters());
    }
}
