package ai.wanaku.core.capabilities.tool.service;

import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import ai.wanaku.core.exchange.InvocationDelegate;
import ai.wanaku.core.exchange.ProvisionReply;
import ai.wanaku.core.exchange.ProvisionRequest;
import ai.wanaku.core.exchange.Provisioner;
import ai.wanaku.core.exchange.ToolInvokeReply;
import ai.wanaku.core.exchange.ToolInvokeRequest;
import ai.wanaku.core.exchange.ToolInvoker;
import ai.wanaku.core.capabilities.config.WanakuServiceConfig;
import io.quarkus.grpc.GrpcService;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Uni;
import java.util.concurrent.TimeUnit;
import org.jboss.logging.Logger;

@GrpcService
public class InvocationService implements ToolInvoker, Provisioner {
    private static final Logger LOG = Logger.getLogger(InvocationService.class);

    @Inject
    InvocationDelegate delegate;

    @Inject
    WanakuServiceConfig config;

    @Blocking
    @Override
    public Uni<ToolInvokeReply> invokeTool(ToolInvokeRequest request) {
        return Uni.createFrom().item(() -> delegate.invoke(request));
    }

    @Blocking
    @Override
    public Uni<ProvisionReply> provision(ProvisionRequest request) {
        return Uni.createFrom().item(() -> delegate.provision(request));
    }

    @Scheduled(every="{wanaku.service.registration.interval}", delayed = "{wanaku.service.registration.delay-seconds}", delayUnit = TimeUnit.SECONDS)
    void register() {
        delegate.register();
    }

    void deregister(@Observes ShutdownEvent ev) {
        LOG.info("De-registering tool service");

        delegate.deregister();
    }
}
