package ai.wanaku.core.forward.discovery.client;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import ai.wanaku.api.exceptions.WanakuException;
import ai.wanaku.api.types.ForwardReference;
import ai.wanaku.core.services.api.ForwardsService;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.scheduler.Scheduled;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import static ai.wanaku.core.util.discovery.DiscoveryUtil.resolveRegistrationAddress;

@ApplicationScoped
public class ForwardDiscoveryClient {
    private static final Logger LOG = Logger.getLogger(ForwardDiscoveryClient.class);

    @ConfigProperty(name = "wanaku.service.registration.uri")
    String registrationUri;

    @ConfigProperty(name = "wanaku.mcp.service.name")
    String name;

    @ConfigProperty(name = "wanaku.service.registration.announce-address", defaultValue = "auto")
    String announceAddress;

    @Scheduled(every="{wanaku.service.registration.interval}", delayed = "{wanaku.service.registration.delay-seconds}", delayUnit = TimeUnit.SECONDS)
    public void register() {
        final ForwardsService forwardsService = newService();

        ForwardReference reference = new ForwardReference();
        reference.setName(name);
        String actualAnnounceAddress = resolveRegistrationAddress(announceAddress);
        reference.setAddress(actualAnnounceAddress);

        try (Response ignored = forwardsService.addForward(reference)) {
            LOG.debugf("The service %s successfully registered.", reference.getName());
        } catch (WebApplicationException ex) {
            throw new WanakuException(ex);
        }
    }

    public void deregister() {
        final ForwardsService forwardsService = newService();

        ForwardReference reference = new ForwardReference();
        reference.setName(name);

        try (Response ignored = forwardsService.removeForward(reference)) {

        } catch (WebApplicationException ex) {
            throw new WanakuException(ex);
        }
    }

    private ForwardsService newService() {
        return QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(registrationUri))
                .build(ForwardsService.class);
    }


    void deregister(@Observes ShutdownEvent ev) {
        LOG.info("De-registering forward service");

        deregister();
    }

}
