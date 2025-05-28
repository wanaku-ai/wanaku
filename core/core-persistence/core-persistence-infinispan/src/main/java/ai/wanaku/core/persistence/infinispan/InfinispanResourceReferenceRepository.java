package ai.wanaku.core.persistence.infinispan;

import ai.wanaku.api.types.ResourceReference;
import ai.wanaku.core.persistence.api.ResourceReferenceRepository;
import java.util.UUID;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.manager.EmbeddedCacheManager;

public class InfinispanResourceReferenceRepository extends AbstractInfinispanRepository<ResourceReference, String> implements
        ResourceReferenceRepository {

    public InfinispanResourceReferenceRepository(EmbeddedCacheManager cacheManager, Configuration configuration) {
        super(cacheManager, configuration);
    }

    @Override
    protected String entityName() {
        return "resource";
    }

    @Override
    protected Class<ResourceReference> entityType() {
        return ResourceReference.class;
    }

    @Override
    protected String newId() {
        return UUID.randomUUID().toString();
    }
}
