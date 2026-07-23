package ai.wanaku.backend.bridge;

import java.util.List;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema;
import io.smallrye.mutiny.Uni;
import ai.wanaku.capabilities.sdk.api.exceptions.ServiceUnavailableException;
import ai.wanaku.capabilities.sdk.api.types.CallableReference;
import ai.wanaku.capabilities.sdk.api.types.RemoteToolReference;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;

/**
 * Bridge interface for interacting with remote MCP servers via the langchain4j MCP client.
 * Provides operations for listing and invoking tools, and for listing and reading resources
 * on remote MCP servers.
 */
public interface McpBridge {

    List<RemoteToolReference> listTools(ForwardClient forwardClient) throws ServiceUnavailableException;

    Uni<McpSchema.CallToolResult> executeTool(
            String address,
            McpSchema.CallToolRequest callToolRequest,
            String sessionId,
            McpTransportContext transportContext,
            CallableReference toolReference);

    List<ResourceReference> listResources(ForwardClient forwardClient) throws ServiceUnavailableException;

    Uni<McpSchema.ReadResourceResult> read(
            ForwardClient forwardClient,
            McpSchema.ReadResourceRequest readRequest,
            String sessionId,
            McpTransportContext transportContext,
            ResourceReference mcpResource);
}
