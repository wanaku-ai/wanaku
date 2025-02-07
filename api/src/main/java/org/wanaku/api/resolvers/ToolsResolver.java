package org.wanaku.api.resolvers;

import java.util.List;
import java.util.Map;

import org.wanaku.api.exceptions.ToolNotFoundException;
import org.wanaku.api.types.McpTool;
import org.wanaku.api.types.McpToolStatus;

public interface ToolsResolver extends Resolver {

    /**
     * List resources
     * @return
     */
    List<McpTool> list();


    /**
     * Find a tool by name
     * @param name the name of the tool to fine
     * @return A reference to the tool
     * @throws ToolNotFoundException if the tool cannot be found
     */
    McpTool find(String name) throws ToolNotFoundException;

    /**
     * Call a tool
     * @param tool the tool to invoke
     * @param properties the properties to use when calling the tool
     * @return
     */
    McpToolStatus call(McpTool tool, Map<String, Object> properties);
}
