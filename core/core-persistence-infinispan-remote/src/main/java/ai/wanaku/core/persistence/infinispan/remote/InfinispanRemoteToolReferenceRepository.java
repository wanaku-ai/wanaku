package ai.wanaku.core.persistence.infinispan.remote;

import java.util.List;
import java.util.UUID;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.commons.api.query.Query;
import ai.wanaku.capabilities.sdk.api.types.ToolReference;
import ai.wanaku.core.persistence.api.ToolReferenceRepository;

public class InfinispanRemoteToolReferenceRepository
        extends AbstractLabelAwareRemoteInfinispanRepository<ToolReference, String> implements ToolReferenceRepository {

    public InfinispanRemoteToolReferenceRepository(RemoteCacheManager cacheManager) {
        super(cacheManager);
    }

    @Override
    protected String entityName() {
        return "tool";
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
        Query<ToolReference> query = cacheManager
                .getCache(entityName())
                .query("from ai.wanaku.capabilities.sdk.api.types.ToolReference t where t.name = :name");
        query.setParameter("name", name);
        return query.execute().list();
    }
}
