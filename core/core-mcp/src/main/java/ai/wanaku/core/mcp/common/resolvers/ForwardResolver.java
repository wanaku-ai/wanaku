package ai.wanaku.core.mcp.common.resolvers;

import ai.wanaku.capabilities.sdk.api.exceptions.ServiceUnavailableException;
import ai.wanaku.capabilities.sdk.api.exceptions.ToolNotFoundException;
import ai.wanaku.capabilities.sdk.api.types.CallableReference;
import ai.wanaku.capabilities.sdk.api.types.RemoteToolReference;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;
import ai.wanaku.core.mcp.common.Tool;
import java.util.List;

/**
 * A resolver that is used to resolve the tools, taking into account the available MCP forwards
 * registered in the router.
 */
public interface ForwardResolver extends ResourceResolver {

    /**
     * Given a reference, resolves what tool would call it
     * @param toolReference the reference to the tool
     * @return An instance of the requested tool
     * @throws ToolNotFoundException if the tools cannot be found
     */
    Tool resolve(CallableReference toolReference) throws ToolNotFoundException;

    /**
     * Lists all available resources that can be resolved by this {@code ForwardResolver}.
     *
     * @return A list of {@link ResourceReference} objects representing the available resources.
     * @throws ServiceUnavailableException if the service responsible for listing resources is unavailable.
     */
    List<ResourceReference> listResources() throws ServiceUnavailableException;

    /**
     * Lists all available remote tools that can be resolved by this {@code ForwardResolver}.
     *
     * @return A list of {@link RemoteToolReference} objects representing the available remote tools.
     * @throws ServiceUnavailableException if the service responsible for listing tools is unavailable.
     */
    List<RemoteToolReference> listTools() throws ServiceUnavailableException;
}
