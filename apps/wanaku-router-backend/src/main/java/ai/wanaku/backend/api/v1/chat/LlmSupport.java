package ai.wanaku.backend.api.v1.chat;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Arrays;
import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicChatModelName;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.mistralai.MistralAiChatModel;
import dev.langchain4j.model.mistralai.MistralAiChatModelName;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModelName;

@ApplicationScoped
public class LlmSupport {

    public static final String ANTHROPIC = "Anthropic";
    public static final String GEMINI = "Gemini";
    public static final String MISTRAL = "Mistral";
    public static final String OLLAMA = "Ollama";
    public static final String OPEN_AI = "OpenAi";

    @ConfigProperty(name = "wanaku.chat.ollama-url", defaultValue = "http://localhost:11434")
    String ollamaServerUrl;

    public List<String> getSupportedLlms() {
        return List.of(ANTHROPIC, GEMINI, MISTRAL, OLLAMA, OPEN_AI);
    }

    public List<String> getModelSuggestions(String llm) {
        return switch (llm) {
            case ANTHROPIC ->
                Arrays.stream(AnthropicChatModelName.values())
                        .map(AnthropicChatModelName::toString)
                        .toList();
            case GEMINI -> List.of("gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.5-flash-lite");
            case MISTRAL ->
                Arrays.stream(MistralAiChatModelName.values())
                        .map(MistralAiChatModelName::toString)
                        .toList();
            case OLLAMA -> List.of();
            case OPEN_AI ->
                Arrays.stream(OpenAiChatModelName.values())
                        .map(OpenAiChatModelName::toString)
                        .toList();
            default -> throw new IllegalArgumentException("%s is not supported".formatted(llm));
        };
    }

    public ChatModel createChatModel(String llm, String modelName, String apiKey) {
        return switch (llm) {
            case ANTHROPIC ->
                AnthropicChatModel.builder().modelName(modelName).apiKey(apiKey).build();
            case GEMINI ->
                GoogleAiGeminiChatModel.builder()
                        .modelName(modelName)
                        .apiKey(apiKey)
                        .sendThinking(true)
                        .returnThinking(true)
                        .build();
            case MISTRAL ->
                MistralAiChatModel.builder().modelName(modelName).apiKey(apiKey).build();
            case OLLAMA ->
                OllamaChatModel.builder()
                        .modelName(modelName)
                        .baseUrl(ollamaServerUrl)
                        .build();
            case OPEN_AI ->
                OpenAiChatModel.builder().modelName(modelName).apiKey(apiKey).build();
            default -> throw new IllegalArgumentException("%s is not supported".formatted(llm));
        };
    }
}
