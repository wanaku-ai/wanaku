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
    private String namespace;

    /**
     * Gets the location of the resource.
     *
     * @return the resource location (e.g., URL, file path)
     */
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

    /**
     * Gets the type of the resource.
     *
     * @return the resource type (e.g., image, text, binary)
     */
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

    /**
     * Gets the brief name of the resource.
     *
     * @return the resource name
     */
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

    /**
     * Gets the longer description of the resource.
     *
     * @return the resource description
     */
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

    /**
     * Gets the MIME type of the resource.
     *
     * @return the MIME type (e.g., image/jpeg, text/plain)
     */
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

    /**
     * Gets the list of parameters associated with the resource.
     *
     * @return the list of resource parameters
     */
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

    /**
     * Gets the URI location for the resource configuration.
     * <p>
     * The configuration URI points to the location where non-sensitive
     * configuration data for this resource is stored.
     * </p>
     *
     * @return the configuration URI, or {@code null} if not configured
     */
    public String getConfigurationURI() {
        return configurationURI;
    }

    /**
     * Sets the URI location for the resource configuration.
     *
     * @param configurationURI the configuration URI to set
     */
    public void setConfigurationURI(String configurationURI) {
        this.configurationURI = configurationURI;
    }

    /**
     * Gets the URI location for the secrets associated with this resource.
     * <p>
     * The secrets URI points to the location where sensitive data such as
     * credentials, API keys, or tokens for this resource are stored.
     * </p>
     *
     * @return the secrets URI, or {@code null} if not configured
     */
    public String getSecretsURI() {
        return secretsURI;
    }

    /**
     * Sets the URI location for the secrets associated with this resource.
     *
     * @param secretsURI the secrets URI to set
     */
    public void setSecretsURI(String secretsURI) {
        this.secretsURI = secretsURI;
    }

    /**
     * Gets the namespace in which this resource is registered.
     *
     * @return the namespace identifier
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Sets the namespace in which this resource is registered.
     *
     * @param namespace the namespace identifier to set
     */
    public void setNamespace(String namespace) {
        this.namespace = namespace;
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

        /**
         * Gets the name of the parameter.
         *
         * @return the parameter name
         */
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

        /**
         * Gets the value of the parameter.
         *
         * @return the parameter value
         */
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
    public String toString() {
        return "ResourceReference{" + "id='"
                + id + '\'' + ", location='"
                + location + '\'' + ", type='"
                + type + '\'' + ", name='"
                + name + '\'' + ", description='"
                + description + '\'' + ", mimeType='"
                + mimeType + '\'' + ", params="
                + params + ", configurationURI='"
                + configurationURI + '\'' + ", secretsURI='"
                + secretsURI + '\'' + ", namespace='"
                + namespace + '\'' + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ResourceReference that = (ResourceReference) o;
        return Objects.equals(id, that.id)
                && Objects.equals(location, that.location)
                && Objects.equals(type, that.type)
                && Objects.equals(name, that.name)
                && Objects.equals(description, that.description)
                && Objects.equals(mimeType, that.mimeType)
                && Objects.equals(params, that.params)
                && Objects.equals(configurationURI, that.configurationURI)
                && Objects.equals(secretsURI, that.secretsURI)
                && Objects.equals(namespace, that.namespace);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id, location, type, name, description, mimeType, params, configurationURI, secretsURI, namespace);
    }
}
