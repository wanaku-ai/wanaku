package ai.wanaku.core.config.provider.api;

import java.net.URI;

/**
 * A base interface for provisioning new configurations and secrets for a tool.
 * Implementations of this interface are responsible for handling the storage
 * of configuration data and sensitive secret information, typically returning
 * a {@link URI} that points to the location of the provisioned data.
 */
public interface ConfigProvisioner {

    /**
     * Provisions a new configuration for a tool.
     * This method takes an identifier and the configuration data as a string,
     * and is expected to store this configuration in a persistent or accessible manner.
     *
     * @param id The unique identifier for the configuration.
     * @param data The configuration data as a String. The format of this data
     * (e.g., JSON, YAML, properties) depends on the specific
     * implementation and the tool's requirements.
     * @return A {@link URI} pointing to the location where the configuration has been provisioned.
     * This URI could be a file path, a URL to a remote service, or any other
     * scheme relevant to the provisioning mechanism.
     * @throws ProvisioningException If an error occurs during the provisioning process.
     */
    URI provisionConfiguration(String id, String data);

    /**
     * Provisions a new secret for a tool.
     * This method is similar to {@link #provisionConfiguration(String, String)} but is
     * specifically for sensitive data (secrets) that may require different
     * storage, encryption, or access control mechanisms.
     *
     * @param id The unique identifier for the secret.
     * @param data The secret data as a String. It is highly recommended that
     * implementations handle this data securely (e.g., encryption at rest).
     * @return A {@link URI} pointing to the location where the secret has been provisioned.
     * Similar to configurations, this URI's scheme depends on the
     * specific provisioning mechanism.
     * @throws ProvisioningException If an error occurs during the provisioning process.
     */
    URI provisionSecret(String id, String data);
}
