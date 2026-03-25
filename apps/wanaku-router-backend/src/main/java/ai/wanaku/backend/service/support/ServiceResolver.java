package ai.wanaku.backend.service.support;

import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;

/**
 * An interface for resolving services.
 */
public interface ServiceResolver {

    /**
     * Resolves a service based on its name and type.
     *
     * @param serviceName the name of the service to resolve.
     * @param serviceType the type of the service to resolve (e.g., "resource-provider", "tool-invoker", "code-execution-engine").
     * @return the resolved service target, or null if no service could be resolved.
     */
    ServiceTarget resolve(String serviceName, String serviceType);

    /**
     * Resolves a code execution service based on service type, sub-type (engine type), and name (language).
     *
     * @param serviceType the type of the service (e.g., "code-execution-engine").
     * @param serviceSubType the sub-type of the service, typically the engine type (e.g., "camel").
     * @param languageName the name of the service, typically the programming language (e.g., "java", "yaml").
     * @return the resolved service target, or null if no service could be resolved.
     */
    ServiceTarget resolveCodeExecution(String serviceType, String serviceSubType, String languageName);
}
