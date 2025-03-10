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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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

    @Inject
    ScheduledExecutorService executor;

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

    private void tryRegistering(String service, String address, int port) {
        int retries = config.registerRetries();
        boolean registered = false;
        do {
            try {
                serviceRegistry.register(ServiceTarget.toolInvoker(service, address, port), serviceConfigurations());
                registered = true;
            } catch (Exception e) {
                retries = waitAndRetry(service, e, retries);
            }
        } while (!registered && (retries > 0));
    }

    private int waitAndRetry(String service, Exception e, int retries) {
        retries--;
        if (retries == 0) {
            LOG.errorf(e, "Failed to register service %s: %s. No more retries left", service, e.getMessage());
            return 0;
        } else {
            LOG.warnf("Failed to register service %s: %s. Retries left: %d", service, e.getMessage(), retries);
        }
        try {
            int waitSeconds = config.registerRetryWaitSeconds();
            Thread.sleep(waitSeconds * 1000);
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
        return retries;
    }

    @Override
    public void register(String service, String address, int port) {
        executor.schedule(() -> tryRegistering(service, address, port), config.registerDelaySeconds(), TimeUnit.SECONDS);
    }

    @Override
    public void deregister(String service, String address, int port) {
        serviceRegistry.deregister(service);
    }
}
