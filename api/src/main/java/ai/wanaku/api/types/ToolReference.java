package ai.wanaku.api.types;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * This class represents a reference to a tool with various attributes such as name, description, URI, type, and input schema.
 */
public class ToolReference {

    private String name;
    private String description;
    private String uri;
    private String type;
    private InputSchema inputSchema;

    /**
     * Gets the name of the tool.
     *
     * @return the name of the tool
     */
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

    /**
     * Represents the input schema for a tool, including type, properties, and required fields.
     */
    public static class InputSchema {

        private String type;
        private Map<String, Property> properties = new HashMap<>();
        private List<String> required;

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

    /**
     * Represents a single property in the input schema.
     */
    public static class Property {

        private String type;
        private String description;
        private String target;
        private String scope;
        private String value;


        /**
         * Gets the type of the property.
         *
         * @return the type of the property
         */
        public String getType() {
            return type;
        }

        /**
         * Sets the type of the property.
         *
         * @param type the new type of the property
         */
        public void setType(String type) {
            this.type = type;
        }

        /**
         * Gets the description of the property.
         *
         * @return the description of the property
         */
        public String getDescription() {
            return description;
        }

        /**
         * Sets the description of the property.
         *
         * @param description the new description of the property
         */
        public void setDescription(String description) {
            this.description = description;
        }

        /**
         * Gets the target of the property.
         *
         * @return the description of the property
         */
        public String getTarget() {
            return target;
        }

        /**
         * Sets the target of the property.
         *
         * @param target the new target of the property
         */
        public void setTarget(String target) {
            this.target = target;
        }

        /**
         * Gets the scope of the property.
         *
         * @return the scope of the property
         */
        public String getScope() {
            return scope;
        }

        /**
         *  Sets the scope of the property.
         *
         * @param scope the new scope of the property
         */
        public void setScope(String scope) {
            this.scope = scope;
        }

        /**
         *  Gets the value of the property.
         *
         * @return the value of the property
         */
        public String getValue() {
            return value;
        }

        /**
         * Sets the value of the property.
         *
         * @param value the new value of the property
         */
        public void setValue(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Property that = (Property) o;
            return  Objects.equals(type, that.type) &&
                    Objects.equals(description, that.description) &&
                    Objects.equals(target, that.target) &&
                    Objects.equals(scope, that.scope) &&
                    Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, description,target,scope,value);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ToolReference that = (ToolReference) o;
        return Objects.equals(name, that.name)
                && Objects.equals(description, that.description)
                && Objects.equals(uri, that.uri)
                && Objects.equals(type, that.type)
                && Objects.equals(inputSchema, that.inputSchema);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, uri, type, inputSchema);
    }
}

