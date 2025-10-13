package ai.wanaku.backend.common;

import ai.wanaku.api.types.WanakuEntity;
import ai.wanaku.core.persistence.api.WanakuRepository;
import org.jboss.logging.Logger;

public abstract class AbstractBean<R extends WanakuEntity<String>> {

    private static final Logger LOG = Logger.getLogger(AbstractBean.class);

    /**
     * Removes entities from the repository by their name field.
     *
     * <p>This method performs a bulk removal operation for all entities
     * where the name field matches the specified value. If no entities
     * are found with the given name, a warning is logged.
     * This is meant for internal use, as it only removes from the repositories
     * but not from the tool/resource managers that actually contain the
     * references</p>
     *
     * @param name the name of the entity/entities to remove, must not be null
     * @return the number of entities removed from the repository
     * @throws IllegalArgumentException if name is null or blank
     * @throws RuntimeException if the repository operation fails
     */
    protected int removeByName(String name) {
        int removedCount = getRepository().removeByField("name", name);
        if (removedCount == 0) {
            LOG.warnf("No entities named '%s' were found for removal", name);
        } else {
            LOG.infof("Successfully removed %d entity(ies) with name '%s'", removedCount, name);
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
    protected abstract <I> WanakuRepository<R, I> getRepository();
}
