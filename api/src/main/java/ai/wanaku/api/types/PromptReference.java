package ai.wanaku.api.types;

import java.util.List;
import java.util.Objects;

/**
 * Represents a prompt reference in the MCP protocol.
 * Prompts are reusable templates that can leverage multiple tools and provide
 * example interactions for LLMs.
 */
public class PromptReference implements WanakuEntity<String> {
    private String id;

    /**
     * The name of the prompt.
     */
    private String name;

    /**
     * A description of what the prompt does.
     */
    private String description;

    /**
     * The prompt messages following MCP specification.
     * Each message has a role (user, assistant) and content.
     */
    private List<PromptMessage> messages;

    /**
     * List of arguments/parameters this prompt accepts.
     */
    private List<PromptArgument> arguments;

    /**
     * Optional list of tool names that this prompt may use.
     */
    private List<String> toolReferences;

    /**
     * The namespace this prompt belongs to.
     */
    private String namespace;

    /**
     * Configuration URI for the prompt.
     */
    private String configurationURI;

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<PromptMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<PromptMessage> messages) {
        this.messages = messages;
    }

    public List<PromptArgument> getArguments() {
        return arguments;
    }

    public void setArguments(List<PromptArgument> arguments) {
        this.arguments = arguments;
    }

    public List<String> getToolReferences() {
        return toolReferences;
    }

    public void setToolReferences(List<String> toolReferences) {
        this.toolReferences = toolReferences;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getConfigurationURI() {
        return configurationURI;
    }

    public void setConfigurationURI(String configurationURI) {
        this.configurationURI = configurationURI;
    }

    /**
     * Represents an argument/parameter for a prompt.
     */
    public static class PromptArgument {
        /**
         * The name of the argument.
         */
        private String name;

        /**
         * A description of the argument.
         */
        private String description;

        /**
         * Whether this argument is required.
         */
        private boolean required;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            PromptArgument that = (PromptArgument) o;
            return required == that.required
                    && Objects.equals(name, that.name)
                    && Objects.equals(description, that.description);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, description, required);
        }

        @Override
        public String toString() {
            return "PromptArgument{" + "name='"
                    + name + '\'' + ", description='"
                    + description + '\'' + ", required="
                    + required + '}';
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PromptReference that = (PromptReference) o;
        return Objects.equals(id, that.id)
                && Objects.equals(name, that.name)
                && Objects.equals(description, that.description)
                && Objects.equals(messages, that.messages)
                && Objects.equals(arguments, that.arguments)
                && Objects.equals(toolReferences, that.toolReferences)
                && Objects.equals(namespace, that.namespace)
                && Objects.equals(configurationURI, that.configurationURI);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, description, messages, arguments, toolReferences, namespace, configurationURI);
    }

    @Override
    public String toString() {
        return "PromptReference{" + "id='"
                + id + '\'' + ", name='"
                + name + '\'' + ", description='"
                + description + '\'' + ", messages="
                + messages + ", arguments="
                + arguments + ", toolReferences="
                + toolReferences + ", namespace='"
                + namespace + '\'' + ", configurationURI='"
                + configurationURI + '\'' + '}';
    }
}
