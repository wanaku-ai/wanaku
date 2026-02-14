package ai.wanaku.core.mcp.util;

import java.util.function.Predicate;
import ai.wanaku.capabilities.sdk.api.types.LabelsAwareEntity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for LabelExpressionParser.
 * Tests verify that the parser creates predicates that correctly filter
 * label-aware entities.
 */
class LabelExpressionParserTest {

    /**
     * Test entity for verifying label expression filtering.
     */
    private static class TestEntity extends LabelsAwareEntity<String> {
        private String id;

        TestEntity(String id) {
            this.id = id;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public void setId(String id) {
            this.id = id;
        }
    }

    private TestEntity createEntity(String id, String... labelPairs) {
        TestEntity entity = new TestEntity(id);
        for (int i = 0; i < labelPairs.length; i += 2) {
            entity.addLabel(labelPairs[i], labelPairs[i + 1]);
        }
        return entity;
    }

    @Test
    void testSimpleEquality() throws Exception {
        String expression = "category=weather";
        Predicate<LabelsAwareEntity<?>> predicate = LabelExpressionParser.parse(expression);

        assertNotNull(predicate);

        // Should match when category=weather
        assertTrue(predicate.test(createEntity("1", "category", "weather")));

        // Should not match when category has different value
        assertFalse(predicate.test(createEntity("2", "category", "news")));

        // Should not match when category is missing
        assertFalse(predicate.test(createEntity("3", "other", "value")));
    }

    @Test
    void testSimpleNotEquals() throws Exception {
        String expression = "category!=weather";
        Predicate<LabelsAwareEntity<?>> predicate = LabelExpressionParser.parse(expression);

        assertNotNull(predicate);

        // Should match when category has different value
        assertTrue(predicate.test(createEntity("1", "category", "news")));

        // Should not match when category=weather
        assertFalse(predicate.test(createEntity("2", "category", "weather")));

        // Should match when category is missing (null != "weather")
        assertTrue(predicate.test(createEntity("3", "other", "value")));
    }

    @Test
    void testAndExpression() throws Exception {
        String expression = "category=weather & action=forecast";
        Predicate<LabelsAwareEntity<?>> predicate = LabelExpressionParser.parse(expression);

        assertNotNull(predicate);

        // Should match when both conditions are true
        assertTrue(predicate.test(createEntity("1", "category", "weather", "action", "forecast")));

        // Should not match when only first condition is true
        assertFalse(predicate.test(createEntity("2", "category", "weather", "action", "current")));

        // Should not match when only second condition is true
        assertFalse(predicate.test(createEntity("3", "category", "news", "action", "forecast")));

        // Should not match when neither condition is true
        assertFalse(predicate.test(createEntity("4", "category", "news", "action", "current")));
    }

    @Test
    void testOrExpression() throws Exception {
        String expression = "category=weather | category=news";
        Predicate<LabelsAwareEntity<?>> predicate = LabelExpressionParser.parse(expression);

        assertNotNull(predicate);

        // Should match when first condition is true
        assertTrue(predicate.test(createEntity("1", "category", "weather")));

        // Should match when second condition is true
        assertTrue(predicate.test(createEntity("2", "category", "news")));

        // Should not match when neither condition is true
        assertFalse(predicate.test(createEntity("3", "category", "sports")));
    }

    @Test
    void testNotExpression() throws Exception {
        String expression = "!action=forecast";
        Predicate<LabelsAwareEntity<?>> predicate = LabelExpressionParser.parse(expression);

        assertNotNull(predicate);

        // Should match when action is not forecast
        assertTrue(predicate.test(createEntity("1", "action", "current")));

        // Should match when action is missing
        assertTrue(predicate.test(createEntity("2", "other", "value")));

        // Should not match when action=forecast
        assertFalse(predicate.test(createEntity("3", "action", "forecast")));
    }

    @Test
    void testComplexExpression() throws Exception {
        String expression = "category=weather & !action=forecast";
        Predicate<LabelsAwareEntity<?>> predicate = LabelExpressionParser.parse(expression);

        assertNotNull(predicate);

        // Should match when category=weather and action is not forecast
        assertTrue(predicate.test(createEntity("1", "category", "weather", "action", "current")));

        // Should not match when category=weather but action=forecast
        assertFalse(predicate.test(createEntity("2", "category", "weather", "action", "forecast")));

        // Should not match when category is not weather
        assertFalse(predicate.test(createEntity("3", "category", "news", "action", "current")));
    }

    @Test
    void testParenthesizedExpression() throws Exception {
        String expression = "(category=weather | category=news) & action=forecast";
        Predicate<LabelsAwareEntity<?>> predicate = LabelExpressionParser.parse(expression);

        assertNotNull(predicate);

        // Should match when category is weather and action is forecast
        assertTrue(predicate.test(createEntity("1", "category", "weather", "action", "forecast")));

        // Should match when category is news and action is forecast
        assertTrue(predicate.test(createEntity("2", "category", "news", "action", "forecast")));

        // Should not match when category is weather but action is not forecast
        assertFalse(predicate.test(createEntity("3", "category", "weather", "action", "current")));

        // Should not match when category is neither weather nor news
        assertFalse(predicate.test(createEntity("4", "category", "sports", "action", "forecast")));
    }

    @Test
    void testWhitespaceHandling() throws Exception {
        String expression = "  category  =  weather  &  action  =  forecast  ";
        Predicate<LabelsAwareEntity<?>> predicate = LabelExpressionParser.parse(expression);

        assertNotNull(predicate);

        // Should work the same as without extra whitespace
        assertTrue(predicate.test(createEntity("1", "category", "weather", "action", "forecast")));
    }

