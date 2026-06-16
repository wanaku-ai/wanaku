package ai.wanaku.operator.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import ai.wanaku.operator.wanaku.WanakuCamelRouteSpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

public final class CamelRoutePackager {

    private static final ObjectMapper YAML_MAPPER =
            new ObjectMapper(new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));

    private CamelRoutePackager() {}

    public static String packageCamelRoute(WanakuCamelRouteSpec spec, String catalogName) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();

        String systemName = catalogName;

        files.put(
                "index.properties",
                synthesizeIndexProperties(catalogName, systemName, spec).getBytes(StandardCharsets.UTF_8));
        files.put(systemName + "/" + systemName + ".camel.yaml", synthesizeCamelYaml(spec.getRoute()));
        files.put(systemName + "/" + systemName + ".rules.yaml", synthesizeRulesYaml(spec.getMcp()));

        Map<String, String> props = spec.getProperties();
        if (props != null && !props.isEmpty()) {
            files.put(systemName + "/service.properties", synthesizePropertiesFile(props));
        }

        byte[] zip = createZipArchive(files);
        return Base64.getEncoder().encodeToString(zip);
    }

    static String synthesizeIndexProperties(String catalogName, String systemName, WanakuCamelRouteSpec spec) {
        Properties p = new Properties();
        p.setProperty("catalog.name", catalogName);
        p.setProperty("catalog.description", catalogName);
        p.setProperty("catalog.services", systemName);
        p.setProperty("catalog.routes." + systemName, systemName + "/" + systemName + ".camel.yaml");
        p.setProperty("catalog.rules." + systemName, systemName + "/" + systemName + ".rules.yaml");

        if (spec.getProperties() != null && !spec.getProperties().isEmpty()) {
            p.setProperty("catalog.properties." + systemName, systemName + "/service.properties");
        }

        StringBuilder sb = new StringBuilder();
        p.stringPropertyNames().stream()
                .sorted()
                .forEach(key ->
                        sb.append(key).append("=").append(p.getProperty(key)).append("\n"));
        return sb.toString();
    }

    static byte[] synthesizeCamelYaml(JsonNode route) throws IOException {
        if (route == null) {
            return "".getBytes(StandardCharsets.UTF_8);
        }
        return YAML_MAPPER.writeValueAsBytes(route);
    }

    static byte[] synthesizeRulesYaml(WanakuCamelRouteSpec.McpSpec mcp) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> mcpMap = new LinkedHashMap<>();
        root.put("mcp", mcpMap);

        if (mcp != null && mcp.getTools() != null && !mcp.getTools().isEmpty()) {
            List<Map<String, Object>> toolsList = new ArrayList<>();
            for (WanakuCamelRouteSpec.ToolSpec tool : mcp.getTools()) {
                Map<String, Object> toolEntry = new LinkedHashMap<>();
                Map<String, Object> toolBody = new LinkedHashMap<>();

                Map<String, Object> routeRef = new LinkedHashMap<>();
                routeRef.put("id", tool.getRouteId());
                toolBody.put("route", routeRef);
                toolBody.put("description", tool.getDescription());

                if (tool.getProperties() != null && !tool.getProperties().isEmpty()) {
                    List<Map<String, Object>> propsList = new ArrayList<>();
                    for (WanakuCamelRouteSpec.PropertySpec prop : tool.getProperties()) {
                        Map<String, Object> propMap = new LinkedHashMap<>();
                        propMap.put("name", prop.getName());
                        propMap.put("type", prop.getType());
                        propMap.put("description", prop.getDescription());
                        propMap.put("required", prop.isRequired());
                        propsList.add(propMap);
                    }
                    toolBody.put("properties", propsList);
                }

                toolEntry.put(tool.getName(), toolBody);
                toolsList.add(toolEntry);
            }
            mcpMap.put("tools", toolsList);
        }

        if (mcp != null && mcp.getResources() != null && !mcp.getResources().isEmpty()) {
            List<Map<String, Object>> resourcesList = new ArrayList<>();
            for (WanakuCamelRouteSpec.ResourceSpec resource : mcp.getResources()) {
                Map<String, Object> resourceEntry = new LinkedHashMap<>();
                Map<String, Object> resourceBody = new LinkedHashMap<>();

                Map<String, Object> routeRef = new LinkedHashMap<>();
                routeRef.put("id", resource.getRouteId());
                routeRef.put("description", resource.getDescription());
                routeRef.put("uri", resource.getUri());
                routeRef.put("mimeType", resource.getMimeType());
                resourceBody.put("route", routeRef);

                resourceEntry.put(resource.getName(), resourceBody);
                resourcesList.add(resourceEntry);
            }
            mcpMap.put("resources", resourcesList);
        }

        return YAML_MAPPER.writeValueAsBytes(root);
    }

    static byte[] synthesizePropertiesFile(Map<String, String> properties) {
        StringBuilder sb = new StringBuilder();
        properties.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e ->
                        sb.append(e.getKey()).append("=").append(e.getValue()).append("\n"));
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    static byte[] createZipArchive(Map<String, byte[]> files) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue());
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }
}
