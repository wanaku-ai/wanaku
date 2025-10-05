package ai.wanaku.core.persistence.infinispan.discovery;

import ai.wanaku.api.types.providers.ServiceTarget;
import ai.wanaku.api.types.providers.ServiceType;
import ai.wanaku.core.persistence.infinispan.AbstractInfinispanRepository;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.UUID;
import org.infinispan.Cache;
import org.infinispan.commons.api.query.Query;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.manager.EmbeddedCacheManager;

@Singleton
public class InfinispanCapabilitiesRepository extends AbstractInfinispanRepository<ServiceTarget, String> {

    protected InfinispanCapabilitiesRepository(EmbeddedCacheManager cacheManager, Configuration configuration) {
        super(cacheManager, configuration);
    }

    @Override
    protected Class<ServiceTarget> entityType() {
        return ServiceTarget.class;
    }

    @Override
    protected String entityName() {
        return "capabilities";
    }

    // For testing
    void deleteAll() {
        super.deleteALl();
    }

    @Override
    protected String newId() {
        return UUID.randomUUID().toString();
    }

    public List<ServiceTarget> findByService(String serviceName, ServiceType serviceType) {
        final Cache<Object, ServiceTarget> cache = cacheManager.getCache(entityName());

        Query<ServiceTarget> query = cache.query(
                "from ai.wanaku.api.types.providers.ServiceTarget t where t.service = :service and t.serviceType = :serviceType");

        query.setParameter("service", serviceName);
        query.setParameter("serviceType", serviceType);

        return query.maxResults(1).execute().list();
    }

    public List<ServiceTarget> listByType(ServiceType serviceType) {
        final Cache<Object, ServiceTarget> cache = cacheManager.getCache(entityName());

        Query<ServiceTarget> query =
                cache.query("from ai.wanaku.api.types.providers.ServiceTarget t where t.serviceType = :serviceType");

        query.setParameter("serviceType", serviceType);

        return query.maxResults(1).execute().list();
    }
}
