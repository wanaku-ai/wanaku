package ai.wanaku.provider.performance.file;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import org.jboss.logging.Logger;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import ai.wanaku.capabilities.sdk.api.discovery.DiscoveryCallback;
import ai.wanaku.capabilities.sdk.api.discovery.RegistrationManager;
import ai.wanaku.capabilities.sdk.api.exceptions.InvalidResponseTypeException;
import ai.wanaku.capabilities.sdk.api.exceptions.NonConvertableResponseException;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;
import ai.wanaku.capabilities.sdk.config.provider.api.ConfigResource;
import ai.wanaku.core.capabilities.config.WanakuServiceConfig;
import ai.wanaku.core.capabilities.provider.AbstractResourceDelegate;
import ai.wanaku.core.exchange.v1.ResourceRequest;
import ai.wanaku.core.services.api.ResourcesService;

@ApplicationScoped
public class PerformanceFileResourceDelegate extends AbstractResourceDelegate {
    private static final Logger LOG = Logger.getLogger(PerformanceFileResourceDelegate.class);

    @Inject
    WanakuServiceConfig config;

    @Override
    protected void initializeRegistrationManager(RegistrationManager registrationManager) {
        super.initializeRegistrationManager(registrationManager);

        registrationManager.addCallBack(new DiscoveryCallback() {

            final ResourcesService resourcesService = QuarkusRestClientBuilder.newBuilder()
                    .baseUri(URI.create(config.registration().uri()))
                    .build(ResourcesService.class);

            final ResourceReference resourceReference = new ResourceReference();

            @Override
            public void onPing(RegistrationManager registrationManager, ServiceTarget serviceTarget, int i) {}

            @Override
            public void onRegistration(RegistrationManager registrationManager, ServiceTarget serviceTarget) {
                resourceReference.setName("in-memory-file for testing");
                resourceReference.setType(config.name());
                resourceReference.setId(config.name());
                resourceReference.setDescription("Static file for performance tests");
                resourceReference.setLocation("in-memory-file.txt");
                resourceReference.setNamespace("public");

                try {
                    resourcesService.expose(resourceReference);
                } catch (WebApplicationException e) {
                    LOG.warn(e);
                }
            }

            @Override
            public void onDeregistration(RegistrationManager registrationManager, ServiceTarget serviceTarget, int i) {
                try {
                    resourcesService.remove(resourceReference.getLocation());
                } catch (WebApplicationException e) {
                    LOG.warn(e);
                }
            }
        });
    }

    @Override
    protected String getEndpointUri(ResourceRequest request, ConfigResource configResource) {
        LOG.info("Converting endpoint ...");
        return request.getLocation();
    }

    @Override
    protected List<String> coerceResponse(Object response)
            throws InvalidResponseTypeException, NonConvertableResponseException {
        if (response == null) {
            throw new InvalidResponseTypeException("Invalid response type from the consumer: null");
        }

        return Collections.singletonList(response.toString());
    }
}
