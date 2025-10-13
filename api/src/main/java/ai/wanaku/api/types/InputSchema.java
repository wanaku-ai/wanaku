package ai.wanaku.api.types;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents the input schema for a tool, including type, properties, and required fields.
 */
public class InputSchema {

    private String type;
    private Map<String, Property> properties = new HashMap<>();
    private List<String> required;

    /**
     * Default constructor for InputSchema.
     */
    public InputSchema() {}

    /**
     * Gets the type of the input schema.
     *
     * @return the type of the input schema
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the type of the input schema.
     *
     * @param type the new type of the input schema
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Gets the properties of the input schema.
     *
     * @return a map of property names to Property objects
     */
    public Map<String, Property> getProperties() {
        return properties;
    }

    /**
     * Sets the properties of the input schema.
     *
     * @param properties a map of property names to Property objects
     */
    public void setProperties(Map<String, Property> properties) {
        this.properties = properties;
    }

    /**
     * Gets the list of required fields in the input schema.
     *
     * @return a list of required field names
     */
    public List<String> getRequired() {
        return required;
    }

    /**
     * Sets the list of required fields in the input schema.
     *
     * @param required a list of required field names
     */
    public void setRequired(List<String> required) {
        this.required = required;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        InputSchema that = (InputSchema) o;
        return Objects.equals(type, that.type)
                && Objects.equals(properties, that.properties)
                && Objects.equals(required, that.required);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, properties, required);
    }
}
