package ai.wanaku.core.capabilities.common;

import jakarta.ws.rs.core.MultivaluedMap;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.rest.client.ext.ClientHeadersFactory;
import org.jboss.logging.Logger;
import io.quarkus.oidc.client.Tokens;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import ai.wanaku.capabilities.sdk.api.discovery.RegistrationManager;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;
import ai.wanaku.core.capabilities.config.WanakuServiceConfig;
import ai.wanaku.core.capabilities.discovery.DefaultRegistrationManager;
import ai.wanaku.core.exchange.v1.PropertySchema;
import ai.wanaku.core.service.discovery.client.DiscoveryService;

import static ai.wanaku.core.util.discovery.DiscoveryUtil.resolveRegistrationAddress;

/**
 * Utility class providing helper methods for service registration,
 * configuration, and lifecycle management.
 * <p>
 * This class contains static utility methods that support capability provider
 * services in their
 * interactions with the MCP router's discovery service. It handles common tasks
 * such as:
 * <ul>
 * <li>Creating and configuring registration managers</li>
 * <li>Building property schemas from service configurations</li>
 * <li>Managing retry logic for registration operations</li>
 * <li>Resolving service home directories and addresses</li>
 * </ul>
 *
 * @see RegistrationManager
 * @see WanakuServiceConfig
 * @see DefaultRegistrationManager
 */
public class ServicesHelper {
    private static final Logger LOG = Logger.getLogger(ServicesHelper.class);

    private ServicesHelper() {}

    /**
     * Implements a wait-and-retry mechanism for failed service registration
     * attempts.
     * <p>
     * This method decrements the retry counter and waits for the specified duration
     * before
     * allowing another attempt. If no retries remain, it logs an error and returns
     * 0.
     *
     * @param service     the name of the service being registered
     * @param e           the exception that caused the failure
     * @param retries     the current number of retries remaining
     * @param waitSeconds the number of seconds to wait before the next retry
     * @return the updated retry count, or 0 if no retries remain or if interrupted
     */
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

    /**
     * Builds a map of property schemas from the service configuration.
     * <p>
     * This method converts the property definitions in the service configuration
     * into
     * {@link PropertySchema} objects that can be used for validation and
     * documentation
     * purposes.
     *
     * @param config the Wanaku service configuration containing property
     *               definitions
     * @return a map of property names to their corresponding schemas
     */
    public static Map<String, PropertySchema> buildPropertiesMap(WanakuServiceConfig config) {
        final Set<WanakuServiceConfig.Service.Property> properties =
                config.service().properties();

        Map<String, PropertySchema> props = properties.stream()
                .collect(
                        Collectors.toMap(WanakuServiceConfig.Service.Property::name, ServicesHelper::toPropertySchema));

        return props;
    }

    private static PropertySchema toPropertySchema(WanakuServiceConfig.Service.Property configProp) {
        return PropertySchema.newBuilder()
                .setDescription(configProp.description())
                .setType(configProp.type())
                .setRequired(configProp.required())
                .build();
    }

    /**
     * Creates a new RegistrationManager instance for the given configuration and
     * service type.
     *
     * @param config      the Wanaku service configuration
     * @param serviceType the type of service to register (e.g.,
     *                    "resource-provider", "tool-invoker",
     *                    "code-execution-engine")
     * @param tokens      the access token used for authenticating requests to the
     *                    registration service
     * @return a new RegistrationManager instance
     */
    public static RegistrationManager newRegistrationManager(
            WanakuServiceConfig config, String serviceType, Tokens tokens) {
        LOG.infof("Using registration service at %s", config.registration().uri());
        DiscoveryService discoveryService = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(config.registration().uri()))
                .clientHeadersFactory(new ServiceClientHeadersFactory(tokens))
                .build(DiscoveryService.class);

        String service = config.name();
        ServiceTarget serviceTarget = newServiceTarget(config, service, serviceType);

        return new DefaultRegistrationManager(discoveryService, serviceTarget, config);
    }

    /**
     * Resolves the canonical service home directory path.
     * <p>
     * This method resolves the service home directory by expanding any
     * {@code ${user.home}}
     * placeholders and appending the service name. The result is a fully qualified
     * path
     * where service-specific data and configuration can be stored.
     *
     * @param config the Wanaku service configuration
     * @return the canonical service home directory path
     */
    public static String getCanonicalServiceHome(WanakuServiceConfig config) {
        String home = config.serviceHome().replace("${user.home}", System.getProperty("user.home"));
        return java.nio.file.Path.of(home).resolve(config.name()).normalize().toString();
    }

    private static ServiceTarget newServiceTarget(WanakuServiceConfig config, String service, String serviceType) {
        String portStr = ConfigProvider.getConfig()
                .getConfigValue("quarkus.grpc.server.port")
                .getValue();
        final int port = Integer.parseInt(portStr);

        String address = resolveRegistrationAddress(config.registration().announceAddress());

        return ServiceTarget.newEmptyTarget(service, address, port, serviceType);
    }

    private record ServiceClientHeadersFactory(Tokens accessToken) implements ClientHeadersFactory {

        @Override
        public MultivaluedMap<String, String> update(
                MultivaluedMap<String, String> incomingHeaders, MultivaluedMap<String, String> outgoingHeaders) {

            if (accessToken == null) {
                return outgoingHeaders;
            }

            if (accessToken.isAccessTokenExpired()) {
                LOG.warn(
                        "The access token is expired. It's likely the current call will fail if the token cannot be refreshed");
            }

            outgoingHeaders.add("Authorization", String.format("Bearer %s", accessToken.getAccessToken()));

            return outgoingHeaders;
        }
    }
}
