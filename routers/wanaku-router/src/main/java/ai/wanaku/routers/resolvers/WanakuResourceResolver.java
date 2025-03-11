package ai.wanaku.routers.resolvers;

import ai.wanaku.api.types.ResourceReference;
import ai.wanaku.core.mcp.common.resolvers.ResourceResolver;
import ai.wanaku.routers.proxies.ResourceProxy;
import io.quarkiverse.mcp.server.ResourceContents;
import io.quarkiverse.mcp.server.ResourceManager;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;

/**
 * Represents resolvers for Wanaku resources
 */
public class WanakuResourceResolver implements ResourceResolver {
    private static final Logger LOG = Logger.getLogger(WanakuResourceResolver.class);
    private final String index;
    private final ResourceProxy proxy;

    public WanakuResourceResolver(String index, ResourceProxy proxy) {
        this.index = index;
        this.proxy = proxy;
    }

    @Override
    public String index() {
        return index;
    }

    @Override
    public List<ResourceContents> read(ResourceManager.ResourceArguments arguments, ResourceReference mcpResource) {
        LOG.infof("Using the resource proxy %s to evaluate MCP uri %s for request %s", proxy.name(), mcpResource,
                arguments.requestId());

        return proxy.eval(arguments, mcpResource);
    }

    @Override
    public Map<String, String> getServiceConfigurations(String target) {
        return proxy.getServiceConfigurations(target);
    }
}
