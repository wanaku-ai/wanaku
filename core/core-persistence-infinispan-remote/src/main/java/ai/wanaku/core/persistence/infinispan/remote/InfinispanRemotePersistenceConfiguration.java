package ai.wanaku.core.persistence.infinispan.remote;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.infinispan.client.hotrod.RemoteCacheManager;
import ai.wanaku.core.persistence.api.DataStoreRepository;
import ai.wanaku.core.persistence.api.ForwardReferenceRepository;
import ai.wanaku.core.persistence.api.NamespaceRepository;
import ai.wanaku.core.persistence.api.PromptReferenceRepository;
import ai.wanaku.core.persistence.api.ResourceReferenceRepository;
import ai.wanaku.core.persistence.api.ToolReferenceRepository;

@ApplicationScoped
public class InfinispanRemotePersistenceConfiguration {

    @Inject
    RemoteCacheManager cacheManager;

    @Produces
    ResourceReferenceRepository resourceReferenceRepository() {
        return new InfinispanRemoteResourceReferenceRepository(cacheManager);
    }

    @Produces
    ToolReferenceRepository toolReferenceRepository() {
        return new InfinispanRemoteToolReferenceRepository(cacheManager);
    }

    @Produces
    ForwardReferenceRepository forwardReferenceRepository() {
        return new InfinispanRemoteForwardReferenceRepository(cacheManager);
    }

    @Produces
    NamespaceRepository namespaceRepository() {
        return new InfinispanRemoteNamespaceRepository(cacheManager);
    }

    @Produces
    PromptReferenceRepository promptReferenceRepository() {
        return new InfinispanRemotePromptReferenceRepository(cacheManager);
    }

    @Produces
    DataStoreRepository dataStoreRepository() {
        return new InfinispanRemoteDataStoreRepository(cacheManager);
    }
}
