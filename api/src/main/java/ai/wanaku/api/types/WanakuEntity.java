package ai.wanaku.api.types;

/**
 * Base interface for all Wanaku entity types.
 * <p>
 * This interface provides a common contract for entities that require identity management
 * within the Wanaku system. All entities implementing this interface must provide an
 * identifier of type {@code K}.
 *
 * @param <K> the type of the entity identifier
 */
public interface WanakuEntity<K> {

    /**
     * Gets the unique identifier for this entity.
     *
     * @return the entity identifier, or {@code null} if not yet assigned
     */
    K getId();

    /**
     * Sets the unique identifier for this entity.
     *
     * @param id the entity identifier to set
     */
    void setId(K id);
}
