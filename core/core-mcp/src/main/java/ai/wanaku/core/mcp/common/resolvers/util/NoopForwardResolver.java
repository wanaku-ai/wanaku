package ai.wanaku.core.mcp.common.resolvers.util;

import ai.wanaku.api.exceptions.ServiceNotFoundException;
import ai.wanaku.api.exceptions.ToolNotFoundException;
import ai.wanaku.api.types.CallableReference;
import ai.wanaku.api.types.RemoteToolReference;
import ai.wanaku.api.types.ResourceReference;
import ai.wanaku.api.types.io.ResourcePayload;
import ai.wanaku.core.mcp.common.Tool;
import ai.wanaku.core.mcp.common.resolvers.ForwardResolver;
import io.quarkiverse.mcp.server.ResourceContents;
import io.quarkiverse.mcp.server.ResourceManager;
import java.util.List;

/**
 * No-operation implementation of {@link ForwardResolver} for testing and default behavior.
 * <p>
 * This resolver provides empty implementations of all {@link ForwardResolver} methods,
 * making it useful for testing scenarios where actual forward resolution is not needed,
 * or as a default/fallback resolver when no real resolver is configured.
 * </p>
 * <p>
 * The noop resolver:
 * <ul>
 *   <li>Returns {@code null} for tool resolution</li>
 *   <li>Returns empty lists for all listing operations</li>
 *   <li>Does not perform any provisioning operations</li>
 *   <li>Returns an empty list for resource read operations</li>
 * </ul>
 * </p>
 *
 * @see ForwardResolver
 */
public class NoopForwardResolver implements ForwardResolver {
    /**
     * Returns {@code null} indicating no tool is resolved.
     *
     * @param toolReference the tool reference (ignored)
     * @return {@code null}
     * @throws ToolNotFoundException never thrown by this implementation
     */
    @Override
    public Tool resolve(CallableReference toolReference) throws ToolNotFoundException {
        return null;
    }

    /**
     * Returns an empty list of resources.
     *
     * @return an empty list
     */
    @Override
    public List<ResourceReference> listResources() {
        return List.of();
    }

    /**
     * Returns an empty list of remote tools.
     *
     * @return an empty list
     */
    @Override
    public List<RemoteToolReference> listTools() {
        return List.of();
    }

    /**
     * No-operation provisioning that does nothing.
     *
     * @param resourcePayload the resource payload (ignored)
     * @throws ServiceNotFoundException never thrown by this implementation
     */
    @Override
    public void provision(ResourcePayload resourcePayload) throws ServiceNotFoundException {}

    /**
     * Returns an empty list of resource contents.
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
