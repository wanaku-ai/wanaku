package ai.wanaku.routers.proxies;

import ai.wanaku.api.types.ToolReference;
import ai.wanaku.core.exchange.PropertySchema;
import java.util.Map;

import ai.wanaku.core.mcp.common.Tool;

/**
 * Proxies between MCP URIs and Camel components capable of handling them
 */
public interface ToolsProxy extends Proxy, Tool {
    /**
     * Retrieve tools properties (aka arguments) from the service
     * @param toolReference given a tool reference, resolve the target service and retrieve the properties accepted by it
     * @return A map of properties and their descriptions
     */
    Map<String, PropertySchema> getProperties(ToolReference toolReference);
}
