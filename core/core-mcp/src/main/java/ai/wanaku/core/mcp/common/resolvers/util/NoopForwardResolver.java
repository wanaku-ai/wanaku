package ai.wanaku.core.mcp.common.resolvers.util;

import ai.wanaku.api.exceptions.ServiceNotFoundException;
import ai.wanaku.api.exceptions.ToolNotFoundException;
import ai.wanaku.api.types.CallableReference;
import ai.wanaku.api.types.RemoteToolReference;
import ai.wanaku.api.types.ResourceReference;
import ai.wanaku.api.types.io.ResourcePayload;
import ai.wanaku.core.mcp.common.Tool;
import ai.wanaku.core.mcp.common.resolvers.ForwardResolver;
import io.quarkiverse.mcp.server.ResourceContents;
import io.quarkiverse.mcp.server.ResourceManager;
import java.util.List;

public class NoopForwardResolver implements ForwardResolver {
    @Override
    public Tool resolve(CallableReference toolReference) throws ToolNotFoundException {
        return null;
    }

    @Override
    public List<ResourceReference> listResources() {
        return List.of();
    }

    @Override
    public List<RemoteToolReference> listTools() {
        return List.of();
    }

    @Override
    public void provision(ResourcePayload resourcePayload) throws ServiceNotFoundException {}

    @Override
    public List<ResourceContents> read(ResourceManager.ResourceArguments arguments, ResourceReference mcpResource) {
        return List.of();
    }
}
