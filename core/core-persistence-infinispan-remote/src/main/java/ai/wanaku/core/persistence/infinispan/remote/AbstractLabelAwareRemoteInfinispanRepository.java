package ai.wanaku.core.persistence.infinispan.remote;

import java.util.List;
import java.util.function.Predicate;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.capabilities.sdk.api.types.LabelsAwareEntity;
import ai.wanaku.core.persistence.api.LabelAwareRepository;
import ai.wanaku.core.persistence.util.LabelExpressionParser;
import ai.wanaku.core.util.StringHelper;

public abstract class AbstractLabelAwareRemoteInfinispanRepository<A extends LabelsAwareEntity<K>, K>
        extends AbstractRemoteInfinispanRepository<A, K> implements LabelAwareRepository<A, K> {

    protected AbstractLabelAwareRemoteInfinispanRepository(RemoteCacheManager cacheManager) {
        super(cacheManager);
    }

    @Override
    public List<A> findAllFilterByLabelExpression(String labelExpression) {
        if (StringHelper.isBlank(labelExpression)) {
            return listAll();
        }
        try {
            @SuppressWarnings("unchecked")
            Predicate<A> predicate = (Predicate<A>) LabelExpressionParser.parse(labelExpression);
            RemoteCache<K, A> c = cacheManager.getCache(entityName());
            return c.values().stream().filter(predicate).toList();
        } catch (LabelExpressionParser.LabelExpressionParseException e) {
            throw new WanakuException("Invalid label expression: %s".formatted(labelExpression), e);
        } catch (Exception e) {
            throw new WanakuException("Failed to execute label query: %s".formatted(labelExpression), e);
        }
    }

    @Override
    public int removeIf(String labelExpression) throws LabelExpressionParser.LabelExpressionParseException {
        RemoteCache<K, A> cache = cacheManager.getCache(entityName());
        @SuppressWarnings("unchecked")
        Predicate<A> predicate = (Predicate<A>) LabelExpressionParser.parse(labelExpression);
        List<A> toRemove = cache.values().stream().filter(predicate).toList();
        int size = toRemove.size();
        toRemove.forEach(entity -> cache.remove(entity.getId()));
        return size;
    }
}
