package ai.wanaku.core.persistence.api;

import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.capabilities.sdk.api.types.LabelsAwareEntity;
import ai.wanaku.core.mcp.util.LabelExpressionParser;
import java.util.List;

/**
 * Repository interface for entities that support label-based filtering and operations.
 * <p>
 * This interface extends {@link WanakuRepository} to provide additional operations
 * for querying and manipulating entities based on label expressions.
 * </p>
 *
 * @param <A> the entity type, must implement LabelsAwareEntity
 * @param <K> the key/ID type for the entity
 */
public interface LabelAwareInfinispanRepository<A extends LabelsAwareEntity<K>, K> extends WanakuRepository<A, K> {

    /**
     * Finds all entities that match the given label expression.
     * <p>
     * The label expression is parsed into a predicate and evaluated
     * in-memory against each entity's label map using Java stream filtering.
     * <p>
     * If the label expression is null or empty, this method returns all entities
     * (equivalent to calling {@link #listAll()}).
     * <p>
     * Example expressions:
     * <ul>
     *   <li>"category=weather" - Find entities with category label equal to weather</li>
     *   <li>"category=weather &amp; !action=forecast" - Find weather entities that are not forecasts</li>
     *   <li>"(category=weather | category=news) &amp; environment=production" - Complex expressions</li>
     * </ul>
     *
     * @param labelExpression the label filter expression (e.g., "category=weather &amp; !action=forecast"),
     *                        or null/empty to return all entities
     * @return list of entities matching the label expression, or all entities if expression is null/empty
     * @throws WanakuException if the label expression is invalid or query execution fails
     * @see LabelExpressionParser
     */
    List<A> findAllFilterByLabelExpression(String labelExpression);

    /**
     * Removes all entities that match the given label expression.
     * <p>
     * This method evaluates the label expression against all entities and removes
     * those that match. The operation is performed atomically.
     * </p>
     *
     * @param labelExpression the label filter expression to match entities for removal
     * @return the number of entities removed
     * @throws LabelExpressionParser.LabelExpressionParseException if the label expression is invalid
     */
    int removeIf(String labelExpression) throws LabelExpressionParser.LabelExpressionParseException;
}
