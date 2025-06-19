package ai.wanaku.api.types;

import java.util.List;
import java.util.Objects;

/**
 * Represents a resource reference, containing details such as location, type, and parameters.
 */
public class ResourceReference implements WanakuEntity<String> {
    private String id;

    /**
     * The location of the resource (e.g., URL, file path).
     */
    private String location;

    /**
     * The type of the resource (e.g., image, text, binary).
     */
    private String type;

    /**
     * A brief name for the resource.
     */
    private String name;

    /**
     * A longer description of the resource.
     */
    private String description;

    /**
     * The MIME type of the resource (e.g., image/jpeg, text/plain).
     */
    private String mimeType;

    /**
     * A list of parameters associated with the resource.
     */
    private List<Param> params;

    private String configurationURI;
    private String secretsURI;

    public String getLocation() {
        return location;
    }

    /**
     * Sets the location of the resource.
     *
     * @param location The new location of the resource.
     */
    public void setLocation(String location) {
        this.location = location;
    }

    public String getType() {
        return type;
    }

    /**
     * Sets the type of the resource.
     *
     * @param type The new type of the resource.
     */
    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    /**
     * Sets a brief name for the resource.
     *
     * @param name The new name of the resource.
     */
    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Sets a longer description of the resource.
     *
     * @param description The new description of the resource.
     */
    public void setDescription(String description) {
        this.description = description;
    }

    public String getMimeType() {
        return mimeType;
    }

    /**
     * Sets the MIME type of the resource.
     *
     * @param mimeType The new MIME type of the resource.
     */
    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public List<Param> getParams() {
        return params;
    }

    /**
     * Sets a list of parameters associated with the resource.
     *
     * @param params The new list of parameters for the resource.
     */
    public void setParams(List<Param> params) {
        this.params = params;
    }

    public String getConfigurationURI() {
        return configurationURI;
    }

    public void setConfigurationURI(String configurationURI) {
        this.configurationURI = configurationURI;
    }

    public String getSecretsURI() {
        return secretsURI;
    }

    public void setSecretsURI(String secretsURI) {
        this.secretsURI = secretsURI;
    }

    /**
     * A nested class representing a parameter of the resource.
     */
    public static class Param {
        /**
         * The name of the parameter (e.g. "key", "value").
         */
        private String name;

        /**
         * The value of the parameter.
         */
        private String value;

        public String getName() {
            return name;
        }

        /**
         * Sets the name of the parameter.
         *
         * @param name The new name of the parameter.
         */
        public void setName(String name) {
            this.name = name;
        }

        public String getValue() {
            return value;
        }

        /**
         * Sets the value of the parameter.
         *
         * @param value The new value of the parameter.
         */
        public void setValue(String value) {
            this.value = value;
        }
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
        ResourceReference that = (ResourceReference) o;
        return Objects.equals(id, that.id) && Objects.equals(location,
                that.location) && Objects.equals(type, that.type) && Objects.equals(name,
                that.name) && Objects.equals(description, that.description) && Objects.equals(mimeType,
                that.mimeType) && Objects.equals(params, that.params) && Objects.equals(configurationURI,
                that.configurationURI) && Objects.equals(secretsURI, that.secretsURI);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, location, type, name, description, mimeType, params, configurationURI, secretsURI);
    }

    @Override
    public String toString() {
        return "ResourceReference{" +
                "id='" + id + '\'' +
                ", location='" + location + '\'' +
                ", type='" + type + '\'' +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", mimeType='" + mimeType + '\'' +
                ", params=" + params +
                ", configurations=" + configurationURI +
                ", secrets=" + secretsURI +
                '}';
    }
}
