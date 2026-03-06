package ai.wanaku.tool.performance.noop;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;

import java.net.URI;
import java.util.List;
import org.jboss.logging.Logger;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import ai.wanaku.capabilities.sdk.api.discovery.DiscoveryCallback;
import ai.wanaku.capabilities.sdk.api.discovery.RegistrationManager;
import ai.wanaku.capabilities.sdk.api.exceptions.InvalidResponseTypeException;
import ai.wanaku.capabilities.sdk.api.exceptions.NonConvertableResponseException;
import ai.wanaku.capabilities.sdk.api.types.InputSchema;
import ai.wanaku.capabilities.sdk.api.types.Property;
import ai.wanaku.capabilities.sdk.api.types.ToolReference;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;
import ai.wanaku.core.capabilities.config.WanakuServiceConfig;
import ai.wanaku.core.capabilities.tool.AbstractToolDelegate;
import ai.wanaku.core.services.api.ToolsService;

@ApplicationScoped
public class PerformanceNoopDelegate extends AbstractToolDelegate {
    private static final Logger LOG = Logger.getLogger(PerformanceNoopDelegate.class);

    @Inject
    WanakuServiceConfig config;

    @Override
    protected void initializeRegistrationManager(RegistrationManager registrationManager) {
        super.initializeRegistrationManager(registrationManager);

        registrationManager.addCallBack(new DiscoveryCallback() {

            final ToolsService toolsService = QuarkusRestClientBuilder.newBuilder()
                    .baseUri(URI.create(config.registration().uri()))
                    .build(ToolsService.class);

            final ToolReference toolReference = new ToolReference();

            @Override
            public void onPing(RegistrationManager registrationManager, ServiceTarget serviceTarget, int i) {}

            @Override
            public void onRegistration(RegistrationManager registrationManager, ServiceTarget serviceTarget) {
                toolReference.setName("test-tool");
                toolReference.setType(config.name());
                toolReference.setId(config.name());
                toolReference.setDescription("Static tool for performance tests");
                toolReference.setUri(config.name() + "://static-tool");
                toolReference.setNamespace("public");

                InputSchema inputSchema = new InputSchema();
                inputSchema.setType("object");

                Property property = new Property();
                property.setType("string");
                property.setDescription("sample parameter");

                inputSchema.getProperties().put("name", property);

                toolReference.setInputSchema(inputSchema);
                inputSchema.setRequired(List.of());

                try {
                    toolsService.add(toolReference);
                } catch (WebApplicationException e) {
                    LOG.warn(e);
                }
            }

            @Override
            public void onDeregistration(RegistrationManager registrationManager, ServiceTarget serviceTarget, int i) {
                try {
                    toolsService.remove(toolReference.getName());
                } catch (WebApplicationException e) {
                    LOG.warn(e);
                }
            }
        });
    }

    @Override
    protected List<String> coerceResponse(Object response)
            throws InvalidResponseTypeException, NonConvertableResponseException {
        if (response == null) {
            throw new InvalidResponseTypeException("Invalid response type from the consumer: null");
        }

        return List.of(response.toString());
    }
}
