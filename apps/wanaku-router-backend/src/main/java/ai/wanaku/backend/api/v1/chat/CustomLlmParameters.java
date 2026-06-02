package ai.wanaku.backend.api.v1.chat;

import jakarta.json.JsonObject;

import java.util.Map;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ToolChoice;

public class CustomLlmParameters {

    private static final String MAX_TOKENS = "max_tokens";
    private static final String TEMPERATURE = "temperature";
    private static final String TOOL_CHOICE = "tool_choice";

    private static final Map<String, ToolChoice> toolChoices = Map.of(
            "auto", ToolChoice.AUTO,
            "required", ToolChoice.REQUIRED,
            "none", ToolChoice.NONE);

    public static ChatRequestParameters fromJson(JsonObject json) {
        var builder = ChatRequestParameters.builder();
        if (json.containsKey(MAX_TOKENS)) {
            builder.maxOutputTokens(json.getInt(MAX_TOKENS));
        }
        if (json.containsKey(TEMPERATURE)) {
            builder.temperature(json.getJsonNumber(TEMPERATURE).doubleValue());
        }
        if (json.containsKey(TOOL_CHOICE)) {
            builder.toolChoice(toolChoices.get(json.getString(TOOL_CHOICE)));
        }
        return builder.build();
    }
}
