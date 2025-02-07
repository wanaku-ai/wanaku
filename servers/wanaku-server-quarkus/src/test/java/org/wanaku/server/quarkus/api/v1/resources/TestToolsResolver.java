package org.wanaku.server.quarkus.api.v1.resources;

import java.io.File;

import org.wanaku.api.resolvers.util.NoopToolsResolver;

public class TestToolsResolver extends NoopToolsResolver {
    public static final String INDEX_FILE = "target/test-data/index/tools.json";

    @Override
    public File indexLocation() {
        File indexPath = new File(INDEX_FILE);
        if (!indexPath.exists()) {
            indexPath.getParentFile().mkdirs();
        }

        return indexPath;
    }
}
