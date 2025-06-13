package ai.wanaku.core.services.tool;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

import ai.wanaku.api.exceptions.InvalidResponseTypeException;
import ai.wanaku.api.exceptions.NonConvertableResponseException;
import ai.wanaku.core.exchange.InvocationDelegate;
import ai.wanaku.core.exchange.PropertySchema;
import ai.wanaku.core.exchange.ToolInvokeReply;
import ai.wanaku.core.exchange.ToolInvokeRequest;
import ai.wanaku.api.types.providers.ServiceTarget;
import ai.wanaku.core.service.discovery.client.DiscoveryService;
import ai.wanaku.core.service.discovery.util.DiscoveryUtil;
import ai.wanaku.core.services.config.WanakuServiceConfig;
import ai.wanaku.core.services.discovery.DefaultRegistrationManager;
import ai.wanaku.core.services.discovery.RegistrationManager;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

/**
 * Base delegate class
 */
public abstract class AbstractToolDelegate implements InvocationDelegate {
    private static final Logger LOG = Logger.getLogger(AbstractToolDelegate.class);

    @Inject
    WanakuServiceConfig config;

    @Inject
    Client client;

    private RegistrationManager registrationManager;

    @PostConstruct
    public void init() {
        LOG.infof("Using registration service at %s", config.registration().uri());
        DiscoveryService discoveryService = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(config.registration().uri()))
                .build(DiscoveryService.class);

        String service = ConfigProvider.getConfig().getConfigValue("wanaku.service.name").getValue();
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
            Object obj = client.exchange(request);

            List<String> response = coerceResponse(obj);

            ToolInvokeReply.Builder builder = ToolInvokeReply.newBuilder().setIsError(false);
            builder.addAllContent(response);

            registrationManager.lastAsSuccessful();
            return builder.build();
        } catch (InvalidResponseTypeException e) {
            String stateMsg = "Invalid response type from the consumer: " + e.getMessage();
            LOG.errorf(e, stateMsg);
            registrationManager.lastAsFail(stateMsg);
            return ToolInvokeReply.newBuilder()
                    .setIsError(true)
                    .addAllContent(List.of(stateMsg)).build();
        } catch (NonConvertableResponseException e) {
            String stateMsg = "Non-convertable response from the consumer " + e.getMessage();
            LOG.errorf(e, stateMsg);
            registrationManager.lastAsFail(stateMsg);
            return ToolInvokeReply.newBuilder()
                    .setIsError(true)
                    .addAllContent(List.of(stateMsg)).build();
        } catch (Exception e) {
            String stateMsg = "Unable to invoke tool: " + e.getMessage();
            LOG.errorf(e, stateMsg, e);
            registrationManager.lastAsFail(stateMsg);
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

    @Override
    public void register() {
        registrationManager.register();
    }

    @Override
    public void deregister() {
        registrationManager.deregister();
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

    private static ServiceTarget newServiceTarget(String service, Map<String, String> configurations) {
        String portStr = ConfigProvider.getConfig().getConfigValue("quarkus.grpc.server.port").getValue();
        final int port = Integer.parseInt(portStr);

        String address = ConfigProvider.getConfig().getConfigValue("wanaku.service.registration.announce-address").getValue();
        if ("auto".equals(address)) {
            LOG.infof("Using announce address %s ", address);
            address = DiscoveryUtil.resolveRegistrationAddress();
        }

        return ServiceTarget.toolInvoker(service, address, port, configurations);
    }
}
