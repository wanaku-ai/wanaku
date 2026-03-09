package ai.wanaku.core.forward.discovery.client;

/**
 * Callback interface for receiving notifications about forward registration lifecycle events.
 *
 * <p>Implementations of this interface can be registered with a {@link ForwardRegistrationManager}
 * to receive notifications when forward registration operations occur, such as successful
 * registration or deregistration.</p>
 *
 * <p>Callbacks are invoked synchronously after the corresponding operation completes.
 * If a callback throws an exception, it will be logged but will not prevent other
 * registered callbacks from executing.</p>
 */
public interface ForwardDiscoveryCallback {

    /**
     * Invoked after the forward has been successfully registered with the router.
     *
     * @param manager the {@link ForwardRegistrationManager} that performed the registration
     */
    void onRegistration(ForwardRegistrationManager manager);

    /**
     * Invoked after a deregistration attempt is made.
     *
     * @param manager the {@link ForwardRegistrationManager} that performed the deregistration
     */
    void onDeregistration(ForwardRegistrationManager manager);
}
