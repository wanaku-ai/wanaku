package ai.wanaku.provider.s3;

import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import ai.wanaku.core.exchange.ResourceAcquirer;
import ai.wanaku.core.exchange.ResourceAcquirerDelegate;
import ai.wanaku.core.exchange.ResourceReply;
import ai.wanaku.core.exchange.ResourceRequest;
import ai.wanaku.core.service.discovery.util.DiscoveryUtil;
import ai.wanaku.core.services.config.WanakuProviderConfig;
import io.quarkus.grpc.GrpcService;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import java.util.concurrent.TimeUnit;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@GrpcService
public class ResourceService implements ResourceAcquirer {
    private static final Logger LOG = Logger.getLogger(ResourceService.class);

    @Inject
    ResourceAcquirerDelegate delegate;

    @Inject
    WanakuProviderConfig config;

    @ConfigProperty(name = "quarkus.grpc.server.port")
    int port;

    @Override
    public Uni<ResourceReply> resourceAcquire(ResourceRequest request) {
        return Uni.createFrom().item(() -> delegate.acquire(request));
    }

    @Scheduled(every="{wanaku.service.provider.registration.interval}", delayed = "{wanaku.service.provider.registration.delay-seconds}", delayUnit = TimeUnit.SECONDS)
    void register() {
        delegate.register();
    }

    void deregister(@Observes ShutdownEvent ev) {
        LOG.info("De-registering resource service");

        delegate.deregister();
    }
}
