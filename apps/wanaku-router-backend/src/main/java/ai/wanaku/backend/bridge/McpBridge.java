package ai.wanaku.backend.bridge;

import java.util.List;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.smallrye.mutiny.Uni;
import ai.wanaku.capabilities.sdk.api.exceptions.ServiceUnavailableException;
import ai.wanaku.capabilities.sdk.api.types.CallableReference;
import ai.wanaku.capabilities.sdk.api.types.RemoteToolReference;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;
import ai.wanaku.core.mcp.client.McpElicitationHandler;
import ai.wanaku.core.mcp.client.McpSamplingHandler;

/**
 * Bridge interface for interacting with remote MCP servers via the langchain4j MCP client.
 * <p>
 * Provides operations for listing and invoking tools, and for listing and reading resources
 * on remote MCP servers.
 */
public interface McpBridge {

    List<RemoteToolReference> listTools(ForwardClient forwardClient) throws ServiceUnavailableException;

    Uni<ToolResponse> executeTool(
            ForwardClient forwardClient, ToolManager.ToolArguments toolArguments, CallableReference toolReference);

    List<ResourceReference> listResources(ForwardClient forwardClient) throws ServiceUnavailableException;

    Uni<ResourceResponse> read(
            ForwardClient forwardClient, ResourceManager.ResourceArguments arguments, ResourceReference mcpResource);

    default McpSamplingHandler createSamplingHandler(String address) {
        return null;
    }

    /**
     * Creates an {@link McpElicitationHandler} bound to the given remote server address.
     * <p>
     * Implementations override this to delegate {@code elicitation/create} requests from
     * remote MCP servers to the local client-side {@code Elicitation} infrastructure.
     *
     * @param address the remote MCP server address
     * @return the handler, or {@code null} if elicitation is not supported
     */
    default McpElicitationHandler createElicitationHandler(String address) {
        return null;
    }

    /**
     * Removes all state associated with a forward connection address.
     *
     * <p>Implementations should clear any cached sampling/elicitation entries
     * or other per-connection state to avoid unbounded growth when connections
     * are closed or become stale.</p>
     *
     * @param address the remote MCP server address to clean up
     */
    default void removeConnection(String address) {
    }
}
