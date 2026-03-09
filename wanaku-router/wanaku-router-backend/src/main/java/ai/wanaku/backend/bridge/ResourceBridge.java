package ai.wanaku.backend.bridge;

import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.smallrye.mutiny.Uni;
import ai.wanaku.backend.support.ProvisioningReference;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;
import ai.wanaku.capabilities.sdk.api.types.io.ResourcePayload;

/**
 * Proxies between MCP URIs and Camel components capable of handling them
 */
public interface ResourceBridge extends Bridge {

    /**
     * Provision a configuration in the service
     *
     * @param payload the payload to provision in the service
     * @return A provisioning reference instance
     */
    ProvisioningReference provision(ResourcePayload payload);

    /**
     * Eval an MCP URI handling it as appropriate by the component asynchronously.
     * @param arguments the resource request arguments
     * @param mcpResource the resource to eval
     * @return Returns a Uni emitting the resource response.
     */
    Uni<ResourceResponse> readAsync(ResourceManager.ResourceArguments arguments, ResourceReference mcpResource);
}
