package ai.wanaku.core.services.provider;

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import ai.wanaku.api.exceptions.InvalidResponseTypeException;
import ai.wanaku.api.exceptions.NonConvertableResponseException;
import ai.wanaku.core.exchange.ResourceAcquirerDelegate;
import ai.wanaku.core.exchange.ResourceReply;
import ai.wanaku.core.exchange.ResourceRequest;
import ai.wanaku.core.services.config.WanakuProviderConfig;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.ComponentModel;
import org.jboss.logging.Logger;

/**
 * Base delegate class
 */
public abstract class AbstractResourceDelegate implements ResourceAcquirerDelegate {
    private static final Logger LOG = Logger.getLogger(AbstractResourceDelegate.class);

    @Inject
    WanakuProviderConfig config;

    @Inject
    ResourceConsumer consumer;

    /**
     * Gets the endpoint URI
     * @param request the request
     * @return the URI as a string
     */
    protected abstract String getEndpointUri(ResourceRequest request);

    /**
     * Convert the response in whatever format it is to a String
     * @param response the response
     * @return the response as a String
     * @throws InvalidResponseTypeException if the response cannot be converted
     */
    protected abstract String coerceResponse(Object response)
            throws InvalidResponseTypeException, NonConvertableResponseException;

    @Override
    public ResourceReply acquire(ResourceRequest request) {
        try {
            String uri = getEndpointUri(request);
            LOG.debugf("Acquiring resource: %s", uri);
            Object obj = consumer.consume(uri);

            String response = coerceResponse(obj);

            return ResourceReply.newBuilder()
                    .setIsError(false)
                    .setContent(response).build();
        } catch (InvalidResponseTypeException e) {
            LOG.errorf("Invalid response type from the consumer: %s", e.getMessage());
            return ResourceReply.newBuilder()
                    .setIsError(true)
                    .setContent("Invalid response type from the consumer: " + e.getMessage()).build();
        } catch (NonConvertableResponseException e) {
            LOG.errorf("Non-convertable response from the consumer: %s", e.getMessage());
            return ResourceReply.newBuilder()
                    .setIsError(true)
                    .setContent("Non-convertable response from the consumer " + e.getMessage()).build();
        } catch (Exception e) {
            LOG.errorf("Unable to read file: %s", e.getMessage(), e);
            return ResourceReply.newBuilder()
                    .setIsError(true)
                    .setContent(e.getMessage()).build();
        }
    }

    @Override
    public Map<String, String> serviceConfigurations() {
        Map<String, String> configurations =  config.service().configurations();

        return componentOptions(configurations);
    }

    @Override
    public Map<String, String> credentialsConfigurations() {
        return config.credentials().configurations();
    }

    protected Map<String, String> componentOptions(Map<String, String> opt) {
        CamelCatalog catalog = new DefaultCamelCatalog(true);

        String name = config.name();
        final ComponentModel componentModel = catalog.componentModel(name);
        final List<ComponentModel.EndpointOptionModel> options = componentModel.getEndpointParameterOptions();
        for (BaseOptionModel option : options) {
            if (option.getLabel().contains("consumer")) {
                opt.put(option.getName(), option.getDescription());
            }
        }

        return opt;
    }
}
