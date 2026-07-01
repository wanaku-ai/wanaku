package ai.wanaku.core.persistence.infinispan.remote;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.UUID;
import org.infinispan.client.hotrod.RemoteCache;
import io.quarkus.infinispan.client.Remote;
import ai.wanaku.capabilities.sdk.api.types.DataStore;
import ai.wanaku.core.persistence.api.DataStoreRepository;

@Singleton
public class InfinispanRemoteDataStoreRepository extends AbstractLabelAwareRemoteInfinispanRepository<DataStore, String>
        implements DataStoreRepository {

    @Inject
    public InfinispanRemoteDataStoreRepository(
            @Remote("datastore") RemoteCache<Object, DataStore> cache,
            InfinispanRemotePersistenceConfiguration config) {
        super(cache);
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
        return cache.values().stream().filter(ds -> name.equals(ds.getName())).toList();
    }
}
