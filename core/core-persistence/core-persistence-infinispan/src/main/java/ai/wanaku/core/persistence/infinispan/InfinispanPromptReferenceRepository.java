package ai.wanaku.core.persistence.infinispan;

import java.util.List;
import java.util.UUID;
import org.infinispan.commons.api.query.Query;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.manager.EmbeddedCacheManager;
import ai.wanaku.capabilities.sdk.api.types.PromptReference;
import ai.wanaku.core.persistence.api.PromptReferenceRepository;

/**
 * Infinispan implementation of PromptReferenceRepository.
 */
public class InfinispanPromptReferenceRepository extends AbstractInfinispanRepository<PromptReference, String>
        implements PromptReferenceRepository {

    /**
     * Constructor for InfinispanPromptReferenceRepository.
     *
     * @param cacheManager the cache manager
     * @param configuration the cache configuration
     */
    public InfinispanPromptReferenceRepository(EmbeddedCacheManager cacheManager, Configuration configuration) {
        super(cacheManager, configuration);
    }

    @Override
    protected String entityName() {
        return "prompt";
    }

    @Override
    protected Class<PromptReference> entityType() {
        return PromptReference.class;
    }

    @Override
    protected String newId() {
        return UUID.randomUUID().toString();
    }

    @Override
    public List<PromptReference> findByName(String name) {
        Query<PromptReference> query = cacheManager
                .getCache(entityName())
                .query("from ai.wanaku.capabilities.sdk.api.types.PromptReference p where p.name = :name");
        query.setParameter("name", name);
        return query.execute().list();
    }
    /**
     * Finds PromptReferences by name and namespace.
     *
     * @param name the name of the prompt
     * @param namespace the namespace of the prompt
     * @return list of PromptReferences matching the name and namespace
     */
    public List<PromptReference> findByNameAndNamespace(String name, String namespace) {
        Query<PromptReference> query = cacheManager
                .getCache(entityName())
                .query(
                        "from ai.wanaku.capabilities.sdk.api.types.PromptReference p where p.name = :name and p.namespace = :namespace");
        query.setParameter("name", name);
        query.setParameter("namespace", namespace);
        return query.execute().list();
    }
}
