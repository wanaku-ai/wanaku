package ai.wanaku.api.types;

import java.util.Objects;

/**
 * Represents a reference to a forward service.
 *
 * This class holds information about the address of the forward service,
 * allowing it to be easily accessed and modified.
 */
public class ForwardReference implements WanakuEntity<String> {
    private String id;
    private String name;
    private String address;
    private String namespace;

    /**
     * The name of the reference
     * @return name of the reference as a string
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the reference
     * @param name the name of the reference as a string
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the address of the forward service.
     *
     * @return the address as a string
     */
    public String getAddress() {
        return address;
    }

    /**
     * Sets the address of the forward service.
     *
     * @param address the new address to use for the forward service
     */
    public void setAddress(String address) {
        this.address = address;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ForwardReference that = (ForwardReference) o;
        return Objects.equals(id, that.id)
                && Objects.equals(name, that.name)
                && Objects.equals(address, that.address)
                && Objects.equals(namespace, that.namespace);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, address, namespace);
    }

    @Override
    public String toString() {
        return "ForwardReference{" + "id='"
                + id + '\'' + ", name='"
                + name + '\'' + ", address='"
                + address + '\'' + ", namespace='"
                + namespace + '\'' + '}';
    }
}
