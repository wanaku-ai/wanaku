package ai.wanaku.api.types;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Base interface for different types of prompt content.
 * According to MCP specification, content can be:
 * - TextContent
 * - ImageContent
 * - AudioContent
 * - EmbeddedResource
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = TextContent.class, name = "text"),
    @JsonSubTypes.Type(value = ImageContent.class, name = "image"),
    @JsonSubTypes.Type(value = AudioContent.class, name = "audio"),
    @JsonSubTypes.Type(value = EmbeddedResource.class, name = "resource")
})
public interface PromptContent {
    /**
     * Gets the type of content.
     * @return The content type (e.g., "text", "image", "audio", "resource")
     */
    String getType();
}
