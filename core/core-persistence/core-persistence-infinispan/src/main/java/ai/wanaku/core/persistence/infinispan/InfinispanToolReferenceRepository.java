package ai.wanaku.core.persistence.infinispan;

import java.util.List;
import java.util.UUID;
import org.infinispan.commons.api.query.Query;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.manager.EmbeddedCacheManager;
import ai.wanaku.capabilities.sdk.api.types.ToolReference;
import ai.wanaku.core.persistence.api.ToolReferenceRepository;

public class InfinispanToolReferenceRepository extends AbstractLabelAwareInfinispanRepository<ToolReference, String>
        implements ToolReferenceRepository {

    public InfinispanToolReferenceRepository(EmbeddedCacheManager cacheManager, Configuration configuration) {
        super(cacheManager, configuration);
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
