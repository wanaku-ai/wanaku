package ai.wanaku.server.quarkus.support;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import ai.wanaku.core.mcp.common.resolvers.ToolsResolver;
import ai.wanaku.core.mcp.common.resolvers.util.NoopForwardRegistry;
import ai.wanaku.core.mcp.providers.ForwardRegistry;

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

    @Produces
    ForwardRegistry forwardRegistry() {
        return new NoopForwardRegistry();
    }
}
