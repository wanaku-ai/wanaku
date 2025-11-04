package ai.wanaku.api.types;

import java.util.Objects;

/**
 * Represents an embedded resource in a prompt message.
 * This allows prompts to reference other resources.
 */
public class EmbeddedResource implements PromptContent {
    private static final String TYPE = "resource";

    /**
     * The resource being embedded.
     */
    private ResourceReference resource;

    public EmbeddedResource() {}

    public EmbeddedResource(ResourceReference resource) {
        this.resource = resource;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    public ResourceReference getResource() {
        return resource;
    }

    public void setResource(ResourceReference resource) {
        this.resource = resource;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EmbeddedResource that = (EmbeddedResource) o;
        return Objects.equals(resource, that.resource);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resource);
    }

    @Override
    public String toString() {
        return "EmbeddedResource{" + "type='" + TYPE + '\'' + ", resource=" + resource + '}';
    }
}
