package ai.wanaku.cli.main.support;

import ai.wanaku.cli.main.support.security.TokenRefresher;
import ai.wanaku.cli.main.support.security.TokenRefresher.RefreshResult;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import java.io.IOException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A JAX-RS client request filter that automatically adds authentication headers
 * to outgoing requests based on stored credentials. Automatically refreshes
 * expired tokens using the stored refresh token.
 */
public class AuthenticationInterceptor implements ClientRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(AuthenticationInterceptor.class);

    /**
     * Buffer time in seconds before token expiry to trigger refresh.
     * Tokens will be refreshed when they are within this many seconds of expiring.
     */
    private static final long REFRESH_BUFFER_SECONDS = 30;

    private final AuthCredentialStore credentialStore;
    private final TokenRefresher tokenRefresher;

    public AuthenticationInterceptor() {
        this.credentialStore = new AuthCredentialStore();
        this.tokenRefresher = new TokenRefresher();
    }

    public AuthenticationInterceptor(AuthCredentialStore credentialStore) {
        this.credentialStore = credentialStore;
        this.tokenRefresher = new TokenRefresher();
    }

    public AuthenticationInterceptor(AuthCredentialStore credentialStore, TokenRefresher tokenRefresher) {
        this.credentialStore = credentialStore;
        this.tokenRefresher = tokenRefresher;
    }

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        String authMode = credentialStore.getAuthMode();

        if ("none".equals(authMode) || !credentialStore.hasCredentials()) {
            return;
        }

        String apiToken = getValidAccessToken();
        if (apiToken != null && !apiToken.trim().isEmpty()) {
            requestContext.getHeaders().add(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken);
        }
    }

    /**
     * Gets a valid access token, refreshing it if necessary.
     *
     * @return a valid access token, or null if unavailable
     */
    private String getValidAccessToken() {
        String apiToken = credentialStore.getApiToken();
        if (apiToken == null || apiToken.trim().isEmpty()) {
            return null;
        }

        // Check if token is expired or about to expire
        if (isTokenExpiredOrExpiring()) {
            LOG.debug("Token is expired or about to expire, attempting refresh");
            if (tryRefreshToken()) {
                apiToken = credentialStore.getApiToken();
            } else {
                LOG.warn("Token refresh failed, using existing token");
            }
        }

        return apiToken;
    }

    /**
     * Checks if the stored token is expired or about to expire.
     *
     * @return true if the token needs to be refreshed
     */
    private boolean isTokenExpiredOrExpiring() {
        long expiryEpochSeconds = credentialStore.getTokenExpiry();
        if (expiryEpochSeconds == 0) {
            // No expiry stored - might be a token from before this feature
            // For backwards compatibility, we don't refresh
            return false;
        }

        long currentEpochSeconds = Instant.now().getEpochSecond();
        long secondsUntilExpiry = expiryEpochSeconds - currentEpochSeconds;

        return secondsUntilExpiry <= REFRESH_BUFFER_SECONDS;
    }

    /**
     * Attempts to refresh the access token using the stored refresh token.
     *
     * @return true if refresh was successful, false otherwise
     */
    private boolean tryRefreshToken() {
        String refreshToken = credentialStore.getRefreshToken();
        String authServerUrl = credentialStore.getAuthServerUrl();
        String clientId = credentialStore.getClientId();

        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            LOG.warn("No refresh token available for token refresh");
            return false;
        }

        if (authServerUrl == null || authServerUrl.trim().isEmpty()) {
            LOG.warn("No auth server URL available for token refresh");
            return false;
        }

        if (clientId == null || clientId.trim().isEmpty()) {
            // Fall back to default client ID for backwards compatibility
            clientId = "admin-cli";
        }

        try {
            RefreshResult result = tokenRefresher.refresh(refreshToken, authServerUrl, clientId);

            // Store the new tokens
            credentialStore.storeApiToken(result.getAccessToken());
            credentialStore.storeRefreshToken(result.getRefreshToken());
            credentialStore.storeTokenExpiry(result.getExpiryEpochSeconds());

            LOG.info("Successfully refreshed access token");
            return true;
        } catch (TokenRefresher.TokenRefreshException e) {
            LOG.error("Failed to refresh token: {}", e.getMessage());
            return false;
        }
    }
}
