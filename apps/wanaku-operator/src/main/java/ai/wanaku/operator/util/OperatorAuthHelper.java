package ai.wanaku.operator.util;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import org.jboss.logging.Logger;
import ai.wanaku.operator.wanaku.WanakuTypes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Obtains and caches OIDC bearer tokens for operator-to-router communication.
 *
 * <p>When the router has OIDC enabled, the operator's REST client must present a
 * bearer token. This helper uses the {@code client_credentials} grant to obtain
 * a token from the Keycloak token endpoint configured in the
 * {@link WanakuTypes.AuthSpec} of the WanakuRouter CR.</p>
 *
 * <p>Tokens are cached and refreshed when they expire (with a safety margin).
 * The client secret is read from the {@value #CLIENT_SECRET_ENV} environment
 * variable, defaulting to {@value #DEFAULT_CLIENT_SECRET}.</p>
 */
public final class OperatorAuthHelper {
    private static final Logger LOG = Logger.getLogger(OperatorAuthHelper.class);

    static final String CLIENT_SECRET_ENV = "WANAKU_OIDC_CLIENT_SECRET";
    static final String DEFAULT_CLIENT_SECRET = "mypasswd";
    static final String CLIENT_ID = "wanaku-service";

    /** Safety margin: refresh token 30 seconds before actual expiry. */
    private static final long EXPIRY_MARGIN_SECONDS = 30;

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    private String cachedToken;
    private Instant tokenExpiry;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OperatorAuthHelper() {
        this.httpClient = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Package-private constructor for testing with custom HTTP client and mapper.
     *
     * @param httpClient the HTTP client to use
     * @param objectMapper the ObjectMapper for JSON parsing
     */
    OperatorAuthHelper(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns a valid bearer token for the given auth configuration, obtaining or
     * refreshing it as needed.
     *
     * @param authSpec the auth configuration from the WanakuRouter CR (must not be null)
     * @return the bearer token string
     * @throws IOException if the token request fails
     */
    public String getToken(WanakuTypes.AuthSpec authSpec) throws IOException {
        if (cachedToken != null && tokenExpiry != null && Instant.now().isBefore(tokenExpiry)) {
            return cachedToken;
        }

        return fetchToken(authSpec);
    }

    /**
     * Invalidates the cached token so the next call to {@link #getToken} will
     * fetch a fresh one. Useful on HTTP 401 to force a retry with a new token.
     */
    public void invalidateToken() {
        cachedToken = null;
        tokenExpiry = null;
    }

    /**
     * Builds the OIDC token endpoint URL from the auth server and realm.
     *
     * @param authSpec the auth configuration containing the auth server URL
     * @return the token endpoint URL
     */
    static String buildTokenEndpoint(WanakuTypes.AuthSpec authSpec) {
        String authServer = authSpec.getAuthServer();
        if (authServer.endsWith("/")) {
            authServer = authServer.substring(0, authServer.length() - 1);
        }

        String realm = authSpec.getAuthRealm();
        if (realm == null || realm.isBlank()) {
            realm = EnvironmentVariables.DEFAULT_AUTH_REALM;
        }

        return authServer + "/realms/" + realm + "/protocol/openid-connect/token";
    }

    /**
     * Fetches a new token from the OIDC token endpoint using client_credentials grant.
     */
    private String fetchToken(WanakuTypes.AuthSpec authSpec) throws IOException {
        String tokenEndpoint = buildTokenEndpoint(authSpec);
        String clientSecret = resolveClientSecret();

        String body = "grant_type=client_credentials" + "&client_id=" + CLIENT_ID + "&client_secret=" + clientSecret;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenEndpoint))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(HTTP_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        LOG.debugf("Requesting OIDC token from %s", tokenEndpoint);

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Token request interrupted", e);
        }

        if (response.statusCode() != 200) {
            throw new IOException(
                    "OIDC token request failed with HTTP " + response.statusCode() + ": " + response.body());
        }

        return parseTokenResponse(response.body());
    }

    /**
     * Parses the OIDC token response JSON and caches the access token with its expiry.
     *
     * @param responseBody the raw JSON response body
     * @return the access token string
     * @throws IOException if the response cannot be parsed or lacks required fields
     */
    String parseTokenResponse(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);

        JsonNode accessTokenNode = root.get("access_token");
        if (accessTokenNode == null || accessTokenNode.isNull()) {
            throw new IOException("OIDC token response does not contain 'access_token'");
        }

        cachedToken = accessTokenNode.asText();

        JsonNode expiresInNode = root.get("expires_in");
        if (expiresInNode != null && !expiresInNode.isNull()) {
            long expiresIn = expiresInNode.asLong();
            tokenExpiry = Instant.now().plusSeconds(Math.max(0, expiresIn - EXPIRY_MARGIN_SECONDS));
        } else {
            // No expiry info; don't cache, force re-fetch next time
            tokenExpiry = Instant.now();
        }

        LOG.debugf(
                "Obtained OIDC token, expires in %s seconds",
                expiresInNode != null ? expiresInNode.asText() : "unknown");

        return cachedToken;
    }

    /**
     * Resolves the client secret from the environment variable, falling back to the default.
     *
     * @return the OIDC client secret
     */
    static String resolveClientSecret() {
        String secret = System.getenv(CLIENT_SECRET_ENV);
        return (secret != null && !secret.isBlank()) ? secret : DEFAULT_CLIENT_SECRET;
    }

    /**
     * Checks whether the given auth configuration indicates OIDC is enabled.
     *
     * @param authSpec the auth spec from the WanakuRouter CR (may be null)
     * @return true if OIDC authentication should be used
     */
    public static boolean isAuthEnabled(WanakuTypes.AuthSpec authSpec) {
        return authSpec != null
                && authSpec.getAuthServer() != null
                && !authSpec.getAuthServer().isBlank();
    }
}
