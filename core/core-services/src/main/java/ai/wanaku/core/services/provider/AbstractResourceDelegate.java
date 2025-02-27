package ai.wanaku.core.services.provider;

import java.util.HashMap;
import java.util.Map;

import jakarta.inject.Inject;

import ai.wanaku.api.exceptions.InvalidResponseTypeException;
import ai.wanaku.api.exceptions.NonConvertableResponseException;
import ai.wanaku.core.exchange.ResourceAcquirerDelegate;
import ai.wanaku.core.exchange.ResourceReply;
import ai.wanaku.core.exchange.ResourceRequest;
import ai.wanaku.core.services.config.WanakuProviderConfig;
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
        return config.service().configurations();
    }

    @Override
    public Map<String, String> credentialsConfigurations() {
        return config.credentials().configurations();
    }

    protected Map<String, String> mergeParameters(ResourceRequest request) {
        Map<String, String> defaults = config.service().defaults();
        Map<String, String> requestParams = new HashMap<>(request.getParamsMap());

        for (Map.Entry<String, String> entry : defaults.entrySet()) {
            requestParams.putIfAbsent(entry.getKey(), entry.getValue());
        }
        return requestParams;
    }
}
