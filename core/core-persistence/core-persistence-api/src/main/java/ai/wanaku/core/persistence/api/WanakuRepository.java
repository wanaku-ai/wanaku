package ai.wanaku.core.persistence.api;

import ai.wanaku.api.types.WanakuEntity;

import java.util.List;
import java.util.stream.Collectors;

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
     * @param entity the entity to update
     * @return true if the record was updated
     */
    boolean update(C id, A entity);


}
