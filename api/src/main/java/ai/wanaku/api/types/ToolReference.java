package ai.wanaku.api.types;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ToolReference {
    private String name;
    private String description;
    private String uri;
    private String type;
    private InputSchema inputSchema;

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

    public String getUri() {
        return uri;
    }

    public ToolReference setUri(String uri) {
        this.uri = uri;
        return this;
    }

    public String getType() {
        return type;
    }

    public ToolReference setType(String type) {
        this.type = type;
        return this;
    }

    public InputSchema getInputSchema() {
        return inputSchema;
    }

    public void setInputSchema(InputSchema inputSchema) {
        this.inputSchema = inputSchema;
    }

    public static class InputSchema {
        private String type;
        private Map<String, Property> properties = new HashMap<>();
        private List<String> required;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Map<String, Property> getProperties() {
            return properties;
        }

        public void setProperties(Map<String, Property> properties) {
            this.properties = properties;
        }

        public List<String> getRequired() {
            return required;
        }

        public void setRequired(List<String> required) {
            this.required = required;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            InputSchema that = (InputSchema) o;
            return Objects.equals(type, that.type) && Objects.equals(properties,
                    that.properties) && Objects.equals(required, that.required);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, properties, required);
        }
    }

    public static class Property {
        private String type;
        private String description;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Property property = (Property) o;
            return Objects.equals(type, property.type) && Objects.equals(description, property.description);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, description);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ToolReference that = (ToolReference) o;
        return Objects.equals(name, that.name) && Objects.equals(description,
                that.description) && Objects.equals(uri, that.uri) && Objects.equals(type,
                that.type) && Objects.equals(inputSchema, that.inputSchema);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, uri, type, inputSchema);
    }
}

