package org.wanaku.routers.camel.proxies;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.wanaku.api.types.McpTool;
import org.wanaku.api.types.McpToolStatus;


/**
 * Proxies between MCP URIs and Camel components capable of handling them
 */
public interface ToolsProxy extends Proxy {
    /**
     * List tools that can be called by this proxy
     * @param index the index file
     * @return
     */
    List<McpTool> list(File index);


    /**
     * Call a tool
     * @param tool the tool to call
     * @param properties the properties to use when calling the tool
     * @return
     */
    McpToolStatus call(McpTool tool, Map<String, Object> properties);

}
