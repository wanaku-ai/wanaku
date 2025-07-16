package ai.wanaku.core.capabilities.common;

import ai.wanaku.core.config.provider.api.ConfigResource;
import ai.wanaku.core.config.provider.api.ConfigStore;
import ai.wanaku.core.config.provider.api.DefaultConfigResource;
import ai.wanaku.core.config.provider.api.NoopConfigStore;
import ai.wanaku.core.config.provider.api.NoopSecretStore;
import ai.wanaku.core.config.provider.api.SecretStore;
import ai.wanaku.core.config.provider.file.ConfigFileStore;
import ai.wanaku.core.config.provider.file.SecretFileStore;
import ai.wanaku.core.exchange.ResourceRequest;
import ai.wanaku.core.exchange.ToolInvokeRequest;
import ai.wanaku.core.util.StringHelper;
import java.net.URI;
import org.jboss.logging.Logger;

public final class ConfigResourceLoader {
    private static final Logger LOG = Logger.getLogger(ConfigResourceLoader.class);
    public static final String FILE_STORE = "file";

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
        ConfigStore configStore = createConfigFromRef(cfgResourceRefs);

        final SecretStore secretStore = createSecretFromRef(secretResourceRefs);

        return new DefaultConfigResource(configStore, secretStore);
    }

    private static SecretStore createSecretFromRef(String secretResourceRefs) {
        if (StringHelper.isNotEmpty(secretResourceRefs)) {
            URI secretUri = URI.create(secretResourceRefs);
            if (secretUri.getScheme().equals(FILE_STORE)) {
                return new SecretFileStore(secretUri);
            }
        }

        LOG.warnf("Creating a new NO-OP secret store");
        return new NoopSecretStore();
    }

    private static ConfigStore createConfigFromRef(String cfgResourceRefs) {
        if (StringHelper.isNotEmpty(cfgResourceRefs)) {
            URI cfgUri = URI.create(cfgResourceRefs);
            if (cfgUri.getScheme().equals(FILE_STORE)) {
                return new ConfigFileStore(cfgUri);
            }
        }

        LOG.warnf("Creating a new NO-OP config store");
        return new NoopConfigStore();
    }
}
