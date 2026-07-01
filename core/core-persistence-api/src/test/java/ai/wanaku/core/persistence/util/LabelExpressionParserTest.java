package ai.wanaku.core.persistence.util;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import ai.wanaku.capabilities.sdk.api.types.LabelsAwareEntity;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LabelExpressionParserTest {

    private static class TestEntity extends LabelsAwareEntity<String> {
        private String id;
        private final Map<String, String> labels = new HashMap<>();

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

        @Override
        public Map<String, String> getLabels() {
            return labels;
        }
    }

    private TestEntity createEntity(String id, String... labelPairs) {
        TestEntity entity = new TestEntity(id);
        for (int i = 0; i < labelPairs.length; i += 2) {
            entity.getLabels().put(labelPairs[i], labelPairs[i + 1]);
        }
        return entity;
    }

    @Test
    void testSimpleEquality() throws Exception {
        TestEntity entity = createEntity("1", "category", "weather");
        Predicate<LabelsAwareEntity<?>> predicate = LabelExpressionParser.parse("category=weather");
        assertTrue(predicate.test(entity));
    }

    @Test
    void testSimpleNotEquals() throws Exception {
        TestEntity entity = createEntity("1", "category", "weather");
        Predicate<LabelsAwareEntity<?>> predicate = LabelExpressionParser.parse("category!=news");
        assertTrue(predicate.test(entity));
    }

    @Test
    void testAndExpression() throws Exception {
        TestEntity entity = createEntity("1", "category", "weather", "action", "forecast");
        Predicate<LabelsAwareEntity<?>> predicate = LabelExpressionParser.parse("category=weather & action=forecast");
        assertTrue(predicate.test(entity));

        TestEntity entity2 = createEntity("2", "category", "weather", "action", "current");
        assertFalse(predicate.test(entity2));
    }

    @Test
    void testOrExpression() throws Exception {
        TestEntity entity = createEntity("1", "category", "weather");
        Predicate<LabelsAwareEntity<?>> predicate = LabelExpressionParser.parse("category=weather | category=news");
        assertTrue(predicate.test(entity));

        TestEntity entity2 = createEntity("2", "category", "news");
        assertTrue(predicate.test(entity2));

        TestEntity entity3 = createEntity("3", "category", "finance");
        assertFalse(predicate.test(entity3));
    }

    @Test
    void testNotExpression() throws Exception {
        TestEntity entity = createEntity("1", "category", "weather");
        Predicate<LabelsAwareEntity<?>> predicate = LabelExpressionParser.parse("!category=weather");
        assertFalse(predicate.test(entity));

        TestEntity entity2 = createEntity("2", "category", "news");
        assertTrue(predicate.test(entity2));
    }

    @Test
    void testComplexExpression() throws Exception {
        TestEntity entity = createEntity("1", "category", "weather", "action", "forecast", "version", "1.0");
        Predicate<LabelsAwareEntity<?>> predicate =
                LabelExpressionParser.parse("(category=weather | category=news) & version=1.0");
        assertTrue(predicate.test(entity));
    }

    @Test
    void testParenthesizedExpression() throws Exception {
        TestEntity entity = createEntity("1", "category", "weather", "action", "forecast");
        Predicate<LabelsAwareEntity<?>> predicate = LabelExpressionParser.parse("(category=weather & action=forecast)");
        assertTrue(predicate.test(entity));
    }

    @Test
    void testWhitespaceHandling() throws Exception {
        TestEntity entity = createEntity("1", "category", "weather");
        Predicate<LabelsAwareEntity<?>> predicate = LabelExpressionParser.parse("  category = weather  ");
        assertTrue(predicate.test(entity));
    }

    @Test
    void testHyphenInValue() throws Exception {
        TestEntity entity = createEntity("1", "environment", "production-us");
        Predicate<LabelsAwareEntity<?>> predicate = LabelExpressionParser.parse("environment=production-us");
        assertTrue(predicate.test(entity));
    }

    @Test
    void testUnderscoreInKey() throws Exception {
        TestEntity entity = createEntity("1", "service_name", "api");
        Predicate<LabelsAwareEntity<?>> predicate = LabelExpressionParser.parse("service_name=api");
        assertTrue(predicate.test(entity));
    }

    @Test
    void testDotInValue() throws Exception {
        TestEntity entity = createEntity("1", "version", "1.0");
        Predicate<LabelsAwareEntity<?>> predicate = LabelExpressionParser.parse("version=1.0");
        assertTrue(predicate.test(entity));
    }

    @Test
    void testSlashInValue() throws Exception {
        TestEntity entity = createEntity("1", "path", "a/b/c");
        Predicate<LabelsAwareEntity<?>> predicate = LabelExpressionParser.parse("path=a/b/c");
        assertTrue(predicate.test(entity));
    }

    @Test
    void testNullExpression() {
        Assertions.assertThrows(LabelExpressionParser.LabelExpressionParseException.class, () -> {
            LabelExpressionParser.parse(null);
        });
    }

    @Test
    void testEmptyExpression() {
        Assertions.assertThrows(LabelExpressionParser.LabelExpressionParseException.class, () -> {
            LabelExpressionParser.parse("");
        });
    }

    @Test
    void testWhitespaceOnlyExpression() {
        Assertions.assertThrows(LabelExpressionParser.LabelExpressionParseException.class, () -> {
            LabelExpressionParser.parse("   ");
        });
    }

    @Test
    void testInvalidCharacters() {
        Assertions.assertThrows(LabelExpressionParser.LabelExpressionParseException.class, () -> {
            LabelExpressionParser.parse("category@weather");
        });
    }

    @Test
    void testUnmatchedParenthesis() {
        Assertions.assertThrows(LabelExpressionParser.LabelExpressionParseException.class, () -> {
            LabelExpressionParser.parse("(category=weather");
        });
    }

    @Test
    void testMissingValue() {
        Assertions.assertThrows(LabelExpressionParser.LabelExpressionParseException.class, () -> {
            LabelExpressionParser.parse("category=");
        });
    }

    @Test
    void testMissingOperator() {
        Assertions.assertThrows(LabelExpressionParser.LabelExpressionParseException.class, () -> {
            LabelExpressionParser.parse("category weather");
        });
    }

    @Test
    void testTooLongExpression() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1001; i++) {
            sb.append("a");
        }
        Assertions.assertThrows(LabelExpressionParser.LabelExpressionParseException.class, () -> {
            LabelExpressionParser.parse(sb.toString());
        });
    }

    @Test
    void testDoubleNegation() throws Exception {
        TestEntity entity = createEntity("1", "category", "weather");
        Predicate<LabelsAwareEntity<?>> predicate = LabelExpressionParser.parse("!!category=weather");
        assertTrue(predicate.test(entity));
    }

    @Test
    void testMultipleConditions() throws Exception {
        TestEntity entity = createEntity("1", "category", "weather", "action", "forecast", "version", "1.0");
        Predicate<LabelsAwareEntity<?>> predicate =
                LabelExpressionParser.parse("category=weather & action=forecast & version=1.0");
        assertTrue(predicate.test(entity));
    }

    @Test
    void testMixedAndOr() throws Exception {
        TestEntity entity = createEntity("1", "category", "weather", "version", "1.0");
        Predicate<LabelsAwareEntity<?>> predicate =
                LabelExpressionParser.parse("category=weather & version=1.0 | category=news");
        assertTrue(predicate.test(entity));
    }

    @Test
    void testOperatorPrecedence() throws Exception {
        TestEntity weatherV1 = createEntity("1", "category", "weather", "version", "1.0");
        TestEntity weatherV2 = createEntity("2", "category", "weather", "version", "2.0");
        TestEntity newsV1 = createEntity("3", "category", "news", "version", "1.0");

        Predicate<LabelsAwareEntity<?>> predicate =
                LabelExpressionParser.parse("category=weather & version=1.0 | category=news");
        assertTrue(predicate.test(weatherV1));
        assertFalse(predicate.test(weatherV2));
        assertTrue(predicate.test(newsV1));
    }

    @Test
    void testEmptyLabels() throws Exception {
        TestEntity entity = createEntity("1");
        Predicate<LabelsAwareEntity<?>> predicate = LabelExpressionParser.parse("category=weather");
        assertFalse(predicate.test(entity));
    }
}
