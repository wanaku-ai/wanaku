package ai.wanaku.core.mcp.providers;

import ai.wanaku.api.types.ForwardReference;
import ai.wanaku.core.mcp.common.resolvers.ForwardResolver;
import java.util.Set;

/**
 * A registry for managing forwarded services and their resolvers.
 * <p>
 * This interface provides methods to create and retrieve forward resolvers,
 * link/unlink services with specific resolvers, and access a list of all registered services.
 */
public interface ForwardRegistry {

    /**
     * Creates a new forward resolver instance for the specified service reference.
     *
     * This method creates a new resolver instance associated with the given service reference,
     * which can then be used to resolve forwarded requests. The newly created resolver is not yet
     * linked to any service; use {@link #link(ForwardReference, ForwardResolver)} to establish this connection.
     *
     * @param service the service reference for which a new resolver is being created
     * @return a newly created forward resolver instance associated with the given service reference
     */
    ForwardResolver newResolverForService(ForwardReference service);

    /**
     * Retrieves an existing forward resolver for the specified service reference.
     *
     * If no existing resolver is found for the given service, this method returns null.
     *
     * @param service the service reference for which a resolver instance is being retrieved
     * @return an existing forward resolver instance associated with the given service reference, or null if not present
     */
    ForwardResolver getResolver(ForwardReference service);

    /**
     * Links a service reference with its corresponding forward resolver.
     *
     * Establishes an association between the specified service reference and the provided resolver,
     * allowing future forwarded requests for the service to be resolved using this resolver.
     *
     * @param service  the service reference to link with a resolver
     * @param resolver the resolver instance to associate with the given service reference
     */
    void link(ForwardReference service, ForwardResolver resolver);

    /**
     * Unlinks a service reference from its associated forward resolver.
     *
     * Releases the association between the specified service reference and its linked resolver,
     * which may lead to resolution failures for future forwarded requests targeting the unlinked service.
     *
     * @param service  the service reference to unlink from its resolver
     */
    void unlink(ForwardReference service);

    /**
     * Retrieves a collection of all registered services in this registry.
     *
     * Provides programmatic access to the list of services for which forward resolvers have been created or retrieved.
     * This allows for iterative exploration of the registry's contents, useful in certain scenarios where explicit
     * lookups or enumerations are not feasible or efficient.
     *
     * @return a set of all registered ForwardReference instances managed by this registry
     */
    Set<ForwardReference> services();
}
