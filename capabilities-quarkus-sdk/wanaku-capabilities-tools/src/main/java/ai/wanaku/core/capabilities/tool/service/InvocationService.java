package ai.wanaku.core.capabilities.tool.service;

import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.util.concurrent.TimeUnit;
import org.jboss.logging.Logger;
import io.quarkus.grpc.GrpcService;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Uni;
import ai.wanaku.core.capabilities.config.WanakuServiceConfig;
import ai.wanaku.core.exchange.HealthProbeDelegate;
import ai.wanaku.core.exchange.InvocationDelegate;
import ai.wanaku.core.exchange.v1.HealthProbe;
import ai.wanaku.core.exchange.v1.HealthProbeReply;
import ai.wanaku.core.exchange.v1.HealthProbeRequest;
import ai.wanaku.core.exchange.v1.ProvisionReply;
import ai.wanaku.core.exchange.v1.ProvisionRequest;
import ai.wanaku.core.exchange.v1.Provisioner;
import ai.wanaku.core.exchange.v1.ToolInvokeReply;
import ai.wanaku.core.exchange.v1.ToolInvokeRequest;
import ai.wanaku.core.exchange.v1.ToolInvoker;

@GrpcService
public class InvocationService implements ToolInvoker, Provisioner, HealthProbe {
    private static final Logger LOG = Logger.getLogger(InvocationService.class);

    @Inject
    InvocationDelegate delegate;

    @Inject
    HealthProbeDelegate healthProbeDelegate;

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

    @Override
    public Uni<HealthProbeReply> getStatus(HealthProbeRequest request) {
        return Uni.createFrom()
                .item(HealthProbeReply.newBuilder()
                        .setStatus(healthProbeDelegate.getStatus(request.getId()))
                        .build());
    }

    @Scheduled(
            every = "{wanaku.service.registration.interval}",
            delayed = "{wanaku.service.registration.delay-seconds}",
            delayUnit = TimeUnit.SECONDS)
    void register() {
        delegate.register();
    }

    void deregister(@Observes ShutdownEvent ev) {
        LOG.info("De-registering tool service");

        delegate.deregister();
    }
}
