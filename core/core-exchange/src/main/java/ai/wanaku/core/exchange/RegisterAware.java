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
     */
    void deregister();
}
