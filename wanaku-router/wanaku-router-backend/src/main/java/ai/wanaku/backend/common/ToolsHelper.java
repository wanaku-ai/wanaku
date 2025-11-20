package ai.wanaku.backend.common;

import ai.wanaku.api.exceptions.EntityAlreadyExistsException;
import ai.wanaku.api.types.CallableReference;
import ai.wanaku.api.types.InputSchema;
import ai.wanaku.api.types.Namespace;
import ai.wanaku.api.types.Property;
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

    private static Class<?> toType(Property property) {
        return switch (property.getType().toLowerCase()) {
            case "string" -> String.class;
            case "int", "integer" -> Integer.class;
            case "boolean" -> Boolean.class;
            case "number" -> Number.class;
            default -> Object.class;
        };
    }

    private static void addArgument(
            String key, Property value, ToolManager.ToolDefinition toolDefinition, boolean required) {
        Class<?> type = toType(value);
        toolDefinition.addArgument(key, value.getDescription(), required, type);
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
            CallableReference toolReference,
            ToolManager toolManager,
            BiFunction<ToolManager.ToolArguments, CallableReference, ToolResponse> handler) {
        registerTool(toolReference, toolManager, null, handler);
    }

    /**
     * Registers a tool with the given tool manager by applying the provided handler function
     * to expose the tool's functionality.
     *
     * @param toolReference The reference to the tool being registered
     * @param toolManager   The tool manager instance responsible for managing tools
     * @param namespace     The namespace to use for registering the tool
     * @param handler       A BiFunction that takes ToolArguments and ToolReference as input,
     *                      and returns a ToolResponse. This function will be applied to invoke the tool's functionality.
     */
    public static void registerTool(
            CallableReference toolReference,
            ToolManager toolManager,
            Namespace namespace,
            BiFunction<ToolManager.ToolArguments, CallableReference, ToolResponse> handler) {

        if (toolManager.getTool(toolReference.getName()) != null) {
            throw EntityAlreadyExistsException.forName(toolReference.getName());
        }

        try {
            ToolManager.ToolDefinition toolDefinition =
                    toolManager.newTool(toolReference.getName()).setDescription(toolReference.getDescription());

            final boolean required = isRequired(toolReference);

            InputSchema inputSchema = toolReference.getInputSchema();
            if (inputSchema != null) {
                inputSchema.getProperties().forEach((key, value) -> addArgument(key, value, toolDefinition, required));
            }

            if (namespace != null) {
                LOG.debugf(
                        "Registering tool %s in namespace %s with path %s",
                        toolReference.getName(), namespace.getName(), namespace.getPath());
                toolDefinition
                        .setServerName(namespace.getPath())
                        .setHandler(ta -> handler.apply(ta, toolReference))
                        .register();
            } else {
                LOG.debugf("Registering tool %s", toolReference.getName());
                toolDefinition
                        .setHandler(ta -> handler.apply(ta, toolReference))
                        .register();
            }
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("already exists")) {
                throw EntityAlreadyExistsException.forName(toolReference.getName());
            } else {
                throw e;
            }
        }
    }
}
