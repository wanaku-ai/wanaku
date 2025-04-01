package ai.wanaku.core.mcp.common;

import ai.wanaku.api.types.CallableReference;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;

/**
 * Represents a tool that can be called and executed.
 */
public interface Tool {
    /**
     * Call a tool
     * @param toolReference the tool reference
     * @param toolArguments the arguments to the tool
     * @return
     */
    ToolResponse call(ToolManager.ToolArguments toolArguments, CallableReference toolReference);
}
