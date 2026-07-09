package ai.wanaku.backend.bridge;

import io.modelcontextprotocol.spec.McpSchema;
import io.smallrye.mutiny.Uni;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;

public interface ResourceBridge extends Bridge {

    Uni<McpSchema.ReadResourceResult> read(
            McpSchema.ReadResourceRequest readRequest, String sessionId, ResourceReference mcpResource);
}
