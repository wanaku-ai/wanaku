package ai.wanaku.core.capabilities.provider;

import ai.wanaku.capabilities.sdk.api.discovery.RegistrationManager;
import ai.wanaku.capabilities.sdk.api.exceptions.InvalidResponseTypeException;
import ai.wanaku.capabilities.sdk.api.exceptions.NonConvertableResponseException;
import ai.wanaku.capabilities.sdk.api.exceptions.ResourceNotFoundException;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceType;
import ai.wanaku.capabilities.sdk.config.provider.api.ConfigProvisioner;
import ai.wanaku.capabilities.sdk.config.provider.api.ConfigResource;
import ai.wanaku.capabilities.sdk.config.provider.api.ProvisionedConfig;
import ai.wanaku.core.capabilities.common.ConfigProvisionerLoader;
import ai.wanaku.core.capabilities.common.ConfigResourceLoader;
import ai.wanaku.core.capabilities.common.ServicesHelper;
import ai.wanaku.core.capabilities.config.WanakuServiceConfig;
import ai.wanaku.core.exchange.ProvisionReply;
import ai.wanaku.core.exchange.ProvisionRequest;
import ai.wanaku.core.exchange.ResourceAcquirerDelegate;
import ai.wanaku.core.exchange.ResourceReply;
import ai.wanaku.core.exchange.ResourceRequest;
import io.quarkus.oidc.client.Tokens;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.List;
import org.jboss.logging.Logger;

/**
 * Base delegate class for resource acquirer implementations.
 * <p>
 * This abstract class provides common functionality for resource acquirer delegates
 * that handle resource requests from the MCP router. It manages service registration,
 * configuration provisioning, and the lifecycle of resource acquisition operations.
 * <p>
 * Subclasses must implement the abstract methods to define how to:
 * <ul>
 *   <li>Build endpoint URIs from resource requests</li>
 *   <li>Convert service responses into the expected format</li>
 * </ul>
 *
 * @see ResourceAcquirerDelegate
 */
public abstract class AbstractResourceDelegate implements ResourceAcquirerDelegate {
    private static final Logger LOG = Logger.getLogger(AbstractResourceDelegate.class);
    private static final String SERVICE_TYPE_RESOURCE_PROVIDER = ServiceType.RESOURCE_PROVIDER.asValue();

    @Inject
    WanakuServiceConfig config;

    @Inject
    ResourceConsumer consumer;

    @Inject
    Instance<Tokens> tokensInstance;

    private RegistrationManager registrationManager;

    /**
     * Default constructor for AbstractResourceDelegate.
     */
    public AbstractResourceDelegate() {}

    /**
     * Initializes the registration manager after construction.
     * <p>
     * This method is automatically called after dependency injection is complete.
     * It creates and configures the registration manager for this resource provider service.
     */
    @PostConstruct
    public void init() {
        registrationManager =
                ServicesHelper.newRegistrationManager(config, SERVICE_TYPE_RESOURCE_PROVIDER, tokensInstance.get());
    }

    /**
     * Gets the endpoint URI.
     * Here you build the Camel URI based on the request parameters.
     * The parameters are already merged w/ the requested ones, but feel free to override or
     * add more if necessary.
     * @param request the request
     * @param configResource the configuration resource containing settings for this resource
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
                    .addAllContent(response)
                    .build();
        } catch (InvalidResponseTypeException e) {
            String stateMsg = "Invalid response type from the consumer: " + e.getMessage();
            LOG.error(stateMsg, e);
            registrationManager.lastAsFail(stateMsg);
            return ResourceReply.newBuilder()
                    .setIsError(true)
                    .addAllContent(List.of(stateMsg))
                    .build();
        } catch (NonConvertableResponseException e) {
            String stateMsg = "Non-convertable response from the consumer " + e.getMessage();
            LOG.error(stateMsg, e);
            registrationManager.lastAsFail(stateMsg);
            return ResourceReply.newBuilder()
                    .setIsError(true)
                    .addAllContent(List.of(stateMsg))
                    .build();
        } catch (Exception e) {
            String stateMsg = findRootCause(e);
            LOG.error(stateMsg, e);
            registrationManager.lastAsFail(stateMsg);
            return ResourceReply.newBuilder()
                    .setIsError(true)
                    .addAllContent(List.of(stateMsg))
                    .build();
        }
    }

    private static String findRootCause(Exception e) {
        String rootCause;
        if (e.getCause() != null) {
            rootCause = e.getCause().getMessage();
        } else {
            rootCause = e.getMessage();
        }

        return "Unable to read or acquire resource: " + rootCause;
    }

    @Override
    public ProvisionReply provision(ProvisionRequest request) {
        ConfigProvisioner provisioner = ConfigProvisionerLoader.newConfigProvisioner(request, config);
        final ProvisionedConfig provision = ConfigProvisionerLoader.provision(request, provisioner);

        return ProvisionReply.newBuilder()
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
