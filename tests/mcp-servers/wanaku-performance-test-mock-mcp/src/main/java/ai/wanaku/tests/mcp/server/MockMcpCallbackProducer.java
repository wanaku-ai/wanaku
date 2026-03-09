package ai.wanaku.tests.mcp.server;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;

import java.net.URI;
import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import ai.wanaku.capabilities.sdk.api.types.InputSchema;
import ai.wanaku.capabilities.sdk.api.types.Property;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;
import ai.wanaku.capabilities.sdk.api.types.ToolReference;
import ai.wanaku.core.forward.discovery.client.ForwardDiscoveryCallback;
import ai.wanaku.core.forward.discovery.client.ForwardRegistrationManager;
import ai.wanaku.core.services.api.ResourcesService;
import ai.wanaku.core.services.api.ToolsService;

public class MockMcpCallbackProducer {
    private static final Logger LOG = Logger.getLogger(MockMcpCallbackProducer.class);

    @ConfigProperty(name = "wanaku.service.registration.uri", defaultValue = "http://localhost:8080")
    String registrationUri;

    @ConfigProperty(name = "wanaku.mcp.service.name")
    String serviceName;

    @ApplicationScoped
    @Produces
    ForwardDiscoveryCallback createCallback() {
        final ToolsService toolsService = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(registrationUri))
                .build(ToolsService.class);

        final ResourcesService resourcesService = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(registrationUri))
                .build(ResourcesService.class);

        final ToolReference toolReference = new ToolReference();
        final ResourceReference resourceReference = new ResourceReference();

        return new ForwardDiscoveryCallback() {
            @Override
            public void onRegistration(ForwardRegistrationManager manager) {
                registerTool();
                registerResource();
            }

            @Override
            public void onDeregistration(ForwardRegistrationManager manager) {
                deregisterTool();
                deregisterResource();
            }

            private void registerTool() {
                toolReference.setName("mockTool");
                toolReference.setType(serviceName);
                toolReference.setId(serviceName);
                toolReference.setDescription("A mock tool that returns static data for testing");
                toolReference.setUri(serviceName + "://mock-tool");
                toolReference.setNamespace("public");

                InputSchema inputSchema = new InputSchema();
                inputSchema.setType("object");

                Property property = new Property();
                property.setType("string");
                property.setDescription("A name parameter");

                inputSchema.getProperties().put("name", property);
                inputSchema.setRequired(List.of("name"));
                toolReference.setInputSchema(inputSchema);

                try {
                    toolsService.add(toolReference);
                    LOG.info("Successfully registered mock tool");
                } catch (WebApplicationException e) {
                    LOG.warn("Failed to register mock tool", e);
                }
            }

            private void registerResource() {
                resourceReference.setName("mock-data");
                resourceReference.setType(serviceName);
                resourceReference.setId(serviceName);
                resourceReference.setDescription("A mock resource that returns static data for testing");
                resourceReference.setLocation("file:///mock/data");
                resourceReference.setNamespace("public");

                try {
                    resourcesService.expose(resourceReference);
                    LOG.info("Successfully registered mock resource");
                } catch (WebApplicationException e) {
                    LOG.warn("Failed to register mock resource", e);
                }
            }

            private void deregisterTool() {
                try {
                    toolsService.remove(toolReference.getName());
                } catch (WebApplicationException e) {
                    LOG.warn("Failed to deregister mock tool", e);
                }
            }

            private void deregisterResource() {
                try {
                    resourcesService.remove(resourceReference.getLocation());
                } catch (WebApplicationException e) {
                    LOG.warn("Failed to deregister mock resource", e);
                }
            }
        };
    }
}
