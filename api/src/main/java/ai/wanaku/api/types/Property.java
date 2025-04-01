package ai.wanaku.api.types;

import java.util.Objects;

/**
 * Represents a single property in the input schema.
 */
public class Property {

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
     * Sets the scope of the property.
     *
     * @param scope the new scope of the property
     */
    public void setScope(String scope) {
        this.scope = scope;
    }

    /**
     * Gets the value of the property.
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
        return Objects.equals(type, that.type) &&
                Objects.equals(description, that.description) &&
                Objects.equals(target, that.target) &&
                Objects.equals(scope, that.scope) &&
                Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, description, target, scope, value);
    }
}
