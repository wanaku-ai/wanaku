package ai.wanaku.backend.api.v1.chat;

import jakarta.json.JsonObject;

import java.util.Map;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ToolChoice;

public class ExtraLlmParameters {

    static final String MAX_TOKENS = "max_tokens";
    static final String TEMPERATURE = "temperature";
    static final String TOOL_CHOICE = "tool_choice";

    private static final Map<String, ToolChoice> toolChoices = Map.of(
            "auto", ToolChoice.AUTO,
            "required", ToolChoice.REQUIRED,
            "none", ToolChoice.NONE);

    public static ChatRequestParameters fromJson(JsonObject json) {
        var builder = ChatRequestParameters.builder();
        try {
            if (json.containsKey(MAX_TOKENS)) {
                builder.maxOutputTokens(json.getJsonNumber(MAX_TOKENS).intValueExact());
            }
            if (json.containsKey(TEMPERATURE)) {
                builder.temperature(json.getJsonNumber(TEMPERATURE).doubleValue());
            }
            if (json.containsKey(TOOL_CHOICE)) {
                ToolChoice toolChoice = toolChoices.get(json.getString(TOOL_CHOICE));
                if (toolChoice == null) {
                    throw new IllegalArgumentException("Invalid tool choice: " + json.getString(TOOL_CHOICE));
                }
                builder.toolChoice(toolChoice);
            }
        } catch (ClassCastException | ArithmeticException ex) {
            throw new IllegalArgumentException(ex);
        }
        return builder.build();
    }
}
