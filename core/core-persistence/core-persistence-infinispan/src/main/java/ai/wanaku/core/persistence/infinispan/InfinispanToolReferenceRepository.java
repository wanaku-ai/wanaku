package ai.wanaku.core.persistence.infinispan;

import ai.wanaku.api.types.ToolReference;
import ai.wanaku.core.persistence.api.ToolReferenceRepository;
import ai.wanaku.core.persistence.types.ToolReferenceEntity;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.manager.EmbeddedCacheManager;

public class InfinispanToolReferenceRepository extends AbstractInfinispanRepository<ToolReference, ToolReferenceEntity, String> implements
        ToolReferenceRepository {

    public InfinispanToolReferenceRepository(EmbeddedCacheManager cacheManager, Configuration configuration) {
        super(cacheManager, configuration);
    }

    @Override
    protected String entityName() {
        return "tool";
    }

    @Override
    protected Class<ToolReferenceEntity> entityType() {
        return ToolReferenceEntity.class;
    }
}
