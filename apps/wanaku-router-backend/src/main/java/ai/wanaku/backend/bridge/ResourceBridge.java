package ai.wanaku.backend.bridge;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema;
import io.smallrye.mutiny.Uni;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;

/**
 * Proxies between MCP URIs and Camel components capable of handling them.
 * This interface defines the contract for resource proxies, which are responsible
 * for reading resource contents.
 */
public interface ResourceBridge extends Bridge {

    /**
     * Reads a resource with the specified request parameters.
     *
     * @param readRequest the MCP read resource request containing the resource URI
     * @param sessionId the MCP session ID for tracing
     * @param transportContext the transport context with HTTP request headers
     * @param mcpResource the resource reference to read
     * @return a Uni emitting the resource read result
     */
    Uni<McpSchema.ReadResourceResult> read(
            McpSchema.ReadResourceRequest readRequest,
            String sessionId,
            McpTransportContext transportContext,
            ResourceReference mcpResource);
}
