package ai.wanaku.backend.bridge;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema;
import io.smallrye.mutiny.Uni;
import ai.wanaku.capabilities.sdk.api.types.CallableReference;

/**
 * Proxies between MCP URIs and Camel components capable of handling them.
 * This interface defines the contract for tool proxies, which are responsible
 * for executing tool invocations.
 */
public interface ToolsBridge extends Bridge {

    /**
     * Executes a tool with the specified arguments.
     *
     * @param callToolRequest the MCP tool call request containing tool name and arguments
     * @param sessionId the MCP session ID for tracing
     * @param transportContext the transport context with HTTP request headers
     * @param toolReference the reference to the tool being called
     * @return a Uni emitting the tool call result
     */
    Uni<McpSchema.CallToolResult> execute(
            McpSchema.CallToolRequest callToolRequest,
            String sessionId,
            McpTransportContext transportContext,
            CallableReference toolReference);
}
