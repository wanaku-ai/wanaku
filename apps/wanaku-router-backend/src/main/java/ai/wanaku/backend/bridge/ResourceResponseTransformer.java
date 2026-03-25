package ai.wanaku.backend.bridge;

import java.util.List;
import io.quarkiverse.mcp.server.ResourceContents;
import io.quarkiverse.mcp.server.ResourceManager;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;

public interface ResourceResponseTransformer<T> {

    List<ResourceContents> transformReply(
            T reply, ResourceManager.ResourceArguments arguments, ResourceReference mcpResource);
}
