package ai.wanaku.core.persistence.infinispan.discovery;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import ai.wanaku.core.mcp.providers.ServiceRegistry;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.manager.EmbeddedCacheManager;

public class InfinispanServiceConfiguration {
    // For now, default to the default capacity of an ArrayList
    @ConfigProperty(name = "wanaku.persistence.infinispan.max-state-count", defaultValue = "10")
    int maxStateCount;

    @Inject
    EmbeddedCacheManager cacheManager;

    @Inject
    Configuration configuration;

    @Inject
    Instance<InfinispanResourceTargetRepository> targetRepositoryInstance;

    @Inject
    Instance<InfinispanToolTargetRepository> toolTargetRepositoryInstance;

    @Inject
    Instance<InfinispanServiceRecordRepository> serviceRecordInstance;

    @Produces
    ServiceRegistry serviceRegistry() {
        InfinispanServiceRegistry registry = new InfinispanServiceRegistry(targetRepositoryInstance.get(), toolTargetRepositoryInstance.get(),
                serviceRecordInstance.get());

        registry.setMaxStateCount(maxStateCount);
        return registry;
    }
}
