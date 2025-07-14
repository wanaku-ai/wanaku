package ai.wanaku.routers.resolvers;

import ai.wanaku.api.exceptions.ServiceNotFoundException;
import ai.wanaku.api.types.Property;
import ai.wanaku.api.types.io.ResourcePayload;
import ai.wanaku.routers.support.ProvisioningReference;
import java.util.List;

import io.quarkiverse.mcp.server.ResourceContents;
import io.quarkiverse.mcp.server.ResourceManager;
import java.util.Map;
import org.jboss.logging.Logger;
import ai.wanaku.api.types.ResourceReference;
import ai.wanaku.core.mcp.common.resolvers.ResourceResolver;
import ai.wanaku.routers.proxies.ResourceProxy;

/**
 * Represents resolvers for Wanaku resources
 */
public class WanakuResourceResolver implements ResourceResolver {
    private static final Logger LOG = Logger.getLogger(WanakuResourceResolver.class);
    private final ResourceProxy proxy;

    public WanakuResourceResolver(ResourceProxy proxy) {
        this.proxy = proxy;
    }

    @Override
    public void provision(ResourcePayload resourcePayload) throws ServiceNotFoundException {
        final ProvisioningReference provisioningReference = proxy.provision(resourcePayload);

        resourcePayload.getPayload().setConfigurationURI(provisioningReference.configurationURI().toString());
        resourcePayload.getPayload().setSecretsURI(provisioningReference.secretsURI().toString());

    }

    @Override
    public List<ResourceContents> read(ResourceManager.ResourceArguments arguments, ResourceReference mcpResource) {
        LOG.infof("Using the resource proxy %s to evaluate MCP uri %s for request %s", proxy.name(), mcpResource,
                arguments.requestId());

        return proxy.eval(arguments, mcpResource);
    }
}
