package ai.wanaku.tests.mcp.server;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import ai.wanaku.capabilities.sdk.api.types.ForwardReference;
import ai.wanaku.core.forward.discovery.client.ForwardDiscoveryCallback;
import ai.wanaku.core.forward.discovery.client.ForwardRegistrationManager;
import ai.wanaku.core.services.api.ForwardsService;

public class MockMcpCallbackProducer {
    private static final Logger LOG = Logger.getLogger(MockMcpCallbackProducer.class);

    @ConfigProperty(name = "wanaku.service.registration.uri", defaultValue = "http://localhost:8080")
    String registrationUri;

    @ConfigProperty(name = "wanaku.mcp.service.name")
    String serviceName;

    @ConfigProperty(name = "wanaku.mcp.service.namespace", defaultValue = "")
    String namespace;

    @ConfigProperty(name = "wanaku.service.registration.mcp-forward-address")
    String forwardAddress;

    @ApplicationScoped
    @Produces
    ForwardDiscoveryCallback createCallback() {
        final ForwardsService forwardsService = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(registrationUri))
                .build(ForwardsService.class);

        final ForwardReference forwardReference = new ForwardReference();

        return new ForwardDiscoveryCallback() {
            @Override
            public void onRegistration(ForwardRegistrationManager manager) {
                forwardReference.setName(serviceName);
                forwardReference.setNamespace(namespace);
                forwardReference.setAddress(forwardAddress);

                try (Response ignored = forwardsService.addForward(forwardReference)) {
                    LOG.infof("Successfully registered mock MCP server as forward: %s", serviceName);
                } catch (WebApplicationException e) {
                    LOG.warn("Failed to register mock MCP server as forward", e);
                }
            }

            @Override
            public void onDeregistration(ForwardRegistrationManager manager) {
                try (Response ignored = forwardsService.removeForward(forwardReference)) {
                    LOG.infof("Successfully deregistered mock MCP server forward: %s", serviceName);
                } catch (WebApplicationException e) {
                    LOG.warn("Failed to deregister mock MCP server forward", e);
                }
            }
        };
    }
}
