package ai.wanaku.backend.providers;

import ai.wanaku.backend.bridge.InvokerBridge;
import ai.wanaku.backend.bridge.WanakuBridgeTransport;
import ai.wanaku.backend.bridge.transports.grpc.GrpcTransport;
import ai.wanaku.backend.common.ToolCallEvent;
import ai.wanaku.backend.resolvers.WanakuToolsResolver;
import ai.wanaku.backend.service.support.FirstAvailable;
import ai.wanaku.backend.service.support.ServiceResolver;
import ai.wanaku.core.mcp.common.resolvers.ToolsResolver;
import ai.wanaku.core.mcp.common.resolvers.util.NoopToolsResolver;
import ai.wanaku.core.mcp.providers.ServiceRegistry;
import ai.wanaku.core.util.VersionHelper;
import io.quarkus.arc.DefaultBean;
import io.smallrye.reactive.messaging.MutinyEmitter;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;
import org.jboss.logging.Logger;
import picocli.CommandLine;

/**
 * A provider for tools resolvers
 */
@ApplicationScoped
public class ToolsProvider extends AbstractProvider<ToolsResolver> {
    private static final Logger LOG = Logger.getLogger(ToolsProvider.class);

    @Inject
    CommandLine.ParseResult parseResult;

    @Inject
    Instance<ServiceRegistry> serviceRegistryInstance;

    @Inject
    @Channel("tool-call-event")
    @OnOverflow(OnOverflow.Strategy.DROP)
    MutinyEmitter<ToolCallEvent> toolCallEventEmitter;

    ServiceRegistry serviceRegistry;

    @PostConstruct
    void init() {
        serviceRegistry = serviceRegistryInstance.get();
    }

    @Produces
    @Override
    @DefaultBean
    ToolsResolver getResolver() {
        if (parseResult.isUsageHelpRequested() || parseResult.isVersionHelpRequested()) {
            return new NoopToolsResolver();
        }

        LOG.infof("Wanaku version %s is starting", VersionHelper.VERSION);
        ServiceResolver resolver = new FirstAvailable(serviceRegistry);
        WanakuBridgeTransport transport = new GrpcTransport();
        return new WanakuToolsResolver(new InvokerBridge(resolver, transport, toolCallEventEmitter));
    }
}
