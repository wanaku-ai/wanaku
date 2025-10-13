package ai.wanaku.backend.proxies;

/**
 * Interface for proxy implementations that mediate between MCP URIs and backend services.
 * <p>
 * Proxies in the Wanaku system handle the routing and translation of requests from
 * Model Context Protocol (MCP) URIs to the appropriate backend services that can
 * process them. Different proxy implementations may support different transport
 * mechanisms, protocols, or service types.
 * </p>
 * <p>
 * Common proxy implementations include:
 * <ul>
 *   <li>HTTP/REST proxies for web-based MCP servers</li>
 *   <li>SSE (Server-Sent Events) proxies for streaming MCP servers</li>
 *   <li>STDIO proxies for process-based MCP servers</li>
 * </ul>
 * </p>
 */
public interface Proxy {
    /**
     * Gets the name identifier for this proxy implementation.
     * <p>
     * The name typically indicates the transport mechanism or protocol
     * that this proxy handles (e.g., "http", "sse", "stdio").
     * </p>
     *
     * @return the proxy name identifier
     */
    String name();
}
