package ai.wanaku.core.persistence.infinispan.discovery;

import jakarta.inject.Singleton;

import ai.wanaku.api.types.providers.ServiceTarget;
import ai.wanaku.api.types.providers.ServiceType;
import ai.wanaku.core.persistence.infinispan.AbstractInfinispanRepository;
import java.util.UUID;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.manager.EmbeddedCacheManager;

@Singleton
public class InfinispanToolTargetRepository extends AbstractInfinispanRepository<ServiceTarget, String> {

    protected InfinispanToolTargetRepository(
            EmbeddedCacheManager cacheManager,
            Configuration configuration) {
        super(cacheManager, configuration);
    }

    @Override
    protected Class<ServiceTarget> entityType() {
        return ServiceTarget.class;
    }

    @Override
    protected String entityName() {
        return ServiceType.TOOL_INVOKER.asValue();
    }

    // For testing
    void deleteAll() {
        super.deleteALl();
    }

    @Override
    protected String newId() {
        return UUID.randomUUID().toString();
    }
}
