package ai.wanaku.backend.proxies;

import ai.wanaku.backend.support.ProvisioningReference;
import ai.wanaku.capabilities.sdk.api.exceptions.ServiceUnavailableException;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;
import ai.wanaku.core.exchange.Configuration;
import ai.wanaku.core.exchange.PropertySchema;
import ai.wanaku.core.exchange.ProvisionReply;
import ai.wanaku.core.exchange.ProvisionRequest;
import ai.wanaku.core.exchange.ProvisionerGrpc;
import ai.wanaku.core.exchange.Secret;
import io.grpc.ManagedChannel;
import java.net.URI;
import java.util.Map;

final class ProxyHelper {

    static ProvisioningReference provision(
            Configuration cfg, Secret secret, ManagedChannel channel, ServiceTarget service) {
        ProvisionRequest provisionRequest = ProvisionRequest.newBuilder()
                .setConfiguration(cfg)
                .setSecret(secret)
                .build();

        ProvisionerGrpc.ProvisionerBlockingStub blockingStub = ProvisionerGrpc.newBlockingStub(channel);

        try {
            ProvisionReply inquire = blockingStub.provision(provisionRequest);

            final String configurationUri = inquire.getConfigurationUri();
            final String secretUri = inquire.getSecretUri();
            final Map<String, PropertySchema> propertiesMap = inquire.getPropertiesMap();

            return new ProvisioningReference(URI.create(configurationUri), URI.create(secretUri), propertiesMap);
        } catch (Exception e) {
            throw ServiceUnavailableException.forAddress(service.toAddress());
        }
    }
}
