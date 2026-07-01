package ai.wanaku.core.persistence.infinispan.remote;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.UUID;
import org.infinispan.client.hotrod.RemoteCache;
import io.quarkus.infinispan.client.Remote;
import ai.wanaku.capabilities.sdk.api.types.ForwardReference;
import ai.wanaku.core.persistence.api.ForwardReferenceRepository;

@Singleton
public class InfinispanRemoteForwardReferenceRepository
        extends AbstractRemoteInfinispanRepository<ForwardReference, String> implements ForwardReferenceRepository {

    @Inject
    public InfinispanRemoteForwardReferenceRepository(@Remote("forward") RemoteCache<Object, ForwardReference> cache) {
        super(cache);
    }

    @Override
    protected Class<ForwardReference> entityType() {
        return ForwardReference.class;
    }

    @Override
    protected String newId() {
        return UUID.randomUUID().toString();
    }

    @Override
    public List<ForwardReference> findByName(String name) {
        return cache.values().stream().filter(fr -> name.equals(fr.getName())).toList();
    }
}
