package ai.wanaku.core.config.provider.api;

import java.util.Map;

/**
 * Represents a resource that provides access to both configurations and secrets.
 * This interface combines functionalities typically found in a {@link ConfigStore}
 * and a {@link SecretStore}, allowing unified access to various types of sensitive and non-sensitive data.
 */
public interface ConfigResource {

    /**
     * Retrieves all available configuration entries.
     *
     * @return A {@link Map} where keys are configuration names (Strings) and values are
     * their corresponding String representations. Returns an empty map if no configurations are found.
     */
    Map<String, String> getConfigs();

    /**
     * Retrieves configuration entries that start with a given prefix.
     * This is useful for grouping and fetching related configurations, such as all settings for a specific module.
     *
     * @param prefix The prefix to filter configuration names by. Must not be null.
     * @return A {@link Map} containing configuration entries whose names start with the specified prefix.
     * Keys are configuration names and values are their corresponding String representations.
     * Returns an empty map if no configurations match the prefix.
     */
    Map<String, String> getConfigs(String prefix);

    /**
     * Retrieves the configuration entry associated with the given key.
     *
     * @param key The exact key of the configuration entry to retrieve. Must not be null.
     * @return The String value of the configuration entry, or {@code null} if no entry
     * with the specified key is found.
     */
    String getConfig(String key);

    /**
     * Retrieves all available secret entries.
     * Secrets are typically sensitive data like API keys, database credentials, etc.
     *
     * @return A {@link Map} where keys are secret names (Strings) and values are
     * their corresponding String representations. Returns an empty map if no secrets are found.
     */
    Map<String, String> getSecrets();

    /**
     * Retrieves all available secret entries that start with a given prefix.
     * Secrets are typically sensitive data like API keys, database credentials, etc.
     * This is useful for grouping and fetching related configurations, such as all settings for a specific module.
     *
     * @param prefix The prefix to filter configuration names by. Must not be null.
     * @return A {@link Map} where keys are secret names (Strings) and values are
     * their corresponding String representations. Returns an empty map if no secrets are found.
     */
    Map<String, String> getSecrets(String prefix);

    /**
     * Retrieves the secret entry associated with the given key.
     *
     * @param key The exact key of the secret entry to retrieve. Must not be null.
     * @return The String value of the secret entry, or {@code null} if no secret
     * with the specified key is found.
     */
    String getSecret(String key);
}
