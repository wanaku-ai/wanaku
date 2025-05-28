package ai.wanaku.core.services.provider;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

import ai.wanaku.api.exceptions.InvalidResponseTypeException;
import ai.wanaku.api.exceptions.NonConvertableResponseException;
import ai.wanaku.api.exceptions.ResourceNotFoundException;
import ai.wanaku.core.exchange.ResourceAcquirerDelegate;
import ai.wanaku.core.exchange.ResourceReply;
import ai.wanaku.core.exchange.ResourceRequest;
import ai.wanaku.api.types.providers.ServiceTarget;
import ai.wanaku.core.service.discovery.client.DiscoveryService;
import ai.wanaku.core.service.discovery.util.DiscoveryUtil;
import ai.wanaku.core.services.config.WanakuProviderConfig;
import ai.wanaku.core.services.discovery.DefaultRegistrationManager;
import ai.wanaku.core.services.discovery.RegistrationManager;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import java.io.File;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private RegistrationManager registrationManager;

    @PostConstruct
    public void init() {
        LOG.infof("Using registration service at %s", config.registration().uri());
        DiscoveryService discoveryService = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(config.registration().uri()))
                .build(DiscoveryService.class);

        String service = ConfigProvider.getConfig().getConfigValue("wanaku.service.provider.name").getValue();
        ServiceTarget serviceTarget = newServiceTarget(service, serviceConfigurations());

        int retries = config.registration().retries();
        int waitSeconds = config.registration().retryWaitSeconds();

        final String serviceHome =
                config.serviceHome().replace("${user.home}", System.getProperty("user.home"))
                        + File.separator
                        + config.name();

        registrationManager = new DefaultRegistrationManager(discoveryService, serviceTarget, retries, waitSeconds, serviceHome);
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
        try {
            Map<String, String> parameters = mergeParameters(request);
            String uri = getEndpointUri(request, parameters);
            LOG.debugf("Acquiring resource: %s", uri);
            Object obj = consumer.consume(uri, request);

            List<String> response = coerceResponse(obj);

            registrationManager.lastAsSuccessful();
            return ResourceReply.newBuilder()
                        .setIsError(false)
                        .addAllContent(response).build();
        } catch (InvalidResponseTypeException e) {
            String stateMsg = "Invalid response type from the consumer: " + e.getMessage();
            LOG.errorf(e,stateMsg);
            registrationManager.lastAsFail(stateMsg);
            return ResourceReply.newBuilder()
                    .setIsError(true)
                    .addAllContent(List.of(stateMsg)).build();
        } catch (NonConvertableResponseException e) {
            String stateMsg = "Non-convertable response from the consumer " + e.getMessage();
            LOG.errorf(e,stateMsg);
            registrationManager.lastAsFail(stateMsg);
            return ResourceReply.newBuilder()
                    .setIsError(true)
                    .addAllContent(List.of(stateMsg)).build();
        } catch (Exception e) {
            String stateMsg = "Unable to read or acquire resource: " + e.getMessage();
            LOG.errorf(e, stateMsg);
            registrationManager.lastAsFail(stateMsg);
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

    @Override
    public void register() {
        registrationManager.register();
    }

    @Override
    public void deregister() {
        registrationManager.deregister();
    }

    private static ServiceTarget newServiceTarget(String service, Map<String, String> configurations) {
        String portStr = ConfigProvider.getConfig().getConfigValue("quarkus.grpc.server.port").getValue();
        final int port = Integer.parseInt(portStr);

        final String address = DiscoveryUtil.resolveRegistrationAddress();
        return ServiceTarget.provider(service, address, port, configurations);
    }
}
