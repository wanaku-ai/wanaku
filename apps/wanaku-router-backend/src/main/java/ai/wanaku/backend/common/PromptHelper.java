package ai.wanaku.backend.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.jboss.logging.Logger;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import ai.wanaku.capabilities.sdk.api.types.AudioContent;
import ai.wanaku.capabilities.sdk.api.types.ImageContent;
import ai.wanaku.capabilities.sdk.api.types.Namespace;
import ai.wanaku.capabilities.sdk.api.types.PromptReference;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;

/**
 * Helper class for registering prompts with MCP servers and converting
 * prompt content between Wanaku and MCP protocol formats.
 */
public final class PromptHelper {
    private static final Logger LOG = Logger.getLogger(PromptHelper.class);

    private PromptHelper() {}

    public static void registerPrompt(
            PromptReference promptReference,
            McpSyncServer server,
            BiFunction<McpSchema.GetPromptRequest, PromptReference, McpSchema.GetPromptResult> handler) {
        registerPrompt(promptReference, server, null, handler);
    }

    public static void registerPrompt(
            PromptReference promptReference,
            McpSyncServer server,
            Namespace namespace,
            BiFunction<McpSchema.GetPromptRequest, PromptReference, McpSchema.GetPromptResult> handler) {

        List<McpSchema.PromptArgument> promptArgs = new ArrayList<>();
        if (promptReference.getArguments() != null) {
            for (PromptReference.PromptArgument arg : promptReference.getArguments()) {
                promptArgs.add(McpSchema.PromptArgument.builder(arg.getName())
                        .description(arg.getDescription())
                        .required(arg.isRequired())
                        .build());
            }
        }

        McpSchema.Prompt prompt = McpSchema.Prompt.builder(promptReference.getName())
                .description(promptReference.getDescription())
                .arguments(promptArgs)
                .build();

        McpServerFeatures.SyncPromptSpecification spec =
                new McpServerFeatures.SyncPromptSpecification(prompt, (exchange, request) -> {
                    return handler.apply(request, promptReference);
                });

        if (namespace != null) {
            LOG.debugf(
                    "Registering prompt %s in namespace %s with path %s",
                    promptReference.getName(), namespace.getName(), namespace.getPath());
        } else {
            LOG.debugf("Registering prompt %s", promptReference.getName());
        }
        server.addPrompt(spec);
    }

    public static McpSchema.GetPromptResult expandAndConvert(
            Map<String, String> arguments, PromptReference promptReference) {

        PromptReference expanded = PromptExpander.expand(promptReference, arguments);

        List<McpSchema.PromptMessage> mcpMessages = new ArrayList<>();
        if (expanded.getMessages() != null) {
            for (ai.wanaku.capabilities.sdk.api.types.PromptMessage message : expanded.getMessages()) {
                mcpMessages.add(convertMessage(message));
            }
        }

        return McpSchema.GetPromptResult.builder(mcpMessages).build();
    }

    private static McpSchema.PromptMessage convertMessage(ai.wanaku.capabilities.sdk.api.types.PromptMessage message) {
        String role = message.getRole();
        Object content = message.getContent();

        McpSchema.Content mcpContent;

        if (content instanceof ai.wanaku.capabilities.sdk.api.types.TextContent textContent) {
            mcpContent = McpSchema.TextContent.builder(textContent.getText()).build();
        } else if (content instanceof ImageContent imageContent) {
            mcpContent = new McpSchema.ImageContent(null, imageContent.getData(), imageContent.getMimeType());
        } else if (content instanceof AudioContent audioContent) {
            mcpContent = new McpSchema.AudioContent(null, audioContent.getData(), audioContent.getMimeType());
        } else if (content instanceof ai.wanaku.capabilities.sdk.api.types.EmbeddedResource embeddedResource) {
            ResourceReference resource = embeddedResource.getResource();
            McpSchema.TextResourceContents resourceContents = new McpSchema.TextResourceContents(
                    resource.getLocation() != null ? resource.getLocation() : "",
                    resource.getMimeType() != null ? resource.getMimeType() : "text/plain",
                    resource.getDescription() != null ? resource.getDescription() : "");
            mcpContent = new McpSchema.EmbeddedResource(null, resourceContents);
        } else {
            mcpContent = McpSchema.TextContent.builder("").build();
        }

        return createMessageByRole(role, mcpContent);
    }

    private static McpSchema.PromptMessage createMessageByRole(String role, McpSchema.Content content) {
        McpSchema.Role mcpRole =
                switch (role.toLowerCase()) {
                    case "user" -> McpSchema.Role.USER;
                    case "assistant" -> McpSchema.Role.ASSISTANT;
                    default -> {
                        LOG.warnf("Unknown role '%s', defaulting to user", role);
                        yield McpSchema.Role.USER;
                    }
                };
        return new McpSchema.PromptMessage(mcpRole, content);
    }
}
