package ai.wanaku.cli.main.commands.prompts;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;

import ai.wanaku.capabilities.sdk.api.types.AudioContent;
import ai.wanaku.capabilities.sdk.api.types.EmbeddedResource;
import ai.wanaku.capabilities.sdk.api.types.ImageContent;
import ai.wanaku.capabilities.sdk.api.types.PromptMessage;
import ai.wanaku.capabilities.sdk.api.types.PromptReference;
import ai.wanaku.capabilities.sdk.api.types.PromptReference.PromptArgument;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;
import ai.wanaku.capabilities.sdk.api.types.TextContent;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.PromptsService;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import org.jboss.logging.Logger;
import org.jline.terminal.Terminal;
import picocli.CommandLine;

@CommandLine.Command(name = "edit", description = "Edit an existing prompt")
public class PromptsEdit extends BaseCommand {
    private static final Logger LOG = Logger.getLogger(PromptsEdit.class);

    @CommandLine.Option(
            names = {"--host"},
            description = "The API host",
            defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @CommandLine.Option(
            names = {"-n", "--name"},
            description = "Name of the prompt to edit",
            required = true)
    private String name;

    @CommandLine.Option(
            names = {"-N", "--namespace"},
            description = "The namespace associated with the prompt")
    private String namespace;

    @CommandLine.Option(
            names = {"-d", "--description"},
            description = "Description of the prompt")
    private String description;

    @CommandLine.Option(
            names = {"-m", "--message"},
            description = "Message in one of these formats:\n"
                    + "  Text: 'role:text:Your message here'\n"
                    + "  Image: 'role:image:base64data:mimeType' (e.g., 'user:image:iVBORw0KG...:image/png')\n"
                    + "  Audio: 'role:audio:base64data:mimeType' (e.g., 'user:audio:UklGRiQAA...:audio/wav')\n"
                    + "  Resource: 'role:resource:location:description:mimeType' (e.g., 'user:resource:file:///path:File content:text/plain')\n"
                    + "  Note: For backward compatibility, 'role:content' defaults to text type\n"
                    + "  If specified, replaces all existing messages")
    private List<String> messages;

    @CommandLine.Option(
            names = {"-a", "--argument"},
            description = "Argument in format 'name:description:required' (e.g., 'code:The code to test:true')\n"
                    + "  If specified, replaces all existing arguments")
    private List<String> arguments;

    @CommandLine.Option(
            names = {"-t", "--tool-references"},
            description = "Comma-separated list of tool names this prompt may use\n"
                    + "  If specified, replaces all existing tool references",
            split = ",")
    private List<String> toolReferences;

    PromptsService promptsService;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {

        promptsService = initService(PromptsService.class, host);

        // First, fetch the existing prompt
        PromptReference existingPrompt;
        try {
            WanakuResponse<PromptReference> response = promptsService.getByName(name);
            existingPrompt = response.data();
            if (existingPrompt == null) {
                printer.printErrorMessage("Prompt '" + name + "' not found");
                return EXIT_ERROR;
            }
        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            commonResponseErrorHandler(response);
            return EXIT_ERROR;
        }

        // Update only the fields that were provided
        if (namespace != null) {
            existingPrompt.setNamespace(namespace);
        }

        if (description != null) {
            existingPrompt.setDescription(description);
        }

        // Parse and update messages if provided
        if (messages != null) {
            List<PromptMessage> promptMessages = new ArrayList<>();
            for (String messageStr : messages) {
                PromptMessage message = parseMessage(messageStr);
                if (message != null) {
                    promptMessages.add(message);
                }
            }
            existingPrompt.setMessages(promptMessages);
        }

        // Parse and update arguments if provided
        if (arguments != null) {
            List<PromptArgument> promptArguments = new ArrayList<>();
            for (String argStr : arguments) {
                String[] parts = argStr.split(":", 3);
                if (parts.length >= 2) {
                    PromptArgument argument = new PromptArgument();
                    argument.setName(parts[0].trim());
                    argument.setDescription(parts[1].trim());
                    argument.setRequired(parts.length == 3 && Boolean.parseBoolean(parts[2].trim()));
                    promptArguments.add(argument);
                }
            }
            existingPrompt.setArguments(promptArguments);
        }

        // Update tool references if provided
        if (toolReferences != null) {
            existingPrompt.setToolReferences(toolReferences);
        }

        // Update the prompt
        try {
            promptsService.update(existingPrompt);
            printer.printSuccessMessage("Successfully updated prompt '" + name + "'");
        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            commonResponseErrorHandler(response);
            return EXIT_ERROR;
        }

        return EXIT_OK;
    }

    /**
     * Parses a message string into a PromptMessage object.
     * Supports formats:
     * - role:text:content (text message)
     * - role:image:base64data:mimeType (image message)
     * - role:audio:base64data:mimeType (audio message)
     * - role:resource:location:description:mimeType (embedded resource)
     * - role:content (backward compatibility, defaults to text)
     */
    private PromptMessage parseMessage(String messageStr) {
        String[] parts = messageStr.split(":", 5); // Max 5 parts for resource type
        if (parts.length < 2) {
            LOG.warnf("Invalid message format: %s", messageStr);
            return null;
        }

        String role = parts[0].trim();
        PromptMessage message = new PromptMessage();
        message.setRole(role);

        // Backward compatibility: role:content defaults to text
        if (parts.length == 2) {
            message.setContent(new TextContent(parts[1].trim()));
            return message;
        }

        String contentType = parts[1].trim().toLowerCase();

        switch (contentType) {
            case "text":
                if (parts.length >= 3) {
                    message.setContent(new TextContent(parts[2].trim()));
                } else {
                    LOG.warnf("Text message requires content: %s", messageStr);
                    return null;
                }
                break;

            case "image":
                if (parts.length >= 4) {
                    ImageContent imageContent = new ImageContent();
                    imageContent.setData(parts[2].trim());
                    imageContent.setMimeType(parts[3].trim());
                    message.setContent(imageContent);
                } else {
                    LOG.warnf("Image message requires data and mimeType: %s", messageStr);
                    return null;
                }
                break;

            case "audio":
                if (parts.length >= 4) {
                    AudioContent audioContent = new AudioContent();
                    audioContent.setData(parts[2].trim());
                    audioContent.setMimeType(parts[3].trim());
                    message.setContent(audioContent);
                } else {
                    LOG.warnf("Audio message requires data and mimeType: %s", messageStr);
                    return null;
                }
                break;

            case "resource":
                if (parts.length >= 5) {
                    ResourceReference resourceRef = new ResourceReference();
                    resourceRef.setLocation(parts[2].trim());
                    resourceRef.setDescription(parts[3].trim());
                    resourceRef.setMimeType(parts[4].trim());

                    EmbeddedResource embeddedResource = new EmbeddedResource();
                    embeddedResource.setResource(resourceRef);
                    message.setContent(embeddedResource);
                } else {
                    LOG.warnf("Resource message requires location, description, and mimeType: %s", messageStr);
                    return null;
                }
                break;

            default:
                // If not a recognized type, treat the second part as text content for backward compatibility
                message.setContent(new TextContent(parts[1].trim()));
                break;
        }

        return message;
    }
}
