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
        return baseUrl + "/protocol/openid-connect/token";
    }

    /**
     * Constructs a token endpoint URL by appending the discovery OpenID Connect path to a base URL.
     *
     * @param baseUrl The base URL of the authentication server.
     * @return The complete token endpoint URL.
     */
    public static String forDiscovery(String baseUrl) {
        return baseUrl + "/q/oidc/";
    }
}
