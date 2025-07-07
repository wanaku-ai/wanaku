package ai.wanaku.api.discovery;

/**
 * The `RegistrationManager` interface defines the contract for a class responsible for managing the lifecycle
 * registration of a capability service within the Wanaku ecosystem.
 *
 * <p>Implementations of this interface handle the processes of registering, deregistering,
 * and monitoring the status of a service's registration. It acts as the primary interface
 * for a capability service to interact with the Wanaku registration mechanism.</p>
 */
public interface RegistrationManager {

    /**
     * Registers the capability service with Wanaku
     */
    void register();

    /**
     * Deregisters the capability service from the Wanaku registration system.
     * This method notifies the registry that the service is no longer available
     * or is shutting down, allowing for proper cleanup and resource release.
     */
    void deregister();

    /**
     * Sends a "ping" or heartbeat signal to the Wanaku registration system.
     * This method is used to periodically inform the registry that the service is still active
     * and operational, preventing its registration from expiring due to inactivity.
     */
    void ping();

    /**
     * Notifies the Wanaku registration system that the last attempted operation (tool call
     * or resource acquisition) from this service failed.
     *
     * @param reason A descriptive string explaining the reason for the failure.
     * This information can be used for logging, debugging, or alerting purposes
     * by the registration system.
     */
    void lastAsFail(String reason);

    /**
     * Notifies the Wanaku registration system that the last attempted operation (tool call
     * or resource acquisition) from this service was successful.
     * This method can be used to update the service's status within the registry,
     * indicating its continued health and availability.
     */
    void lastAsSuccessful();
}
