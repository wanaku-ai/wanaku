package ai.wanaku.core.mcp.common.resolvers;

import java.io.File;
import java.util.Map;

import ai.wanaku.api.exceptions.ToolNotFoundException;
import ai.wanaku.api.types.ToolReference;
import ai.wanaku.core.mcp.common.Tool;

/**
 * A resolver that consumes MCP requests and resolves what type of tool
 * should handle it
 */
public interface ToolsResolver extends Resolver {

    /**
     * The index file containing the targets
     * @return
     */
    default File targetsIndexFile() {
        return new File(indexBaseDirectory(), DEFAULT_TARGET_TOOLS_INDEX_FILE_NAME);
    }

    /**
     * Given a reference, resolves what tool would call it
     * @param toolReference the reference to the tool
     * @return An instance of the requested tool
     * @throws ToolNotFoundException if the tools cannot be found
     */
    Tool resolve(ToolReference toolReference) throws ToolNotFoundException;

    /**
     * Retrieve configurations from the service
     * @param target the target service to retrieve configurations from
     * @return A map of configurations and their descriptions
     */
    Map<String, String> getServiceConfigurations(String target);
}
