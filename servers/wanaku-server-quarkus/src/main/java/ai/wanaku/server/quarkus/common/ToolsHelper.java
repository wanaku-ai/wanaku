package ai.wanaku.server.quarkus.common;

import ai.wanaku.api.types.CallableReference;
import ai.wanaku.api.types.InputSchema;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import java.util.List;
import java.util.function.BiFunction;
import org.jboss.logging.Logger;

/**
 * Helper class for dealing with tools
 */
public class ToolsHelper {
    private static final Logger LOG = Logger.getLogger(ToolsHelper.class);

    private static boolean isRequired(CallableReference toolReference) {
        boolean required = false;

        InputSchema inputSchema = toolReference.getInputSchema();
        if (inputSchema == null) {
            return false;
        }

        List<String> requiredList = inputSchema.getRequired();

        if (requiredList != null) {
            required = requiredList.contains(toolReference.getName());
        }
        return required;
    }

    // TODO:
    private static Class<?> toType(CallableReference mcpResource) {
        return String.class;
    }

    /**
     * Registers a tool with the given tool manager by applying the provided handler function
     * to expose the tool's functionality.
     *
     * @param toolReference The reference to the tool being registered
     * @param toolManager   The tool manager instance responsible for managing tools
     * @param handler       A BiFunction that takes ToolArguments and ToolReference as input,
     *                      and returns a ToolResponse. This function will be applied to invoke the tool's functionality.
     */
    public static void registerTool(
            CallableReference toolReference, ToolManager toolManager,
            BiFunction<ToolManager.ToolArguments, CallableReference, ToolResponse> handler) {
        LOG.debugf("Registering tool: %s", toolReference.getName());

        ToolManager.ToolDefinition toolDefinition = toolManager.newTool(toolReference.getName())
                .setDescription(toolReference.getDescription());

        final boolean required = isRequired(toolReference);

        Class<?> type = toType(toolReference);
        InputSchema inputSchema = toolReference.getInputSchema();
        if (inputSchema != null) {
            inputSchema.getProperties().forEach((key, value) ->
                    toolDefinition.addArgument(key, value.getDescription(), required, type));
        }


        toolDefinition
                .setHandler(ta ->  handler.apply(ta, toolReference))
                .register();
    }
}
