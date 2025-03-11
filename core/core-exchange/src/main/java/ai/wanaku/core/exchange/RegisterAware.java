package ai.wanaku.core.exchange;

/**
 * Defines services that can be registered with the router
 */
public interface RegisterAware {
    /**
     * Register a service with the router
     */
    void register();

    /**
     * Deregister a service with the router
     * @param host the router host
     * @param service the service to deregister
     */
    void deregister(String host, String service, int port);
}
