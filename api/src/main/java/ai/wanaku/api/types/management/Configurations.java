package ai.wanaku.api.types.management;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Represents a set of configuration for the downstream service.
 *
 * This class encapsulates a collection of {@link Configuration} objects,
 * where each configuration setting has a unique key and value.
 */
public class Configurations {

    /**
     * A map that stores all configurations, where each key is a string
     * identifier and each value is a {@link Configuration} object.
     */
    public Map<String, Configuration> configurations = new HashMap<>();

    /**
     * Returns the entire collection of configuration settings as a map.
     *
     * @return The map of configurations.
     */
    public Map<String, Configuration> getConfigurations() {
        return configurations;
    }

    /**
     * Sets the entire collection of configuration settings as a map.
     *
     * @param configurations The new map of configurations.
     */
    public void setConfigurations(Map<String, Configuration> configurations) {
        this.configurations = configurations;
    }

    /**
     * Converts the map of {@link Configuration} objects to a map of string values,
     * filtering out any null or empty values.
     *
     * @param configurations The input map of configurations.
     * @return A new map containing only the non-null and non-empty configuration values as strings.
     */
    public static Map<String, String> toStringMap(Map<String, Configuration> configurations) {
        return configurations.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue().getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getValue()));
    }
}
