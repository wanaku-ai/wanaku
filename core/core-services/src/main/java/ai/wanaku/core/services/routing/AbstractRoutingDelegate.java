package ai.wanaku.core.services.routing;

import jakarta.inject.Inject;

import ai.wanaku.api.exceptions.InvalidResponseTypeException;
import ai.wanaku.api.exceptions.NonConvertableResponseException;
import ai.wanaku.core.exchange.InvocationDelegate;
import ai.wanaku.core.exchange.ToolInvokeReply;
import ai.wanaku.core.exchange.ToolInvokeRequest;
import ai.wanaku.core.mcp.providers.ServiceRegistry;
import ai.wanaku.core.mcp.providers.ServiceTarget;
import ai.wanaku.core.services.config.WanakuRoutingConfig;
import java.util.List;
import java.util.Map;
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

    @Inject
    ServiceRegistry serviceRegistry;


    /**
     * Convert the response in whatever format it is to a String
     *
     * @param response the response
     * @return the response as a list of Strings
     * @throws InvalidResponseTypeException if the response type is invalid (such as null)
     * @throws NonConvertableResponseException if the response cannot be converted
     */
    protected abstract List<String> coerceResponse(Object response)
            throws InvalidResponseTypeException, NonConvertableResponseException;


    @Override
    public ToolInvokeReply invoke(ToolInvokeRequest request) {
        try {
            Object obj = client.exchange(request);

            List<String> response = coerceResponse(obj);

            ToolInvokeReply.Builder builder = ToolInvokeReply.newBuilder().setIsError(false);
            builder.addAllContent(response);

            return builder.build();
        } catch (InvalidResponseTypeException e) {
            LOG.errorf("Invalid response type from the consumer: %s", e.getMessage());
            return ToolInvokeReply.newBuilder()
                    .setIsError(true)
                    .addAllContent(List.of("Invalid response type from the consumer: " + e.getMessage())).build();
        } catch (NonConvertableResponseException e) {
            LOG.errorf("Non-convertable response from the consumer: %s", e.getMessage());
            return ToolInvokeReply.newBuilder()
                    .setIsError(true)
                    .addAllContent(List.of("Non-convertable response from the consumer " + e.getMessage())).build();
        } catch (Exception e) {
            LOG.errorf(e,"Unable to invoke tool: %s", e.getMessage(), e);
            return ToolInvokeReply.newBuilder()
                    .setIsError(true)
                    .addAllContent(List.of(e.getMessage())).build();
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

    @Override
    public void register(String service, String address, int port) {
        serviceRegistry.register(ServiceTarget.toolInvoker(service, address, port), serviceConfigurations());
    }

    @Override
    public void deregister(String service, String address, int port) {
        serviceRegistry.deregister(service);
    }
}
