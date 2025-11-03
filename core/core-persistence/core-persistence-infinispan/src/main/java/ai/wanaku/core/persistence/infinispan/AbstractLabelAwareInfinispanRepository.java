package ai.wanaku.core.persistence.infinispan;

import ai.wanaku.api.exceptions.WanakuException;
import ai.wanaku.api.types.LabelsAwareEntity;
import ai.wanaku.core.mcp.util.LabelExpressionParser;
import ai.wanaku.core.mcp.util.LabelExpressionParser.LabelExpressionParseException;
import ai.wanaku.core.persistence.api.LabelAwareInfinispanRepository;
import java.util.List;
import java.util.function.Predicate;
import org.infinispan.Cache;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.manager.EmbeddedCacheManager;

public abstract class AbstractLabelAwareInfinispanRepository<A extends LabelsAwareEntity<K>, K>
        extends AbstractInfinispanRepository<A, K> implements LabelAwareInfinispanRepository<A, K> {

    protected AbstractLabelAwareInfinispanRepository(EmbeddedCacheManager cacheManager, Configuration configuration) {
        super(cacheManager, configuration);
    }

    public List<A> findAllFilterByLabelExpression(String labelExpression) {
        // If no label expression provided, return all entities
        if (labelExpression == null || labelExpression.trim().isEmpty()) {
            return listAll();
        }
        try {
            // Parse the label expression into a predicate
            @SuppressWarnings("unchecked")
            Predicate<A> predicate = (Predicate<A>) LabelExpressionParser.parse(labelExpression);

            Cache<K, A> c = cacheManager.getCache(entityName());
            return c.values().stream().filter(predicate).toList();

        } catch (LabelExpressionParseException e) {
            throw new WanakuException("Invalid label expression: " + labelExpression, e);
        } catch (Exception e) {
            throw new WanakuException("Failed to execute label query: " + labelExpression, e);
        }
    }

    public int removeIf(String labelExpression) throws LabelExpressionParseException {
        int size;
        Cache<K, A> cache = cacheManager.getCache(entityName());
        @SuppressWarnings("unchecked")
        Predicate<A> predicate = (Predicate<A>) LabelExpressionParser.parse(labelExpression);
        size = cache.values().stream().filter(predicate).toList().size();
        cache.values().removeIf(predicate);
        return size;
    }
}
