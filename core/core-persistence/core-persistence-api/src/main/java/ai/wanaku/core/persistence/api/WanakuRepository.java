package ai.wanaku.core.persistence.api;

import ai.wanaku.core.persistence.types.IdEntity;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Repository interface for the Wanaku persistence layer.
 *
 * <p>This interface provides standard operations for persisting, retrieving,
 * and managing entities in the Wanaku system.</p>
 *
 * @param <A> the model type
 * @param <B> the entity type which must extend IdEntity
 * @param <C> the ID type
 */
public interface WanakuRepository<A, B extends IdEntity, C> {

    /**
     * Persists a model to the repository.
     *
     * @param model the model to persist
     */
    void persist(A model);

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
     * Converts a model to its entity representation.
     *
     * @param model the model to convert
     * @return the entity representation of the model
     */
    B convertToEntity(A model);

    /**
     * Converts an entity to its model representation.
     *
     * @param model the entity to convert
     * @return the model representation of the entity
     */
    A convertToModel(B model);

    /**
     * Converts a list of entities to a list of models.
     *
     * <p>This default implementation uses the convertToModel method to convert each entity.</p>
     *
     * @param entities the list of entities to convert
     * @return a list of models
     */
    default List<A> convertToModels(List<B> entities) {
        return entities.stream().map(entity -> convertToModel(entity)).collect(Collectors.toList());
    }
}
