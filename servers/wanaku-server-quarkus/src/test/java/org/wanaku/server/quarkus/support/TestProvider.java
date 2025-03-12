package org.wanaku.server.quarkus.support;

import ai.wanaku.core.mcp.common.resolvers.ToolsResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class TestProvider {

    @Produces
    TestResourceResolver resourceProvider() {
        return new TestResourceResolver();
    }

    @Produces
    ToolsResolver toolsResolver() {
        return new TestToolsResolver();
    }
}
