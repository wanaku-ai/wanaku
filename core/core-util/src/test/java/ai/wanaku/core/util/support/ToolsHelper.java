package ai.wanaku.core.util.support;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import ai.wanaku.api.types.ToolReference;

public class ToolsHelper {
    public static final String TOOLS_INDEX = "target/test-classes/tools.json";

    public static ToolReference createToolReference(String name, String description, String uri, ToolReference.InputSchema inputSchema) {
        ToolReference toolReference = new ToolReference();
        toolReference.setName(name);
        toolReference.setDescription(description);
        toolReference.setUri(uri);
        toolReference.setType("http");
        toolReference.setInputSchema(inputSchema);

        return toolReference;
    }

    public static ToolReference.InputSchema createInputSchema(String type, Map<String, ToolReference.Property> properties) {
        ToolReference.InputSchema schemaInstance = new ToolReference.InputSchema();
        schemaInstance.setType(type);
        for (Map.Entry<String, ToolReference.Property> propertyEntry : properties.entrySet()) {
            propertyEntry.getValue().setPropertyName(propertyEntry.getKey());
            schemaInstance.getProperties().add(propertyEntry.getValue());
        }

        return schemaInstance;
    }

    public static ToolReference.Property createProperty(String type, String description) {
        ToolReference.Property propertyInstance = new ToolReference.Property();
        propertyInstance.setType(type);
        propertyInstance.setDescription(description);
        return propertyInstance;
    }


    public static List<ToolReference> testFixtures() {
        ToolReference.InputSchema inputSchema1 = createInputSchema(
                "http",
                Collections.singletonMap("username", createProperty("string", "A username."))
        );

        ToolReference toolReference1 = createToolReference(
                "Tool 1",
                "This is a description of Tool 1.",
                "https://example.com/tool-1",
                inputSchema1
        );

        ToolReference.InputSchema inputSchema2 = createInputSchema(
                "https",
                Map.of("id", createProperty("integer", "An order ID."),
                        "date", createProperty("Date", "The date of the order."))
        );

        ToolReference toolReference2 = createToolReference(
                "Tool 2",
                "This is a description of Tool 2.",
                "https://example.com/tool-2",
                inputSchema2
        );

        return Arrays.asList(toolReference1, toolReference2);
    }

}
