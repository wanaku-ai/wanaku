package ai.wanaku.core.persistence.infinispan;

import ai.wanaku.capabilities.sdk.api.types.ResourceReference;
import ai.wanaku.core.persistence.api.ResourceReferenceRepository;
import java.util.List;
import java.util.UUID;
import org.infinispan.commons.api.query.Query;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.manager.EmbeddedCacheManager;

public class InfinispanResourceReferenceRepository
        extends AbstractLabelAwareInfinispanRepository<ResourceReference, String>
        implements ResourceReferenceRepository {

    public InfinispanResourceReferenceRepository(EmbeddedCacheManager cacheManager, Configuration configuration) {
        super(cacheManager, configuration);
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
