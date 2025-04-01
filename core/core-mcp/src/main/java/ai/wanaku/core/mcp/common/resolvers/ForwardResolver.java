package ai.wanaku.core.mcp.common.resolvers;

import ai.wanaku.api.exceptions.ServiceUnavailableException;
import ai.wanaku.api.exceptions.ToolNotFoundException;
import ai.wanaku.api.types.CallableReference;
import ai.wanaku.api.types.RemoteToolReference;
import ai.wanaku.api.types.ResourceReference;
import ai.wanaku.core.mcp.common.Tool;
import java.util.List;

public interface ForwardResolver extends ResourceResolver {

    /**
     * Given a reference, resolves what tool would call it
     * @param toolReference the reference to the tool
     * @return An instance of the requested tool
     * @throws ToolNotFoundException if the tools cannot be found
     */
    Tool resolve(CallableReference toolReference) throws ToolNotFoundException;

    List<ResourceReference> listResources() throws ServiceUnavailableException;

    List<RemoteToolReference> listTools() throws ServiceUnavailableException;

}
