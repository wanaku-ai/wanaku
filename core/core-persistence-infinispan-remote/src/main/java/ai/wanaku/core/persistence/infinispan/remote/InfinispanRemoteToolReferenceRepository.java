package ai.wanaku.core.persistence.infinispan.remote;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.UUID;
import org.infinispan.client.hotrod.RemoteCache;
import io.quarkus.infinispan.client.Remote;
import ai.wanaku.capabilities.sdk.api.types.ToolReference;
import ai.wanaku.core.persistence.api.ToolReferenceRepository;

@Singleton
public class InfinispanRemoteToolReferenceRepository
        extends AbstractLabelAwareRemoteInfinispanRepository<ToolReference, String> implements ToolReferenceRepository {

    @Inject
    public InfinispanRemoteToolReferenceRepository(
            @Remote("tool") RemoteCache<Object, ToolReference> cache, InfinispanRemotePersistenceConfiguration config) {
        super(cache);
    }

    @Override
    protected Class<ToolReference> entityType() {
        return ToolReference.class;
    }

    @Override
    protected String newId() {
        return UUID.randomUUID().toString();
    }

    @Override
    public List<ToolReference> findByName(String name) {
        return cache.values().stream().filter(tr -> name.equals(tr.getName())).toList();
    }
}
