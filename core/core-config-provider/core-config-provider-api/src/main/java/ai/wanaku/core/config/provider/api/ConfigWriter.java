package ai.wanaku.core.config.provider.api;

import java.net.URI;

/**
 * An interface for writing configuration data.
 * Implementations of this interface are responsible for persisting or otherwise
 * making available configuration data identified by a unique ID.
 */
public interface ConfigWriter {

    /**
     * Writes configuration data to a destination.
     * The specific destination and mechanism for writing are determined by the
     * implementing class.
     *
     * @param id The unique identifier for the configuration data. This ID can be used
     * by the implementation to determine the storage location or naming convention.
     * @param data The actual configuration data as a String. The format of this string
     * (e.g., JSON, XML, plain text) is dependent on the specific usage
     * and the expectations of the system consuming the configuration.
     * @return A {@link URI} representing the location or identifier of the written
     * configuration data. This URI can be used later to access or reference
     * the stored configuration.
     * @throws ConfigWriteException If an error occurs during the writing process,
     * such as an I/O error, permission issue, or invalid data.
     */
    URI write(String id, String data);
}
