package ai.wanaku.core.persistence.infinispan;

import java.util.List;
import java.util.UUID;
import org.infinispan.Cache;
import org.infinispan.commons.api.query.Query;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.manager.EmbeddedCacheManager;
import ai.wanaku.capabilities.sdk.api.types.Namespace;
import ai.wanaku.core.persistence.api.NamespaceRepository;

public class InfinispanNamespaceRepository extends AbstractLabelAwareInfinispanRepository<Namespace, String>
        implements NamespaceRepository {

    protected InfinispanNamespaceRepository(EmbeddedCacheManager cacheManager, Configuration configuration) {
        super(cacheManager, configuration);
    }

    @Override
    protected Class<Namespace> entityType() {
        return Namespace.class;
    }

    @Override
    protected String entityName() {
        return "namespace";
    }

    @Override
    protected String newId() {
        return UUID.randomUUID().toString();
    }

    @Override
    public List<Namespace> findByName(String name) {
        Query<Namespace> query = cacheManager
                .getCache(entityName())
                .query("from ai.wanaku.capabilities.sdk.api.types.Namespace t where t.name = :name");
        query.setParameter("name", name);
        return query.execute().list();
    }

    @Override
    public List<Namespace> findFirstAvailable(String name) {
        final Cache<Object, Namespace> cache = cacheManager.getCache(entityName());
        Query<Namespace> query = cache.query(
                "from ai.wanaku.capabilities.sdk.api.types.Namespace t where t.name = :name OR (t.path != '' AND t.name is NULL)");

        query.setParameter("name", name);
        return query.maxResults(1).execute().list();
    }
}
