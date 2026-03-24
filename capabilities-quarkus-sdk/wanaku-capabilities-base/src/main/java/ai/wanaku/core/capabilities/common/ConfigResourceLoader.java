package ai.wanaku.core.capabilities.common;

import java.net.URI;
import org.jboss.logging.Logger;
import ai.wanaku.capabilities.sdk.config.provider.api.ConfigResource;
import ai.wanaku.capabilities.sdk.config.provider.api.ConfigStore;
import ai.wanaku.capabilities.sdk.config.provider.api.DefaultConfigResource;
import ai.wanaku.capabilities.sdk.config.provider.api.NoopConfigStore;
import ai.wanaku.capabilities.sdk.config.provider.api.NoopSecretStore;
import ai.wanaku.capabilities.sdk.config.provider.api.SecretStore;
import ai.wanaku.capabilities.sdk.config.provider.file.ConfigFileStore;
import ai.wanaku.capabilities.sdk.config.provider.file.SecretFileStore;
import ai.wanaku.core.exchange.v1.ResourceRequest;
import ai.wanaku.core.exchange.v1.ToolInvokeRequest;
import ai.wanaku.core.util.StringHelper;

/**
 * Utility class for loading configuration resources from requests.
 * <p>
 * This class provides factory methods for creating {@link ConfigResource} instances
 * from tool invocation and resource requests. It handles the creation of appropriate
 * configuration and secret stores based on the URI schemes specified in the requests.
 * <p>
 * The loader supports:
 * <ul>
 *   <li>Loading configuration from file-based stores</li>
 *   <li>Loading secrets from file-based stores</li>
 *   <li>Falling back to no-op stores when URIs are not provided or schemes are unsupported</li>
 * </ul>
 *
 * @see ConfigResource
 * @see ConfigStore
 * @see SecretStore
 */
public final class ConfigResourceLoader {
    private static final Logger LOG = Logger.getLogger(ConfigResourceLoader.class);
    private static final NoopConfigStore NONE_CONFIG = new NoopConfigStore();
    private static final NoopSecretStore NONE_SECRET = new NoopSecretStore();

    /**
     * Constant identifying the file-based storage scheme.
     */
    public static final String FILE_STORE = "file";

    private ConfigResourceLoader() {}

    /**
     * Loads a configuration resource from a tool invocation request.
     * <p>
     * Extracts configuration and secret URIs from the request and creates
     * appropriate stores based on their schemes.
     *
     * @param request the tool invocation request containing configuration and secret URIs
     * @return a {@link ConfigResource} instance with loaded configuration and secrets
     */
    public static ConfigResource loadFromRequest(ToolInvokeRequest request) {
        final String cfgResourceRef = request.getConfigurationUri();
        final String secretResourceRef = request.getSecretsUri();
        return loadFromReference(cfgResourceRef, secretResourceRef);
    }

    /**
     * Loads a configuration resource from a resource request.
     * <p>
     * Extracts configuration and secret URIs from the request and creates
     * appropriate stores based on their schemes.
     *
     * @param request the resource request containing configuration and secret URIs
     * @return a {@link ConfigResource} instance with loaded configuration and secrets
     */
    public static ConfigResource loadFromRequest(ResourceRequest request) {
        final String cfgResourceRef = request.getConfigurationUri();
        final String secretResourceRef = request.getSecretsUri();
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

        LOG.debugf("Returning an empty NO-OP secret store");
        return NONE_SECRET;
    }

    private static ConfigStore createConfigFromRef(String cfgResourceRefs) {
        if (StringHelper.isNotEmpty(cfgResourceRefs)) {
            URI cfgUri = URI.create(cfgResourceRefs);
            if (cfgUri.getScheme().equals(FILE_STORE)) {
                return new ConfigFileStore(cfgUri);
            }
        }

        LOG.debugf("Creating an empty NO-OP config store");
        return NONE_CONFIG;
    }
}
