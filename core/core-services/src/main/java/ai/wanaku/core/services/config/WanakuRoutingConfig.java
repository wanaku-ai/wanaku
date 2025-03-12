package ai.wanaku.core.services.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration class for tool services.
 *
 * This interface provides configuration options for tool services, including their name, base URI,
 * credentials, and registration information. Tool services are typically used to perform specific tasks
 * or operations on data, such as routing or processing.
 */
@ConfigMapping(prefix = "wanaku.service.routing")
public interface WanakuRoutingConfig extends WanakuServiceConfig {

    /**
     * Returns the name of the tool service.
     *
     * This is a required configuration option that identifies the tool service.
     *
     * @return The name of the tool service.
     */
    String name();

    /**
     * Returns the base URI for the tool service.
     *
     * This is a template string with placeholders for the scheme and host, which can be used to construct
     * the actual URI for the tool service. By default, it will use the scheme and host from the credentials,
     * but this value can be overridden by providing a custom base URI.
     *
     * @return The base URI for the tool service.
     */
    @WithDefault("%s://%s")
    String baseUri();

    /**
     * Returns the service associated with the tool service.
     *
     * This is an optional configuration option, but it's required if you want to register a service
     * with this provider. If not specified, the service will be automatically discovered based on the
     * name and credentials.
     *
     * @return The service associated with the tool service.
     */
    Service service();

    /**
     * Returns the credentials for accessing the tool service.
     *
     * These credentials can include authentication information such as username and password,
     * or other types of credentials like API keys. They will be used to construct the actual URI
     * for the tool service based on the base URI template string provided above.
     *
     * @return The credentials for accessing the tool service.
     */
    Credentials credentials();

    /**
     * Returns the registration information for the tool service.
     *
     * This is an optional configuration option, but it's required if you want to register a service
     * with this provider. If not specified, the registration will be automatically generated based on
     * the name and credentials.
     *
     * @return The registration information for the tool service.
     */
    Registration registration();
}
