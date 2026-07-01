package ai.wanaku.core.persistence.infinispan.remote;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.UUID;
import org.infinispan.client.hotrod.RemoteCache;
import io.quarkus.infinispan.client.Remote;
import ai.wanaku.capabilities.sdk.api.types.Namespace;
import ai.wanaku.core.persistence.api.NamespaceRepository;

@Singleton
public class InfinispanRemoteNamespaceRepository extends AbstractLabelAwareRemoteInfinispanRepository<Namespace, String>
        implements NamespaceRepository {

    @Inject
    public InfinispanRemoteNamespaceRepository(
            @Remote("namespace") RemoteCache<Object, Namespace> cache,
            InfinispanRemotePersistenceConfiguration config) {
        super(cache);
    }

    @Override
    protected Class<Namespace> entityType() {
        return Namespace.class;
    }

    @Override
    protected String newId() {
        return UUID.randomUUID().toString();
    }

    @Override
    public List<Namespace> findByName(String name) {
        return cache.values().stream().filter(ns -> name.equals(ns.getName())).toList();
    }

    @Override
    public List<Namespace> findFirstAvailable(String name) {
        return cache.values().stream()
                .filter(ns -> ns.getName() == null
                        && ns.getPath() != null
                        && !ns.getPath().isEmpty())
                .findFirst()
                .map(List::of)
                .orElse(List.of());
    }
}
