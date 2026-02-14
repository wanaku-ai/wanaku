package ai.wanaku.core.persistence.infinispan;

import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import io.quarkus.test.junit.QuarkusTest;
import ai.wanaku.capabilities.sdk.api.types.InputSchema;
import ai.wanaku.capabilities.sdk.api.types.Property;
import ai.wanaku.capabilities.sdk.api.types.ToolReference;
import ai.wanaku.core.persistence.api.ToolReferenceRepository;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for label expression filtering using Infinispan.
 * <p>
 * This test spins up an embedded Infinispan cache, populates it with
 * ToolReference
 * objects that have various labels, and tests different label query
 * expressions.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class LabelFilteringIntegrationTest {

    @Inject
    ToolReferenceRepository toolReferenceRepository;

    /**
     * Setup test data with various label combinations.
     */
    @BeforeAll
    public void setupTestData() {
        // Clear any existing data
        if (toolReferenceRepository instanceof AbstractInfinispanRepository) {
            toolReferenceRepository.removeAll();
        }

        // Create test tools with different label combinations

        // Weather tools
        createTool(
                "weather-forecast-1",
                "Weather Forecast API",
                "weather",
                Map.of("category", "weather", "action", "forecast", "version", "1.0"));

        createTool(
                "weather-current-1",
                "Current Weather API",
                "weather",
                Map.of("category", "weather", "action", "current", "version", "1.0"));

        createTool(
                "weather-forecast-2",
                "Advanced Weather Forecast",
                "weather",
                Map.of("category", "weather", "action", "forecast", "version", "2.0"));

        // News tools
        createTool(
                "news-headlines-1",
                "News Headlines API",
                "news",
                Map.of("category", "news", "action", "headlines", "version", "1.0"));

        createTool(
                "news-search-1",
                "News Search API",
                "news",
                Map.of("category", "news", "action", "search", "version", "1.0"));

        // Finance tools
        createTool(
                "finance-stock-1",
                "Stock Price API",
                "finance",
                Map.of("category", "finance", "action", "stock", "version", "1.0"));

        // Tools with environment labels
        createTool("tool-prod-1", "Production Tool", "misc", Map.of("environment", "production", "status", "stable"));

        createTool("tool-dev-1", "Development Tool", "misc", Map.of("environment", "development", "status", "beta"));

        // Deprecated tool
        createTool("old-tool-1", "Deprecated Tool", "misc", Map.of("deprecated", "true", "category", "weather"));

        // Tool with no labels
        createTool("no-labels-1", "Tool Without Labels", "misc", Map.of());
    }

    /**
     * Helper method to create a tool with labels.
     */
    private void createTool(String id, String name, String type, Map<String, String> labels) {
        ToolReference tool = new ToolReference();
        tool.setId(id);
        tool.setName(name);
        tool.setDescription("Description for " + name);
        tool.setUri("https://api.example.com/" + id);
        tool.setType(type);

        // Add input schema
        InputSchema inputSchema = new InputSchema();
        inputSchema.setType("object");
        Property property = new Property();
        property.setDescription("Test property");
        property.setType("string");
        inputSchema.setProperties(Map.of("param1", property));
        tool.setInputSchema(inputSchema);

        // Add labels using the new API
        tool.addLabels(labels);

        toolReferenceRepository.persist(tool);
    }

    @Test
    @Order(1)
    public void testListAllTools() {
        List<ToolReference> allTools = toolReferenceRepository.listAll();
        assertEquals(10, allTools.size(), "Should have 10 tools in total");
    }

    @Test
    @Order(2)
    public void testSimpleEquality() {
        // Find all weather tools
        List<ToolReference> weatherTools = toolReferenceRepository.findAllFilterByLabelExpression("category=weather");

        assertEquals(4, weatherTools.size(), "Should find 4 weather tools");
        assertTrue(weatherTools.stream()
                .allMatch(t -> t.getLabelValue("category") != null
                        && t.getLabelValue("category").equals("weather")));
    }

    @Test
    @Order(3)
    public void testAndExpression() {
        // Find weather forecast tools
        List<ToolReference> forecastTools =
                toolReferenceRepository.findAllFilterByLabelExpression("category=weather & action=forecast");

        assertEquals(2, forecastTools.size(), "Should find 2 weather forecast tools");
        assertTrue(forecastTools.stream()
                .allMatch(t -> t.getLabelValue("category") != null
                        && t.getLabelValue("category").equals("weather")
                        && t.getLabelValue("action") != null
                        && t.getLabelValue("action").equals("forecast")));
    }

    @Test
    @Order(4)
    public void testNotExpression() {
        // Find weather tools that are NOT forecasts
        List<ToolReference> nonForecastWeather =
                toolReferenceRepository.findAllFilterByLabelExpression("category=weather & !action=forecast");

        assertEquals(2, nonForecastWeather.size(), "Should find 2 non-forecast weather tools");
        assertTrue(nonForecastWeather.stream()
                .allMatch(t -> t.getLabelValue("category") != null
                        && t.getLabelValue("category").equals("weather")
                        && (t.getLabelValue("action") == null
                                || !t.getLabelValue("action").equals("forecast"))));
    }

    @Test
    @Order(5)
    public void testOrExpression() {
        // Find weather or news tools
        List<ToolReference> weatherOrNews =
                toolReferenceRepository.findAllFilterByLabelExpression("category=weather | category=news");

        assertEquals(6, weatherOrNews.size(), "Should find 6 weather or news tools");
        assertTrue(weatherOrNews.stream().allMatch(t -> {
            String category = t.getLabelValue("category");
            return category != null && (category.equals("weather") || category.equals("news"));
        }));
    }

    @Test
    @Order(6)
    public void testComplexExpression() {
        // Find (weather or news) tools with version 1.0
        List<ToolReference> result = toolReferenceRepository.findAllFilterByLabelExpression(
                "(category=weather | category=news) & version=1.0");

        assertEquals(4, result.size(), "Should find 4 tools matching complex expression");
        assertTrue(result.stream().allMatch(t -> {
            String category = t.getLabelValue("category");
            String version = t.getLabelValue("version");
            return category != null
                    && version != null
                    && (category.equals("weather") || category.equals("news"))
                    && version.equals("1.0");
        }));
    }

    @Test
    @Order(7)
    public void testNotEquals() {
        // Find tools where environment is NOT production
        List<ToolReference> nonProdTools =
                toolReferenceRepository.findAllFilterByLabelExpression("environment!=production");

        // Should find the development tool and all tools without environment label
        assertTrue(nonProdTools.size() >= 1, "Should find at least the development tool");

        // Verify none of them have environment=production
        assertTrue(nonProdTools.stream()
                .noneMatch(t -> t.getLabelValue("environment") != null
                        && t.getLabelValue("environment").equals("production")));
    }

    @Test
    @Order(8)
    public void testNegationOfDeprecated() {
        // Find non-deprecated weather tools
        List<ToolReference> activeWeather =
                toolReferenceRepository.findAllFilterByLabelExpression("category=weather & !deprecated=true");

        assertEquals(3, activeWeather.size(), "Should find 3 non-deprecated weather tools");
        assertTrue(activeWeather.stream()
                .allMatch(t -> t.getLabelValue("category") != null
                        && t.getLabelValue("category").equals("weather")
                        && (t.getLabelValue("deprecated") == null
                                || !t.getLabelValue("deprecated").equals("true"))));
    }

    @Test
    @Order(9)
    public void testVersionFiltering() {
        // Find version 2.0 tools
        List<ToolReference> v2Tools = toolReferenceRepository.findAllFilterByLabelExpression("version=2.0");

        assertEquals(1, v2Tools.size(), "Should find 1 version 2.0 tool");
        assertEquals("weather-forecast-2", v2Tools.get(0).getId());
    }

    @Test
    @Order(10)
    public void testMultipleAndConditions() {
        // Find weather forecast version 1.0
        List<ToolReference> specific = toolReferenceRepository.findAllFilterByLabelExpression(
                "category=weather & action=forecast & version=1.0");

        assertEquals(1, specific.size(), "Should find exactly 1 tool");
        assertEquals("weather-forecast-1", specific.get(0).getId());
    }

    @Test
    @Order(11)
    public void testComplexNegation() {
        // Find production tools that are NOT deprecated
        List<ToolReference> result =
                toolReferenceRepository.findAllFilterByLabelExpression("environment=production & !deprecated=true");

        assertEquals(1, result.size(), "Should find 1 production non-deprecated tool");
        assertEquals("tool-prod-1", result.get(0).getId());
    }

    @Test
    @Order(12)
    public void testNullLabelExpression() {
        // Null expression should return all tools
        List<ToolReference> allTools = toolReferenceRepository.findAllFilterByLabelExpression(null);

        assertEquals(10, allTools.size(), "Null expression should return all tools");
    }

    @Test
    @Order(13)
    public void testEmptyLabelExpression() {
        // Empty expression should return all tools
        List<ToolReference> allTools = toolReferenceRepository.findAllFilterByLabelExpression("");

        assertEquals(10, allTools.size(), "Empty expression should return all tools");
    }

    @Test
    @Order(14)
    public void testNoMatchingTools() {
        // Find tools with non-existent label
        List<ToolReference> result = toolReferenceRepository.findAllFilterByLabelExpression("category=nonexistent");

        assertEquals(0, result.size(), "Should find no tools with non-existent category");
    }

    @Test
    @Order(15)
    public void testInvalidLabelExpression() {
        // Invalid expression should throw exception
        assertThrows(
                Exception.class,
                () -> toolReferenceRepository.findAllFilterByLabelExpression("invalid syntax without operator"));
    }

    @Test
    @Order(16)
    public void testParenthesesPrecedence() {
        // Test operator precedence with parentheses
        // (category=weather | category=news) & version=1.0
        // Should be different from: category=weather | (category=news & version=1.0)
        List<ToolReference> result = toolReferenceRepository.findAllFilterByLabelExpression(
                "(category=weather | category=news) & version=1.0");

        // Should find weather and news tools with version 1.0
        assertEquals(4, result.size());
    }

    @Test
    @Order(17)
    public void testDoubleNegation() {
        // Double negation: !!category=weather should equal category=weather
        List<ToolReference> result = toolReferenceRepository.findAllFilterByLabelExpression("!!category=weather");

        assertEquals(4, result.size(), "Double negation should work correctly");
    }

    @Test
    @Order(18)
    public void testStatusFiltering() {
        // Find stable tools
        List<ToolReference> stableTools = toolReferenceRepository.findAllFilterByLabelExpression("status=stable");

        assertEquals(1, stableTools.size(), "Should find 1 stable tool");
        assertEquals("tool-prod-1", stableTools.get(0).getId());
    }

    @AfterAll
    public void cleanup() {
        // Clean up test data
        if (toolReferenceRepository instanceof AbstractInfinispanRepository) {
            toolReferenceRepository.removeAll();
        }
    }
}
