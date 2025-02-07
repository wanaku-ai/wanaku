package org.wanaku.api.types;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    }
}

