package org.wanaku.routers.camel;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.wanaku.api.resolvers.ResourceResolver;
import org.wanaku.api.resolvers.util.NoopResourceResolver;
import org.wanaku.routers.camel.proxies.ResourceProxy;
import org.wanaku.routers.camel.proxies.resources.FileProxy;
import org.wanaku.routers.camel.resolvers.CamelResourceResolver;
import picocli.CommandLine;

import static org.wanaku.api.resolvers.Resolver.DEFAULT_RESOURCES_INDEX_FILE_NAME;

@ApplicationScoped
public class ResourcesProvider extends AbstractProvider<ResourceProxy, ResourceResolver> {
    @Inject
    CommandLine.ParseResult parseResult;

    @Inject
    CamelContext camelContext;

    @Produces
    @Override
    ResourceResolver getResolver() {
        if (parseResult.isUsageHelpRequested() || parseResult.isVersionHelpRequested()) {
            return new NoopResourceResolver();
        }

        File resourcesIndexFile = initializeIndex();

        initializeCamel(camelContext);
        Map<String, ? extends ResourceProxy> proxies = loadProxies();
        return new CamelResourceResolver(resourcesIndexFile, proxies);
    }


    @Override
    protected File initializeIndex() {
        String indexPath = parseResult.matchedOptionValue("indexes-path", "${user.home}/.wanaku/router/")
                .replace("${user.home}", System.getProperty("user.home"));

        return initializeResourcesIndex(indexPath, DEFAULT_RESOURCES_INDEX_FILE_NAME);
    }

    @Override
    public Map<String, ResourceProxy> loadProxies() {
        Map<String, ResourceProxy> proxies = new HashMap<>();

        proxies.put("file", new FileProxy(camelContext));

        return proxies;

    }
}
