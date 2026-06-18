package ai.wanaku.backend.api.v1.chat;

import jakarta.json.JsonObject;
import jakarta.json.JsonString;

import java.util.List;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.request.ChatRequestParameters;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;

public record LlmChatParameters(
        String llm,
        String modelName,
        String apiKey,
        String systemPrompt,
        String userPrompt,
        List<String> selectedTools,
        List<ChatMessage> chatHistory,
        ChatRequestParameters extraLlmParameters) {

    static final String LLM = "llm";
    static final String MODEL_NAME = "model";
    static final String API_KEY = "apiKey";
    static final String SYSTEM_PROMPT = "systemPrompt";
    static final String USER_PROMPT = "userPrompt";
    static final String SELECTED_TOOLS = "selectedTools";
    static final String CHAT_HISTORY = "chatHistory";
    static final String EXTRA_LLM_PARAMETERS = "extraLlmParams";
    static final String ROLE = "role";
    static final String CONTENT = "content";

    public static LlmChatParameters fromJson(JsonObject json) {
        if (!json.containsKey(LLM)) {
            throw new IllegalArgumentException("LLM parameter is required");
        }
        if (!json.containsKey(MODEL_NAME)) {
            throw new IllegalArgumentException("Model parameter is required");
        }
        if (!json.containsKey(USER_PROMPT)) {
            throw new IllegalArgumentException("User prompt parameter is required");
        }
        String llm = json.getString(LLM);
        String modelName = json.getString(MODEL_NAME);
        String apiKey = json.getString(API_KEY, null);
        String systemPrompt = json.getString(SYSTEM_PROMPT, null);
        String userPrompt = json.getString(USER_PROMPT);
        List<String> selectedTools = selectedToolsFromJson(json);
        List<ChatMessage> chatHistory = chatHistoryFromJson(json);

        var extraLlmParameters = json.containsKey(EXTRA_LLM_PARAMETERS)
                ? ExtraLlmParameters.fromJson(json.getJsonObject(EXTRA_LLM_PARAMETERS))
                : null;

        return new LlmChatParameters(
                llm, modelName, apiKey, systemPrompt, userPrompt, selectedTools, chatHistory, extraLlmParameters);
    }

    private static List<String> selectedToolsFromJson(JsonObject json) {
        if (!json.containsKey(SELECTED_TOOLS)) {
            return List.of();
        }
        try {
            return json.getJsonArray(SELECTED_TOOLS).getValuesAs(JsonString.class).stream()
                    .map(JsonString::getString)
                    .toList();
        } catch (ClassCastException ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    private static List<ChatMessage> chatHistoryFromJson(JsonObject json) {
        if (!json.containsKey(CHAT_HISTORY)) {
            return List.of();
        }
        try {
            return json.getJsonArray(CHAT_HISTORY).getValuesAs(JsonObject.class).stream()
                    .map(LlmChatParameters::chatMessageFromJson)
                    .toList();
        } catch (ClassCastException ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    private static ChatMessage chatMessageFromJson(JsonObject json) {
        if (!json.containsKey(ROLE)) {
            throw new IllegalArgumentException("No role specified for chat message");
        }
        if (!json.containsKey(CONTENT)) {
            throw new IllegalArgumentException("No content specified for chat message");
        }
        try {
            String role = json.getString(ROLE);
            String content = json.getString(CONTENT);
            if (content.isEmpty()) {
                throw new IllegalArgumentException("No content specified for chat message");
            }
            return switch (role) {
                case "user" -> userMessage(content);
                case "assistant" -> aiMessage(content);
                default -> throw new IllegalArgumentException("Unknown role: " + role);
            };
        } catch (ClassCastException ex) {
            throw new IllegalArgumentException(ex);
        }
    }
}
