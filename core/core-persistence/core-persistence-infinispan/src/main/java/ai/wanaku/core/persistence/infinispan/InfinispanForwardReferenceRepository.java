package ai.wanaku.core.persistence.infinispan;

import ai.wanaku.api.types.ForwardReference;
import ai.wanaku.core.persistence.api.ForwardReferenceRepository;
import java.util.UUID;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.manager.EmbeddedCacheManager;

public class InfinispanForwardReferenceRepository extends AbstractInfinispanRepository<ForwardReference, String> implements
        ForwardReferenceRepository {

    public InfinispanForwardReferenceRepository(EmbeddedCacheManager cacheManager, Configuration configuration) {
        super(cacheManager, configuration);
    }

    @Override
    protected String entityName() {
        return "forward";
    }

    @Override
    protected Class<ForwardReference> entityType() {
        return ForwardReference.class;
    }

    @Override
    protected String newId() {
        return UUID.randomUUID().toString();
    }
}