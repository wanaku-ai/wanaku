package ai.wanaku.core.persistence.api;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import ai.wanaku.capabilities.sdk.api.types.WanakuEntity;

/**
 * Repository interface for the Wanaku persistence layer.
 *
 * <p>This interface provides standard operations for persisting, retrieving,
 * and managing entities in the Wanaku system.</p>
 *
 * @param <A> the model type
 * @param <C> the ID type
 */
public interface WanakuRepository<A extends WanakuEntity, C> {

    /**
     * Persists an entity to the repository.
     *
     * @param entity the entity to persist
     * @return the updated entity with the newly created ID if none was given
     */
    A persist(A entity);

    /**
     * Retrieves all models from the repository.
     *
     * @return a list of all models
     */
    List<A> listAll();

    /**
     * Deletes an entity by its ID.
     *
     * @param id the ID of the entity to delete
     * @return true if the entity was successfully deleted, false otherwise
     */
    boolean deleteById(C id);

    /**
     * Finds a model by its ID.
     *
     * @param id the ID of the model to find
     * @return the found model, or null if not found
     */
    A findById(C id);

    /**
     * Updates an entity to the repository.
     *
     * @param id the ID of the entity to update
     * @param entity the entity to update
     * @return true if the record was updated
     */
    boolean update(C id, A entity);

    /**
     * Remove entities matching a predicate
     *
     * @param matching the predicate to match
     * @return true if records were removed or false otherwise
     */
    boolean remove(Predicate<A> matching);

    /**
     * Gets the size (number of records) of the repository
     * @return the number of records in the repository
     */
    int size();

    /**
     * Removes entities from the cache where the specified field matches the given value.
     * Uses an Ickle query to perform bulk deletion based on field criteria.
     *
     * @param fieldName  the name of the field to match against
     * @param fieldValue the value that the field must equal for removal
     * @return the number of entities removed from the cache
     */
    int removeByField(String fieldName, Object fieldValue);

    /**
     * Removes entities from the cache where all specified fields match their corresponding values.
     * Uses an Ickle query with AND conditions for multiple field criteria.
     *
     * @param fields map of field names to their required values for removal
     * @return the number of entities removed from the cache
     * @throws IllegalArgumentException if fields map is null or empty
     */
    int removeByFields(Map<String, Object> fields);

    /**
     * Removes all entities from the repository.
     *
     * @return the number of entities removed
     */
    int removeAll();

    /**
     * Checks if an entity with the specified key exists in the repository.
     *
     * @param key the key to check for existence
     * @return {@code true} if an entity with the given key exists, {@code false} otherwise
     */
    boolean exists(C key);
}
