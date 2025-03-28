package ai.wanaku.server.quarkus.support;

import java.io.File;

import ai.wanaku.core.mcp.common.resolvers.util.NoopResourceResolver;
import ai.wanaku.core.util.support.ResourcesHelper;

public class TestResourceResolver extends NoopResourceResolver {

    @Override
    public File indexLocation() {
        File indexPath = new File(ResourcesHelper.RESOURCES_INDEX);
        if (!indexPath.exists()) {
            indexPath.getParentFile().mkdirs();
        }

        return indexPath;
    }
}
