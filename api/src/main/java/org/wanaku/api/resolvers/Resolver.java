package org.wanaku.api.resolvers;

import java.io.File;

public interface Resolver {
    String DEFAULT_RESOURCES_INDEX_FILE_NAME = "resources.json";
    String DEFAULT_TOOLS_INDEX_FILE_NAME = "tools.json";

    /**
     * The location of the index file
     * @return
     */
    File indexLocation();
}
