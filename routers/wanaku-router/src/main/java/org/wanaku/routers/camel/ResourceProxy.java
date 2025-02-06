package org.wanaku.routers.camel;

import java.io.File;
import java.util.List;

import org.wanaku.api.resolvers.AsyncRequestHandler;
import org.wanaku.api.types.McpRequestStatus;
import org.wanaku.api.types.McpResource;
import org.wanaku.api.types.McpResourceData;

/**
 * Proxies between MCP URIs and Camel components capable of handling them
 */
public interface ResourceProxy {
    /**
     * The name of the proxy
     * @return
     */
    String name();

    /**
     * List resources that can be handled by this proxy
     * @param resourcesFile the resources' index file
     * @return
     */
    List<McpResource> list(File resourcesFile);

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
