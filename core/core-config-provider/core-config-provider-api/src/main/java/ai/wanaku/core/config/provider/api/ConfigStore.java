package ai.wanaku.core.config.provider.api;

import java.util.Map;

/**
 * A config store abstracts the actual store where the configs are located.
 * Implementations of this interface provide methods for retrieving configuration entries
 * from various underlying storage mechanisms.
 */
public interface ConfigStore {

    /**
     * Retrieves all configuration entries from the store.
     *
     * @return A {@link Map} where keys are configuration names (Strings) and values are
     * their corresponding String representations. If no entries are found, an empty map is returned.
     */
    Map<String, String> getEntries();

    /**
     * Retrieves configuration entries that start with a given prefix.
     * This can be useful for fetching related configurations, e.g., all database-related settings.
     *
     * @param prefix The prefix to filter configuration names by. Must not be null.
     * @return A {@link Map} containing configuration entries whose names start with the specified prefix.
     * Keys are configuration names and values are their corresponding String representations.
     * If no entries match the prefix, an empty map is returned.
     */
    Map<String, String> getEntries(String prefix);

    /**
     * Retrieves the configuration entry associated with the given name.
     *
     * @param name The exact name of the configuration entry to retrieve. Must not be null.
     * @return The String value of the configuration entry, or {@code null} if no entry
     * with the specified name is found.
     */
    String get(String name);
}
