package ai.wanaku.api.types.io;

import ai.wanaku.api.types.ToolReference;

/**
 * Payload for provisioning tool capabilities with associated configuration and secrets.
 * <p>
 * This class bundles a {@link ToolReference} with its corresponding configuration
 * and secrets data, facilitating the complete provisioning of a tool capability
 * within the Wanaku system.
 * <p>
 * The payload includes:
 * <ul>
 *   <li>A tool reference that identifies and describes the tool</li>
 *   <li>Configuration data with non-sensitive settings</li>
 *   <li>Secrets data with sensitive credentials or tokens</li>
 * </ul>
 */
public final class ToolPayload implements ProvisionAwarePayload<ToolReference> {
    private ToolReference toolReference;
    private String configurationData;
    private String secretsData;

    /**
     * Gets the tool reference contained in this payload.
     *
     * @return the tool reference
     */
    @Override
    public ToolReference getPayload() {
        return toolReference;
    }

    /**
     * Sets the tool reference for this payload.
     *
     * @param toolReference the tool reference to set
     */
    public void setPayload(ToolReference toolReference) {
        this.toolReference = toolReference;
    }

    /**
     * Gets the configuration data for provisioning this tool.
     *
     * @return the configuration data, or {@code null} if not provided
     */
    @Override
    public String getConfigurationData() {
        return configurationData;
    }

    /**
     * Sets the configuration data for provisioning this tool.
     *
     * @param configurationData the configuration data to set
     */
    public void setConfigurationData(String configurationData) {
        this.configurationData = configurationData;
    }

    /**
     * Gets the secrets data required for provisioning this tool.
     *
     * @return the secrets data, or {@code null} if not provided
     */
    @Override
    public String getSecretsData() {
        return secretsData;
    }

    /**
     * Sets the secrets data required for provisioning this tool.
     *
     * @param secretsData the secrets data to set
     */
    public void setSecretsData(String secretsData) {
        this.secretsData = secretsData;
    }
}
