package ai.wanaku.core.mcp.common;

import ai.wanaku.capabilities.sdk.api.types.CallableReference;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;

/**
 * Interface for executing tool invocations.
 * <p>
 * This interface defines the contract for executing tools, separate from
 * proxy and provisioning concerns. Implementations handle the actual
 * execution logic for tool invocations.
 * <p>
 * This interface is part of the composition pattern used to decouple tool
 * execution from proxy responsibilities, allowing for better separation of
 * concerns and easier testing.
 *
 * @see Tool
 * @see ToolAdapter
 */
public interface ToolExecutor {
    /**
     * Executes a tool with the specified arguments.
     * <p>
     * This method performs the actual tool execution using the provided arguments
     * and returns a response containing the execution results. The tool reference
     * provides metadata about the tool being invoked, while the arguments contain
     * the actual parameter values for this specific invocation.
     *
     * @param toolArguments the arguments to pass to the tool, containing parameter values
     * @param toolReference the reference to the tool being called, providing metadata and schema information
     * @return a tool response containing the execution results, status, and any output or error information
     */
    ToolResponse execute(ToolManager.ToolArguments toolArguments, CallableReference toolReference);
}
