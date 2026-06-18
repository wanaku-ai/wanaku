package ai.wanaku.backend.api.v1.chat;

import jakarta.json.Json;

import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ToolChoice;

import static ai.wanaku.backend.api.v1.chat.ExtraLlmParameters.MAX_TOKENS;
import static ai.wanaku.backend.api.v1.chat.ExtraLlmParameters.TEMPERATURE;
import static ai.wanaku.backend.api.v1.chat.ExtraLlmParameters.TOOL_CHOICE;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ExtraLlmParametersTest {

    private ChatRequestParameters simpleParameter(String name, int value) {
        return ExtraLlmParameters.fromJson(
                Json.createObjectBuilder().add(name, value).build());
    }

    private ChatRequestParameters simpleParameter(String name, double value) {
        return ExtraLlmParameters.fromJson(
                Json.createObjectBuilder().add(name, value).build());
    }

    private ChatRequestParameters simpleParameter(String name, String value) {
        return ExtraLlmParameters.fromJson(
                Json.createObjectBuilder().add(name, value).build());
    }

    @Test
    public void testMaxTokens() {
        var parameters = simpleParameter(MAX_TOKENS, 1);
        assertEquals(1, parameters.maxOutputTokens());
    }

    @Test
    public void testMaxTokensInvalidFormat() {
        assertThrows(
                IllegalArgumentException.class,
                () -> simpleParameter(MAX_TOKENS, 1.2),
                "Max tokens shouldn't allow floating point numbers");
    }

    @Test
    public void testTemperature() {
        var parameters = simpleParameter(TEMPERATURE, 0.8);
        assertEquals(0.8, parameters.temperature());
    }

    @Test
    public void testTemperatureInvalidFormat() {
        assertThrows(
                IllegalArgumentException.class,
                () -> simpleParameter(TEMPERATURE, "large"),
                "Temperature shouldn't allow strings");
    }

    @Test
    public void testToolChoiceAuto() {
        var parameters = simpleParameter(TOOL_CHOICE, "auto");
        assertEquals(ToolChoice.AUTO, parameters.toolChoice());
    }

    @Test
    public void testToolChoiceRequired() {
        var parameters = simpleParameter(TOOL_CHOICE, "required");
        assertEquals(ToolChoice.REQUIRED, parameters.toolChoice());
    }

    @Test
    public void testToolChoiceNone() {
        var parameters = simpleParameter(TOOL_CHOICE, "none");
        assertEquals(ToolChoice.NONE, parameters.toolChoice());
    }

    @Test
    public void testToolChoiceInvalidFormat() {
        assertThrows(
                IllegalArgumentException.class,
                () -> simpleParameter(TOOL_CHOICE, "as-you-please"),
                "Tool choice shouldn't allow values besides 'auto', 'required' and 'none'");
    }
}
