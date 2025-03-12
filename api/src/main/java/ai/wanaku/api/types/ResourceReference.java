package ai.wanaku.api.types;

import java.util.List;

/**
 * Represents a resource reference, containing details such as location, type, and parameters.
 */
public class ResourceReference {
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
}
