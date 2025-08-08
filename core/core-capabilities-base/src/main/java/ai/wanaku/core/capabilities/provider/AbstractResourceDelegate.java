package ai.wanaku.core.capabilities.provider;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import ai.wanaku.api.exceptions.InvalidResponseTypeException;
import ai.wanaku.api.exceptions.NonConvertableResponseException;
import ai.wanaku.api.exceptions.ResourceNotFoundException;
import ai.wanaku.api.types.providers.ServiceType;
import ai.wanaku.core.capabilities.common.ConfigProvisionerLoader;
import ai.wanaku.core.capabilities.common.ConfigResourceLoader;
import ai.wanaku.core.capabilities.common.ServicesHelper;
import ai.wanaku.core.capabilities.config.WanakuServiceConfig;
import ai.wanaku.api.discovery.RegistrationManager;
import ai.wanaku.core.config.provider.api.ConfigProvisioner;
import ai.wanaku.core.config.provider.api.ConfigResource;
import ai.wanaku.core.config.provider.api.ProvisionedConfig;
import ai.wanaku.core.exchange.ProvisionReply;
import ai.wanaku.core.exchange.ProvisionRequest;
import ai.wanaku.core.exchange.ResourceAcquirerDelegate;
import ai.wanaku.core.exchange.ResourceReply;
import ai.wanaku.core.exchange.ResourceRequest;

import ai.wanaku.core.security.SecurityHelper;
import io.quarkus.oidc.client.Tokens;
import java.util.List;

import org.jboss.logging.Logger;

/**
 * Base delegate class
 */
public abstract class AbstractResourceDelegate implements ResourceAcquirerDelegate {
    private static final Logger LOG = Logger.getLogger(AbstractResourceDelegate.class);

    @Inject
    WanakuServiceConfig config;

    @Inject
    ResourceConsumer consumer;

    @Inject
    Instance<Tokens> tokensInstance;

    private RegistrationManager registrationManager;

    @PostConstruct
    public void init() {
        final String accessToken = retrieveAccessToken();

        registrationManager = ServicesHelper.newRegistrationManager(config, ServiceType.RESOURCE_PROVIDER, accessToken);
    }

    private String retrieveAccessToken() {
        if (SecurityHelper.isAuthEnabled()) {
            return ServicesHelper.retrieveAccessToken(tokensInstance);
        } else {
            return null;
        }
    }

    /**
     * Gets the endpoint URI.
     * Here you build the Camel URI based on the request parameters.
     * The parameters are already merged w/ the requested ones, but feel free to override or
     * add more if necessary.
     * @param request the request
     * @return the URI as a string
     */
    protected abstract String getEndpointUri(ResourceRequest request, ConfigResource configResource);

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
            ConfigResource configResource = ConfigResourceLoader.loadFromRequest(request);

            String uri = getEndpointUri(request, configResource);
            LOG.debugf("Acquiring resource: %s", uri);
            Object obj = consumer.consume(uri, request);

            List<String> response = coerceResponse(obj);

            registrationManager.lastAsSuccessful();
            return ResourceReply.newBuilder()
                        .setIsError(false)
                        .addAllContent(response).build();
        } catch (InvalidResponseTypeException e) {
            String stateMsg = "Invalid response type from the consumer: " + e.getMessage();
            LOG.error(stateMsg, e);
            registrationManager.lastAsFail(stateMsg);
            return ResourceReply.newBuilder()
                    .setIsError(true)
                    .addAllContent(List.of(stateMsg)).build();
        } catch (NonConvertableResponseException e) {
            String stateMsg = "Non-convertable response from the consumer " + e.getMessage();
            LOG.error(stateMsg, e);
            registrationManager.lastAsFail(stateMsg);
            return ResourceReply.newBuilder()
                    .setIsError(true)
                    .addAllContent(List.of(stateMsg)).build();
        } catch (Exception e) {
            String stateMsg = "Unable to read or acquire resource: " + e.getMessage();
            LOG.error(stateMsg, e);;
            registrationManager.lastAsFail(stateMsg);
            return ResourceReply.newBuilder()
                    .setIsError(true)
                    .addAllContent(List.of(stateMsg)).build();
        }
    }

    @Override
    public ProvisionReply provision(ProvisionRequest request) {
        ConfigProvisioner provisioner = ConfigProvisionerLoader.newConfigProvisioner(request, config);
        final ProvisionedConfig provision = ConfigProvisionerLoader.provision(request, provisioner);

        return ProvisionReply
                .newBuilder()
                .putAllProperties(ServicesHelper.buildPropertiesMap(config))
                .setConfigurationUri(provision.configurationsUri().toString())
                .setSecretUri(provision.secretsUri().toString())
                .build();
    }

    @Override
    public void register() {
        registrationManager.register();
    }

    @Override
    public void deregister() {
        registrationManager.deregister();
    }
}
