package org.wanaku.routers.camel.proxies;

/**
 * Proxies between MCP URIs and Camel components capable of handling them
 */
public interface Proxy {
    /**
     * The name of the proxy
     * @return
     */
    String name();

}
