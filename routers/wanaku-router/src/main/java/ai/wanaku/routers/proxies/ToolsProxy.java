package ai.wanaku.routers.proxies;

import java.util.Map;

import ai.wanaku.core.mcp.common.Tool;

/**
 * Proxies between MCP URIs and Camel components capable of handling them
 */
public interface ToolsProxy extends Proxy, Tool {
    /**
     * Retrieve tools configurations from the service
     * @param target the target service to retrieve configurations from
     * @return A map of configurations and their descriptions
     */
    Map<String, String> getServiceConfigurations(String target);
}
