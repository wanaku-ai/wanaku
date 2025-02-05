package org.wanaku.routers.camel;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.wanaku.api.types.McpResource;
import org.wanaku.api.types.McpResourceData;

/**
 * Proxies between MCP URIs and Camel components capable of handling them
 */
public interface ResourceProxy {
    String INDEX_FILE = "resources.json";

    /**
     * The name of the proxy
     * @return
     */
    String name();

    default List<McpResource> list(String resourcesPath) {
        List<McpResource> mcpResources = new ArrayList<>();

        return list(new File(resourcesPath, INDEX_FILE));
    }

    /**
     * List resources that can be handled by this proxy
     * @param resourcesFile the resources' index file
     * @return
     */
    List<McpResource> list(File resourcesFile);

    /**
     * Eval an MCP URI handling it as appropriate by the component
     * @param uri
     * @return
     */
    List<McpResourceData> eval(String uri);

}
