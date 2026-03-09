package ai.wanaku.backend.bridge;

import ai.wanaku.backend.support.ProvisioningReference;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;

/**
 * Interface for provisioning configuration and secrets to a remote service.
 */
public interface ProvisionBridge {

    /**
     * Provisions configuration and secrets to a remote service.
     *
     * @param name the name identifier for the configuration and secrets
     * @param configData the configuration data to provision
     * @param secretsData the secrets data to provision
     * @param service the target service to provision to
     * @return a provisioning reference containing URIs and properties
     */
    ProvisioningReference provision(String name, String configData, String secretsData, ServiceTarget service);
}
