package ai.wanaku.backend.common;

import ai.wanaku.api.types.AudioContent;
import ai.wanaku.api.types.ImageContent;
import ai.wanaku.api.types.Namespace;
import ai.wanaku.api.types.PromptReference;
import ai.wanaku.api.types.ResourceReference;
import io.quarkiverse.mcp.server.EmbeddedResource;
import io.quarkiverse.mcp.server.PromptManager;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.TextResourceContents;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.jboss.logging.Logger;

/**
 * Helper class for dealing with prompts and MCP protocol exposure
 */
public final class PromptHelper {
    private static final Logger LOG = Logger.getLogger(PromptHelper.class);

    private PromptHelper() {}

    /**
     * Registers a prompt with the given prompt manager by applying the provided handler function
     * to expose the prompt's functionality via the MCP protocol.
     *
     * @param promptReference The reference to the prompt being registered
     * @param promptManager   The prompt manager instance responsible for managing prompts
     * @param handler       A BiFunction that takes PromptArguments and PromptReference as input,
     *                      and returns a PromptResponse. This function will be applied to retrieve the prompt.
     */
    public static void registerPrompt(
            PromptReference promptReference,
            PromptManager promptManager,
            BiFunction<PromptManager.PromptArguments, PromptReference, PromptResponse> handler) {
        registerPrompt(promptReference, promptManager, null, handler);
    }

    /**
     * Registers a prompt with the given prompt manager by applying the provided handler function
     * to expose the prompt's functionality via the MCP protocol.
     *
     * @param promptReference The reference to the prompt being registered
     * @param promptManager   The prompt manager instance responsible for managing prompts
     * @param namespace     The namespace to use for registering the prompt
     * @param handler       A BiFunction that takes PromptArguments and PromptReference as input,
     *                      and returns a PromptResponse. This function will be applied to retrieve the prompt.
     */
    public static void registerPrompt(
            PromptReference promptReference,
            PromptManager promptManager,
            Namespace namespace,
            BiFunction<PromptManager.PromptArguments, PromptReference, PromptResponse> handler) {

        PromptManager.PromptDefinition promptDefinition =
                promptManager.newPrompt(promptReference.getName()).setDescription(promptReference.getDescription());

        // Add arguments
        if (promptReference.getArguments() != null) {
            for (PromptReference.PromptArgument arg : promptReference.getArguments()) {
                promptDefinition.addArgument(arg.getName(), arg.getDescription(), arg.isRequired());
            }
        }

        if (namespace != null) {
            LOG.debugf(
                    "Registering prompt %s in namespace %s with path %s",
                    promptReference.getName(), namespace.getName(), namespace.getPath());
            promptDefinition
                    .setServerName(namespace.getPath())
                    .setHandler(pa -> handler.apply(pa, promptReference))
                    .register();
        } else {
            LOG.debugf("Registering prompt %s", promptReference.getName());
            promptDefinition
                    .setHandler(pa -> handler.apply(pa, promptReference))
                    .register();
        }
    }

    /**
     * Expands a prompt template with the provided arguments and converts it to MCP format.
     *
     * @param arguments Map of argument names to values
     * @param promptReference The prompt reference with template messages
     * @return A PromptResponse with expanded messages in MCP format
     */
    public static PromptResponse expandAndConvert(Map<String, String> arguments, PromptReference promptReference) {

        // Expand template variables
        PromptReference expanded = PromptExpander.expand(promptReference, arguments);

        // Convert to MCP format
        List<PromptMessage> mcpMessages = new ArrayList<>();
        if (expanded.getMessages() != null) {
            for (ai.wanaku.api.types.PromptMessage message : expanded.getMessages()) {
                mcpMessages.add(convertMessage(message));
            }
        }

        return PromptResponse.withMessages(mcpMessages);
    }

    /**
     * Converts a Wanaku PromptMessage to MCP PromptMessage format.
     * Supports all MCP content types: text, image, audio, and embedded resources.
     */
    private static PromptMessage convertMessage(ai.wanaku.api.types.PromptMessage message) {
        String role = message.getRole();
        Object content = message.getContent();

        io.quarkiverse.mcp.server.Content mcpContent;

        if (content instanceof ai.wanaku.api.types.TextContent textContent) {
            // Text content
            mcpContent = new TextContent(textContent.getText());
        } else if (content instanceof ImageContent imageContent) {
            // Image content (base64 encoded data)
            mcpContent = new io.quarkiverse.mcp.server.ImageContent(imageContent.getData(), imageContent.getMimeType());
        } else if (content instanceof AudioContent audioContent) {
            // Audio content (base64 encoded data)
            mcpContent = new io.quarkiverse.mcp.server.AudioContent(audioContent.getData(), audioContent.getMimeType());
        } else if (content instanceof ai.wanaku.api.types.EmbeddedResource embeddedResource) {
            // Embedded resource
            ResourceReference resource = embeddedResource.getResource();

            // Create TextResourceContents with location (URI), text/description, and mime type
            TextResourceContents resourceContents = new TextResourceContents(
                    resource.getLocation() != null ? resource.getLocation() : "",
                    resource.getDescription() != null ? resource.getDescription() : "",
                    resource.getMimeType() != null ? resource.getMimeType() : "text/plain");

            mcpContent = new EmbeddedResource(resourceContents);
        } else {
            // Fallback to empty text
            mcpContent = new TextContent("");
        }

        return createMessageByRole(role, mcpContent);
    }

    /**
     * Creates a PromptMessage with the appropriate role.
     */
    private static PromptMessage createMessageByRole(String role, io.quarkiverse.mcp.server.Content content) {
        return switch (role.toLowerCase()) {
            case "user" -> PromptMessage.withUserRole(content);
            case "assistant" -> PromptMessage.withAssistantRole(content);
            default -> {
                LOG.warnf("Unknown role '%s', defaulting to user", role);
                yield PromptMessage.withUserRole(content);
            }
        };
    }
}
