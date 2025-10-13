package ai.wanaku.api.types.io;

import ai.wanaku.api.types.ResourceReference;

/**
 * Payload for provisioning resource capabilities with associated configuration and secrets.
 * <p>
 * This class bundles a {@link ResourceReference} with its corresponding configuration
 * and secrets data, facilitating the complete provisioning of a resource capability
 * within the Wanaku system.
 * </p>
 * <p>
 * The payload includes:
 * <ul>
 *   <li>A resource reference that identifies and describes the resource</li>
 *   <li>Configuration data with non-sensitive settings</li>
 *   <li>Secrets data with sensitive credentials or tokens</li>
 * </ul>
 * </p>
 */
public class ResourcePayload implements ProvisionAwarePayload<ResourceReference> {
    private ResourceReference resourceReference;
    private String configurationData;
    private String secretsData;

    /**
     * Gets the resource reference contained in this payload.
     *
     * @return the resource reference
     */
    @Override
    public ResourceReference getPayload() {
        return resourceReference;
    }

    /**
     * Sets the resource reference for this payload.
     *
     * @param resourceReference the resource reference to set
     */
    public void setPayload(ResourceReference resourceReference) {
        this.resourceReference = resourceReference;
    }

    /**
     * Gets the configuration data for provisioning this resource.
     *
     * @return the configuration data, or {@code null} if not provided
     */
    @Override
    public String getConfigurationData() {
        return configurationData;
    }

    /**
     * Sets the configuration data for provisioning this resource.
     *
     * @param configurationData the configuration data to set
     */
    public void setConfigurationData(String configurationData) {
        this.configurationData = configurationData;
    }

    /**
     * Gets the secrets data required for provisioning this resource.
     *
     * @return the secrets data, or {@code null} if not provided
     */
    @Override
    public String getSecretsData() {
        return secretsData;
    }

    /**
     * Sets the secrets data required for provisioning this resource.
     *
     * @param secretsData the secrets data to set
     */
    public void setSecretsData(String secretsData) {
        this.secretsData = secretsData;
    }
}
