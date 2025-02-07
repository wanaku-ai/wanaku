package org.wanaku.routers.camel.proxies;

import java.io.File;
import java.util.List;

import org.wanaku.api.resolvers.AsyncRequestHandler;
import org.wanaku.api.types.McpRequestStatus;
import org.wanaku.api.types.McpResource;
import org.wanaku.api.types.McpResourceData;

/**
 * Proxies between MCP URIs and Camel components capable of handling them
 */
public interface ResourceProxy extends Proxy {
    /**
     * List resources that can be handled by this proxy
     * @param index the index file
     * @return
     */
    List<McpResource> list(File index);

    /**
     * Eval an MCP URI handling it as appropriate by the component
     * @param uri
     * @return
     */
    List<McpResourceData> eval(String uri);

    /**
     * Subscribe to the given MCP URI
     * @param uri
     * @return
     */
    void subscribe(String uri, AsyncRequestHandler<McpRequestStatus<McpResourceData>> callback);

}
