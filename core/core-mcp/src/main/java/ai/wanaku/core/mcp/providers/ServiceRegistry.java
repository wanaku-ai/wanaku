package ai.wanaku.core.mcp.providers;

import ai.wanaku.api.types.management.Service;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Collection;
import java.util.Map;

/**
 * Defines a registry of downstream services
 */
public interface ServiceRegistry {

    /**
     * Register a service target in the registry
     * @param serviceTarget the service target
     * @param configurations any configurations applicable for the service
     */
    void register(ServiceTarget serviceTarget, Map<String, String> configurations);

    /**
     * De-register a service from the registry
     * @param service the service name
     */
    void deregister(String service);

    /**
     * Gets a registered service by name
     * @param service the name of the service
     * @return the service instance or null if not found
     */
    Service getService(String service);

    /**
     * Get a map of all registered services and their configurations
     * @param serviceType the type of service to get
     * @return a map of all registered services and their configurations
     */
    Map<String, Service> getEntries(ServiceType serviceType);
}
