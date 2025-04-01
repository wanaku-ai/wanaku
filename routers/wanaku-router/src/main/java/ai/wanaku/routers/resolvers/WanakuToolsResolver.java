package ai.wanaku.routers.resolvers;

import java.util.Map;

import ai.wanaku.api.types.ToolReference;
import ai.wanaku.core.mcp.common.Tool;
import ai.wanaku.core.mcp.common.resolvers.ToolsResolver;
import ai.wanaku.routers.proxies.ToolsProxy;

public class WanakuToolsResolver implements ToolsResolver {
    private final ToolsProxy proxy;

    public WanakuToolsResolver(ToolsProxy proxy) {
        this.proxy = proxy;
    }

    @Override
    public Tool resolve(ToolReference toolReference) {
        return proxy;
    }

    @Override
    public Map<String, String> getServiceConfigurations(String target) {
        return proxy.getServiceConfigurations(target);
    }
}
