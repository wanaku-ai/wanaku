package ai.wanaku.core.capabilities.common;

import ai.wanaku.core.config.provider.api.ConfigResource;
import ai.wanaku.core.config.provider.api.ConfigStore;
import ai.wanaku.core.config.provider.api.DefaultConfigResource;
import ai.wanaku.core.config.provider.api.SecretStore;
import ai.wanaku.core.config.provider.file.ConfigFileStore;
import ai.wanaku.core.config.provider.file.SecretFileStore;
import ai.wanaku.core.exchange.ResourceRequest;
import ai.wanaku.core.exchange.ToolInvokeRequest;
import java.net.URI;
import java.util.List;

public final class ConfigResourceLoader {
    private ConfigResourceLoader() {}

    public static ConfigResource loadFromRequest(ToolInvokeRequest request) {
        final String cfgResourceRef = request.getConfigurationURI();
        final String secretResourceRef = request.getSecretsURI();
        return loadFromReference(cfgResourceRef, secretResourceRef);
    }

    public static ConfigResource loadFromRequest(ResourceRequest request) {
        final String cfgResourceRef = request.getConfigurationURI();
        final String secretResourceRef = request.getSecretsURI();
        return loadFromReference(cfgResourceRef, secretResourceRef);
    }

    private static ConfigResource loadFromReference(String cfgResourceRefs, String secretResourceRefs) {
        URI cfgUri = URI.create(cfgResourceRefs);
        URI secretUri = URI.create(secretResourceRefs);

        ConfigStore configStore = null;
        SecretStore secretStore = null;

        if (cfgUri.getScheme().equals("file")) {
            configStore = new ConfigFileStore(cfgUri);
        }

        if (secretUri.getScheme().equals("file")) {
            secretStore = new SecretFileStore(secretUri);
        }

        return new DefaultConfigResource(configStore, secretStore);
    }
}
