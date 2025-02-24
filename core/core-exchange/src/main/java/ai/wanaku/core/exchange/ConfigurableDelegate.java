package ai.wanaku.core.exchange;

import java.util.Map;

/**
 * Base interface for delegates that provide configuration information. Most delegates should
 * comply with this interface
 */
public interface ConfigurableDelegate {
    /**
     * Service-specific configurations
     * @return A map of service-specific configurations
     */
    Map<String, String> serviceConfigurations();

    /**
     * Configurations related to handling credentials
     * @return A map of credential-specific configurations
     */
    Map<String, String> credentialsConfigurations();
}
