package ai.wanaku.routers.resolvers;

import java.io.File;
import java.util.Map;

import ai.wanaku.api.types.ToolReference;
import ai.wanaku.core.mcp.common.Tool;
import ai.wanaku.core.mcp.common.resolvers.ToolsResolver;
import ai.wanaku.routers.proxies.ToolsProxy;

public class WanakuToolsResolver implements ToolsResolver {
    private final String index;
    private final ToolsProxy proxy;

    public WanakuToolsResolver(String index, ToolsProxy proxy) {
        this.index = index;
        this.proxy = proxy;
    }

    @Override
    public String index() {
        return index;
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
