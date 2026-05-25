package ai.wanaku.core.persistence.infinispan.remote;

import java.util.List;
import java.util.UUID;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.commons.api.query.Query;
import ai.wanaku.capabilities.sdk.api.types.PromptReference;
import ai.wanaku.core.persistence.api.PromptReferenceRepository;

public class InfinispanRemotePromptReferenceRepository
        extends AbstractRemoteInfinispanRepository<PromptReference, String> implements PromptReferenceRepository {

    public InfinispanRemotePromptReferenceRepository(RemoteCacheManager cacheManager) {
        super(cacheManager);
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

    @Override
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
