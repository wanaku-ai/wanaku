package ai.wanaku.core.mcp.common.resolvers.util;

import ai.wanaku.capabilities.sdk.api.types.ForwardReference;
import ai.wanaku.capabilities.sdk.api.types.NameNamespacePair;
import ai.wanaku.core.mcp.common.resolvers.ForwardResolver;
import ai.wanaku.core.mcp.providers.ForwardRegistry;
import java.util.Set;

/**
 * No-operation implementation of {@link ForwardRegistry} for testing and default behavior.
 * <p>
 * This registry provides empty implementations of all {@link ForwardRegistry} methods,
 * making it useful for testing scenarios where actual forward registry management is not needed,
 * or as a default/fallback registry when no real registry is configured.
 * <p>
 * The noop registry:
 * <ul>
 *   <li>Always returns {@link NoopForwardResolver} instances for resolver requests</li>
 *   <li>Ignores all link and unlink operations</li>
 *   <li>Returns an empty set for all service queries</li>
 *   <li>Never throws exceptions during normal operation</li>
 * </ul>
 *
 * @see ForwardRegistry
 * @see NoopForwardResolver
 */
public class NoopForwardRegistry implements ForwardRegistry {

    /**
     * Default constructor for NoopForwardRegistry.
     */
    public NoopForwardRegistry() {}

    @Override
    public ForwardResolver newResolverForService(NameNamespacePair service, ForwardReference forwardReference) {
        return new NoopForwardResolver();
    }

    @Override
    public ForwardResolver getResolver(NameNamespacePair service) {
        return new NoopForwardResolver();
    }

    @Override
    public void link(NameNamespacePair service, ForwardResolver resolver) {}

    @Override
    public void unlink(NameNamespacePair service) {}

    @Override
    public Set<NameNamespacePair> services() {
        return Set.of();
    }
}
