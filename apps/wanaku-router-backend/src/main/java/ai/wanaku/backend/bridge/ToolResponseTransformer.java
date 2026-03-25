package ai.wanaku.backend.bridge;

import io.quarkiverse.mcp.server.ToolResponse;

public interface ToolResponseTransformer<T> {

    ToolResponse transformReply(T reply);
}
