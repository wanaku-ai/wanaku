package ai.wanaku.core.config.provider.api;

import java.util.Properties;

/**
 * A provider of properties.
 * This interface is designed for use with property-based stores; refer to the {@link PropertyBasedStore} class
 * for more details on its application within such contexts.
 */
public interface PropertyProvider {

    /**
     * Retrieves a {@link Properties} object containing key-value pairs.
     *
     * @return A {@link Properties} object. Returns an empty {@link Properties} object if no properties are available.
     * Never returns {@code null}.
     */
    Properties getProperties();
}
