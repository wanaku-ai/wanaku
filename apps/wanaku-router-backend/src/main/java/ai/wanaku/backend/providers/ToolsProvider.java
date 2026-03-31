package ai.wanaku.backend.providers;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;
import org.jboss.logging.Logger;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import ai.wanaku.backend.bridge.EventNotifier;
import ai.wanaku.backend.bridge.InvokerBridge;
import ai.wanaku.backend.bridge.ToolsBridge;
import ai.wanaku.backend.bridge.WanakuBridgeTransport;
import ai.wanaku.backend.common.ToolCallEvent;
import ai.wanaku.backend.service.support.ServiceResolver;
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
    ServiceResolver serviceResolver;

    @Inject
    WanakuBridgeTransport transport;

    @Inject
    @Channel("tool-call-event")
    @OnOverflow(OnOverflow.Strategy.DROP)
    MutinyEmitter<ToolCallEvent> toolCallEventEmitter;

    @Produces
    ToolsBridge getToolsBridge() {
        if (parseResult.isUsageHelpRequested() || parseResult.isVersionHelpRequested()) {
            return (toolArguments, toolReference) -> Uni.createFrom().nullItem();
        }

        LOG.infof("Wanaku version %s is starting", VersionHelper.VERSION);
        return new InvokerBridge(serviceResolver, transport, new EventNotifier(toolCallEventEmitter));
    }
}
