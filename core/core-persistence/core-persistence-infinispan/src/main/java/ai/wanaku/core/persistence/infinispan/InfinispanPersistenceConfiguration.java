package ai.wanaku.core.persistence.infinispan;

import ai.wanaku.core.persistence.api.ForwardReferenceRepository;
import ai.wanaku.core.persistence.api.NamespaceRepository;
import ai.wanaku.core.persistence.api.PromptReferenceRepository;
import ai.wanaku.core.persistence.api.ResourceReferenceRepository;
import ai.wanaku.core.persistence.api.ToolReferenceRepository;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.manager.EmbeddedCacheManager;

public class InfinispanPersistenceConfiguration {

    @Inject
    EmbeddedCacheManager cacheManager;

    @Inject
    Configuration configuration;

    @Produces
    ResourceReferenceRepository resourceReferenceRepository() {
        return new InfinispanResourceReferenceRepository(cacheManager, configuration);
    }

    @Produces
    ToolReferenceRepository toolReferenceRepository() {
        return new InfinispanToolReferenceRepository(cacheManager, configuration);
    }

    @Produces
    ForwardReferenceRepository forwardReferenceRepository() {
        return new InfinispanForwardReferenceRepository(cacheManager, configuration);
    }

    @Produces
    NamespaceRepository namespaceRepository() {
        return new InfinispanNamespaceRepository(cacheManager, configuration);
    }

    @Produces
    PromptReferenceRepository promptReferenceRepository() {
        return new InfinispanPromptReferenceRepository(cacheManager, configuration);
    }
}
