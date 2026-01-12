package ai.wanaku.backend.bridge;

/**
 * Interface for proxy implementations that mediate between MCP URIs and backend services.
 * <p>
 * Proxies in the Wanaku system handle the routing and translation of requests from
 * Model Context Protocol (MCP) URIs to the appropriate backend services that can
 * process them. Different proxy implementations may support different transport
 * mechanisms, protocols, or service types.
 */
public interface Bridge {
    /**
     * Returns the name of this proxy implementation.
     * <p>
     * This is primarily used for logging and debugging purposes to identify
     * which proxy is handling a particular request.
     *
     * @return the name of this proxy
     */
    default String name() {
        return this.getClass().getSimpleName();
    }
}
