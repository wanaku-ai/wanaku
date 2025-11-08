package ai.wanaku.core.capabilities.tool;

import ai.wanaku.api.discovery.RegistrationManager;
import ai.wanaku.api.exceptions.InvalidResponseTypeException;
import ai.wanaku.api.exceptions.NonConvertableResponseException;
import ai.wanaku.api.types.providers.ServiceType;
import ai.wanaku.core.capabilities.common.ConfigProvisionerLoader;
import ai.wanaku.core.capabilities.common.ConfigResourceLoader;
import ai.wanaku.core.capabilities.common.ServicesHelper;
import ai.wanaku.core.capabilities.config.WanakuServiceConfig;
import ai.wanaku.core.config.provider.api.ConfigProvisioner;
import ai.wanaku.core.config.provider.api.ConfigResource;
import ai.wanaku.core.config.provider.api.ProvisionedConfig;
import ai.wanaku.core.exchange.InvocationDelegate;
import ai.wanaku.core.exchange.ProvisionReply;
import ai.wanaku.core.exchange.ProvisionRequest;
import ai.wanaku.core.exchange.ToolInvokeReply;
import ai.wanaku.core.exchange.ToolInvokeRequest;
import io.quarkus.oidc.client.Tokens;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.List;
import org.jboss.logging.Logger;

/**
 * Base delegate class for tool invoker implementations.
 * <p>
 * This abstract class provides common functionality for invocation delegates
 * that handle tool invocation requests from the MCP router. It manages service registration,
 * configuration provisioning, and the lifecycle of tool invocation operations.
 * <p>
 * Subclasses must implement the abstract method to define how to convert
 * service responses into the expected format.
 *
 * @see InvocationDelegate
 */
public abstract class AbstractToolDelegate implements InvocationDelegate {
    private static final Logger LOG = Logger.getLogger(AbstractToolDelegate.class);

    @Inject
    WanakuServiceConfig config;

    @Inject
    Client client;

    @Inject
    Instance<Tokens> tokensInstance;

    private RegistrationManager registrationManager;

    /**
     * Default constructor for AbstractToolDelegate.
     */
    public AbstractToolDelegate() {}

    /**
     * Initializes the registration manager after construction.
     * <p>
     * This method is automatically called after dependency injection is complete.
     * It creates and configures the registration manager for this tool invoker service.
     */
    @PostConstruct
    public void init() {
        registrationManager =
                ServicesHelper.newRegistrationManager(config, ServiceType.TOOL_INVOKER, tokensInstance.get());
    }

    /**
     * Convert the response in whatever format it is to a String
     *
     * @param response the response
     * @return the response as a list of Strings
     * @throws InvalidResponseTypeException    if the response type is invalid (such as null)
     * @throws NonConvertableResponseException if the response cannot be converted
     */
    protected abstract List<String> coerceResponse(Object response)
            throws InvalidResponseTypeException, NonConvertableResponseException;

    @Override
    public ToolInvokeReply invoke(ToolInvokeRequest request) {
        try {
            ConfigResource configResource = ConfigResourceLoader.loadFromRequest(request);

            Object obj = client.exchange(request, configResource);

            List<String> response = coerceResponse(obj);

            ToolInvokeReply.Builder builder = ToolInvokeReply.newBuilder().setIsError(false);
            builder.addAllContent(response);

            registrationManager.lastAsSuccessful();
            return builder.build();
        } catch (InvalidResponseTypeException e) {
            String stateMsg = "Invalid response type from the consumer: " + e.getMessage();
            LOG.error(stateMsg, e);
            registrationManager.lastAsFail(stateMsg);
            return ToolInvokeReply.newBuilder()
                    .setIsError(true)
                    .addAllContent(List.of(stateMsg))
                    .build();
        } catch (NonConvertableResponseException e) {
            String stateMsg = "Non-convertable response from the consumer " + e.getMessage();
            LOG.error(stateMsg, e);
            registrationManager.lastAsFail(stateMsg);
            return ToolInvokeReply.newBuilder()
                    .setIsError(true)
                    .addAllContent(List.of(stateMsg))
                    .build();
        } catch (Exception e) {
            String stateMsg = findRootCause(e);
            LOG.error(stateMsg, e);
            registrationManager.lastAsFail(stateMsg);
            return ToolInvokeReply.newBuilder()
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

        return "Unable to invoke tool: " + rootCause;
    }

    public void register() {
        registrationManager.register();
    }

    public void deregister() {
        registrationManager.deregister();
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
}
