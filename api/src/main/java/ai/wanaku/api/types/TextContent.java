package ai.wanaku.api.types;

import java.util.Objects;

/**
 * Represents text content in a prompt message.
 * This is the most common content type for prompts.
 */
public class TextContent implements PromptContent {
    private static final String TYPE = "text";

    /**
     * The text content.
     */
    private String text;

    public TextContent() {}

    public TextContent(String text) {
        this.text = text;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TextContent that = (TextContent) o;
        return Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text);
    }

    @Override
    public String toString() {
        return "TextContent{" + "type='" + TYPE + '\'' + ", text='" + text + '\'' + '}';
    }
}
