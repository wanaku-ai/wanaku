package ai.wanaku.backend.bridge;

import io.modelcontextprotocol.spec.McpSchema;

public interface ToolResponseTransformer<T> {

    McpSchema.CallToolResult transformReply(T reply);
}
