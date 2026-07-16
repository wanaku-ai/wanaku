package ai.wanaku.backend.api.v1.chat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.io.StringReader;
import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;

@ApplicationScoped
@Path("/api/v1/chat")
public class LlmChatResource {

    private static final Logger LOG = Logger.getLogger(LlmChatResource.class);

    @ConfigProperty(name = "wanaku.chat.mcp-url", defaultValue = "http://localhost:8080/public/mcp")
    String mcpServerUrl;

    @ConfigProperty(name = "wanaku.chat.allowlist")
    List<String> allowlist;

    @Inject
    LlmSupport llmSupport;

    @GET
    @Path("/llms")
    @Produces(APPLICATION_JSON)
    public List<String> getAllowedLlms() {
        return llmSupport.getSupportedLlms().stream()
                .filter(llm -> allowlist.contains(llm))
                .toList();
    }

    @GET
    @Path("/{llm}/models")
    @Produces(APPLICATION_JSON)
    public Response getModelSuggestions(@PathParam("llm") String llm) {
        String llmCapitalized = llm.substring(0, 1).toUpperCase() + llm.substring(1);
        if (!llmSupport.getSupportedLlms().contains(llmCapitalized)) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("%s is not supported".formatted(llm))
                    .build();
        }
        if (!allowlist.contains(llmCapitalized)) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("%s is not allowed".formatted(llm))
                    .build();
        }
        List<String> models = llmSupport.getModelSuggestions(llmCapitalized);
        return Response.ok(models).build();
    }

    @POST
    @Path("/completions")
    @Consumes(APPLICATION_JSON)
    @Produces(TEXT_PLAIN)
    public Response codeCompletions(String data) {
        JsonObject json = Json.createReader(new StringReader(data)).readObject();
        LlmChatParameters parameters = LlmChatParameters.fromJson(json);

        if (!allowlist.contains(parameters.llm())) {
            return Response.status(BAD_REQUEST)
                    .entity("%s is not allowed".formatted(parameters.llm()))
                    .build();
        }

        McpTransport transport = StreamableHttpMcpTransport.builder()
                .url(mcpServerUrl)
                .logRequests(true)
                .logResponses(true)
                .build();

        try (McpClient mcpClient =
                DefaultMcpClient.builder().transport(transport).build()) {

            McpToolProvider toolProvider = McpToolProvider.builder()
                    .mcpClients(mcpClient)
                    .filterToolNames(parameters.selectedTools())
                    .build();

            ChatModel model = llmSupport.createChatModel(parameters.llm(), parameters.modelName(), parameters.apiKey());
            ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);
            chatMemory.set(parameters.chatHistory());

            AiServices<ChatBot> aiServices = AiServices.builder(ChatBot.class)
                    .chatModel(model)
                    .chatMemory(chatMemory)
                    .toolProvider(toolProvider);

            if (parameters.systemPrompt() != null) {
                aiServices = aiServices.systemMessage(parameters.systemPrompt());
            }

            ChatBot bot = aiServices.build();

            String responseText = parameters.extraLlmParameters() == null
                    ? bot.chat(parameters.userPrompt())
                    : bot.chat(parameters.userPrompt(), parameters.extraLlmParameters());
            return Response.ok(responseText).build();
        } catch (Exception ex) {
            LOG.errorf("Error in LLM chat: %s", ex);
            throw new WebApplicationException(ex, INTERNAL_SERVER_ERROR);
        }
    }

    private interface ChatBot {

        String chat(@UserMessage String userMessage);

        String chat(@UserMessage String userMessage, ChatRequestParameters params);
    }
}
