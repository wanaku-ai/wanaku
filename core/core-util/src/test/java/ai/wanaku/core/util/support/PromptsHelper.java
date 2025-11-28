package ai.wanaku.core.util.support;

import ai.wanaku.capabilities.sdk.api.types.EmbeddedResource;
import ai.wanaku.capabilities.sdk.api.types.PromptMessage;
import ai.wanaku.capabilities.sdk.api.types.PromptReference;
import ai.wanaku.capabilities.sdk.api.types.PromptReference.PromptArgument;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;
import ai.wanaku.capabilities.sdk.api.types.TextContent;
import java.util.List;

public class PromptsHelper {

    public static PromptReference createPromptReference(String name, String description, List<PromptMessage> messages) {
        PromptReference promptReference = new PromptReference();
        promptReference.setName(name);
        promptReference.setDescription(description);
        promptReference.setMessages(messages);
        return promptReference;
    }

    public static PromptReference createPromptReference(
            String name,
            String description,
            List<PromptMessage> messages,
            List<PromptArgument> arguments,
            List<String> toolReferences) {
        PromptReference promptReference = new PromptReference();
        promptReference.setName(name);
        promptReference.setDescription(description);
        promptReference.setMessages(messages);
        promptReference.setArguments(arguments);
        promptReference.setToolReferences(toolReferences);
        return promptReference;
    }

    public static PromptMessage createPromptMessage(String role, String text) {
        return new PromptMessage(role, new TextContent(text));
    }

    public static PromptMessage createPromptMessageWithResource(String role, ResourceReference resource) {
        return new PromptMessage(role, new EmbeddedResource(resource));
    }

    public static PromptArgument createPromptArgument(String name, String description, boolean required) {
        PromptArgument argument = new PromptArgument();
        argument.setName(name);
        argument.setDescription(description);
        argument.setRequired(required);
        return argument;
    }
}
