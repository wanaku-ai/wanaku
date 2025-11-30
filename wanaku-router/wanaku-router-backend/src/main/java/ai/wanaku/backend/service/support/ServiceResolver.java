package ai.wanaku.backend.service.support;

import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceType;

/**
 * An interface for resolving services.
 */
public interface ServiceResolver {

    /**
     * Resolves a service based on its name and type.
     *
     * @param serviceName the name of the service to resolve.
     * @param serviceType the type of the service to resolve.
     * @return the resolved service target, or null if no service could be resolved.
     */
    ServiceTarget resolve(String serviceName, ServiceType serviceType);
}
