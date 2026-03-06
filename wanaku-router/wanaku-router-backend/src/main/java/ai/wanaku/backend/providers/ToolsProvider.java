package ai.wanaku.backend.providers;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;
import org.jboss.logging.Logger;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import ai.wanaku.backend.bridge.InvokerBridge;
import ai.wanaku.backend.bridge.ToolsBridge;
import ai.wanaku.backend.bridge.WanakuBridgeTransport;
import ai.wanaku.backend.common.ToolCallEvent;
import ai.wanaku.backend.service.support.ServiceResolver;
import ai.wanaku.backend.support.ProvisioningReference;
import ai.wanaku.capabilities.sdk.api.types.CallableReference;
import ai.wanaku.capabilities.sdk.api.types.io.ToolPayload;
import ai.wanaku.core.util.VersionHelper;
import picocli.CommandLine;

/**
 * A provider for tools bridge
 */
@ApplicationScoped
public class ToolsProvider {
    private static final Logger LOG = Logger.getLogger(ToolsProvider.class);

    @Inject
    CommandLine.ParseResult parseResult;

    @Inject
    @Channel("tool-call-event")
    @OnOverflow(OnOverflow.Strategy.DROP)
    MutinyEmitter<ToolCallEvent> toolCallEventEmitter;

    @Inject
    ServiceResolver serviceResolver;

    @Inject
    WanakuBridgeTransport transport;

    @Produces
    ToolsBridge getToolsBridge() {
        if (parseResult.isUsageHelpRequested() || parseResult.isVersionHelpRequested()) {
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

        LOG.infof("Wanaku version %s is starting", VersionHelper.VERSION);
        return new InvokerBridge(serviceResolver, transport, toolCallEventEmitter);
    }
}
