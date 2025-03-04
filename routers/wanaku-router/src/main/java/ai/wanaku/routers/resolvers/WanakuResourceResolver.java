package ai.wanaku.routers.resolvers;

import java.io.File;
import java.util.List;
import java.util.Map;

import io.quarkiverse.mcp.server.ResourceContents;
import io.quarkiverse.mcp.server.ResourceManager;
import org.jboss.logging.Logger;
import ai.wanaku.api.types.ResourceReference;
import ai.wanaku.core.mcp.common.resolvers.ResourceResolver;
import ai.wanaku.routers.proxies.ResourceProxy;

/**
 * Represents resolvers for Wanaku resources
 */
public class WanakuResourceResolver implements ResourceResolver {
    private static final Logger LOG = Logger.getLogger(WanakuResourceResolver.class);
    private final File indexFile;
    private final ResourceProxy proxy;

    public WanakuResourceResolver(File indexFile, ResourceProxy proxy) {
        this.indexFile = indexFile;
        this.proxy = proxy;
    }

    @Override
    public File indexLocation() {
        return indexFile;
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
