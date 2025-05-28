package ai.wanaku.api.types;

import java.util.Objects;

/**
 * This class represents a reference to a tool with various attributes such as name, description, URI, type, and input schema.
 */
public class RemoteToolReference implements CallableReference, WanakuEntity<String> {
    private String id;
    private String name;
    private String description;
    private String type;
    private InputSchema inputSchema;

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
        return Objects.equals(id, that.id) && Objects.equals(name, that.name) && Objects.equals(
                description, that.description) && Objects.equals(type, that.type) && Objects.equals(inputSchema,
                that.inputSchema);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, description, type, inputSchema);
    }

    @Override
    public String toString() {
        return "RemoteToolReference{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", type='" + type + '\'' +
                ", inputSchema=" + inputSchema +
                '}';
    }

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
}

