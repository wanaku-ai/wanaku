package ai.wanaku.core.mcp.common.resolvers.util;

import java.io.File;
import java.util.Map;

import ai.wanaku.api.exceptions.ToolNotFoundException;
import ai.wanaku.api.types.ToolReference;
import ai.wanaku.core.mcp.common.Tool;
import ai.wanaku.core.mcp.common.resolvers.ToolsResolver;

public class NoopToolsResolver implements ToolsResolver {
    @Override
    public Tool resolve(ToolReference toolReference) throws ToolNotFoundException {
        return null;
    }

    @Override
    public Map<String, String> getServiceConfigurations(String target) {
        return Map.of();
    }

    @Override
    public File indexLocation() {
        return null;
    }
}
