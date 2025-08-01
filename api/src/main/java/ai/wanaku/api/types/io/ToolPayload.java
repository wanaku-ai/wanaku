package ai.wanaku.api.types.io;

import ai.wanaku.api.types.ToolReference;

public final class ToolPayload implements ProvisionAwarePayload<ToolReference> {
    private ToolReference toolReference;
    private String configurationData;
    private String secretsData;

    @Override
    public ToolReference getPayload() {
        return toolReference;
    }

    public void setPayload(ToolReference toolReference) {
        this.toolReference = toolReference;
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
