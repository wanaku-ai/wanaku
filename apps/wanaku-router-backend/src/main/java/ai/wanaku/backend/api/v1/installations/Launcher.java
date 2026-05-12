package ai.wanaku.backend.api.v1.installations;

import java.util.Map;

/**
 * Manages the lifecycle of capability processes.
 *
 * <p>Implementations handle launching, stopping, and monitoring of external
 * processes that serve capability services (tools, resources, etc.).
 *
 * <p>The launcher is enabled or disabled via configuration; when disabled,
 * all operations are no-ops (see {@link NoopLauncher}).
 */
public interface Launcher {

    /**
     * Launches a capability process for the given catalog and system.
     *
     * @param catalogName the name of the service catalog containing the system
     * @param systemName  the system identifier within the catalog
     * @return the process status after launch, including the allocated port
     * @throws ai.wanaku.capabilities.sdk.api.exceptions.WanakuException if the process is already running or launch fails
     */
    ProcessStatus launch(String catalogName, String systemName);

    /**
     * Stops a running capability process.
     *
     * @param catalogName the name of the service catalog containing the system
     * @param systemName  the system identifier within the catalog
     * @throws ai.wanaku.capabilities.sdk.api.exceptions.WanakuException if the process is not found
     */
    void stop(String catalogName, String systemName);

    /**
     * Returns the current status of a capability process.
     *
     * @param catalogName the name of the service catalog containing the system
     * @param systemName  the system identifier within the catalog
     * @return the current process status; never null
     */
    ProcessStatus getStatus(String catalogName, String systemName);

    /**
     * Returns the status of all managed processes.
     *
     * @return a map of process key ({@code catalogName:systemName}) to status; never null
     */
    Map<String, ProcessStatus> getAllStatuses();

    /**
     * Shuts down all managed processes. Called during application shutdown.
     */
    default void shutdown() {}
}
