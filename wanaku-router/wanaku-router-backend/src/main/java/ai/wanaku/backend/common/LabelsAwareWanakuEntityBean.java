package ai.wanaku.backend.common;

import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.capabilities.sdk.api.types.LabelsAwareEntity;
import ai.wanaku.core.mcp.util.LabelExpressionParser;
import ai.wanaku.core.persistence.api.LabelAwareInfinispanRepository;

public abstract class LabelsAwareWanakuEntityBean<W extends LabelsAwareEntity<String>> extends AbstractBean<W> {

    /**
     * Removes all entities that match the given label expression.
     * <p>
     * This method provides batch removal capabilities based on label filtering.
     * The label expression is parsed into a predicate and evaluated against each
     * entity's label map. All entities that match the expression are removed from
     * the repository.
     * </p>
     * <p>
     * The label expression syntax supports:
     * </p>
     * <ul>
     *   <li><b>Equality:</b> {@code key=value} - Matches entities where label key equals value</li>
     *   <li><b>Inequality:</b> {@code key!=value} - Matches entities where label key does not equal value</li>
     *   <li><b>Logical AND:</b> {@code expr1 & expr2} - Both conditions must match</li>
     *   <li><b>Logical OR:</b> {@code expr1 | expr2} - At least one condition must match</li>
     *   <li><b>Logical NOT:</b> {@code !expr} - Negates the expression</li>
     *   <li><b>Grouping:</b> {@code (expr)} - Groups expressions with parentheses</li>
     * </ul>
     * <p>
     * Example expressions:
     * </p>
     * <ul>
     *   <li>{@code "category=weather"} - Remove all entities with category=weather</li>
     *   <li>{@code "environment!=production"} - Remove all non-production entities</li>
     *   <li>{@code "category=weather & !action=forecast"} - Remove weather entities that are not forecasts</li>
     *   <li>{@code "(category=weather | category=news) & deprecated=true"} - Remove deprecated weather or news entities</li>
     * </ul>
     * <p>
     *
     * @param labelExpression the label filter expression to match entities for removal;
     *                        must be a valid label expression following the syntax described above
     * @return the number of entities that were removed from the repository
     * @throws WanakuException if the label expression is invalid or malformed, or if the
     *                         removal operation fails
     * @see LabelExpressionParser
     * @see LabelAwareInfinispanRepository#removeIf(String)
     */
    public int removeIf(String labelExpression) throws WanakuException {
        try {
            int removed = ((LabelAwareInfinispanRepository) getRepository()).removeIf(labelExpression);
            return removed;
        } catch (LabelExpressionParser.LabelExpressionParseException e) {
            throw new WanakuException(e.getMessage());
        }
    }
}
