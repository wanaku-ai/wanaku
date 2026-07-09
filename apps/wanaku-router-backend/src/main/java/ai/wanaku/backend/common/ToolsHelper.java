package ai.wanaku.backend.common;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jboss.logging.Logger;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import io.smallrye.mutiny.Uni;
import ai.wanaku.capabilities.sdk.api.exceptions.EntityAlreadyExistsException;
import ai.wanaku.capabilities.sdk.api.types.CallableReference;
import ai.wanaku.capabilities.sdk.api.types.InputSchema;
import ai.wanaku.capabilities.sdk.api.types.Namespace;

public class ToolsHelper {
    private static final Logger LOG = Logger.getLogger(ToolsHelper.class);

    @FunctionalInterface
    public interface ToolHandler {
        Uni<McpSchema.CallToolResult> execute(
                McpSchema.CallToolRequest request,
                String sessionId,
                McpTransportContext transportContext,
                CallableReference toolReference);
    }

    private static boolean isRequired(CallableReference toolReference) {
        InputSchema inputSchema = toolReference.getInputSchema();
        if (inputSchema == null) {
            return false;
        }

        List<String> requiredList = inputSchema.getRequired();
        if (requiredList != null) {
            return requiredList.contains(toolReference.getName());
        }
        return false;
    }

    public static void registerTool(CallableReference toolReference, McpSyncServer server, ToolHandler handler) {
        registerTool(toolReference, server, null, handler);
    }

    public static void registerTool(
            CallableReference toolReference, McpSyncServer server, Namespace namespace, ToolHandler handler) {

        Map<String, Object> schemaMap = buildInputSchema(toolReference);

        McpSchema.Tool tool = McpSchema.Tool.builder(toolReference.getName(), schemaMap)
                .description(toolReference.getDescription())
                .build();

        McpServerFeatures.SyncToolSpecification spec = McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    return handler.execute(request, exchange.sessionId(), exchange.transportContext(), toolReference)
                            .await()
                            .indefinitely();
                })
                .build();

        try {
            if (namespace != null) {
                LOG.debugf(
                        "Registering tool %s in namespace %s with path %s",
                        toolReference.getName(), namespace.getName(), namespace.getPath());
            } else {
                LOG.debugf("Registering tool %s", toolReference.getName());
            }
            server.addTool(spec);
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                throw EntityAlreadyExistsException.forName(toolReference.getName());
            }
            throw e;
        }
    }

    private static Map<String, Object> buildInputSchema(CallableReference toolReference) {
        Map<String, Object> schemaMap = new LinkedHashMap<>();
        schemaMap.put("type", "object");

        InputSchema inputSchema = toolReference.getInputSchema();
        if (inputSchema != null && inputSchema.getProperties() != null) {
            Map<String, Object> propsMap = new LinkedHashMap<>();
            inputSchema.getProperties().forEach((key, property) -> {
                Map<String, Object> propDef = new LinkedHashMap<>();
                propDef.put("type", property.getType() != null ? property.getType() : "string");
                if (property.getDescription() != null) {
                    propDef.put("description", property.getDescription());
                }
                propsMap.put(key, propDef);
            });
            schemaMap.put("properties", propsMap);

            List<String> required = inputSchema.getRequired();
            if (required != null && !required.isEmpty()) {
                schemaMap.put("required", required);
            }
        }

        return schemaMap;
    }
}
