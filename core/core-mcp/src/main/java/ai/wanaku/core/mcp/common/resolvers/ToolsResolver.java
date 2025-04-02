package ai.wanaku.core.mcp.common.resolvers;

import ai.wanaku.api.exceptions.ToolNotFoundException;
import ai.wanaku.api.types.ToolReference;
import ai.wanaku.core.mcp.common.Tool;

/**
 * A resolver that consumes MCP requests and resolves what type of tool
 * should handle it
 */
public interface ToolsResolver extends Resolver {

    /**
     * Given a reference, resolves what tool would call it
     * @param toolReference the reference to the tool
     * @return An instance of the requested tool
     * @throws ToolNotFoundException if the tools cannot be found
     */
    Tool resolve(ToolReference toolReference) throws ToolNotFoundException;
}
