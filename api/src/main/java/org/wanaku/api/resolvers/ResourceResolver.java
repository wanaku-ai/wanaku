package org.wanaku.api.resolvers;

import java.util.List;

import org.wanaku.api.types.McpRequestStatus;
import org.wanaku.api.types.McpResource;
import org.wanaku.api.types.McpResourceData;

public interface ResourceResolver extends Resolver {

    /**
     * List resources
     * @return
     */
    List<McpResource> list();

    /**
     * Read resources
     * @param uri
     * @return
     */
    List<McpResourceData> read(String uri);

    /**
     * Subscribe to resources
     * @param uri
     * @return The status of the request
     */
    void subscribe(String uri, AsyncRequestHandler<McpRequestStatus<McpResourceData>> callback);
}
