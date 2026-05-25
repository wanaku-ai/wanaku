package ai.wanaku.core.persistence.infinispan.remote;

import java.util.List;
import java.util.UUID;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.commons.api.query.Query;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;
import ai.wanaku.core.persistence.api.ResourceReferenceRepository;

public class InfinispanRemoteResourceReferenceRepository
        extends AbstractLabelAwareRemoteInfinispanRepository<ResourceReference, String>
        implements ResourceReferenceRepository {

    public InfinispanRemoteResourceReferenceRepository(RemoteCacheManager cacheManager) {
        super(cacheManager);
    }

    @Override
    protected String entityName() {
        return "resource";
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
        Query<ResourceReference> query = cacheManager
                .getCache(entityName())
                .query("from ai.wanaku.capabilities.sdk.api.types.ResourceReference t where t.name = :name");
        query.setParameter("name", name);
        return query.execute().list();
    }
}
