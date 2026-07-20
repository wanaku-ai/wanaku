package ai.wanaku.backend.core.persistence.infinispan;

import java.util.List;
import java.util.UUID;
import org.infinispan.commons.api.query.Query;
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
        return "forward_roots";
    }

    @Override
    protected Class<ForwardRoots> entityType() {
        return ForwardRoots.class;
    }

    @Override
    protected String newId() {
        return UUID.randomUUID().toString();
    }

    @Override
    public List<ForwardRoots> findByForwardName(String forwardName) {
        Query<ForwardRoots> query = cacheManager
                .getCache(entityName())
                .query("from ai.wanaku.backend.bridge.ForwardRoots t where t.forwardName = :forwardName");
        query.setParameter("forwardName", forwardName);
        return query.execute().list();
    }
}
