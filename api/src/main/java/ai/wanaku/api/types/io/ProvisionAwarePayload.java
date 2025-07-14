package ai.wanaku.api.types.io;

public interface ProvisionAwarePayload<T> {

    T getPayload();
    String getConfigurationData();
    String getSecretsData();
}
