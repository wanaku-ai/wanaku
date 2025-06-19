package ai.wanaku.api.types.io;

import ai.wanaku.api.types.ToolReference;

public final class ToolPayload {
    private ToolReference toolReference;
    private String configurationData;
    private String secretsData;

    public ToolReference getToolReference() {
        return toolReference;
    }

    public void setToolReference(ToolReference toolReference) {
        this.toolReference = toolReference;
    }

    public String getConfigurationData() {
        return configurationData;
    }

    public void setConfigurationData(String configurationData) {
        this.configurationData = configurationData;
    }

    public String getSecretsData() {
        return secretsData;
    }

    public void setSecretsData(String secretsData) {
        this.secretsData = secretsData;
    }
}
