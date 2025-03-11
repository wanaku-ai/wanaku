package ai.wanaku.core.mcp.common.resolvers;

import java.io.File;

/**
 * A resolver that consumes MCP requests and resolves what type of tool or resource acquirer
 * should handle it
 */
public interface Resolver {
    String DEFAULT_RESOURCES_INDEX_FILE_NAME = "resources.json";
    String DEFAULT_TOOLS_INDEX_FILE_NAME = "tools.json";
    String DEFAULT_TARGET_RESOURCES_INDEX_FILE_NAME = "resources-targets.json";
    String DEFAULT_TARGET_TOOLS_INDEX_FILE_NAME = "tools-targets.json";

    /**
     * The location of the index file
     * @return
     */
    String index();
}
