package ai.wanaku.api.types.io;

/**
 * Interface for payloads that carry provisioning information.
 * <p>
 * This interface defines the contract for payloads that bundle together a primary
 * payload object (such as a tool or resource reference) along with associated
 * configuration and secrets data needed for provisioning and deployment.
 * <p>
 * Implementations of this interface are used during capability provisioning to
 * ensure that all necessary configuration and sensitive data are transmitted
 * together with the capability reference.
 *
 * @param <T> the type of the primary payload object
 */
public interface ProvisionAwarePayload<T> {

    /**
     * Gets the primary payload object.
     * <p>
     * This typically contains the reference to a tool, resource, or other capability
     * being provisioned.
     *
     * @return the payload object
     */
    T getPayload();

    /**
     * Gets the configuration data associated with this payload.
     * <p>
     * Configuration data contains non-sensitive settings and parameters required
     * for provisioning the capability. The format and content are specific to the
     * type of capability being provisioned.
     *
     * @return the configuration data as a string, or {@code null} if no configuration is provided
     */
    String getConfigurationData();

    /**
     * Gets the secrets data associated with this payload.
     * <p>
     * Secrets data contains sensitive information such as credentials, API keys,
     * or tokens required for provisioning the capability. This data should be
     * handled securely and not logged or persisted in plain text.
     *
     * @return the secrets data as a string, or {@code null} if no secrets are provided
     */
    String getSecretsData();
}
