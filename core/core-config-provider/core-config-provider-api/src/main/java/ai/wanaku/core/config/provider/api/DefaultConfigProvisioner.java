package ai.wanaku.core.config.provider.api;

import java.net.URI;

/**
 * Default implementation of the {@link ConfigProvisioner} interface.
 * This class uses a {@link ConfigWriter} to provision configurations
 * and a {@link SecretWriter} to provision secrets. It acts as a simple
 * delegator, passing the provisioning requests directly to the
 * respective writer implementations.
 */
public class DefaultConfigProvisioner implements ConfigProvisioner {
    private final ConfigWriter configWriter;
    private final SecretWriter secretWriter;

    /**
     * Constructs a new {@code DefaultConfigProvisioner} with the specified
     * {@link ConfigWriter} and {@link SecretWriter}.
     *
     * @param configWriter The {@link ConfigWriter} to use for provisioning configurations.
     * Must not be {@code null}.
     * @param secretWriter The {@link SecretWriter} to use for provisioning secrets.
     * Must not be {@code null}.
     */
    public DefaultConfigProvisioner(ConfigWriter configWriter, SecretWriter secretWriter) {
        this.configWriter = configWriter;
        this.secretWriter = secretWriter;
    }

    @Override
    public URI provisionConfiguration(String id, String data) {
        return configWriter.write(id, data);
    }

    @Override
    public URI provisionSecret(String id, String data) {
        return secretWriter.write(id, data);
    }
}
