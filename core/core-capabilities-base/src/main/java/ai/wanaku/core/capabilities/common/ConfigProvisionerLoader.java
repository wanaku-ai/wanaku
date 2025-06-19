package ai.wanaku.core.capabilities.common;

import ai.wanaku.core.capabilities.config.WanakuServiceConfig;
import ai.wanaku.core.config.provider.api.ConfigProvisioner;
import ai.wanaku.core.config.provider.api.ConfigWriter;
import ai.wanaku.core.config.provider.api.DefaultConfigProvisioner;
import ai.wanaku.core.config.provider.api.ProvisionedConfig;
import ai.wanaku.core.config.provider.api.SecretWriter;
import ai.wanaku.core.config.provider.file.FileConfigurationWriter;
import ai.wanaku.core.config.provider.file.FileSecretWriter;
import ai.wanaku.core.exchange.Configuration;
import ai.wanaku.core.exchange.PayloadType;
import ai.wanaku.core.exchange.ProvisionRequest;
import ai.wanaku.core.exchange.Secret;
import java.io.File;
import java.net.URI;
import java.util.UUID;

public final class ConfigProvisionerLoader {

    private ConfigProvisionerLoader() {}

    public static ConfigProvisioner newConfigProvisioner(ProvisionRequest request, WanakuServiceConfig config) {
        final Configuration configuration = request.getConfiguration();
        final Secret secret = request.getSecret();
        ConfigWriter configurationWriter;
        SecretWriter secretWriter;

        final String serviceHome = ServicesHelper.getCanonicalServiceHome(config);

        if (configuration.getType() == PayloadType.BUILTIN) {
            final File dataFile = newRandomizedDataFile(serviceHome);

            configurationWriter = new FileConfigurationWriter(dataFile);
        } else {
            throw new UnsupportedOperationException("Provisioner not supported yet.");
        }

        if (secret.getType() == PayloadType.BUILTIN) {
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

    public static ProvisionedConfig provision(ProvisionRequest request, ConfigProvisioner provisioner) {
        final Configuration configuration = request.getConfiguration();
        final Secret secret = request.getSecret();

        final URI configurationUri = provisioner.provisionConfiguration(request.getUri(), configuration.getPayload());
        final URI secretUri = provisioner.provisionSecret(request.getUri(), secret.getPayload());

        return new ProvisionedConfig(configurationUri, secretUri);
    }
}
