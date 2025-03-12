package ai.wanaku.api.types;

import jakarta.persistence.CascadeType;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "TOOL_REFERENCE")
public class ToolReference {
    @Id
    private String name;
    private String description;
    private String uri;
    private String type;
    @OneToOne(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
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

    @Entity
    @Table(name = "INPUT_SCHEMA")
    public static class InputSchema {
        @Id
        private String type;
        @Id
        private String name;
        @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
        @JoinColumns({
                @JoinColumn(name = "name"),
                @JoinColumn(name = "inputSchemaType")
        })
        private List<Property> properties = new ArrayList<>();
        @ElementCollection(fetch = FetchType.EAGER)
        private List<String> required;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public List<Property> getProperties() {
            return properties;
        }

        public void setProperties(List<Property> properties) {
            this.properties = properties;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
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

    @Entity
    @Table(name = "PROPERTY")
    public static class Property {
        @Id
        private String propertyName;
        @Id
        private String name;
        private String inputSchemaType;
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

        public String getInputSchemaType() {
            return inputSchemaType;
        }

        public void setInputSchemaType(String inputSchemaType) {
            this.inputSchemaType = inputSchemaType;
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

        public String getPropertyName() {
            return propertyName;
        }

        public void setPropertyName(String propertyName) {
            this.propertyName = propertyName;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
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

