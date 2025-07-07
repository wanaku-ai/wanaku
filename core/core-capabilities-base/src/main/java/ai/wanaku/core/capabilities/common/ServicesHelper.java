package ai.wanaku.core.capabilities.common;

import ai.wanaku.api.types.providers.ServiceTarget;
import ai.wanaku.api.types.providers.ServiceType;
import ai.wanaku.core.capabilities.config.WanakuServiceConfig;
import ai.wanaku.core.capabilities.discovery.DefaultRegistrationManager;
import ai.wanaku.api.discovery.RegistrationManager;
import ai.wanaku.core.exchange.PropertySchema;
import ai.wanaku.core.service.discovery.client.DiscoveryService;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import java.io.File;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import static ai.wanaku.core.util.discovery.DiscoveryUtil.resolveRegistrationAddress;

public class ServicesHelper {
    private static final Logger LOG = Logger.getLogger(ServicesHelper.class);

    private ServicesHelper() {}


    public static int waitAndRetry(String service, Exception e, int retries, int waitSeconds) {
        retries--;
        if (retries == 0) {
            LOG.errorf(e, "Failed to register service %s: %s. No more retries left", service, e.getMessage());
            return 0;
        } else {
            LOG.warnf("Failed to register service %s: %s. Retries left: %d", service, e.getMessage(), retries);
        }
        try {
            Thread.sleep(waitSeconds * 1000L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOG.warnf("Interrupted while still having %d retries", retries);
            return 0;
        }
        return retries;
    }

    public static Map<String, PropertySchema> buildPropertiesMap(WanakuServiceConfig config) {
        final Set<WanakuServiceConfig.Service.Property> properties = config.service().properties();

        Map<String, PropertySchema> props = properties.stream().collect(Collectors.toMap(WanakuServiceConfig.Service.Property::name,
                ServicesHelper::toPropertySchema));

        return props;
    }

    private static PropertySchema toPropertySchema(WanakuServiceConfig.Service.Property configProp) {
        return PropertySchema.newBuilder()
                .setDescription(configProp.description())
                .setType(configProp.type())
                .setRequired(configProp.required())
                .build();
    }

    public static RegistrationManager newRegistrationManager(WanakuServiceConfig config, ServiceType  serviceType) {
        LOG.infof("Using registration service at %s", config.registration().uri());
        DiscoveryService discoveryService = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(config.registration().uri()))
                .build(DiscoveryService.class);

        String service = config.name();
        ServiceTarget serviceTarget = newServiceTarget(config, service, serviceType);

        int retries = config.registration().retries();
        int waitSeconds = config.registration().retryWaitSeconds();

        final String serviceHome = getCanonicalServiceHome(config);

        return new DefaultRegistrationManager(discoveryService, serviceTarget, retries, waitSeconds, serviceHome);
    }

    public static String getCanonicalServiceHome(WanakuServiceConfig config) {
        return config.serviceHome().replace("${user.home}", System.getProperty("user.home"))
                + File.separator
                + config.name();
    }

    private static ServiceTarget newServiceTarget(WanakuServiceConfig config, String service, ServiceType serviceType) {
        String portStr = ConfigProvider.getConfig().getConfigValue("quarkus.grpc.server.port").getValue();
        final int port = Integer.parseInt(portStr);


        String address = resolveRegistrationAddress(config.registration().announceAddress());

        return ServiceTarget.newEmptyTarget(service, address, port, serviceType);
    }
}
