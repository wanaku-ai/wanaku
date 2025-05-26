package ai.wanaku.core.persistence.infinispan;

import ai.wanaku.api.types.ResourceReference;
import ai.wanaku.core.persistence.api.ResourceReferenceRepository;
import ai.wanaku.core.persistence.types.ResourceReferenceEntity;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.manager.EmbeddedCacheManager;

public class InfinispanResourceReferenceRepository extends AbstractInfinispanRepository<ResourceReference, ResourceReferenceEntity, String> implements
        ResourceReferenceRepository {

    public InfinispanResourceReferenceRepository(EmbeddedCacheManager cacheManager, Configuration configuration) {
        super(cacheManager, configuration);
    }

    @Override
    protected String entityName() {
        return "resource";
    }

    @Override
    protected Class<ResourceReferenceEntity> entityType() {
        return ResourceReferenceEntity.class;
    }
}
