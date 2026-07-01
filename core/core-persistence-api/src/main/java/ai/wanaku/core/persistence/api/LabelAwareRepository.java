package ai.wanaku.core.persistence.api;

import java.util.List;
import ai.wanaku.capabilities.sdk.api.types.LabelsAwareEntity;
import ai.wanaku.core.persistence.util.LabelExpressionParser;

public interface LabelAwareRepository<A extends LabelsAwareEntity<K>, K> extends WanakuRepository<A, K, K> {

    List<A> findAllFilterByLabelExpression(String labelExpression);

    int removeIf(String labelExpression) throws LabelExpressionParser.LabelExpressionParseException;
}
