package ai.wanaku.core.mcp.common.resolvers.util;

import java.io.File;
import java.util.List;
import java.util.Map;

import io.quarkiverse.mcp.server.ResourceContents;
import ai.wanaku.api.types.ResourceReference;
import ai.wanaku.core.mcp.common.resolvers.ResourceResolver;
import io.quarkiverse.mcp.server.ResourceManager;

/**
 * A resolver that does not to anything (mostly used for testing)
 */
public class NoopResourceResolver implements ResourceResolver {

    @Override
    public File indexLocation() {
        return null;
    }

    @Override
    public List<ResourceContents> read(ResourceManager.ResourceArguments arguments, ResourceReference mcpResource) {
        return List.of();
    }

    @Override
    public Map<String, String> getServiceConfigurations(String target) {
        return Map.of();
    }
}
