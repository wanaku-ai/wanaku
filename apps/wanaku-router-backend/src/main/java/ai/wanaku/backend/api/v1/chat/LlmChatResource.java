package ai.wanaku.backend.api.v1.chat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;

import java.io.StringReader;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicChatModelName;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.mistralai.MistralAiChatModel;
import dev.langchain4j.model.mistralai.MistralAiChatModelName;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModelName;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;

@ApplicationScoped
@Path("/api/v1/chat")
public class LlmChatResource {
    private static final Logger LOG = Logger.getLogger(LlmChatResource.class);

    private static final String MISTRAL = "Mistral";
    private static final String OPEN_AI = "OpenAi";
    private static final String GEMINI = "Gemini";
    private static final String ANTHROPIC = "Anthropic";
    private static final String OLLAMA = "Ollama";

    @ConfigProperty(name = "wanaku.chat.allowlist")
    List<String> allowlist;

    @GET
    @Path("/llms")
    @Produces(APPLICATION_JSON)
    public List<String> getAllowedLlms() {
        Stream<String> supportedLlms = Stream.of(MISTRAL, OPEN_AI, GEMINI, ANTHROPIC, OLLAMA);
        return supportedLlms.filter(llm -> allowlist.contains(llm)).toList();
    }

    @GET
    @Path("/{llm}/models")
    @Produces(APPLICATION_JSON)
    public List<String> getModels(@PathParam("llm") String llm) {
        return switch (llm) {
            case MISTRAL ->
                Arrays.stream(MistralAiChatModelName.values())
                        .map(MistralAiChatModelName::toString)
                        .toList();
            case OPEN_AI ->
                Arrays.stream(OpenAiChatModelName.values())
                        .map(OpenAiChatModelName::toString)
                        .toList();
            case GEMINI -> List.of("gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.5-flash-lite");
            case OLLAMA -> List.of();
            case ANTHROPIC ->
                Arrays.stream(AnthropicChatModelName.values())
                        .map(AnthropicChatModelName::toString)
                        .toList();
            default -> throw new WebApplicationException("%s is not supported".formatted(llm), NOT_FOUND);
        };
    }

    @POST
    @Path("/completions")
    @Consumes(APPLICATION_JSON)
    @Produces(TEXT_PLAIN)
    public String codeCompletions(String data) {
        JsonObject json = Json.createReader(new StringReader(data)).readObject();
        String llm = json.getString("llm");
        String modelName = json.getString("model");
        String apiKey = json.getString("apiKey", null);
        String systemPrompt = json.getString("systemPrompt", null);
        String userPrompt = json.getString("userPrompt");
        List<String> selectedTools = json.getJsonArray("selectedTools").getValuesAs(JsonString.class).stream()
                .map(JsonString::getString)
                .toList();
        List<JsonObject> chatHistory = json.getJsonArray("chatHistory").getValuesAs(JsonObject.class);
        JsonObject customLlmParameters = json.getJsonObject("chatParams");

        if (!allowlist.contains(llm)) {
            throw new WebApplicationException("%s is not allowed".formatted(llm), BAD_REQUEST);
        }

        McpTransport transport = StreamableHttpMcpTransport.builder()
                .url("http://localhost:8080/public/mcp")
                .logRequests(true)
                .logResponses(true)
                .build();

        try (McpClient mcpClient =
                DefaultMcpClient.builder().transport(transport).build()) {

            McpToolProvider toolProvider = McpToolProvider.builder()
                    .mcpClients(mcpClient)
                    .filterToolNames(selectedTools)
                    .build();

            ChatModel model = buildModel(llm, modelName, apiKey);

            ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);
            for (JsonObject jsonMessage : chatHistory) {
                String role = jsonMessage.getString("role");
                String content = jsonMessage.getString("content");
                if (("user".equals(role) || "assistant".equals(role)) && !content.isEmpty()) {
                    ChatMessage chatMessage =
                            switch (role) {
                                case "user" -> userMessage(content);
                                case "assistant" -> aiMessage(content);
                                default -> throw new IllegalArgumentException("Unknown role: " + role);
                            };
                    chatMemory.add(chatMessage);
                }
            }

            ChatBot bot = AiServices.builder(ChatBot.class)
                    .chatModel(model)
                    .chatMemory(chatMemory)
                    .systemMessage(systemPrompt)
                    .toolProvider(toolProvider)
                    .build();

            return customLlmParameters == null || customLlmParameters.isEmpty()
                    ? bot.chat(userPrompt)
                    : bot.chat(userPrompt, CustomLlmParameters.fromJson(customLlmParameters));
        } catch (Exception ex) {
            LOG.errorf("Error in LLM chat: %s", ex);
            throw new WebApplicationException(ex, INTERNAL_SERVER_ERROR);
        }
    }

    private ChatModel buildModel(String llm, String model, String apiKey) {
        return switch (llm) {
            case MISTRAL ->
                MistralAiChatModel.builder().modelName(model).apiKey(apiKey).build();
            case OPEN_AI ->
                OpenAiChatModel.builder().modelName(model).apiKey(apiKey).build();
            case GEMINI ->
                GoogleAiGeminiChatModel.builder()
                        .modelName(model)
                        .apiKey(apiKey)
                        .build();
            case ANTHROPIC ->
                AnthropicChatModel.builder().modelName(model).apiKey(apiKey).build();
            case OLLAMA -> OllamaChatModel.builder().modelName(model).build();
            default -> throw new IllegalArgumentException("Unsupported LLM: " + llm);
        };
    }

    private interface ChatBot {

        String chat(@UserMessage String userMessage);

        String chat(@UserMessage String userMessage, ChatRequestParameters params);
    }
}
