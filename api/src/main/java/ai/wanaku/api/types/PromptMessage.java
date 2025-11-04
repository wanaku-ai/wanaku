package ai.wanaku.api.types;

import java.util.Objects;

/**
 * Represents a message in an MCP prompt.
 * Each message has a role and content according to the MCP specification.
 */
public class PromptMessage {
    /**
     * The role of the message sender.
     * Valid values: "user", "assistant"
     */
    private String role;

    /**
     * The content of the message.
     * This is a polymorphic field that can be one of:
     * - TextContent
     * - ImageContent
     * - AudioContent
     * - EmbeddedResource
     */
    private PromptContent content;

    public PromptMessage() {}

    public PromptMessage(String role, PromptContent content) {
        this.role = role;
        this.content = content;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public PromptContent getContent() {
        return content;
    }

    public void setContent(PromptContent content) {
        this.content = content;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PromptMessage that = (PromptMessage) o;
        return Objects.equals(role, that.role) && Objects.equals(content, that.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(role, content);
    }

    @Override
    public String toString() {
        return "PromptMessage{" + "role='" + role + '\'' + ", content=" + content + '}';
    }
}
