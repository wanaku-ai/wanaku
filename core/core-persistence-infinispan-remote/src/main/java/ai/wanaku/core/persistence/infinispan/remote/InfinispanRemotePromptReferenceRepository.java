package ai.wanaku.core.persistence.infinispan.remote;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.UUID;
import org.infinispan.client.hotrod.RemoteCache;
import io.quarkus.infinispan.client.Remote;
import ai.wanaku.capabilities.sdk.api.types.PromptReference;
import ai.wanaku.core.persistence.api.PromptReferenceRepository;

@Singleton
public class InfinispanRemotePromptReferenceRepository
        extends AbstractRemoteInfinispanRepository<PromptReference, String> implements PromptReferenceRepository {

    @Inject
    public InfinispanRemotePromptReferenceRepository(
            @Remote("prompt") RemoteCache<Object, PromptReference> cache,
            InfinispanRemotePersistenceConfiguration config) {
        super(cache);
    }

    @Override
    protected Class<PromptReference> entityType() {
        return PromptReference.class;
    }

    @Override
    protected String newId() {
        return UUID.randomUUID().toString();
    }

    @Override
    public List<PromptReference> findByName(String name) {
        return cache.values().stream().filter(pr -> name.equals(pr.getName())).toList();
    }

    @Override
    public List<PromptReference> findByNameAndNamespace(String name, String namespace) {
        return cache.values().stream()
                .filter(pr -> name.equals(pr.getName()) && namespace.equals(pr.getNamespace()))
                .toList();
    }
}
