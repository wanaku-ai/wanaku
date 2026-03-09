package ai.wanaku.core.forward.discovery.client;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.jboss.logging.Logger;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.scheduler.Scheduled;

@ApplicationScoped
public class ForwardRegistrationManager {
    private static final Logger LOG = Logger.getLogger(ForwardRegistrationManager.class);

    @Inject
    ForwardDiscoveryClient discoveryClient;

    private final List<ForwardDiscoveryCallback> callbacks = new CopyOnWriteArrayList<>();

    /**
     * Adds a callback to be invoked after forward registration lifecycle events.
     *
     * @param callback the callback to add
     */
    public void addCallBack(ForwardDiscoveryCallback callback) {
        callbacks.add(callback);
    }

    @Scheduled(
            every = "{wanaku.service.registration.interval}",
            delayed = "{wanaku.service.registration.delay-seconds}",
            delayUnit = TimeUnit.SECONDS)
    void registrationCheck() {
        if (!discoveryClient.isRegistered()) {
            discoveryClient.register();
            if (discoveryClient.isRegistered()) {
                runCallBack(c -> c.onRegistration(this));
            }
        }
    }

    void deregister(@Observes ShutdownEvent ev) {
        runCallBack(c -> c.onDeregistration(this));
        discoveryClient.deregister();
    }

    private void runCallBack(Consumer<ForwardDiscoveryCallback> action) {
        for (ForwardDiscoveryCallback callback : callbacks) {
            try {
                action.accept(callback);
            } catch (Exception e) {
                LOG.warnf(e, "Unable to run callback %s", callback.getClass().getName());
            }
        }
    }
}
