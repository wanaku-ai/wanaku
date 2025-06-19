package ai.wanaku.core.config.provider.api;

import java.util.Map;

/**
 * A default implementation of the {@link ConfigResource} interface.
 * This class aggregates a {@link ConfigStore} and a {@link SecretStore} to provide a unified
 * interface for accessing both configurations and secrets.
 */
public class DefaultConfigResource implements ConfigResource {
    private final ConfigStore configStore;
    private final SecretStore secretStore;

    /**
     * Constructs a new {@link DefaultConfigResource} with the specified configuration and secret stores.
     *
     * @param configStore The {@link ConfigStore} to use for retrieving configurations. Must not be null.
     * @param secretStore The {@link SecretStore} to use for retrieving secrets. Must not be null.
     */
    public DefaultConfigResource(ConfigStore configStore, SecretStore secretStore) {
        this.configStore = configStore;
        this.secretStore = secretStore;
    }

    @Override
    public Map<String, String> getConfigs() {
        return configStore.getEntries();
    }

    @Override
    public Map<String, String> getConfigs(String prefix) {
        return configStore.getEntries(prefix);
    }

    @Override
    public String getConfig(String key) {
        return configStore.get(key);
    }

    @Override
    public Map<String, String> getSecrets() {
        return secretStore.getEntries();
    }

    @Override
    public Map<String, String> getSecrets(String prefix) {
        return secretStore.getEntries(prefix);
    }

    @Override
    public String getSecret(String key) {
        return secretStore.get(key);
    }
}
