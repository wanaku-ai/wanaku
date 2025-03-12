package org.wanaku.server.quarkus.support;

import ai.wanaku.core.mcp.common.resolvers.util.NoopToolsResolver;
import ai.wanaku.core.util.support.ToolsHelper;

import java.io.File;

public class TestToolsResolver extends NoopToolsResolver {
    @Override
    public File indexLocation() {
        File indexPath = new File(ToolsHelper.TOOLS_INDEX);
        if (!indexPath.exists()) {
            indexPath.getParentFile().mkdirs();
        }

        return indexPath;
    }
}
