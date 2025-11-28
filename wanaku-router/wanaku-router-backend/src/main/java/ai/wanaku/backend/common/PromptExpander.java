package ai.wanaku.backend.common;

import ai.wanaku.capabilities.sdk.api.types.PromptMessage;
import ai.wanaku.capabilities.sdk.api.types.PromptReference;
import ai.wanaku.capabilities.sdk.api.types.TextContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for expanding prompt templates with argument substitution.
 * Supports Mustache-style {{variable}} syntax.
 */
public class PromptExpander {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*([\\w\\.]+)\\s*\\}\\}");

    /**
     * Expands a prompt template by substituting argument values.
     *
     * @param prompt The prompt reference with template messages
     * @param arguments Map of argument names to values
     * @return A new PromptReference with expanded messages
     * @throws IllegalArgumentException if required arguments are missing
     */
    public static PromptReference expand(PromptReference prompt, Map<String, String> arguments) {
        // Validate required arguments
        validateRequiredArguments(prompt, arguments);

        // Create a copy with expanded messages
        PromptReference expanded = new PromptReference();
        expanded.setId(prompt.getId());
        expanded.setName(prompt.getName());
        expanded.setDescription(prompt.getDescription());
        expanded.setArguments(prompt.getArguments());
        expanded.setToolReferences(prompt.getToolReferences());
        expanded.setNamespace(prompt.getNamespace());
        expanded.setConfigurationURI(prompt.getConfigurationURI());

        // Expand messages
        List<PromptMessage> expandedMessages = new ArrayList<>();
        if (prompt.getMessages() != null) {
            for (PromptMessage message : prompt.getMessages()) {
                expandedMessages.add(expandMessage(message, arguments));
            }
        }
        expanded.setMessages(expandedMessages);

        return expanded;
    }

    private static void validateRequiredArguments(PromptReference prompt, Map<String, String> arguments) {
        if (prompt.getArguments() == null) {
            return;
        }

        for (PromptReference.PromptArgument arg : prompt.getArguments()) {
            if (arg.isRequired() && !arguments.containsKey(arg.getName())) {
                throw new IllegalArgumentException("Required argument '" + arg.getName() + "' is missing");
            }
        }
    }

    private static PromptMessage expandMessage(PromptMessage message, Map<String, String> arguments) {
        PromptMessage expanded = new PromptMessage();
        expanded.setRole(message.getRole());

        // Only expand TextContent for now
        // Image, Audio, and EmbeddedResource don't need expansion
        if (message.getContent() instanceof TextContent textContent) {
            String expandedText = expandText(textContent.getText(), arguments);
            expanded.setContent(new TextContent(expandedText));
        } else {
            expanded.setContent(message.getContent());
        }

        return expanded;
    }

    private static String expandText(String text, Map<String, String> arguments) {
        if (text == null) {
            return null;
        }

        Matcher matcher = VARIABLE_PATTERN.matcher(text);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String varName = matcher.group(1);
            String value = arguments.getOrDefault(varName, "");
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);

        return result.toString();
    }
}
