package ai.wanaku.api.types;

import java.util.Objects;

/**
 * Represents a reference to a tool capability available on a remote MCP server.
 * <p>
 * A remote tool reference contains metadata about a tool that is provided by an
 * external service or remote MCP server, rather than being locally implemented.
 * This class is used when tools are discovered through forward proxies or when
 * integrating with remote capability providers.
 * <p>
 * Remote tool references can be converted to standard {@link ToolReference}
 * instances using the {@link #asToolReference(RemoteToolReference)} method,
 * which assigns a placeholder URI to indicate the remote nature of the tool.
 *
 * @see ToolReference
 * @see CallableReference
 */
public class RemoteToolReference extends LabelsAwareEntity<String> implements CallableReference {
    private String id;
    private String name;
    private String description;
    private String type;
    private InputSchema inputSchema;
    private String namespace;

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
     */
    public void setType(String type) {
        this.type = type;
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
        RemoteToolReference that = (RemoteToolReference) o;
        return Objects.equals(id, that.id)
                && Objects.equals(name, that.name)
                && Objects.equals(description, that.description)
                && Objects.equals(type, that.type)
                && Objects.equals(inputSchema, that.inputSchema)
                && Objects.equals(this.getLabels(), that.getLabels());
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, description, type, inputSchema);
    }

    @Override
    public String toString() {
        return "RemoteToolReference{" + "id='"
                + id + '\'' + ", name='"
                + name + '\'' + ", description='"
                + description + '\'' + ", type='"
                + type + '\'' + ", inputSchema="
                + inputSchema + '\'' + ", labels='"
                + this.getLabels() + '\'' + '}';
    }

    /**
     * Converts a remote tool reference to a standard tool reference.
     * <p>
     * This utility method creates a {@link ToolReference} from a {@link RemoteToolReference}
     * by copying all metadata and assigning a placeholder URI {@code "<remote>"} to indicate
     * that the tool is provided by a remote service.
     *
     * @param ref the remote tool reference to convert
     * @return a tool reference with the same metadata and a placeholder remote URI
     */
    public static ToolReference asToolReference(RemoteToolReference ref) {
        ToolReference ret = new ToolReference();

        ret.setDescription(ref.getDescription());
        ret.setInputSchema(ref.getInputSchema());
        ret.setName(ref.getName());
        ret.setType(ref.getType());
        ret.setUri("<remote>");
        ret.setId(ref.getId());

        return ret;
    }

    /**
     * Gets the namespace in which this remote tool is registered.
     *
     * @return the namespace identifier
     */
    @Override
    public String getNamespace() {
        return namespace;
    }

    /**
     * Sets the namespace in which this remote tool is registered.
     *
     * @param namespace the namespace identifier to set
     */
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }
}
