package ai.wanaku.server.quarkus.common;

import ai.wanaku.api.types.WanakuEntity;
import ai.wanaku.core.persistence.api.WanakuRepository;
import org.jboss.logging.Logger;

public abstract class AbstractBean<R extends WanakuEntity> {

    private static final Logger LOG = Logger.getLogger(AbstractBean.class);

    /**
     * Removes entities from the repository by their name field.
     *
     * <p>This method performs a bulk removal operation for all entities
     * where the name field matches the specified value. If no entities
     * are found with the given name, a warning is logged.</p>
     *
     * @param name the name of the entity/entities to remove, must not be null
     * @return the number of entities removed from the repository
     * @throws IllegalArgumentException if name is null or blank
     * @throws RuntimeException if the repository operation fails
     */

    public int removeByName(String name) {

        int removedCount = getRepository().removeByField("name", name);
        if (removedCount == 0) {
            LOG.warnf("No entities named '%s' were found for removal", name);
        } else {
            LOG.infof("Successfully removed %d entity(ies) with name '%s'", removedCount, name);
        }

        return removedCount;
    }


    /**
     * Removes an entity from the repository by its unique identifier.
     *
     * <p>This method removes a single entity matching the specified ID.
     * If no entity is found with the given ID, a warning is logged.</p>
     *
     * @param id the unique identifier of the entity to remove, must not be null
     * @return the number of entities removed (0 or 1)
     * @throws IllegalArgumentException if id is null or blank
     * @throws RuntimeException if the repository operation fails
     */
    public int removeById(String id) {
        LOG.debugf("Attempting to remove entity with id: %s", id);
        int removedCount = getRepository().removeByField("id", id);

        if (removedCount == 0) {
            LOG.warnf("No entity with id '%s' was found for removal", id);
        } else {
            LOG.infof("Successfully removed entity with id '%s'", id);
        }

        return removedCount;
    }


    /**
     * Retrieves the repository instance for the managed entity type.
     *
     * <p>Concrete implementations must provide the appropriate repository
     * instance. This method is called by the base class operations to
     * perform data access operations.</p>
     *
     * @param <I> the type of the entity identifier
     * @return the repository instance for type R with identifier type I
     */
    protected abstract <I> WanakuRepository<R,I> getRepository();



}
