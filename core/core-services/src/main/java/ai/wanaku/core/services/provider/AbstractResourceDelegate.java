package ai.wanaku.core.services.provider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ai.wanaku.api.exceptions.InvalidResponseTypeException;
import ai.wanaku.api.exceptions.NonConvertableResponseException;
import ai.wanaku.api.exceptions.ResourceNotFoundException;
import ai.wanaku.core.exchange.ResourceAcquirerDelegate;
import ai.wanaku.core.exchange.ResourceReply;
import ai.wanaku.core.exchange.ResourceRequest;
import ai.wanaku.core.mcp.providers.ServiceRegistry;
import ai.wanaku.core.mcp.providers.ServiceTarget;
import ai.wanaku.core.mcp.providers.ServiceType;
import ai.wanaku.core.service.discovery.util.DiscoveryUtil;
import ai.wanaku.core.services.config.WanakuProviderConfig;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import static ai.wanaku.core.services.common.ServicesHelper.waitAndRetry;

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
    Instance<ServiceRegistry> serviceRegistryInstance;

    ServiceRegistry serviceRegistry;

    @PostConstruct
    public void init() {
        serviceRegistry = serviceRegistryInstance.get();
        LOG.info("Using service registry implementation " + serviceRegistry.getClass().getName());
    }

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
        String service = ConfigProvider.getConfig().getConfigValue("wanaku.service.provider.name").getValue();

        try {
            Map<String, String> parameters = mergeParameters(request);
            String uri = getEndpointUri(request, parameters);
            LOG.debugf("Acquiring resource: %s", uri);
            Object obj = consumer.consume(uri, request);

            List<String> response = coerceResponse(obj);

            try {
                return ResourceReply.newBuilder()
                        .setIsError(false)
                        .addAllContent(response).build();
            } finally {
                serviceRegistry.saveState(service, true, null);
            }
        } catch (InvalidResponseTypeException e) {
            String stateMsg = "Invalid response type from the consumer: " + e.getMessage();
            LOG.errorf(e,stateMsg);
            serviceRegistry.saveState(service, false, stateMsg);
            return ResourceReply.newBuilder()
                    .setIsError(true)
                    .addAllContent(List.of(stateMsg)).build();
        } catch (NonConvertableResponseException e) {
            String stateMsg = "Non-convertable response from the consumer " + e.getMessage();
            LOG.errorf(e,stateMsg);
            serviceRegistry.saveState(service, false, stateMsg);
            return ResourceReply.newBuilder()
                    .setIsError(true)
                    .addAllContent(List.of(stateMsg)).build();
        } catch (Exception e) {
            String stateMsg = "Unable to read or acquire resource: " + e.getMessage();
            LOG.errorf(e, stateMsg);
            serviceRegistry.saveState(service, false, stateMsg);
            return ResourceReply.newBuilder()
                    .setIsError(true)
                    .addAllContent(List.of(stateMsg)).build();
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

    private void tryRegistering(String service, String address, int port) {
        int retries = config.registration().retries();
        boolean registered = false;
        do {
            try {
                serviceRegistry.register(ServiceTarget.provider(service, address, port), serviceConfigurations());
                registered = true;
            } catch (Exception e) {
                int waitSeconds = config.registration().retryWaitSeconds();
                retries = waitAndRetry(service, e, retries, waitSeconds);
            }
        } while (!registered && (retries > 0));
    }

    @Override
    public void register() {
        String service = ConfigProvider.getConfig().getConfigValue("wanaku.service.provider.name").getValue();
        String port = ConfigProvider.getConfig().getConfigValue("quarkus.grpc.server.port").getValue();

        final String address = DiscoveryUtil.resolveRegistrationAddress();
        LOG.debugf("Registering resource service %s with address %s:%s", service, address, port);

        tryRegistering(service, address, Integer.parseInt(port));
    }

    @Override
    public void deregister(String service, String address, int port) {
        serviceRegistry.deregister(service, ServiceType.RESOURCE_PROVIDER);
    }
}
