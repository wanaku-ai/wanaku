package org.wanaku.server.quarkus.api.v1.resources;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.wanaku.api.resolvers.ToolsResolver;

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
