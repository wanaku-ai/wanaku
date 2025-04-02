package ai.wanaku.core.mcp.common.resolvers.util;

import ai.wanaku.api.exceptions.ToolNotFoundException;
import ai.wanaku.api.types.ToolReference;
import ai.wanaku.core.mcp.common.Tool;
import ai.wanaku.core.mcp.common.resolvers.ToolsResolver;

/**
 * A resolver that does not to anything (mostly used for testing)
 */
public class NoopToolsResolver implements ToolsResolver {
    @Override
    public Tool resolve(ToolReference toolReference) throws ToolNotFoundException {
        return null;
    }
}
