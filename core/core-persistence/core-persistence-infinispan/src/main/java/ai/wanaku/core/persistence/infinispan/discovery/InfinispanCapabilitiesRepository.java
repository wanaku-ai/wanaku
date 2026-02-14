package ai.wanaku.core.persistence.infinispan.discovery;

import jakarta.inject.Singleton;

import java.util.List;
import java.util.UUID;
import org.infinispan.Cache;
import org.infinispan.commons.api.query.Query;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.manager.EmbeddedCacheManager;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceType;
import ai.wanaku.core.persistence.infinispan.AbstractInfinispanRepository;

@Singleton
public class InfinispanCapabilitiesRepository extends AbstractInfinispanRepository<ServiceTarget, String> {
    private static final String MULTI_CAPABILITY = ServiceType.MULTI_CAPABILITY.asValue();

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

    /**
     * Find services by name and type.
     *
     * @param serviceName the name of the service
     * @param serviceType the type of the service (e.g., "resource-provider", "tool-invoker", "code-execution-engine")
     * @return a list of matching service targets
     */
    public List<ServiceTarget> findByService(String serviceName, String serviceType) {
        final Cache<Object, ServiceTarget> cache = cacheManager.getCache(entityName());

        Query<ServiceTarget> query = cache.query(
                "from ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget t where t.serviceName = :serviceName and (t.serviceType = :serviceType or t.serviceType = :multi)");

        query.setParameter("serviceName", serviceName);
        query.setParameter("serviceType", serviceType);
        query.setParameter("multi", MULTI_CAPABILITY);

        return query.maxResults(1).execute().list();
    }

    /**
     * List all services capable of serving resources of the given type.
     *
     * @param serviceType the service type (e.g., "resource-provider", "tool-invoker", "code-execution-engine")
     * @return a list of service targets capable of serving the given type
     */
    public List<ServiceTarget> listCapable(String serviceType) {
        final Cache<Object, ServiceTarget> cache = cacheManager.getCache(entityName());

        Query<ServiceTarget> query = cache.query(
                "from ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget t where (t.serviceType = :serviceType or t.serviceType = :multi)");

        query.setParameter("serviceType", serviceType);
        query.setParameter("multi", MULTI_CAPABILITY);

        return query.execute().list();
    }

    /**
     * Find a code execution service by matching serviceType, serviceSubType (engine-type), and serviceName (language).
     *
     * @param serviceType the service type (e.g., "code-execution-engine")
     * @param serviceSubType the engine type (e.g., "camel")
     * @param languageName the programming language (e.g., "yaml", "xml")
     * @return a list of matching service targets
     */
    public List<ServiceTarget> findCodeExecutionService(
            String serviceType, String serviceSubType, String languageName) {
        final Cache<Object, ServiceTarget> cache = cacheManager.getCache(entityName());

        Query<ServiceTarget> query = cache.query(
                "from ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget t where t.serviceType = :serviceType and t.serviceSubType = :serviceSubType and t.languageName = :languageName");

        query.setParameter("serviceType", serviceType);
        query.setParameter("serviceSubType", serviceSubType);
        query.setParameter("languageName", languageName);

        return query.maxResults(1).execute().list();
    }
}
