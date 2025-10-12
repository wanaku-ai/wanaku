package ai.wanaku.backend.service.support;

import ai.wanaku.api.types.providers.ServiceTarget;
import ai.wanaku.api.types.providers.ServiceType;
import ai.wanaku.core.mcp.providers.ServiceRegistry;
import java.util.List;

/**
 * A service resolver that returns the first available service from the registry.
 */
public class FirstAvailable implements ServiceResolver {

    private final ServiceRegistry serviceRegistry;

    /**
     * Creates a new FirstAvailable service resolver.
     *
     * @param serviceRegistry the service registry to use for resolving services.
     */
    public FirstAvailable(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
    }

    /**
     * Resolves a service by returning the first available service from the registry that matches the given name and type.
     *
     * @param serviceName the name of the service to resolve.
     * @param serviceType the type of the service to resolve.
     * @return the first available service target, or null if no service could be resolved.
     */
    @Override
    public ServiceTarget resolve(String serviceName, ServiceType serviceType) {
        List<ServiceTarget> services = serviceRegistry.getServiceByName(serviceName, serviceType);
        if (services != null && !services.isEmpty()) {
            return services.getFirst();
        }
        return null;
    }
}
