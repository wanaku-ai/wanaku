package ai.wanaku.core.persistence.infinispan.remote;

import java.util.List;
import java.util.UUID;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.commons.api.query.Query;
import ai.wanaku.capabilities.sdk.api.types.DataStore;
import ai.wanaku.core.persistence.api.DataStoreRepository;

public class InfinispanRemoteDataStoreRepository extends AbstractLabelAwareRemoteInfinispanRepository<DataStore, String>
        implements DataStoreRepository {

    public InfinispanRemoteDataStoreRepository(RemoteCacheManager cacheManager) {
        super(cacheManager);
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
    public List<DataStore> findByName(String name) {
        Query<DataStore> query = cacheManager
                .getCache(entityName())
                .query("from ai.wanaku.capabilities.sdk.api.types.DataStore d where d.name = :name");
        query.setParameter("name", name);
        return query.execute().list();
    }
}
