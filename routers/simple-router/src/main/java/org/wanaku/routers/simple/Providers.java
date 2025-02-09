package org.wanaku.routers.simple;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.wanaku.api.resolvers.ResourceResolver;
import org.wanaku.api.resolvers.ToolsResolver;
import org.wanaku.api.resolvers.util.NoopToolsResolver;
import picocli.CommandLine;

@ApplicationScoped
public class Providers {
    private static final Logger LOG = Logger.getLogger(Providers.class);

    @Inject
    CommandLine.ParseResult parseResult;

    @Inject
    org.wanaku.server.quarkus.McpResource mcpResource;

    @Produces
    ResourceResolver getResourceResolver() {
        var resourcesPath = parseResult.matchedOption("resources-path").getValue().toString();
        return new SimpleResourceResolver(resourcesPath);
    }

    @Produces
    ToolsResolver getToolsResolver() {
        return new NoopToolsResolver();
    }

}
