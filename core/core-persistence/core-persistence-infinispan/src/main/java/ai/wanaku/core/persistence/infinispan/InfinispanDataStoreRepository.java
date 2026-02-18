package ai.wanaku.core.persistence.infinispan;

import java.util.List;
import java.util.UUID;
import org.infinispan.Cache;
import org.infinispan.commons.api.query.Query;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.manager.EmbeddedCacheManager;
import ai.wanaku.capabilities.sdk.api.exceptions.EntityAlreadyExistsException;
import ai.wanaku.capabilities.sdk.api.types.DataStore;
import ai.wanaku.core.persistence.api.DataStoreRepository;

/**
 * Infinispan implementation of the DataStoreRepository with label support.
 */
public class InfinispanDataStoreRepository extends AbstractLabelAwareInfinispanRepository<DataStore, String>
        implements DataStoreRepository {

    public InfinispanDataStoreRepository(EmbeddedCacheManager cacheManager, Configuration configuration) {
        super(cacheManager, configuration);
    }

    @Override
    protected String entityName() {
        return "datastore";
    }

    @Override
    protected Class<DataStore> entityType() {
        return DataStore.class;
    }

    @Override
    protected String newId() {
        return UUID.randomUUID().toString();
    }

    @Override
    public DataStore persist(DataStore entity) {
        try {
            lock.lock();
            if (!findByName(entity.getName()).isEmpty()) {
                throw EntityAlreadyExistsException.forName(entity.getName());
            }
            if (entity.getId() == null) {
                entity.setId(newId());
            }
            final Cache<Object, DataStore> cache = cacheManager.getCache(entityName());
            cache.put(entity.getId(), entity);
        } finally {
            lock.unlock();
        }
        return entity;
    }

    @Override
    public List<DataStore> findByName(String name) {
        Query<DataStore> query = cacheManager
                .getCache(entityName())
                .query("from ai.wanaku.capabilities.sdk.api.types.DataStore d where d.name = :name");
        query.setParameter("name", name);
        return query.execute().list();
    }
}
