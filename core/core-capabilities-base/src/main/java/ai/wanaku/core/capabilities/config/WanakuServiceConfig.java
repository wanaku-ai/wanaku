package ai.wanaku.core.capabilities.config;

import ai.wanaku.core.config.WanakuConfig;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.Map;
import java.util.Set;

/**
 * Base configuration class for downstream services, extending {@link WanakuConfig}.
 */
@ConfigMapping(prefix = "wanaku.service")
public interface WanakuServiceConfig extends WanakuConfig {

    @WithDefault("${user.home}/.wanaku/services/")
    String serviceHome();

    /**
     * Interface defining service-specific configurations.
     */
    interface Service {
        /**
         * Returns a map of default values for the service.
         *
         * @return A map of default values.
         */
        Map<String, String> defaults();

        /**
         * Returns the set of properties accepted by the service.
         *
         * @return the set of properties accepted by the service.
         */
        Set<Property> properties();

        /**
         * Represents a property in a schema or configuration.
         */
        interface Property {
            /**
             * Returns the human-readable name of this property.
             *
             * @return The property's name, not null.
             */
            String name();

            /**
             * Returns the data type associated with this property, as a string (e.g., "string", "integer").
             *
             * @return The property's data type, never null.
             */
            String type();

            /**
             * Returns a human-readable description of this property, including any relevant details about its configuration or usage.
             *
             * @return A descriptive text for the property, may be empty but never null.
             */
            String description();

            /**
             * Indicates whether this property is strictly required in the schema or configuration (true) or optional (false).
             *
             * @return Whether the property is required (true) or not (false).
             */
            boolean required();
        }
    }

    /**
     * Interface defining registration-specific configurations.
     */
    interface Registration {
        /**
         * Returns the interval between registrations in seconds (default: 10s).
         *
         * @return The registration interval.
         */
        @WithDefault("10s")
        String interval();

        /**
         * Returns the number of delay seconds before attempting a registration (default: 3).
         *
         * @return The delay seconds.
         */
        @WithDefault("3")
        int delaySeconds();

        /**
         * Returns the maximum number of retries for registration (default: 3).
         *
         * @return The maximum retries.
         */
        @WithDefault("3")
        int retries();

        /**
         * Returns the retry wait seconds between attempts (default: 1 second).
         *
         * @return The retry wait seconds.
         */
        @WithDefault("1")
        int retryWaitSeconds();

        /**
         * The URI used for the registration service
         * @return The URI used for the registration service
         */
        @WithDefault("http://localhost:8080")
        String uri();

        @WithDefault("auto")
        String announceAddress();
    }

    /**
     * Returns the service name
     *
     * @return The service name
     */
    String name();

    /**
     * Returns the base URI for the tool service.
     * <p>
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
     * <p>
     * This is an optional configuration option, but it's required if you want to register a service
     * with this provider. If not specified, the service will be automatically discovered based on the
     * name and credentials.
     *
     * @return The service associated with the tool service.
     */
    Service service();

    /**
     * Returns the registration information for the tool service.
     * <p>
     * This is an optional configuration option, but it's required if you want to register a service
     * with this provider. If not specified, the registration will be automatically generated based on
     * the name and credentials.
     *
     * @return The registration information for the tool service.
     */
    Registration registration();
}
