package org.wanaku.api.resolvers;

import java.io.File;
import java.util.List;

import org.wanaku.api.types.McpResource;
import org.wanaku.api.types.McpResourceData;

public interface ResourceResolver extends Resolver {

    /**
     * The location of the index file
     * @return
     */
    File indexLocation();

    /**
     * List resources
     * @return
     */
    List<McpResource> resources();

    /**
     * Read resources
     * @param uri
     * @return
     */
    List<McpResourceData> read(String uri);
}
