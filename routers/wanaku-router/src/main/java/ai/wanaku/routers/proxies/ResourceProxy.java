package ai.wanaku.routers.proxies;

import java.util.List;

import io.quarkiverse.mcp.server.ResourceContents;
import ai.wanaku.api.types.ResourceReference;
import io.quarkiverse.mcp.server.ResourceManager;

/**
 * Proxies between MCP URIs and Camel components capable of handling them
 */
public interface ResourceProxy extends Proxy {


    /**
     * Eval an MCP URI handling it as appropriate by the component (i.e.: read a file, GET a static web page, etc.)
     * @param arguments the resource request arguments
     * @param mcpResource the resource to eval
     * @return Returns the data read by the proxy.
     */
    List<ResourceContents> eval(ResourceManager.ResourceArguments arguments, ResourceReference mcpResource);
}
