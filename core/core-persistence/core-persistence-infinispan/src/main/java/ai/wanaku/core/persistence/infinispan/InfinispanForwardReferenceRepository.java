package ai.wanaku.core.persistence.infinispan;

import ai.wanaku.api.types.ForwardReference;
import ai.wanaku.core.persistence.api.ForwardReferenceRepository;
import ai.wanaku.core.persistence.types.ForwardEntity;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.manager.EmbeddedCacheManager;

public class InfinispanForwardReferenceRepository extends AbstractInfinispanRepository<ForwardReference, ForwardEntity, String> implements
        ForwardReferenceRepository {

    public InfinispanForwardReferenceRepository(EmbeddedCacheManager cacheManager, Configuration configuration) {
        super(cacheManager, configuration);
    }

    @Override
    protected String entityName() {
        return "forward";
    }

    @Override
    protected Class<ForwardEntity> entityType() {
        return ForwardEntity.class;
    }
}