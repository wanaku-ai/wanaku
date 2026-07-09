package ai.wanaku.backend.bridge;

import java.util.List;
import io.modelcontextprotocol.spec.McpSchema;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;

public interface ResourceResponseTransformer<T> {

    List<McpSchema.ResourceContents> transformReply(
            T reply, McpSchema.ReadResourceRequest readRequest, ResourceReference mcpResource);
}
