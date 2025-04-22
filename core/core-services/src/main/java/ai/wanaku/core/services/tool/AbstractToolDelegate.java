package ai.wanaku.core.services.tool;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import ai.wanaku.api.exceptions.InvalidResponseTypeException;
import ai.wanaku.api.exceptions.NonConvertableResponseException;
import ai.wanaku.core.exchange.InvocationDelegate;
import ai.wanaku.core.exchange.PropertySchema;
import ai.wanaku.core.exchange.ToolInvokeReply;
import ai.wanaku.core.exchange.ToolInvokeRequest;
import ai.wanaku.core.mcp.providers.ServiceRegistry;
import ai.wanaku.core.mcp.providers.ServiceTarget;
import ai.wanaku.core.mcp.providers.ServiceType;
import ai.wanaku.core.service.discovery.util.DiscoveryUtil;
import ai.wanaku.core.services.config.WanakuServiceConfig;
import ai.wanaku.core.services.config.WanakuToolConfig;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import static ai.wanaku.core.services.common.ServicesHelper.waitAndRetry;

/**
 * Base delegate class
 */
public abstract class AbstractToolDelegate implements InvocationDelegate {
    private static final Logger LOG = Logger.getLogger(AbstractToolDelegate.class);

    @Inject
    WanakuToolConfig config;

    @Inject
    Client client;

    @Inject
    Instance<ServiceRegistry> serviceRegistryInstance;

    ServiceRegistry serviceRegistry;

    @PostConstruct
    public void init() {
        serviceRegistry = serviceRegistryInstance.get();
        LOG.info("Using service registry implementation " + serviceRegistry.getClass().getName());
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
        String service = ConfigProvider.getConfig().getConfigValue("wanaku.service.tool.name").getValue();
        try {
            Object obj = client.exchange(request);

            List<String> response = coerceResponse(obj);

            ToolInvokeReply.Builder builder = ToolInvokeReply.newBuilder().setIsError(false);
            builder.addAllContent(response);

            try {
                return builder.build();
            } finally {
                serviceRegistry.saveState(service, true, null);
            }
        } catch (InvalidResponseTypeException e) {
            String stateMsg = "Invalid response type from the consumer: " + e.getMessage();
            LOG.errorf(e, stateMsg);
            serviceRegistry.saveState(service, false, stateMsg);
            return ToolInvokeReply.newBuilder()
                    .setIsError(true)
                    .addAllContent(List.of(stateMsg)).build();
        } catch (NonConvertableResponseException e) {
            String stateMsg = "Non-convertable response from the consumer " + e.getMessage();
            LOG.errorf(e, stateMsg);
            serviceRegistry.saveState(service, false, stateMsg);
            return ToolInvokeReply.newBuilder()
                    .setIsError(true)
                    .addAllContent(List.of(stateMsg)).build();
        } catch (Exception e) {
            String stateMsg = "Unable to invoke tool: " + e.getMessage();
            LOG.errorf(e, stateMsg, e);
            serviceRegistry.saveState(service, false, stateMsg);
            return ToolInvokeReply.newBuilder()
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

    private void tryRegistering(String service, String address, int port) {
        int retries = config.registration().retries();
        boolean registered = false;
        do {
            try {
                serviceRegistry.register(ServiceTarget.toolInvoker(service, address, port), serviceConfigurations());
                registered = true;
            } catch (Exception e) {
                int waitSeconds = config.registration().retryWaitSeconds();
                retries = waitAndRetry(service, e, retries, waitSeconds);
            }
        } while (!registered && (retries > 0));
    }

    @Override
    public void register() {
        String service = ConfigProvider.getConfig().getConfigValue("wanaku.service.tool.name").getValue();
        String port = ConfigProvider.getConfig().getConfigValue("quarkus.grpc.server.port").getValue();

        final String address = DiscoveryUtil.resolveRegistrationAddress();
        LOG.debugf("Registering tool service %s with address %s:%s", service, address, port);

        tryRegistering(service, address, Integer.parseInt(port));
    }

    @Override
    public void deregister(String service, String address, int port) {
        serviceRegistry.deregister(service, ServiceType.TOOL_INVOKER);
    }

    @Override
    public Map<String, PropertySchema> properties() {
        final Set<WanakuServiceConfig.Service.Property> properties = config.service().properties();

        return properties.stream().collect(Collectors.toMap(WanakuServiceConfig.Service.Property::name,
                AbstractToolDelegate::toPropertySchema));
    }

    private static PropertySchema toPropertySchema(WanakuServiceConfig.Service.Property configProp) {
        return PropertySchema.newBuilder()
                .setDescription(configProp.description())
                .setType(configProp.type())
                .setRequired(configProp.required())
                .build();
    }
}
