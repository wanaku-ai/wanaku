package org.wanaku.routers.camel;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.wanaku.api.resolvers.ToolsResolver;
import org.wanaku.api.resolvers.util.NoopToolsResolver;
import org.wanaku.routers.camel.proxies.ToolsProxy;
import org.wanaku.routers.camel.proxies.tools.CamelEndpointProxy;
import org.wanaku.routers.camel.resolvers.CamelToolsResolver;
import picocli.CommandLine;

import static org.wanaku.api.resolvers.Resolver.DEFAULT_TOOLS_INDEX_FILE_NAME;

@ApplicationScoped
public class ToolsProvider extends AbstractProvider<ToolsProxy, ToolsResolver> {
    @Inject
    CommandLine.ParseResult parseResult;

    @Inject
    CamelContext camelContext;

    @Produces
    @Override
    ToolsResolver getResolver() {
        if (parseResult.isUsageHelpRequested() || parseResult.isVersionHelpRequested()) {
            return new NoopToolsResolver();
        }

        initializeCamel(camelContext);

        File resourcesIndexFile = initializeIndex();

        Map<String, ? extends ToolsProxy> proxies = loadProxies();
        return new CamelToolsResolver(resourcesIndexFile, proxies);
    }

    @Override
    protected File initializeIndex() {
        String indexPath = parseResult.matchedOptionValue("indexes-path", "${user.home}/.wanaku/router/")
                .replace("${user.home}", System.getProperty("user.home"));

        return initializeResourcesIndex(indexPath, DEFAULT_TOOLS_INDEX_FILE_NAME);
    }

    @Override
    public Map<String, ToolsProxy> loadProxies() {
        Map<String, ToolsProxy> proxies = new HashMap<>();

        proxies.put("routes", new CamelEndpointProxy(camelContext));

        return proxies;

    }
}
