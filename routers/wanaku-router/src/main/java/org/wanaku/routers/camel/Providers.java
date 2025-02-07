package org.wanaku.routers.camel;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.apache.camel.CamelContext;
import org.jboss.logging.Logger;
import org.wanaku.api.resolvers.ResourceResolver;
import org.wanaku.api.resolvers.util.NoopResourceResolver;
import org.wanaku.routers.camel.translators.FileProxy;
import picocli.CommandLine;

@ApplicationScoped
public class Providers {
    private static final Logger LOG = Logger.getLogger(Providers.class);

    @Inject
    CommandLine.ParseResult parseResult;

    @Inject
    CamelContext camelContext;

    @Produces
    ResourceResolver getResourceResolver() {
        if (parseResult.isUsageHelpRequested() || parseResult.isVersionHelpRequested()) {
            return new NoopResourceResolver();
        }

        String resourcesPath = parseResult.matchedOptionValue("resources-path", "${user.home}/.wanaku/router/")
                .replace("${user.home}", System.getProperty("user.home"));
        createSettingsDirectory(resourcesPath);

        if (!camelContext.isStarted()) {
            camelContext.start();
        }
        Map<String, ? extends ResourceProxy> proxies = loadProxies();
        return new CamelResourceResolver(resourcesPath, proxies);
    }

    private static void createSettingsDirectory(String resourcesPath) {
        File resourcesDir = new File(resourcesPath);
        if (!resourcesDir.exists()) {
            resourcesDir.mkdirs();
        }
    }

    public Map<String, ? extends ResourceProxy> loadProxies() {
        Map<String, ResourceProxy> proxies = new HashMap<>();

        proxies.put("file", new FileProxy(camelContext));

        return proxies;

    }

}
