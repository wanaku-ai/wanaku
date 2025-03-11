package ai.wanaku.core.mcp.common.resolvers.util;

import java.io.File;
import java.util.List;
import java.util.Map;

import io.quarkiverse.mcp.server.ResourceContents;
import ai.wanaku.api.types.ResourceReference;
import ai.wanaku.core.mcp.common.resolvers.ResourceResolver;
import io.quarkiverse.mcp.server.ResourceManager;

public class NoopResourceResolver implements ResourceResolver {

    @Override
    public String index() {
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
