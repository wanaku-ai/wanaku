package ai.wanaku.core.forward.discovery.client;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class ForwardRegistrationManager {

    @Inject
    ForwardDiscoveryClient discoveryClient;

    @Scheduled(
            every = "{wanaku.service.registration.interval}",
            delayed = "{wanaku.service.registration.delay-seconds}",
            delayUnit = TimeUnit.SECONDS)
    void registrationCheck() {
        if (!discoveryClient.isRegistered()) {
            discoveryClient.register();
        }
    }

    void deregister(@Observes ShutdownEvent ev) {
        discoveryClient.deregister();
    }
}
