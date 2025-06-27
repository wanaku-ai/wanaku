package ai.wanaku.api.types;

import java.util.Objects;

/**
 * This class represents a reference to a tool with various attributes such as name, description, URI, type, and input schema.
 */
public class ToolReference implements CallableReference, WanakuEntity<String> {
    private String id;
    private String name;
    private String description;
    private String uri;
    private String type;
    private InputSchema inputSchema;
    private String namespace;
    private String configurationURI;
    private String secretsURI;

    /**
     * Gets the name of the tool.
     *
     * @return the name of the tool
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the tool.
     *
     * @param name the new name of the tool
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the description of the tool.
     *
     * @return the description of the tool
     */
    @Override
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description of the tool.
     *
     * @param description the new description of the tool
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Gets the URI of the tool reference.
     *
     * @return the URI of the tool reference
     */
    public String getUri() {
        return uri;
    }

    /**
     * Sets the URI of the tool reference and returns the current object for method chaining.
     *
     * @param uri the new URI of the tool reference
     * @return the current ToolReference object
     */
    public ToolReference setUri(String uri) {
        this.uri = uri;
        return this;
    }

    /**
     * Gets the type of the tool reference.
     *
     * @return the type of the tool reference
     */
    @Override
    public String getType() {
        return type;
    }

    /**
     * Sets the type of the tool reference and returns the current object for method chaining.
     *
     * @param type the new type of the tool reference
     * @return the current ToolReference object
     */
    public ToolReference setType(String type) {
        this.type = type;
        return this;
    }

    /**
     * Gets the input schema of the tool reference.
     *
     * @return the input schema of the tool reference
     */
    @Override
    public InputSchema getInputSchema() {
        return inputSchema;
    }

    /**
     * Sets the input schema of the tool reference.
     *
     * @param inputSchema the new input schema of the tool reference
     */
    public void setInputSchema(InputSchema inputSchema) {
        this.inputSchema = inputSchema;
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    /**
     * Gets the location for the tool configuration
     * @return
     */
    public String getConfigurationURI() {
        return configurationURI;
    }

    public void setConfigurationURI(String configurationURI) {
        this.configurationURI = configurationURI;
    }

    /**
     * Gets the location for the secrets associated with this tool
     * @return
     */
    public String getSecretsURI() {
        return secretsURI;
    }

    public void setSecretsURI(String secretsURI) {
        this.secretsURI = secretsURI;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ToolReference that = (ToolReference) o;
        return Objects.equals(id, that.id) && Objects.equals(name, that.name) && Objects.equals(
                description, that.description) && Objects.equals(uri, that.uri) && Objects.equals(type,
                that.type) && Objects.equals(inputSchema, that.inputSchema) && Objects.equals(configurationURI,
                that.configurationURI) && Objects.equals(secretsURI, that.secretsURI);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, description, uri, type, inputSchema, configurationURI, secretsURI);
    }

    @Override
    public String toString() {
        return "ToolReference{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", uri='" + uri + '\'' +
                ", type='" + type + '\'' +
                ", inputSchema=" + inputSchema +
                ", configurations=" + configurationURI +
                ", secrets=" + secretsURI +
                '}';
    }
}

