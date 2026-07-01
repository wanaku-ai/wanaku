package ai.wanaku.core.persistence.infinispan.remote;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.UUID;
import org.infinispan.client.hotrod.RemoteCache;
import io.quarkus.infinispan.client.Remote;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;
import ai.wanaku.core.persistence.api.ResourceReferenceRepository;

@Singleton
public class InfinispanRemoteResourceReferenceRepository
        extends AbstractLabelAwareRemoteInfinispanRepository<ResourceReference, String>
        implements ResourceReferenceRepository {

    @Inject
    public InfinispanRemoteResourceReferenceRepository(
            @Remote("resource") RemoteCache<Object, ResourceReference> cache,
            InfinispanRemotePersistenceConfiguration config) {
        super(cache);
    }

    @Override
    protected Class<ResourceReference> entityType() {
        return ResourceReference.class;
    }

    @Override
    protected String newId() {
        return UUID.randomUUID().toString();
    }

    @Override
    public List<ResourceReference> findByName(String name) {
        return cache.values().stream().filter(rr -> name.equals(rr.getName())).toList();
    }
}
