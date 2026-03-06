package ai.wanaku.backend.support;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import org.jboss.logging.Logger;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkus.arc.DefaultBean;
import io.smallrye.mutiny.Uni;
import ai.wanaku.backend.bridge.ToolsBridge;
import ai.wanaku.capabilities.sdk.api.types.CallableReference;
import ai.wanaku.capabilities.sdk.api.types.io.ToolPayload;
import ai.wanaku.core.mcp.common.resolvers.util.NoopForwardRegistry;
import ai.wanaku.core.mcp.providers.ForwardRegistry;

@ApplicationScoped
public class TestProvider {
    private static final Logger LOG = Logger.getLogger(TestProvider.class);

    @Produces
    @DefaultBean
    @Priority(100)
    ToolsBridge toolsBridge() {
        LOG.infof("Creating test tools bridge");
        return new ToolsBridge() {
            @Override
            public ProvisioningReference provision(ToolPayload payload) {
                return null;
            }

            @Override
            public ToolResponse execute(ToolManager.ToolArguments toolArguments, CallableReference toolReference) {
                return null;
            }

            @Override
            public Uni<ToolResponse> executeAsync(
                    ToolManager.ToolArguments toolArguments, CallableReference toolReference) {
                return Uni.createFrom().nullItem();
            }
        };
    }

    @Produces
    @DefaultBean
    @Priority(100)
    ForwardRegistry forwardRegistry() {
        return new NoopForwardRegistry();
    }
}
