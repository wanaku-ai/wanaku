package org.wanaku.api.resolvers.util;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.wanaku.api.exceptions.ToolNotFoundException;
import org.wanaku.api.resolvers.ToolsResolver;
import org.wanaku.api.types.McpTool;
import org.wanaku.api.types.McpToolStatus;

public class NoopToolsResolver implements ToolsResolver {
    @Override
    public List<McpTool> list() {
        return List.of();
    }

    @Override
    public McpTool find(String name) throws ToolNotFoundException {
        return null;
    }

    @Override
    public McpToolStatus call(McpTool tool, Map<String, Object> properties) {
        return null;
    }

    @Override
    public File indexLocation() {
        return null;
    }
}
