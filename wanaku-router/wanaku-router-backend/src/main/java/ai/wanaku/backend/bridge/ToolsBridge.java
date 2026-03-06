package ai.wanaku.backend.bridge;

import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.smallrye.mutiny.Uni;
import ai.wanaku.backend.support.ProvisioningReference;
import ai.wanaku.capabilities.sdk.api.types.CallableReference;
import ai.wanaku.capabilities.sdk.api.types.io.ToolPayload;

/**
 * Proxies between MCP URIs and Camel components capable of handling them.
 * <p>
 * This interface defines the contract for tool proxies, which are responsible
 * for provisioning tool configurations and executing tool invocations.
 */
public interface ToolsBridge extends Bridge {
    /**
     * Provision a configuration in the service.
     *
     * @param payload the payload to provision in the service
     * @return A provisioning reference instance
     */
    ProvisioningReference provision(ToolPayload payload);

    /**
     * Executes a tool with the specified arguments synchronously.
     *
     * @param toolArguments the arguments to pass to the tool
     * @param toolReference the reference to the tool being called
     * @return a tool response containing the execution results
     */
    @Deprecated
    ToolResponse execute(ToolManager.ToolArguments toolArguments, CallableReference toolReference);

    /**
     * Executes a tool with the specified arguments asynchronously.
     *
     * @param toolArguments the arguments to pass to the tool
     * @param toolReference the reference to the tool being called
     * @return a Uni emitting the tool response
     */
    Uni<ToolResponse> executeAsync(ToolManager.ToolArguments toolArguments, CallableReference toolReference);
}
