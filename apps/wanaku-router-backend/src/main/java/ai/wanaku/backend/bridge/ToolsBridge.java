package ai.wanaku.backend.bridge;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema;
import io.smallrye.mutiny.Uni;
import ai.wanaku.capabilities.sdk.api.types.CallableReference;

public interface ToolsBridge extends Bridge {

    Uni<McpSchema.CallToolResult> execute(
            McpSchema.CallToolRequest callToolRequest,
            String sessionId,
            McpTransportContext transportContext,
            CallableReference toolReference);
}
