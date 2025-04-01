package ai.wanaku.core.mcp.common.resolvers;

import java.util.List;
import java.util.Map;

import io.quarkiverse.mcp.server.ResourceContents;
import ai.wanaku.api.types.ResourceReference;
import io.quarkiverse.mcp.server.ResourceManager;

/**
 * A resolver that consumes MCP requests and resolves what type of resource acquirer
 * should handle it
 */
public interface ResourceResolver extends Resolver {

    /**
     * Read resources
     * @param arguments the resource request arguments
     * @param mcpResource the resource to read
     * @return the resource contents in a format specific to the content that had been read
     */
    List<ResourceContents> read(ResourceManager.ResourceArguments arguments, ResourceReference mcpResource);
}
