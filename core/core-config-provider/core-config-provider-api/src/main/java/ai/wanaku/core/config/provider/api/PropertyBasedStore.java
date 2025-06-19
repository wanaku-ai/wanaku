package ai.wanaku.core.config.provider.api;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import static ai.wanaku.core.config.provider.api.ReservedConfigs.CONFIG_QUERY_PARAMETERS_PREFIX;

/**
 * An abstract base class for {@link ConfigStore} implementations that retrieve configurations from a
 * {@link Properties} object.
 * This class handles the common logic for accessing properties, providing a foundation for
 * concrete property-based configuration stores.
 */
public abstract class PropertyBasedStore implements ConfigStore {

    protected Properties properties;

    /**
     * Constructs a new {@link PropertyBasedStore} with the given {@link PropertyProvider}.
     * The properties are loaded from the provider during construction.
     *
     * @param propertyProvider The provider from which to obtain the {@link Properties} object.
     * Must not be {@code null}.
     */
    protected PropertyBasedStore(PropertyProvider propertyProvider) {
        this.properties = propertyProvider.getProperties();
    }

    @Override
    public Map<String, String> getEntries() {
        Map<String, String> map = new HashMap<>();

        properties.forEach((key, value) -> map.put(key.toString(), value.toString()));
        return map;
    }

    @Override
    public Map<String, String> getEntries(String prefix) {
        final Map<String, String> configs = properties.entrySet().stream()
                .filter(e -> e.getKey().toString().startsWith(CONFIG_QUERY_PARAMETERS_PREFIX))
                .collect(Collectors.toMap(e -> e.getKey().toString(), e -> e.getValue().toString()));
        return configs;
    }

    @Override
    public String get(String name) {
        return properties.getProperty(name);
    }

}
