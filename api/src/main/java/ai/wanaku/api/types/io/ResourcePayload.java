package ai.wanaku.api.types.io;

import ai.wanaku.api.types.ResourceReference;

public class ResourcePayload implements ProvisionAwarePayload<ResourceReference> {
    private ResourceReference resourceReference;
    private String configurationData;
    private String secretsData;

    @Override
    public ResourceReference getPayload() {
        return resourceReference;
    }

    public void setPayload(ResourceReference resourceReference) {
        this.resourceReference = resourceReference;
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
