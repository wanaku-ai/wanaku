package ai.wanaku.core.mcp.common.resolvers;

import ai.wanaku.capabilities.sdk.api.exceptions.ServiceNotFoundException;
import ai.wanaku.capabilities.sdk.api.exceptions.ToolNotFoundException;
import ai.wanaku.capabilities.sdk.api.types.ToolReference;
import ai.wanaku.capabilities.sdk.api.types.io.ToolPayload;
import ai.wanaku.core.mcp.common.Tool;

/**
 * Resolver interface for tool capability requests in the MCP system.
 * <p>
 * This resolver processes MCP tool requests and determines which tool provider
 * service should handle them. It supports both tool provisioning (registering
 * tools with their configuration) and tool resolution (finding the appropriate
 * tool implementation for a given reference).
 * <p>
 * Implementations of this interface are responsible for:
 * <ul>
 *   <li>Provisioning tools by loading their properties from remote services</li>
 *   <li>Resolving tool references to concrete {@link Tool} implementations</li>
 *   <li>Managing the lifecycle of tool capabilities</li>
 * </ul>
 *
 * @see Resolver
 * @see Tool
 */
public interface ToolsResolver extends Resolver {

    /**
     * Provisions a tool by loading its properties from the remote service capable of handling it.
     * <p>
     * This method registers the tool with the system and retrieves any necessary
     * configuration or metadata from the service provider that will handle tool
     * invocations.
     *
     * @param toolPayload the tool payload containing the tool reference and provisioning data
     * @throws ServiceNotFoundException if a service capable of handling the tool cannot be found
     */
    void provision(ToolPayload toolPayload) throws ServiceNotFoundException;

    /**
     * Resolves a tool reference to a concrete tool implementation.
     * <p>
     * Given a tool reference, this method determines which {@link Tool} instance
     * should be used to handle invocations of that tool. The resolved tool is
     * ready to be called with appropriate arguments.
     *
     * @param toolReference the reference to the tool to resolve
     * @return an instance of the requested tool
     * @throws ToolNotFoundException if the tool cannot be found or resolved
     */
    Tool resolve(ToolReference toolReference) throws ToolNotFoundException;
}
