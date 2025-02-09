package org.wanaku.server.quarkus.support;

import java.io.File;

import org.wanaku.api.resolvers.util.NoopResourceResolver;
import org.wanaku.core.util.support.ResourcesHelper;

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
