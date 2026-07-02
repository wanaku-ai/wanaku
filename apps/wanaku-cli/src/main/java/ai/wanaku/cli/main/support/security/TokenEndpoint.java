package ai.wanaku.cli.main.support.security;

/**
 * Utility class for constructing OAuth2 token endpoint URLs.
 * Provides methods for creating endpoint URLs either directly or from base URLs.
 */
public final class TokenEndpoint {

    /**
     * Private constructor to prevent instantiation.
     */
    private TokenEndpoint() {}

    /**
     * Returns the provided URI directly as the token endpoint.
     *
     * @param uri The complete token endpoint URI.
     * @return The same URI.
     */
    public static String direct(String uri) {
        return uri;
    }

    /**
     * Constructs a token endpoint URL by appending the standard OpenID Connect path to a base URL.
     *
     * @param baseUrl The base URL of the authentication server.
     * @return The complete token endpoint URL.
     */
    public static String fromBaseUrl(String baseUrl) {
        return stripTrailingSlash(baseUrl) + "/protocol/openid-connect/token";
    }

    /**
     * Constructs a discovery URL using the Keycloak-native realm path when a realm is provided,
     * or falls back to the Quarkus OIDC proxy path otherwise.
     *
     * @param baseUrl The base URL of the authentication server.
     * @param realm   The authentication realm, or {@code null} if not applicable.
     * @return The complete discovery URL.
     */
    public static String forDiscovery(String baseUrl, String realm) {
        String url = stripTrailingSlash(baseUrl);
        if (realm != null && !realm.isBlank()) {
            return url + "/realms/" + realm.strip();
        }
        return url + "/q/oidc/";
    }

    private static String stripTrailingSlash(String url) {
        if (url != null && url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }
}
