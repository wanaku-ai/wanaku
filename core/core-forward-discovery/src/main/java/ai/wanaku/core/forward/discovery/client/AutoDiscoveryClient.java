package ai.wanaku.core.forward.discovery.client;

import static ai.wanaku.core.util.discovery.DiscoveryUtil.resolveRegistrationAddress;

import ai.wanaku.capabilities.sdk.api.types.ForwardReference;
import ai.wanaku.core.services.api.ForwardsService;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import org.jboss.logging.Logger;

public class AutoDiscoveryClient implements ForwardDiscoveryClient {
    private static final Logger LOG = Logger.getLogger(AutoDiscoveryClient.class);

    private final String namespace;
    private final String name;
    private final String registrationUri;
    private final String announceAddress;
    private final AtomicBoolean registered = new AtomicBoolean(false);
    private final ReentrantLock lock = new ReentrantLock();

    public AutoDiscoveryClient(String registrationUri, String name, String namespace, String announceAddress) {
        this.registrationUri = registrationUri;
        this.name = name;
        this.namespace = namespace;
        this.announceAddress = announceAddress;
    }

    @Override
    public boolean isRegistered() {
        return registered.get();
    }

    @Override
    public void register() {
        try {
            lock.lock();

            ForwardReference reference = new ForwardReference();
            reference.setName(name);
            reference.setNamespace(namespace);

            String actualAnnounceAddress = resolveRegistrationAddress(announceAddress);

            reference.setAddress(actualAnnounceAddress);

            final ForwardsService forwardsService = newService();
            try (Response ignored = forwardsService.addForward(reference)) {
                registered.set(true);
                LOG.debugf("The service %s successfully registered.", reference.getName());
            } catch (WebApplicationException ex) {
                if (LOG.isTraceEnabled()) {
                    LOG.errorf(ex, "Unable to register forward service %s", reference.getName());
                } else {
                    LOG.errorf("Unable to register forward service %s", reference.getName());
                }
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void deregister() {
        try {
            lock.lock();

            final ForwardsService forwardsService = newService();

            ForwardReference reference = new ForwardReference();
            reference.setName(name);

            try (Response ignored = forwardsService.removeForward(reference)) {

            } catch (WebApplicationException ex) {
                if (LOG.isTraceEnabled()) {
                    LOG.errorf(ex, "Unable to deregister forward service %s", reference.getName());
                } else {
                    LOG.errorf("Unable to deregister forward service %s", reference.getName());
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private ForwardsService newService() {
        return QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(registrationUri))
                .build(ForwardsService.class);
    }
}
