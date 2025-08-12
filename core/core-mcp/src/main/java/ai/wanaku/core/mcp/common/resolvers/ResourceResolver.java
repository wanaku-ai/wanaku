package ai.wanaku.core.mcp.common.resolvers;

import ai.wanaku.api.exceptions.ServiceNotFoundException;
import ai.wanaku.api.types.ResourceReference;
import ai.wanaku.api.types.io.ResourcePayload;
import io.quarkiverse.mcp.server.ResourceContents;
import io.quarkiverse.mcp.server.ResourceManager;
import java.util.List;

/**
 * A resolver that consumes MCP requests and resolves what type of resource acquirer
 * should handle it
 */
public interface ResourceResolver extends Resolver {

    /**
     * Given a reference, load properties (arguments) defined on the remote service capable of handling it
     * @param resourcePayload the resource payload
     * @throws ServiceNotFoundException if a service capable of handling such a resource cannot be found
     */
    void provision(ResourcePayload resourcePayload) throws ServiceNotFoundException;

    /**
     * Read resources
     * @param arguments the resource request arguments
     * @param mcpResource the resource to read
     * @return the resource contents in a format specific to the content that had been read
     */
    List<ResourceContents> read(ResourceManager.ResourceArguments arguments, ResourceReference mcpResource);
}
