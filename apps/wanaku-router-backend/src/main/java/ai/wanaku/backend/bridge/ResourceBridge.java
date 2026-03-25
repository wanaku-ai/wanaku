package ai.wanaku.backend.bridge;

import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.smallrye.mutiny.Uni;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;

/**
 * Proxies between MCP URIs and Camel components capable of handling them
 */
public interface ResourceBridge extends Bridge {

    /**
     * Eval an MCP URI handling it as appropriate by the component.
     * @param arguments the resource request arguments
     * @param mcpResource the resource to eval
     * @return Returns a Uni emitting the resource response.
     */
    Uni<ResourceResponse> read(ResourceManager.ResourceArguments arguments, ResourceReference mcpResource);
}
