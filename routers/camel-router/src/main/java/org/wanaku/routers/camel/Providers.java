package org.wanaku.routers.camel;

import java.util.HashMap;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.apache.camel.CamelContext;
import org.jboss.logging.Logger;
import org.wanaku.api.resolvers.ResourceResolver;
import org.wanaku.routers.camel.translators.FileProxy;
import org.wanaku.server.quarkus.McpResource;
import picocli.CommandLine;

@ApplicationScoped
public class Providers {
    private static final Logger LOG = Logger.getLogger(Providers.class);

    @Inject
    CommandLine.ParseResult parseResult;

    @Inject
    McpResource mcpResource;

    @Inject
    CamelContext camelContext;



    @Produces
    ResourceResolver getResourceResolver() {
        var resourcesPath = parseResult.matchedOption("resources-path").getValue().toString();
        if (!camelContext.isStarted()) {
            camelContext.start();
        }
        Map<String, ? extends ResourceProxy> proxies = loadProxies();
        return new CamelResourceResolver(resourcesPath, proxies);
    }

    public Map<String, ? extends ResourceProxy> loadProxies() {
        Map<String, ResourceProxy> proxies = new HashMap<>();

        proxies.put("file", new FileProxy(camelContext));

        return proxies;

    }

}
