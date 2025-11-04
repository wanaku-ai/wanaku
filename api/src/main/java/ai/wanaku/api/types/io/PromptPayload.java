package ai.wanaku.api.types.io;

import ai.wanaku.api.types.PromptReference;

/**
 * Represents a payload for prompt operations that may require provisioning.
 */
public final class PromptPayload implements ProvisionAwarePayload<PromptReference> {
    private PromptReference promptReference;
    private String configurationData;
    private String secretsData;

    @Override
    public PromptReference getPayload() {
        return promptReference;
    }

    public void setPayload(PromptReference promptReference) {
        this.promptReference = promptReference;
    }

    @Override
    public String getConfigurationData() {
        return configurationData;
    }

    public void setConfigurationData(String configurationData) {
        this.configurationData = configurationData;
    }

    @Override
    public String getSecretsData() {
        return secretsData;
    }

    public void setSecretsData(String secretsData) {
        this.secretsData = secretsData;
    }
}
