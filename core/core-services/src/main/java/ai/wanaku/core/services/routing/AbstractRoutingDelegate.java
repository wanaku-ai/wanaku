package ai.wanaku.core.services.routing;

import java.util.Map;

import jakarta.inject.Inject;

import ai.wanaku.api.exceptions.InvalidResponseTypeException;
import ai.wanaku.api.exceptions.NonConvertableResponseException;
import ai.wanaku.core.exchange.InvocationDelegate;
import ai.wanaku.core.exchange.ToolInvokeReply;
import ai.wanaku.core.exchange.ToolInvokeRequest;
import ai.wanaku.core.services.config.WanakuRoutingConfig;
import org.jboss.logging.Logger;

/**
 * Base delegate class
 */
public abstract class AbstractRoutingDelegate implements InvocationDelegate {
    private static final Logger LOG = Logger.getLogger(AbstractRoutingDelegate.class);

    @Inject
    WanakuRoutingConfig config;

    @Inject
    Client client;


    /**
     * Convert the response in whatever format it is to a String
     *
     * @param response the response
     * @return the response as a String
     * @throws InvalidResponseTypeException if the response type is invalid (such as null)
     * @throws NonConvertableResponseException if the response cannot be converted
     */
    protected abstract String coerceResponse(Object response)
            throws InvalidResponseTypeException, NonConvertableResponseException;


    @Override
    public ToolInvokeReply invoke(ToolInvokeRequest request) {
        try {
            Object obj = client.exchange(request);

            String response = coerceResponse(obj);

            return ToolInvokeReply.newBuilder()
                    .setIsError(false)
                    .setContent(response).build();
        } catch (InvalidResponseTypeException e) {
            LOG.errorf("Invalid response type from the consumer: %s", e.getMessage());
            return ToolInvokeReply.newBuilder()
                    .setIsError(true)
                    .setContent("Invalid response type from the consumer: " + e.getMessage()).build();
        } catch (NonConvertableResponseException e) {
            LOG.errorf("Non-convertable response from the consumer: %s", e.getMessage());
            return ToolInvokeReply.newBuilder()
                    .setIsError(true)
                    .setContent("Non-convertable response from the consumer " + e.getMessage()).build();
        } catch (Exception e) {
            LOG.errorf("Unable to read file: %s", e.getMessage(), e);
            return ToolInvokeReply.newBuilder()
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
}
