package ai.wanaku.api.types.discovery;

/**
 * Standard status messages used in the service discovery and health monitoring system.
 * <p>
 * This utility class defines constant messages that represent common service states
 * within the Wanaku discovery mechanism. These messages are used to communicate
 * service health and availability status.
 */
public final class StandardMessages {
    /**
     * Message indicating that a service is operating normally and is healthy.
     */
    public static final String HEALTHY = "healthy";

    /**
     * Message indicating that a service has not been observed within the expected timeframe
     * and may be unavailable.
     */
    public static final String MISSING_IN_ACTION = "missing in action";

    /**
     * Message indicating that a service has been automatically deregistered due to inactivity.
     */
    public static final String AUTO_DEREGISTRATION = "inactive due to service auto-deregistration";

    private StandardMessages() {}
}
