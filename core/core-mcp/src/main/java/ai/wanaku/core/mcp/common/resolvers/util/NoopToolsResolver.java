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
 * A resolver that does not to anything (mostly used for testing)
 */
public class NoopToolsResolver implements ToolsResolver {
    @Override
    public void provision(ToolPayload toolPayload) throws ServiceNotFoundException {}

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
