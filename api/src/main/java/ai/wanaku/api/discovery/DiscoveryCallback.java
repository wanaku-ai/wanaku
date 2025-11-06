package ai.wanaku.api.discovery;

import ai.wanaku.api.types.providers.ServiceTarget;

/**
 * Callback interface for receiving notifications about service registration lifecycle events.
 *
 * <p>Implementations of this interface can be registered with a {@link RegistrationManager}
 * to receive notifications when registration-related operations occur, such as successful
 * registration, deregistration attempts, or periodic health check pings.</p>
 *
 * <p>Callbacks are invoked synchronously after the corresponding operation completes.
 * If a callback throws an exception, it will be logged but will not prevent other
 * registered callbacks from executing.</p>
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * RegistrationManager manager = ...;
 * manager.addCallBack(new RegistrationCallback() {
 *     @Override
 *     public void onRegistration(RegistrationManager manager) {
 *         // Handle successful registration
 *     }
 *
 *     @Override
 *     public void onPing(RegistrationManager manager, int status) {
 *         // Handle ping result
 *     }
 *
 *     @Override
 *     public void onDeregistration(RegistrationManager manager, int status) {
 *         // Handle deregistration result
 *     }
 * });
 * }</pre>
 *
 * @see RegistrationManager#addCallBack(DiscoveryCallback)
 */
public interface DiscoveryCallback {

    /**
     * Invoked after a ping operation is sent to the discovery service.
     * This callback is triggered when the service sends a heartbeat to indicate
     * it is still active and operational.
     *
     * @param manager the {@link RegistrationManager} that performed the ping operation
     * @param target the {@link ServiceTarget} that was pinged
     * @param status the HTTP status code returned by the ping operation
     *               (200 indicates success, other values indicate various failure conditions)
     */
    void onPing(RegistrationManager manager, ServiceTarget target, int status);

    /**
     * Invoked after the service has been successfully registered with the discovery service.
     * This callback is only called when registration completes successfully.
     *
     * @param manager the {@link RegistrationManager} that performed the registration
     * @param target the {@link ServiceTarget} that was registered
     */
    void onRegistration(RegistrationManager manager, ServiceTarget target);

    /**
     * Invoked after a deregistration attempt is made with the discovery service.
     * This callback is triggered when the service is being shut down or explicitly
     * removed from the registry.
     *
     * @param manager the {@link RegistrationManager} that performed the deregistration operation
     * @param target the {@link ServiceTarget} that was deregistered
     * @param status the HTTP status code returned by the deregistration operation
     *               (200 indicates success, other values indicate various failure conditions)
     */
    void onDeregistration(RegistrationManager manager, ServiceTarget target, int status);
}
