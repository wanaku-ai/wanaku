package ai.wanaku.core.mcp.common.resolvers.util;

import ai.wanaku.api.exceptions.ServiceNotFoundException;
import ai.wanaku.api.exceptions.ToolNotFoundException;
import ai.wanaku.api.types.CallableReference;
import ai.wanaku.api.types.ToolReference;
import ai.wanaku.api.types.io.ToolPayload;
import ai.wanaku.core.mcp.common.Tool;
import ai.wanaku.core.mcp.common.resolvers.ToolsResolver;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;

/**
 * No-operation implementation of {@link ToolsResolver} for testing and default behavior.
 * <p>
 * This resolver provides empty implementations of all {@link ToolsResolver} methods,
 * making it useful for testing scenarios where actual tool resolution is not needed,
 * or as a default/fallback resolver when no real resolver is configured.
 * </p>
 * <p>
 * The noop resolver:
 * <ul>
 *   <li>Does not perform any provisioning operations</li>
 *   <li>Returns a stub tool implementation that returns {@code null} for all calls</li>
 *   <li>Never throws exceptions during normal operation</li>
 * </ul>
 * </p>
 *
 * @see ToolsResolver
 */
public class NoopToolsResolver implements ToolsResolver {
    /**
     * No-operation provisioning that does nothing.
     * <p>
     * This method accepts the tool payload but performs no actual provisioning.
     * </p>
     *
     * @param toolPayload the tool payload (ignored)
     * @throws ServiceNotFoundException never thrown by this implementation
     */
    @Override
    public void provision(ToolPayload toolPayload) throws ServiceNotFoundException {}

    /**
     * Returns a stub tool implementation that does nothing.
     * <p>
     * The returned tool's {@code call} method will always return {@code null}.
     * </p>
     *
     * @param toolReference the tool reference (ignored)
     * @return a stub tool implementation
     * @throws ToolNotFoundException never thrown by this implementation
     */
    @Override
    public Tool resolve(ToolReference toolReference) throws ToolNotFoundException {
        return new Tool() {
            @Override
            public ToolResponse call(ToolManager.ToolArguments toolArguments, CallableReference toolReference) {
                return null;
            }
        };
    }
}
