package ai.wanaku.core.services.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration class for providers
 */
@ConfigMapping(prefix = "wanaku.service.provider")
public interface WanakuProviderConfig extends WanakuServiceConfig {

    /**
     * Returns the name of the provider.
     *
     * @return The name of the provider.
     */
    String name();

    /**
     * Returns the base URI for the provider.
     * <p>
     * This is a template string with placeholders for the scheme and host, which can be used to construct
     * the actual URI for the provider. By default, it will use the scheme and host format, but this value
     * can be overridden by providing a custom base URI in the settings.
     *
     * @return The base URI for the provider.
     */
    @WithDefault("%s://%s")
    String baseUri();


    /**
     * Returns the service-specific configurations
     *
     * @return The service-specific configurations associated with the provider.
     */
    Service service();

    /**
     * Returns the credentials used by the provider.
     * <p>
     * These credentials can include authentication information such as username and password,
     * or other types of credentials like API keys. They will be used to construct the actual URI
     * for the provider based on the base URI template string provided above.
     *
     * @return The credentials for accessing the provider.
     */
    Credentials credentials();

    /**
     * Returns the registration information for the provider.
     * <p>
     * This is used to adjust how the service handles registration with the service registry.
     *
     * @return The registration information for the provider.
     */
    Registration registration();
}
