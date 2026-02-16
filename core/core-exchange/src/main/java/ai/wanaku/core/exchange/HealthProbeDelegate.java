package ai.wanaku.core.exchange;

/**
 * A delegate that handles health probe requests from the router.
 * <p>
 * Implementations should return the current runtime status of the capability.
 */
public interface HealthProbeDelegate {

    /**
     * Returns the current runtime status of the capability.
     *
     * @param id the identifier of the integration being checked
     * @return the runtime status
     */
    RuntimeStatus getStatus(String id);
}
