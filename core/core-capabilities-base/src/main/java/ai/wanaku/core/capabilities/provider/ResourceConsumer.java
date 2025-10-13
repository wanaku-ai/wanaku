package ai.wanaku.core.capabilities.provider;

import ai.wanaku.core.exchange.ResourceRequest;

/**
 * Interface for consuming resources from capability provider services.
 * <p>
 * A resource consumer handles the retrieval and consumption of resource content
 * from services that provide them. This interface abstracts the mechanism of
 * accessing resources, allowing different implementations for various transport
 * protocols and service types.
 * </p>
 * <p>
 * Resource consumers are responsible for:
 * <ul>
 *   <li>Connecting to resource provider services</li>
 *   <li>Translating resource requests into service-specific calls</li>
 *   <li>Retrieving resource content from the provider</li>
 *   <li>Converting provider responses into a standard format</li>
 * </ul>
 * </p>
 * <p>
 * Different implementations may support various protocols such as HTTP, file system,
 * database connections, or other custom resource providers.
 * </p>
 */
public interface ResourceConsumer {
    /**
     * Consumes a resource from the specified service provider.
     * <p>
     * This method connects to the resource provider service at the given URI
     * and retrieves the requested resource content. The response format depends
     * on the resource type and provider implementation, but must be convertible
     * to a String representation.
     * </p>
     *
     * @param uri the URI pointing to the service provider that hosts the resource
     * @param request the resource request as received by the MCP router, containing
     *                resource identification and any parameters needed for retrieval
     * @return the resource content obtained from the provider service, in a format
     *         that can be converted to a String
     */
    Object consume(String uri, ResourceRequest request);
}
