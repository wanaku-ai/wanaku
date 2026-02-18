package ai.wanaku.core.capabilities.common;

import java.io.File;
import java.net.URI;
import java.util.UUID;
import ai.wanaku.capabilities.sdk.config.provider.api.ConfigProvisioner;
import ai.wanaku.capabilities.sdk.config.provider.api.ConfigWriter;
import ai.wanaku.capabilities.sdk.config.provider.api.DefaultConfigProvisioner;
import ai.wanaku.capabilities.sdk.config.provider.api.ProvisionedConfig;
import ai.wanaku.capabilities.sdk.config.provider.api.SecretWriter;
import ai.wanaku.capabilities.sdk.config.provider.file.FileConfigurationWriter;
import ai.wanaku.capabilities.sdk.config.provider.file.FileSecretWriter;
import ai.wanaku.core.capabilities.config.WanakuServiceConfig;
import ai.wanaku.core.exchange.v1.Configuration;
import ai.wanaku.core.exchange.v1.PayloadType;
import ai.wanaku.core.exchange.v1.ProvisionRequest;
import ai.wanaku.core.exchange.v1.Secret;

/**
 * Utility class for loading and creating configuration provisioners.
 * <p>
 * This class provides factory methods for creating {@link ConfigProvisioner} instances
 * based on provision requests and service configuration. It handles the creation of
 * appropriate configuration and secret writers based on the payload types specified
 * in the provision request.
 * <p>
 * The loader supports:
 * <ul>
 *   <li>Creating provisioners with file-based configuration and secret writers</li>
 *   <li>Provisioning configuration and secrets to storage locations</li>
 *   <li>Generating unique file paths for configuration data</li>
 * </ul>
 *
 * @see ConfigProvisioner
 * @see ProvisionRequest
 */
public final class ConfigProvisionerLoader {

    private ConfigProvisionerLoader() {}

    /**
     * Creates a new configuration provisioner based on the provision request and service configuration.
     * <p>
     * This method analyzes the payload types in the request and creates appropriate
     * configuration and secret writers. Currently supports file-based (BUILTIN) payload types.
     *
     * @param request the provision request containing configuration and secret data
     * @param config the service configuration specifying storage locations
     * @return a new {@link ConfigProvisioner} instance configured for the request
     * @throws UnsupportedOperationException if the payload type is not supported
     */
    public static ConfigProvisioner newConfigProvisioner(ProvisionRequest request, WanakuServiceConfig config) {
        final Configuration configuration = request.getConfiguration();
        final Secret secret = request.getSecret();
        ConfigWriter configurationWriter;
        SecretWriter secretWriter;

        final String serviceHome = ServicesHelper.getCanonicalServiceHome(config);

        if (configuration.getType() == PayloadType.PAYLOAD_TYPE_BUILTIN) {
            final File dataFile = newRandomizedDataFile(serviceHome);

            configurationWriter = new FileConfigurationWriter(dataFile);
        } else {
            throw new UnsupportedOperationException("Provisioner not supported yet.");
        }

        if (secret.getType() == PayloadType.PAYLOAD_TYPE_BUILTIN) {
            final File dataFile = newRandomizedDataFile(serviceHome);

            secretWriter = new FileSecretWriter(dataFile);
        } else {
            throw new UnsupportedOperationException("Provisioner not supported yet.");
        }

        return new DefaultConfigProvisioner(configurationWriter, secretWriter);
    }

    private static File newRandomizedDataFile(String serviceHome) {
        String uuid = UUID.randomUUID() + ".properties";
        File dataFile = new File(serviceHome, uuid);
        return dataFile;
    }

    /**
     * Provisions configuration and secrets using the provided provisioner.
     * <p>
     * This method extracts configuration and secret data from the provision request
     * and delegates to the provisioner to write them to their designated storage locations.
     *
     * @param request the provision request containing the data to provision
     * @param provisioner the provisioner instance to use for writing configuration and secrets
     * @return a {@link ProvisionedConfig} containing URIs to the provisioned configuration and secret locations
     */
    public static ProvisionedConfig provision(ProvisionRequest request, ConfigProvisioner provisioner) {
        final Configuration configuration = request.getConfiguration();
        final Secret secret = request.getSecret();

        final URI configurationUri = provisioner.provisionConfiguration(request.getUri(), configuration.getPayload());
        final URI secretUri = provisioner.provisionSecret(request.getUri(), secret.getPayload());

        return new ProvisionedConfig(configurationUri, secretUri);
    }
}
