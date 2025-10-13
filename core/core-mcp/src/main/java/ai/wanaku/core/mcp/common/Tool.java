package ai.wanaku.core.mcp.common;

import ai.wanaku.api.types.CallableReference;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;

/**
 * Interface for executable tool implementations in the MCP system.
 * <p>
 * A tool represents an executable capability that can be invoked by AI agents
 * through the Model Context Protocol (MCP). Tools encapsulate specific functionality
 * and can accept arguments to customize their behavior during execution.
 * </p>
 * <p>
 * Implementations of this interface handle the actual execution logic for tools,
 * processing arguments and returning results in a standardized format. Tools may
 * interact with external services, perform computations, or execute any other
 * programmatic task.
 * </p>
 * <p>
 * Example tool types include:
 * <ul>
 *   <li>HTTP-based tools that call REST APIs</li>
 *   <li>Command execution tools that run system commands</li>
 *   <li>Search tools that query external data sources</li>
 *   <li>Computation tools that perform calculations</li>
 * </ul>
 * </p>
 *
 * @see CallableReference
 */
public interface Tool {
    /**
     * Invokes this tool with the specified arguments.
     * <p>
     * This method executes the tool's logic using the provided arguments and
     * returns a response containing the execution results. The tool reference
     * provides metadata about the tool being invoked, while the arguments contain
     * the actual parameter values for this specific invocation.
     * </p>
     *
     * @param toolArguments the arguments to pass to the tool, containing parameter values
     * @param toolReference the reference to the tool being called, providing metadata and schema information
     * @return a tool response containing the execution results, status, and any output or error information
     */
    ToolResponse call(ToolManager.ToolArguments toolArguments, CallableReference toolReference);
}
