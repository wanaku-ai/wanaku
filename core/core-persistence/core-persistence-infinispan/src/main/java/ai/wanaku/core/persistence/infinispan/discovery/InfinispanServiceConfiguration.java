package ai.wanaku.core.persistence.infinispan.discovery;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.manager.EmbeddedCacheManager;
import ai.wanaku.core.mcp.providers.ServiceRegistry;

public class InfinispanServiceConfiguration {
    // For now, default to the default capacity of an ArrayList
    @ConfigProperty(name = "wanaku.persistence.infinispan.max-state-count", defaultValue = "10")
    int maxStateCount;

    @ConfigProperty(name = "wanaku.persistence.infinispan.service-lookup-cache-ttl-seconds", defaultValue = "30")
    long lookupCacheTtlSeconds;

    @Inject
    EmbeddedCacheManager cacheManager;

    @Inject
    Configuration configuration;

    @Inject
    Instance<InfinispanCapabilitiesRepository> capabilitiesRepositoryInstance;

    @Inject
    Instance<InfinispanServiceRecordRepository> serviceRecordInstance;

    @Produces
    ServiceRegistry serviceRegistry() {
        ServiceLookupCache lookupCache = new ServiceLookupCache(cacheManager, lookupCacheTtlSeconds);

        InfinispanServiceRegistry registry = new InfinispanServiceRegistry(
                capabilitiesRepositoryInstance.get(), serviceRecordInstance.get(), lookupCache);

        registry.setMaxStateCount(maxStateCount);
        return registry;
    }
}
