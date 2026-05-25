package ai.wanaku.core.persistence.infinispan.remote;

import java.util.List;
import java.util.UUID;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.commons.api.query.Query;
import ai.wanaku.capabilities.sdk.api.types.ForwardReference;
import ai.wanaku.core.persistence.api.ForwardReferenceRepository;

public class InfinispanRemoteForwardReferenceRepository
        extends AbstractRemoteInfinispanRepository<ForwardReference, String> implements ForwardReferenceRepository {

    public InfinispanRemoteForwardReferenceRepository(RemoteCacheManager cacheManager) {
        super(cacheManager);
    }

    @Override
    protected String entityName() {
        return "forward";
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
        Query<ForwardReference> query = cacheManager
                .getCache(entityName())
                .query("from ai.wanaku.capabilities.sdk.api.types.ForwardReference t where t.name = :name");
        query.setParameter("name", name);
        return query.execute().list();
    }
}
