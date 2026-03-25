package ai.wanaku.backend.bridge;

import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.smallrye.mutiny.Uni;
import ai.wanaku.capabilities.sdk.api.types.CallableReference;

/**
 * Proxies between MCP URIs and Camel components capable of handling them.
 * <p>
 * This interface defines the contract for tool proxies, which are responsible
 * for executing tool invocations.
 */
public interface ToolsBridge extends Bridge {

    /**
     * Executes a tool with the specified arguments.
     *
     * @param toolArguments the arguments to pass to the tool
     * @param toolReference the reference to the tool being called
     * @return a Uni emitting the tool response
     */
    Uni<ToolResponse> execute(ToolManager.ToolArguments toolArguments, CallableReference toolReference);
}
