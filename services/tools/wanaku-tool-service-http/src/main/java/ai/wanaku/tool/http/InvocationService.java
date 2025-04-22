package ai.wanaku.tool.http;

import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import ai.wanaku.core.exchange.InquireReply;
import ai.wanaku.core.exchange.InquireRequest;
import ai.wanaku.core.exchange.Inquirer;
import ai.wanaku.core.exchange.InvocationDelegate;
import ai.wanaku.core.exchange.ToolInvokeReply;
import ai.wanaku.core.exchange.ToolInvokeRequest;
import ai.wanaku.core.exchange.ToolInvoker;
import ai.wanaku.core.service.discovery.util.DiscoveryUtil;
import ai.wanaku.core.services.common.ServicesHelper;
import ai.wanaku.core.services.config.WanakuToolConfig;
import io.quarkus.grpc.GrpcService;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import java.util.concurrent.TimeUnit;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@GrpcService
public class InvocationService implements ToolInvoker, Inquirer {
    private static final Logger LOG = Logger.getLogger(InvocationService.class);

    @Inject
    InvocationDelegate delegate;

    @Inject
    WanakuToolConfig config;

    @ConfigProperty(name = "quarkus.grpc.server.port")
    int port;

    @Override
    public Uni<ToolInvokeReply> invokeTool(ToolInvokeRequest request) {
        return Uni.createFrom().item(() -> delegate.invoke(request));
    }

    @Override
    public Uni<InquireReply> inquire(InquireRequest request) {
        return Uni.createFrom().item(() -> ServicesHelper.buildInquireReply(delegate));
    }

    @Scheduled(every="{wanaku.service.tool.registration.interval}", delayed = "{wanaku.service.tool.registration.delay-seconds}", delayUnit = TimeUnit.SECONDS)
    void register() {
        delegate.register();
    }

    void deregister(@Observes ShutdownEvent ev) {
        LOG.info("De-registering resource service");

        delegate.deregister(config.name(), DiscoveryUtil.resolveRegistrationAddress(), port);
    }
}
