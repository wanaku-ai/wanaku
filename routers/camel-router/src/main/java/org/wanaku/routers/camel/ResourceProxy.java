package org.wanaku.routers.camel;

import java.util.List;

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
     * Eval an MCP URI handling it as appropriate by the component
     * @param uri
     * @return
     */
    List<McpResourceData> eval(String uri);

}
