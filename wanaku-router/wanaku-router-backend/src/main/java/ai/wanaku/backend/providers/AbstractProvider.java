package ai.wanaku.backend.providers;

import ai.wanaku.core.mcp.common.resolvers.Resolver;

/**
 * Base provider class for tools and resources
 * @param <Y> The type of the resolver
 */
abstract class AbstractProvider<Y extends Resolver> {
    /**
     * Gets the resolver associated with this provider
     * @return the resolver instance
     */
    abstract Y getResolver();
}
