package ai.wanaku.core.mcp.providers;

import java.util.List;
import ai.wanaku.capabilities.sdk.api.types.discovery.ActivityRecord;
import ai.wanaku.capabilities.sdk.api.types.discovery.ServiceState;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;

/**
 * Defines a registry of downstream services
 */
public interface ServiceRegistry {

    /**
     * Register a service target in the registry
     * @param serviceTarget the service target
     * @return the updated service target with its newly created ID if not previously provided
     */
    ServiceTarget register(ServiceTarget serviceTarget);

    /**
     * De-register a service from the registry
     * @param serviceTarget the service target
     */
    void deregister(ServiceTarget serviceTarget);

    /**
     * Gets a registered service by name
     *
     * @param serviceName the name of the service
     * @param serviceType the service type (e.g., "resource-provider", "tool-invoker", "code-execution-engine")
     * @return the service instance or null if not found
     */
    List<ServiceTarget> getServiceByName(String serviceName, String serviceType);

    /**
     * Gets a code execution service by matching service type, sub-type, and name.
     *
     * @param serviceType the type of the service (e.g., "code-execution-engine")
     * @param serviceSubType the sub-type of the service, typically the engine type (e.g., "jvm", "interpreted")
     * @param serviceName the name of the service, typically the programming language (e.g., "java", "python")
     * @return a list of matching service targets
     */
    List<ServiceTarget> getCodeExecutionService(String serviceType, String serviceSubType, String serviceName);

    /**
     * Gets the state of the given service
     *
     * @param id the service ID
     * @return the last count states of the given service
     */
    ActivityRecord getStates(String id);

    /**
     * Get all registered services
     * @return A list of all available services
     */
    List<ServiceTarget> getEntries();

    /**
     * Get registered services of the given type
     *
     * @param serviceType the type of service to get (e.g., "resource-provider", "tool-invoker", "code-execution-engine")
     * @return a list of all registered services
     */
    List<ServiceTarget> getEntries(String serviceType);

    /**
     * Update a registered service target in the registry
     * @param serviceTarget the service target
     */
    void update(ServiceTarget serviceTarget);

    /**
     * Update a registered service target in the registry
     * @param id the service ID
     * @param state the state to record
     */
    void updateLastState(String id, ServiceState state);

    /**
     * Register a ping from a service
     * @param id the service ID
     */
    void ping(String id);

    /**
     * Find capabilities that are considered stale based on the given criteria.
     *
     * @param maxAgeSeconds the maximum age in seconds since last seen; capabilities older than this are considered stale
     * @param inactiveOnly if true, only return capabilities that are also marked as inactive
     * @return a list of stale capabilities with their activity records
     */
    List<StaleCapability> findStaleCapabilities(long maxAgeSeconds, boolean inactiveOnly);

    /**
     * Remove a capability by its ID.
     * This removes both the service target and its associated activity record.
     *
     * @param id the ID of the capability to remove
     * @return true if the capability was found and removed, false otherwise
     */
    boolean removeById(String id);
}
