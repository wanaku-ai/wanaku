package org.wanaku.server.quarkus.support;

import java.io.File;

import org.wanaku.api.resolvers.util.NoopToolsResolver;
import org.wanaku.core.util.support.ToolsHelper;

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
