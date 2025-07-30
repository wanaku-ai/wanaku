package ai.wanaku.backend.support;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import ai.wanaku.core.mcp.common.resolvers.ToolsResolver;
import ai.wanaku.core.mcp.common.resolvers.util.NoopForwardRegistry;
import ai.wanaku.core.mcp.providers.ForwardRegistry;
import io.quarkus.arc.DefaultBean;
import org.jboss.logging.Logger;

@ApplicationScoped
public class TestProvider {
    private static final Logger LOG = Logger.getLogger(TestProvider.class);

    @Produces
    @DefaultBean
    @Priority(100)
    TestResourceResolver resourceProvider() {
        LOG.infof("Creating test resource resolver");
        return new TestResourceResolver();
    }

    @Produces
    @DefaultBean
    @Priority(100)
    ToolsResolver toolsResolver() {
        LOG.infof("Creating test tools resolver");
        return new TestToolsResolver();
    }

    @Produces
    @DefaultBean
    @Priority(100)
    ForwardRegistry forwardRegistry() {
        return new NoopForwardRegistry();
    }
}