    @Test
    void testHyphenInValue() throws Exception {
        String expression = "category=weather-forecast";
        Predicate<LabelsAwareEntity<?>> predicate = LabelExpressionParser.parse(expression);

        assertNotNull(predicate);

        assertTrue(predicate.test(createEntity("1", "category", "weather-forecast")));
        assertFalse(predicate.test(createEntity("2", "category", "weather")));
    }

    @Test
    void testUnderscoreInKey() throws Exception {
        String expression = "my_category=weather";
        Predicate<LabelsAwareEntity<?>> predicate = LabelExpressionParser.parse(expression);

        assertNotNull(predicate);

        assertTrue(predicate.test(createEntity("1", "my_category", "weather")));
        assertFalse(predicate.test(createEntity("2", "category", "weather")));
    }

    @Test
    void testDotInValue() throws Exception {
        String expression = "version=1.2.3";
        Predicate<LabelsAwareEntity<?>> predicate = LabelExpressionParser.parse(expression);

        assertNotNull(predicate);

        assertTrue(predicate.test(createEntity("1", "version", "1.2.3")));
        assertFalse(predicate.test(createEntity("2", "version", "1.2.4")));
    }

    @Test
    void testSlashInValue() throws Exception {
        String expression = "path=api/v1/tools";
        Predicate<LabelsAwareEntity<?>> predicate = LabelExpressionParser.parse(expression);

        assertNotNull(predicate);

        assertTrue(predicate.test(createEntity("1", "path", "api/v1/tools")));
        assertFalse(predicate.test(createEntity("2", "path", "api/v2/tools")));
    }

    // Negative tests

    @Test
    void testNullExpression() {
        assertThrows(
                LabelExpressionParser.LabelExpressionParseException.class, () -> LabelExpressionParser.parse(null));
    }

    @Test
    void testEmptyExpression() {
        assertThrows(LabelExpressionParser.LabelExpressionParseException.class, () -> LabelExpressionParser.parse(""));
    }

    @Test
    void testWhitespaceOnlyExpression() {
        assertThrows(
                LabelExpressionParser.LabelExpressionParseException.class, () -> LabelExpressionParser.parse("   "));
    }

    @Test
    void testInvalidCharacters() {
        assertThrows(
                LabelExpressionParser.LabelExpressionParseException.class,
                () -> LabelExpressionParser.parse("category=weather; DROP TABLE"));
    }

    @Test
    void testUnmatchedParenthesis() {
        assertThrows(
                LabelExpressionParser.LabelExpressionParseException.class,
                () -> LabelExpressionParser.parse("(category=weather & action=forecast"));
    }

    @Test
    void testMissingValue() {
        assertThrows(
                LabelExpressionParser.LabelExpressionParseException.class,
                () -> LabelExpressionParser.parse("category="));
    }

    @Test
    void testMissingOperator() {
        assertThrows(
                LabelExpressionParser.LabelExpressionParseException.class,
                () -> LabelExpressionParser.parse("category weather"));
    }

    @Test
    void testTooLongExpression() {
        String longExpression = "a=b".repeat(500); // Creates a very long expression
        assertThrows(
                LabelExpressionParser.LabelExpressionParseException.class,
                () -> LabelExpressionParser.parse(longExpression));
    }

    @Test
    void testDoubleNegation() throws Exception {
        String expression = "!!category=weather";
        Predicate<LabelsAwareEntity<?>> predicate = LabelExpressionParser.parse(expression);

        assertNotNull(predicate);

        // Double negation should cancel out
        assertTrue(predicate.test(createEntity("1", "category", "weather")));
        assertFalse(predicate.test(createEntity("2", "category", "news")));
    }

    @Test
    void testMultipleConditions() throws Exception {
        String expression = "a=1 & b=2 & c=3";
        Predicate<LabelsAwareEntity<?>> predicate = LabelExpressionParser.parse(expression);

        assertNotNull(predicate);

        // All conditions must be true
        assertTrue(predicate.test(createEntity("1", "a", "1", "b", "2", "c", "3")));
        assertFalse(predicate.test(createEntity("2", "a", "1", "b", "2", "c", "4")));
    }

    @Test
    void testMixedAndOr() throws Exception {
        String expression = "a=1 & b=2 | c=3";
        Predicate<LabelsAwareEntity<?>> predicate = LabelExpressionParser.parse(expression);

        assertNotNull(predicate);

        // (a=1 & b=2) | c=3
        // Should match when a=1 AND b=2
        assertTrue(predicate.test(createEntity("1", "a", "1", "b", "2")));

        // Should match when c=3
        assertTrue(predicate.test(createEntity("2", "c", "3")));

        // Should not match when neither condition is true
        assertFalse(predicate.test(createEntity("3", "a", "1", "b", "3", "c", "4")));
    }

    @Test
    void testOperatorPrecedence() throws Exception {
        // & has higher precedence than |
        String expression = "a=1 | b=2 & c=3";
        Predicate<LabelsAwareEntity<?>> predicate = LabelExpressionParser.parse(expression);

        assertNotNull(predicate);

        // a=1 | (b=2 & c=3)
        // Should match when a=1
        assertTrue(predicate.test(createEntity("1", "a", "1")));

        // Should match when b=2 AND c=3
        assertTrue(predicate.test(createEntity("2", "b", "2", "c", "3")));

        // Should not match when b=2 but c!=3
        assertFalse(predicate.test(createEntity("3", "b", "2", "c", "4")));
    }

    @Test
    void testEmptyLabels() throws Exception {
        String expression = "category=weather";
        Predicate<LabelsAwareEntity<?>> predicate = LabelExpressionParser.parse(expression);

        assertNotNull(predicate);

        // Should not match entity with no labels
        assertFalse(predicate.test(new TestEntity("empty")));
    }
}
