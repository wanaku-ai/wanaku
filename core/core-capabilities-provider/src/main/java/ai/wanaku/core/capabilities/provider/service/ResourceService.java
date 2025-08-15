package ai.wanaku.core.capabilities.provider.service;

import ai.wanaku.core.capabilities.config.WanakuServiceConfig;
import ai.wanaku.core.exchange.ProvisionReply;
import ai.wanaku.core.exchange.ProvisionRequest;
import ai.wanaku.core.exchange.Provisioner;
import ai.wanaku.core.exchange.ResourceAcquirer;
import ai.wanaku.core.exchange.ResourceAcquirerDelegate;
import ai.wanaku.core.exchange.ResourceReply;
import ai.wanaku.core.exchange.ResourceRequest;
import io.quarkus.grpc.GrpcService;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.concurrent.TimeUnit;
import org.jboss.logging.Logger;

@GrpcService
public class ResourceService implements ResourceAcquirer, Provisioner {
    private static final Logger LOG = Logger.getLogger(ResourceService.class);

    @Inject
    ResourceAcquirerDelegate delegate;

    @Inject
    WanakuServiceConfig config;

    @Blocking
    @Override
    public Uni<ResourceReply> resourceAcquire(ResourceRequest request) {
        return Uni.createFrom().item(() -> delegate.acquire(request));
    }

    @Blocking
    @Override
    public Uni<ProvisionReply> provision(ProvisionRequest request) {
        return Uni.createFrom().item(() -> delegate.provision(request));
    }

    @Scheduled(
            every = "{wanaku.service.registration.interval}",
            delayed = "{wanaku.service.registration.delay-seconds}",
            delayUnit = TimeUnit.SECONDS)
    void register() {
        delegate.register();
    }

    void deregister(@Observes ShutdownEvent ev) {
        LOG.info("De-registering resource service");

        delegate.deregister();
    }
}
