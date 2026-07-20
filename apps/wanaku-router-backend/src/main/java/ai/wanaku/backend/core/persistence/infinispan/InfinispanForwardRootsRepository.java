package ai.wanaku.backend.core.persistence.infinispan;

import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.cache.StorageType;
import org.infinispan.manager.EmbeddedCacheManager;
import ai.wanaku.backend.bridge.ForwardRoots;
import ai.wanaku.backend.core.persistence.api.ForwardRootsRepository;

public class InfinispanForwardRootsRepository extends AbstractInfinispanRepository<ForwardRoots, String>
        implements ForwardRootsRepository {

    public InfinispanForwardRootsRepository(EmbeddedCacheManager cacheManager, Configuration configuration) {
        super(cacheManager, configuration);
    }

    @Override
    protected void configure(Configuration configuration) {
        if (cacheManager.getCacheConfiguration(entityName()) == null) {
            // Use a simple heap-only configuration without a file store.
            // Root data is lightweight and is reconstructed on startup from
            // persisted forward references, so file persistence is not needed.
            Configuration heapOnly = new ConfigurationBuilder()
                    .clustering()
                    .cacheMode(CacheMode.LOCAL)
                    .memory()
                    .storage(StorageType.HEAP)
                    .maxCount(1000)
                    .build();
            cacheManager.defineConfiguration(entityName(), heapOnly);
        }
    }

    @Override
    protected String entityName() {
        return "forward-roots";
    }

    @Override
    protected Class<ForwardRoots> entityType() {
        return ForwardRoots.class;
    }

    @Override
    protected String newId() {
        // Not used; name is the key
        return null;
    }
}
