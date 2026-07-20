package ai.wanaku.backend.core.persistence.infinispan;

import org.infinispan.configuration.cache.Configuration;
import org.infinispan.manager.EmbeddedCacheManager;
import ai.wanaku.backend.bridge.ForwardRoots;
import ai.wanaku.backend.core.persistence.api.ForwardRootsRepository;

public class InfinispanForwardRootsRepository extends AbstractInfinispanRepository<ForwardRoots, String>
        implements ForwardRootsRepository {

    public InfinispanForwardRootsRepository(EmbeddedCacheManager cacheManager, Configuration configuration) {
        super(cacheManager, configuration);
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
