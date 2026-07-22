package ai.wanaku.operator.util;

import java.util.Base64;
import org.jboss.logging.Logger;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import ai.wanaku.capabilities.sdk.common.security.SecurityServiceConfig;
import ai.wanaku.capabilities.sdk.security.TokenEndpoint;
import ai.wanaku.operator.wanaku.WanakuTypes;

/**
 * Adapts the operator's {@link WanakuTypes.AuthSpec} to the SDK's
 * {@link SecurityServiceConfig} interface so that {@code ServiceAuthenticator}
 * can be used for OIDC token acquisition.
 *
 * <p>When a {@link KubernetesClient} is provided, the client secret is resolved
 * by reading the Kubernetes Secret directly via the API on each call to
 * {@link #getSecret()}, allowing the operator to pick up secret rotations
 * without a pod restart. Falls back to the {@code WANAKU_OIDC_CLIENT_SECRET}
 * environment variable when the Kubernetes Secret cannot be read.</p>
 */
public final class OperatorSecurityConfig implements SecurityServiceConfig {
    private static final Logger LOG = Logger.getLogger(OperatorSecurityConfig.class);

    static final String CLIENT_SECRET_ENV = "WANAKU_OIDC_CLIENT_SECRET";
    static final String SECRET_NAME_ENV = "WANAKU_OIDC_SECRET_NAME";
    static final String DEFAULT_SECRET_NAME = "wanaku-oidc";
    static final String SECRET_KEY = "client-secret";
    static final String DEFAULT_CLIENT_SECRET = "mypasswd";
    static final String CLIENT_ID = "wanaku-service";

    private final String tokenEndpoint;
    private final KubernetesClient kubernetesClient;
    private final String namespace;

    /**
     * Creates a config that resolves the client secret from the environment
     * variable only (legacy behavior, no dynamic refresh).
     *
     * @param authSpec the auth specification from the WanakuRouter
     */
    public OperatorSecurityConfig(WanakuTypes.AuthSpec authSpec) {
        this(authSpec, null, null);
    }

    /**
     * Creates a config that resolves the client secret from a Kubernetes Secret
     * via the API on each call to {@link #getSecret()}, falling back to the
     * environment variable when the API read fails.
     *
     * @param authSpec the auth specification from the WanakuRouter
     * @param kubernetesClient the Kubernetes client for reading Secrets (may be null)
     * @param namespace the namespace containing the OIDC secret (may be null)
     */
    public OperatorSecurityConfig(WanakuTypes.AuthSpec authSpec, KubernetesClient kubernetesClient, String namespace) {
        this.tokenEndpoint = buildTokenEndpoint(authSpec);
        this.kubernetesClient = kubernetesClient;
        this.namespace = namespace;
    }

    @Override
    public String getClientId() {
        return CLIENT_ID;
    }

    @Override
    public String getSecret() {
        if (kubernetesClient != null && namespace != null) {
            String fromK8s = readSecretFromKubernetes();
            if (fromK8s != null) {
                return fromK8s;
            }
        }
        return resolveClientSecret();
    }

    @Override
    public String getTokenEndpoint() {
        return tokenEndpoint;
    }

    public static boolean isAuthEnabled(WanakuTypes.AuthSpec authSpec) {
        return authSpec != null
                && authSpec.getAuthServer() != null
                && !authSpec.getAuthServer().isBlank();
    }

    /**
     * Reads the OIDC client secret from a Kubernetes Secret via the API.
     *
     * @return the decoded secret value, or null if the secret cannot be read
     */
    private String readSecretFromKubernetes() {
        String secretName = resolveSecretName();
        try {
            Secret secret = kubernetesClient
                    .secrets()
                    .inNamespace(namespace)
                    .withName(secretName)
                    .get();
            if (secret != null && secret.getData() != null) {
                String encoded = secret.getData().get(SECRET_KEY);
                if (encoded != null && !encoded.isBlank()) {
                    return new String(Base64.getDecoder().decode(encoded));
                }
            }
            LOG.debugf(
                    "Kubernetes Secret '%s' in namespace '%s' does not contain key '%s'",
                    secretName, namespace, SECRET_KEY);
        } catch (Exception e) {
            LOG.warnf(
                    "Failed to read OIDC secret '%s' from namespace '%s': %s. "
                            + "Falling back to environment variable.",
                    secretName, namespace, e.getMessage());
        }
        return null;
    }

    /**
     * Resolves the name of the Kubernetes Secret that contains the OIDC
     * client secret, using the {@code WANAKU_OIDC_SECRET_NAME} environment
     * variable or the default {@value #DEFAULT_SECRET_NAME}.
     *
     * @return the secret name
     */
    static String resolveSecretName() {
        String name = System.getenv(SECRET_NAME_ENV);
        return (name != null && !name.isBlank()) ? name : DEFAULT_SECRET_NAME;
    }

    private static String buildTokenEndpoint(WanakuTypes.AuthSpec authSpec) {
        String authServer = authSpec.getAuthServer();
        if (authServer.endsWith("/")) {
            authServer = authServer.substring(0, authServer.length() - 1);
        }

        String realm = authSpec.getAuthRealm();
        if (realm == null || realm.isBlank()) {
            realm = EnvironmentVariables.DEFAULT_AUTH_REALM;
        }

        return TokenEndpoint.fromBaseUrl(authServer + "/realms/" + realm);
    }

    public static String resolveClientSecret() {
        String secret = System.getenv(CLIENT_SECRET_ENV);
        return (secret != null && !secret.isBlank()) ? secret : DEFAULT_CLIENT_SECRET;
    }
}
