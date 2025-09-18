package ai.wanaku.backend.proxies;

/**
 * Proxies between MCP URIs and services capable of handling them
 */
public interface Proxy {
    /**
     * The name of the proxy
     * @return the name of the proxy
     */
    String name();
}
