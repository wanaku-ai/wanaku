package ai.wanaku.core.mcp.common.resolvers.util;

import ai.wanaku.capabilities.sdk.api.exceptions.ServiceNotFoundException;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;
import ai.wanaku.capabilities.sdk.api.types.io.ResourcePayload;
import ai.wanaku.core.mcp.common.resolvers.ResourceResolver;
import io.quarkiverse.mcp.server.ResourceContents;
import io.quarkiverse.mcp.server.ResourceManager;
import java.util.List;

/**
 * No-operation implementation of {@link ResourceResolver} for testing and default behavior.
 * <p>
 * This resolver provides empty implementations of all {@link ResourceResolver} methods,
 * making it useful for testing scenarios where actual resource resolution is not needed,
 * or as a default/fallback resolver when no real resolver is configured.
 * <p>
 * The noop resolver:
 * <ul>
 *   <li>Does not perform any provisioning operations</li>
 *   <li>Returns an empty list for all resource read operations</li>
 *   <li>Never throws exceptions during normal operation</li>
 * </ul>
 *
 * @see ResourceResolver
 */
public class NoopResourceResolver implements ResourceResolver {

    /**
     * Default constructor for NoopResourceResolver.
     */
    public NoopResourceResolver() {}

    /**
     * No-operation provisioning that does nothing.
     * <p>
     * This method accepts the resource payload but performs no actual provisioning.
     *
     * @param resourcePayload the resource payload (ignored)
     * @throws ServiceNotFoundException never thrown by this implementation
     */
    @Override
    public void provision(ResourcePayload resourcePayload) throws ServiceNotFoundException {}

    /**
     * Returns an empty list of resource contents.
     * <p>
     * This method always returns an empty list, indicating that no resource
     * content is available.
     *
     * @param arguments the resource request arguments (ignored)
     * @param mcpResource the resource reference (ignored)
     * @return an empty list
     */
    @Override
    public List<ResourceContents> read(ResourceManager.ResourceArguments arguments, ResourceReference mcpResource) {
        return List.of();
    }
}
