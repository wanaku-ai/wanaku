package ai.wanaku.operator.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import ai.wanaku.operator.wanaku.WanakuCamelRouteSpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CamelRoutePackagerTest {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    @Test
    void packageCamelRouteProducesValidZip() throws IOException {
        WanakuCamelRouteSpec spec = createToolSpec();

        String base64 = CamelRoutePackager.packageCamelRoute(spec, "my-tool");
        assertNotNull(base64);

        Map<String, String> zipContents = extractZip(base64);
        assertTrue(zipContents.containsKey("index.properties"));
        assertTrue(zipContents.containsKey("my-tool/my-tool.camel.yaml"));
        assertTrue(zipContents.containsKey("my-tool/my-tool.rules.yaml"));
        assertTrue(zipContents.containsKey("my-tool/service.properties"));
    }

    @Test
    void indexPropertiesHasCorrectStructure() throws IOException {
        WanakuCamelRouteSpec spec = createToolSpec();

        String base64 = CamelRoutePackager.packageCamelRoute(spec, "my-tool");
        Map<String, String> zipContents = extractZip(base64);

        Properties props = new Properties();
        props.load(new ByteArrayInputStream(zipContents.get("index.properties").getBytes(StandardCharsets.UTF_8)));

        assertEquals("my-tool", props.getProperty("catalog.name"));
        assertEquals("my-tool", props.getProperty("catalog.services"));
        assertEquals("my-tool/my-tool.camel.yaml", props.getProperty("catalog.routes.my-tool"));
        assertEquals("my-tool/my-tool.rules.yaml", props.getProperty("catalog.rules.my-tool"));
        assertEquals("my-tool/service.properties", props.getProperty("catalog.properties.my-tool"));
    }

    @Test
    void rulesYamlContainsToolDefinition() throws IOException {
        WanakuCamelRouteSpec spec = createToolSpec();

        String base64 = CamelRoutePackager.packageCamelRoute(spec, "my-tool");
        Map<String, String> zipContents = extractZip(base64);

        String rulesYaml = zipContents.get("my-tool/my-tool.rules.yaml");
        JsonNode rules = YAML_MAPPER.readTree(rulesYaml);

        assertNotNull(rules.get("mcp"));
        assertNotNull(rules.get("mcp").get("tools"));
        assertEquals(1, rules.get("mcp").get("tools").size());

        JsonNode tool = rules.get("mcp").get("tools").get(0).get("search-tool");
        assertNotNull(tool);
        assertEquals("search-route", tool.get("route").get("id").asText());
        assertEquals("Search the web", tool.get("description").asText());
        assertEquals(1, tool.get("properties").size());
        assertEquals("wanaku_body", tool.get("properties").get(0).get("name").asText());
    }

    @Test
    void rulesYamlContainsResourceDefinition() throws IOException {
        WanakuCamelRouteSpec spec = createResourceSpec();

        String base64 = CamelRoutePackager.packageCamelRoute(spec, "my-resource");
        Map<String, String> zipContents = extractZip(base64);

        String rulesYaml = zipContents.get("my-resource/my-resource.rules.yaml");
        JsonNode rules = YAML_MAPPER.readTree(rulesYaml);

        assertNotNull(rules.get("mcp").get("resources"));
        JsonNode resource = rules.get("mcp").get("resources").get(0).get("s3-read");
        assertNotNull(resource);
        assertEquals("s3-route", resource.get("route").get("id").asText());
        assertEquals("wanaku://s3-read", resource.get("route").get("uri").asText());
    }

    @Test
    void emptyPropertiesOmitsServicePropertiesFile() throws IOException {
        WanakuCamelRouteSpec spec = createToolSpec();
        spec.setProperties(null);

        String base64 = CamelRoutePackager.packageCamelRoute(spec, "my-tool");
        Map<String, String> zipContents = extractZip(base64);

        assertFalse(zipContents.containsKey("my-tool/service.properties"));

        Properties props = new Properties();
        props.load(new ByteArrayInputStream(zipContents.get("index.properties").getBytes(StandardCharsets.UTF_8)));
        assertFalse(props.containsKey("catalog.properties.my-tool"));
    }

    @Test
    void emptyToolsProducesValidRulesYaml() throws IOException {
        WanakuCamelRouteSpec spec = new WanakuCamelRouteSpec();
        spec.setRouterRef("my-router");
        spec.setMcp(new WanakuCamelRouteSpec.McpSpec());

        byte[] rules = CamelRoutePackager.synthesizeRulesYaml(spec.getMcp());
        JsonNode node = YAML_MAPPER.readTree(rules);
        assertNotNull(node.get("mcp"));
    }

    @Test
    void nullRouteProducesEmptyCamelYaml() throws IOException {
        byte[] result = CamelRoutePackager.synthesizeCamelYaml(null);
        assertEquals(0, result.length);
    }

    @Test
    void base64RoundTripsCorrectly() throws IOException {
        WanakuCamelRouteSpec spec = createToolSpec();
        String base64 = CamelRoutePackager.packageCamelRoute(spec, "roundtrip-test");

        byte[] decoded = Base64.getDecoder().decode(base64);
        assertTrue(decoded.length > 0);

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(decoded))) {
            ZipEntry entry = zis.getNextEntry();
            assertNotNull(entry);
        }
    }

    private WanakuCamelRouteSpec createToolSpec() {
        WanakuCamelRouteSpec spec = new WanakuCamelRouteSpec();
        spec.setRouterRef("my-router");

        String routeYaml =
                """
                - route:
                    id: search-route
                    from:
                      uri: direct:wanaku
                      steps:
                        - log:
                            message: "Searching..."
                """;
        try {
            spec.setRoute(YAML_MAPPER.readTree(routeYaml));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        WanakuCamelRouteSpec.PropertySpec prop = new WanakuCamelRouteSpec.PropertySpec();
        prop.setName("wanaku_body");
        prop.setType("string");
        prop.setDescription("The search term");
        prop.setRequired(true);

        WanakuCamelRouteSpec.ToolSpec tool = new WanakuCamelRouteSpec.ToolSpec();
        tool.setName("search-tool");
        tool.setRouteId("search-route");
        tool.setDescription("Search the web");
        tool.setProperties(List.of(prop));

        WanakuCamelRouteSpec.McpSpec mcp = new WanakuCamelRouteSpec.McpSpec();
        mcp.setTools(List.of(tool));
        spec.setMcp(mcp);

        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("api.key", "{{api.key}}");
        spec.setProperties(properties);

        return spec;
    }

    private WanakuCamelRouteSpec createResourceSpec() {
        WanakuCamelRouteSpec spec = new WanakuCamelRouteSpec();
        spec.setRouterRef("my-router");

        String routeYaml =
                """
                - route:
                    id: s3-route
                    from:
                      uri: direct:wanaku
                """;
        try {
            spec.setRoute(YAML_MAPPER.readTree(routeYaml));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        WanakuCamelRouteSpec.ResourceSpec resource = new WanakuCamelRouteSpec.ResourceSpec();
        resource.setName("s3-read");
        resource.setRouteId("s3-route");
        resource.setDescription("Read from S3");
        resource.setUri("wanaku://s3-read");
        resource.setMimeType("application/octet-stream");

        WanakuCamelRouteSpec.McpSpec mcp = new WanakuCamelRouteSpec.McpSpec();
        mcp.setResources(List.of(resource));
        spec.setMcp(mcp);

        return spec;
    }

    private Map<String, String> extractZip(String base64) throws IOException {
        byte[] decoded = Base64.getDecoder().decode(base64);
        Map<String, String> contents = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(decoded))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                contents.put(entry.getName(), new String(zis.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return contents;
    }
}
