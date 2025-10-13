package ai.wanaku.api.types;

/**
 * Represents a namespace within the Wanaku system.
 * <p>
 * Namespaces provide logical grouping and isolation for capabilities, tools, and resources.
 * Each namespace has a unique identifier, a human-readable name, and a path that defines
 * its location within the namespace hierarchy.
 * </p>
 */
public class Namespace implements WanakuEntity<String> {
    private String id;
    private String name;
    private String path;

    /**
     * Gets the unique identifier for this namespace.
     *
     * @return the namespace identifier
     */
    @Override
    public String getId() {
        return id;
    }

    /**
     * Sets the unique identifier for this namespace.
     *
     * @param id the namespace identifier to set
     */
    @Override
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Gets the human-readable name of this namespace.
     *
     * @return the namespace name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the human-readable name of this namespace.
     *
     * @param name the namespace name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the hierarchical path of this namespace.
     * <p>
     * The path defines the location of this namespace within the namespace hierarchy
     * and is used for namespace resolution and organization.
     * </p>
     *
     * @return the namespace path
     */
    public String getPath() {
        return path;
    }

    /**
     * Sets the hierarchical path of this namespace.
     *
     * @param path the namespace path to set
     */
    public void setPath(String path) {
        this.path = path;
    }
}
