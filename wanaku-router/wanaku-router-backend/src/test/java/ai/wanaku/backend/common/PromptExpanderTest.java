package ai.wanaku.backend.common;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import ai.wanaku.capabilities.sdk.api.types.ImageContent;
import ai.wanaku.capabilities.sdk.api.types.PromptMessage;
import ai.wanaku.capabilities.sdk.api.types.PromptReference;
import ai.wanaku.capabilities.sdk.api.types.TextContent;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptExpanderTest {

    private static PromptReference.PromptArgument createArgument(String name, boolean required) {
        PromptReference.PromptArgument arg = new PromptReference.PromptArgument();
        arg.setName(name);
        arg.setRequired(required);
        return arg;
    }

    private static PromptMessage createTextMessage(String role, String text) {
        PromptMessage message = new PromptMessage();
        message.setRole(role);
        message.setContent(new TextContent(text));
        return message;
    }

    private static PromptReference createPrompt(
            List<PromptReference.PromptArgument> args, List<PromptMessage> messages) {
        PromptReference prompt = new PromptReference();
        prompt.setName("test-prompt");
        prompt.setDescription("A test prompt");
        prompt.setArguments(args);
        prompt.setMessages(messages);
        return prompt;
    }

    @Test
    void expandSingleVariable() {
        PromptReference prompt = createPrompt(
                List.of(createArgument("name", true)), List.of(createTextMessage("user", "Hello, {{name}}!")));

        PromptReference result = PromptExpander.expand(prompt, Map.of("name", "World"));

        TextContent content = (TextContent) result.getMessages().get(0).getContent();
        assertEquals("Hello, World!", content.getText());
    }

    @Test
    void expandMultipleVariables() {
        PromptReference prompt = createPrompt(
                List.of(createArgument("greeting", false), createArgument("target", false)),
                List.of(createTextMessage("user", "{{greeting}}, {{target}}! How are you?")));

        PromptReference result = PromptExpander.expand(prompt, Map.of("greeting", "Hi", "target", "Alice"));

        TextContent content = (TextContent) result.getMessages().get(0).getContent();
        assertEquals("Hi, Alice! How are you?", content.getText());
    }

    @Test
    void expandWithSpacesInMustacheTags() {
        PromptReference prompt = createPrompt(
                List.of(createArgument("name", false)), List.of(createTextMessage("user", "Hello, {{ name }}!")));

        PromptReference result = PromptExpander.expand(prompt, Map.of("name", "World"));

        TextContent content = (TextContent) result.getMessages().get(0).getContent();
        assertEquals("Hello, World!", content.getText());
    }

    @Test
    void expandMissingOptionalVariableReplacedWithEmpty() {
        PromptReference prompt = createPrompt(
                List.of(createArgument("name", false)), List.of(createTextMessage("user", "Hello, {{name}}!")));

        PromptReference result = PromptExpander.expand(prompt, Collections.emptyMap());

        TextContent content = (TextContent) result.getMessages().get(0).getContent();
        assertEquals("Hello, !", content.getText());
    }

    @Test
    void expandThrowsWhenRequiredArgumentMissing() {
        PromptReference prompt = createPrompt(
                List.of(createArgument("name", true)), List.of(createTextMessage("user", "Hello, {{name}}!")));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> PromptExpander.expand(prompt, Collections.emptyMap()));

        assertTrue(ex.getMessage().contains("name"));
    }

    @Test
    void expandPreservesNonTextContent() {
        ImageContent image = new ImageContent();
        image.setData("base64data");
        image.setMimeType("image/png");

        PromptMessage imageMessage = new PromptMessage();
        imageMessage.setRole("user");
        imageMessage.setContent(image);

        PromptReference prompt = createPrompt(null, List.of(imageMessage));

        PromptReference result = PromptExpander.expand(prompt, Collections.emptyMap());

        assertInstanceOf(ImageContent.class, result.getMessages().get(0).getContent());
    }

    @Test
    void expandWithNullMessages() {
        PromptReference prompt = createPrompt(null, null);

        PromptReference result = PromptExpander.expand(prompt, Collections.emptyMap());

        assertNotNull(result.getMessages());
        assertTrue(result.getMessages().isEmpty());
    }

    @Test
    void expandMultipleMessages() {
        PromptReference prompt = createPrompt(
                List.of(createArgument("topic", false)),
                List.of(
                        createTextMessage("user", "Tell me about {{topic}}"),
                        createTextMessage("assistant", "Sure, {{topic}} is interesting")));

        PromptReference result = PromptExpander.expand(prompt, Map.of("topic", "Java"));

        assertEquals(2, result.getMessages().size());
        assertEquals(
                "Tell me about Java", ((TextContent) result.getMessages().get(0).getContent()).getText());
        assertEquals(
                "Sure, Java is interesting",
                ((TextContent) result.getMessages().get(1).getContent()).getText());
    }
}
