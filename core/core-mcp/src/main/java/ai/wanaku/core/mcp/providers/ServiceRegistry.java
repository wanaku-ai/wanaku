package ai.wanaku.core.mcp.providers;

import ai.wanaku.api.types.discovery.ActivityRecord;
import ai.wanaku.api.types.discovery.ServiceState;

import ai.wanaku.api.types.providers.ServiceTarget;
import ai.wanaku.api.types.providers.ServiceType;
import java.util.List;

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
     * @param service     the name of the service
     * @param serviceType the service type
     * @return the service instance or null if not found
     */
    ServiceTarget getServiceByName(String service, ServiceType serviceType);


    /**
     * Gets the state of the given service
     *
     * @param id the service ID
     * @return the last count states of the given service
     */
    ActivityRecord getStates(String id);

    /**
     * Get a map of all registered services and their configurations
     *
     * @param serviceType the type of service to get
     * @return a map of all registered services and their configurations
     */
    List<ServiceTarget> getEntries(ServiceType serviceType);


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
}
