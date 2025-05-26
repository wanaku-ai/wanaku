package ai.wanaku.core.persistence.infinispan;

import ai.wanaku.core.mcp.providers.ServiceTarget;
import ai.wanaku.core.mcp.providers.ServiceType;
import ai.wanaku.core.persistence.types.ServiceTargetEntity;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.manager.EmbeddedCacheManager;

public class InfinispanResourceTargetRepository extends AbstractInfinispanRepository<ServiceTarget, ServiceTargetEntity, String> {

    protected InfinispanResourceTargetRepository(
            EmbeddedCacheManager cacheManager,
            Configuration configuration) {
        super(cacheManager, configuration);
    }

    @Override
    protected Class<ServiceTargetEntity> entityType() {
        return ServiceTargetEntity.class;
    }

    @Override
    protected String entityName() {
        return ServiceType.RESOURCE_PROVIDER.asValue();
    }

    @Override
    public ServiceTargetEntity convertToEntity(ServiceTarget model) {
        return new ServiceTargetEntity(model.getService(), model.getHost(), model.getPort(), model.getServiceType());
    }

    @Override
    public ServiceTarget convertToModel(ServiceTargetEntity entity) {
        return new ServiceTarget(entity.getService(), entity.getHost(), entity.getPort(), entity.getServiceType());
    }
}
