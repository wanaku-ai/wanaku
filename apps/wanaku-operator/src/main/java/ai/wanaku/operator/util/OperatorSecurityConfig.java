package ai.wanaku.operator.util;

import ai.wanaku.capabilities.sdk.common.security.SecurityServiceConfig;
import ai.wanaku.capabilities.sdk.security.TokenEndpoint;
import ai.wanaku.operator.wanaku.WanakuTypes;

/**
 * Adapts the operator's {@link WanakuTypes.AuthSpec} to the SDK's
 * {@link SecurityServiceConfig} interface so that {@code ServiceAuthenticator}
 * can be used for OIDC token acquisition.
 */
public final class OperatorSecurityConfig implements SecurityServiceConfig {
    static final String CLIENT_SECRET_ENV = "WANAKU_OIDC_CLIENT_SECRET";
    static final String DEFAULT_CLIENT_SECRET = "mypasswd";
    static final String CLIENT_ID = "wanaku-service";

    private final String tokenEndpoint;
    private final String clientSecret;

    public OperatorSecurityConfig(WanakuTypes.AuthSpec authSpec) {
        this.tokenEndpoint = buildTokenEndpoint(authSpec);
        this.clientSecret = resolveClientSecret();
    }

    @Override
    public String getClientId() {
        return CLIENT_ID;
    }

    @Override
    public String getSecret() {
        return clientSecret;
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
