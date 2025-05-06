package ai.wanaku.core.services.config;

import io.smallrye.config.WithDefault;
import java.util.Map;

import ai.wanaku.core.config.WanakuConfig;
import java.util.Set;

/**
 * Base configuration class for downstream services, extending {@link WanakuConfig}.
 */
public interface WanakuServiceConfig extends WanakuConfig {

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
         * Returns a map of custom configuration values for the service.
         *
         * @return A map of custom configurations.
         */
        Map<String, String> configurations();

        /**
         * Returns the map of properties accepted by the service.
         *
         * @return the map of properties accepted by the service.
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
     * Interface defining credentials-specific configurations.
     */
    interface Credentials {
        /**
         * Returns a map of custom configuration values for credentials.
         *
         * @return A map of custom configurations.
         */
        Map<String, String> configurations();
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
    }
}
