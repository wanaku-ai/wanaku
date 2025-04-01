package ai.wanaku.core.mcp.common.resolvers;

/**
 * A resolver that consumes MCP requests and resolves what type of tool or resource acquirer
 * should handle it
 */
public interface Resolver {

    // Whatever is using this, needs to get it from the persistence API
    @Deprecated
    String DEFAULT_RESOURCES_INDEX_FILE_NAME = "resources.json";

    // Whatever is using this, needs to get it from the persistence API
    @Deprecated
    String DEFAULT_TOOLS_INDEX_FILE_NAME = "tools.json";
}
