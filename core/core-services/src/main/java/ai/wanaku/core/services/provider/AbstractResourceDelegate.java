package ai.wanaku.core.services.provider;

import jakarta.inject.Inject;

import ai.wanaku.api.exceptions.InvalidResponseTypeException;
import ai.wanaku.api.exceptions.NonConvertableResponseException;
import ai.wanaku.api.exceptions.ResourceNotFoundException;
import ai.wanaku.core.exchange.ResourceAcquirerDelegate;
import ai.wanaku.core.exchange.ResourceReply;
import ai.wanaku.core.exchange.ResourceRequest;
import ai.wanaku.core.mcp.providers.ServiceRegistry;
import ai.wanaku.core.mcp.providers.ServiceTarget;
import ai.wanaku.core.service.discovery.util.DiscoveryUtil;
import ai.wanaku.core.services.config.WanakuProviderConfig;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.ComponentModel;
import org.eclipse.microprofile.config.ConfigProvider;
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

    @Inject
    ServiceRegistry serviceRegistry;

    @Inject
    ScheduledExecutorService executor;

    /**
     * Gets the endpoint URI.
     * Here you build the Camel URI based on the request parameters.
     * The parameters are already merged w/ the requested ones, but feel free to override or
     * add more if necessary.
     * @param request the request
     * @param parameters the merged (between config and defaults) request parameters
     * @return the URI as a string
     */
    protected abstract String getEndpointUri(ResourceRequest request, Map<String, String> parameters);

    /**
     * Convert the response in whatever format it is to a String
     * @param response the response
     * @return the response as a String
     * @throws InvalidResponseTypeException if the response type is invalid (such as null)
     * @throws NonConvertableResponseException if the response cannot be converted
     */
    protected abstract List<String> coerceResponse(Object response)
            throws InvalidResponseTypeException, NonConvertableResponseException, ResourceNotFoundException;

    @Override
    public ResourceReply acquire(ResourceRequest request) {
        try {
            Map<String, String> parameters = mergeParameters(request);
            String uri = getEndpointUri(request, parameters);
            LOG.debugf("Acquiring resource: %s", uri);
            Object obj = consumer.consume(uri, request);

            List<String> response = coerceResponse(obj);

            return ResourceReply.newBuilder()
                    .setIsError(false)
                    .addAllContent(response).build();
        } catch (InvalidResponseTypeException e) {
            LOG.errorf("Invalid response type from the consumer: %s", e.getMessage());
            return ResourceReply.newBuilder()
                    .setIsError(true)
                    .addAllContent(List.of("Invalid response type from the consumer: " + e.getMessage())).build();
        } catch (NonConvertableResponseException e) {
            LOG.errorf("Non-convertable response from the consumer: %s", e.getMessage());
            return ResourceReply.newBuilder()
                    .setIsError(true)
                    .addAllContent(List.of("Non-convertable response from the consumer " + e.getMessage())).build();
        } catch (Exception e) {
            LOG.errorf(e, "Unable to read resource: %s", e.getMessage(), e);
            return ResourceReply.newBuilder()
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

    protected Map<String, String> mergeParameters(ResourceRequest request) {
        Map<String, String> defaults = config.service().defaults();
        Map<String, String> requestParams = new HashMap<>(request.getParamsMap());

        addToRequestParams(requestParams, defaults);
        addToRequestParams(requestParams, request.getCredentialsConfigurationsMap());
        addToRequestParams(requestParams, request.getServiceConfigurationsMap());

        return requestParams;
    }

    private void addToRequestParams(Map<String, String> requestParams, Map<String, String> map) {
        if (map != null) {
            for (Map.Entry<String, String> entry : map.entrySet()) {
                requestParams.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
    }

    protected Map<String, String> componentOptions(String name, Map<String, String> opt) {
        CamelCatalog catalog = new DefaultCamelCatalog(true);

        final ComponentModel componentModel = catalog.componentModel(name);
        final List<ComponentModel.EndpointOptionModel> options = componentModel.getEndpointParameterOptions();
        for (BaseOptionModel option : options) {
            if (option.getLabel().contains("consumer") || option.getLabel().contains("common") ||
                    option.getGroup().contains("common") || option.getLabel().contains("security")) {
                opt.put(option.getName(), option.getDescription());
            }
        }

        return opt;
    }

    private void tryRegistering(String service, String address, int port) {
        int retries = config.registration().retries();
        boolean registered = false;
        do {
            try {
                serviceRegistry.register(ServiceTarget.provider(service, address, port), serviceConfigurations());
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
            int waitSeconds = config.registration().retryWaitSeconds();
            Thread.sleep(waitSeconds * 1000L);
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
        return retries;
    }

    @Override
    public void register() {
        String service = ConfigProvider.getConfig().getConfigValue("wanaku.service.provider.name").getValue();
        String port = ConfigProvider.getConfig().getConfigValue("quarkus.grpc.server.port").getValue();

        LOG.infof("Registering resource service %s", service);
        tryRegistering(service, DiscoveryUtil.resolveRegistrationAddress(), Integer.parseInt(port));
    }

    @Override
    public void deregister(String service, String address, int port) {
        serviceRegistry.deregister(service);
    }
}
