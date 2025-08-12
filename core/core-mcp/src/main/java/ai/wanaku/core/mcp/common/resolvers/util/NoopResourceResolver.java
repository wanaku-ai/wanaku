package ai.wanaku.core.mcp.common.resolvers.util;

import ai.wanaku.api.exceptions.ServiceNotFoundException;
import ai.wanaku.api.types.ResourceReference;
import ai.wanaku.api.types.io.ResourcePayload;
import ai.wanaku.core.mcp.common.resolvers.ResourceResolver;
import io.quarkiverse.mcp.server.ResourceContents;
import io.quarkiverse.mcp.server.ResourceManager;
import java.util.List;

/**
 * A resolver that does not to anything (mostly used for testing)
 */
public class NoopResourceResolver implements ResourceResolver {

    @Override
    public void provision(ResourcePayload resourcePayload) throws ServiceNotFoundException {}

    @Override
    public List<ResourceContents> read(ResourceManager.ResourceArguments arguments, ResourceReference mcpResource) {
        return List.of();
    }
}
