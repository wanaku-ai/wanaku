package ai.wanaku.core.capabilities.common;

import ai.wanaku.api.types.providers.ServiceTarget;
import ai.wanaku.core.capabilities.config.WanakuServiceConfig;
import ai.wanaku.core.capabilities.discovery.DefaultRegistrationManager;
import ai.wanaku.core.capabilities.discovery.RegistrationManager;
import ai.wanaku.core.exchange.InquireReply;
import ai.wanaku.core.exchange.InvocationDelegate;
import ai.wanaku.core.service.discovery.client.DiscoveryService;
import ai.wanaku.core.service.discovery.util.DiscoveryUtil;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import java.io.File;
import java.net.URI;
import java.util.Map;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

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

    public static InquireReply buildInquireReply(InvocationDelegate delegate) {
        return InquireReply.newBuilder()
                .putAllProperties(delegate.properties())
                .build();
    }

    public static RegistrationManager newRegistrationManager(WanakuServiceConfig config, Map<String, String> serviceConfigurations) {
        LOG.infof("Using registration service at %s", config.registration().uri());
        DiscoveryService discoveryService = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(config.registration().uri()))
                .build(DiscoveryService.class);


        String service = config.name();
        ServiceTarget serviceTarget = newServiceTarget(config, service, serviceConfigurations);

        int retries = config.registration().retries();
        int waitSeconds = config.registration().retryWaitSeconds();

        final String serviceHome =
                config.serviceHome().replace("${user.home}", System.getProperty("user.home"))
                        + File.separator
                        + config.name();

        return new DefaultRegistrationManager(discoveryService, serviceTarget, retries, waitSeconds, serviceHome);
    }

    private static ServiceTarget newServiceTarget(WanakuServiceConfig config, String service, Map<String, String> configurations) {
        String portStr = ConfigProvider.getConfig().getConfigValue("quarkus.grpc.server.port").getValue();
        final int port = Integer.parseInt(portStr);


        String address = config.registration().announceAddress();
        if ("auto".equals(address)) {
            LOG.infof("Using announce address %s ", address);
            address = DiscoveryUtil.resolveRegistrationAddress();
        }

        return ServiceTarget.provider(service, address, port, configurations);
    }
}
