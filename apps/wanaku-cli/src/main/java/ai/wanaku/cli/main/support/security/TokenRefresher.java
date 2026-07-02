package ai.wanaku.cli.main.support.security;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.nimbusds.oauth2.sdk.AccessTokenResponse;
import com.nimbusds.oauth2.sdk.AuthorizationGrant;
import com.nimbusds.oauth2.sdk.GeneralException;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.RefreshTokenGrant;
import com.nimbusds.oauth2.sdk.TokenErrorResponse;
import com.nimbusds.oauth2.sdk.TokenRequest;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.oauth2.sdk.token.AccessToken;
import com.nimbusds.oauth2.sdk.token.RefreshToken;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;

/**
 * Handles OAuth2 token refresh using stored refresh tokens.
 * This class is designed to be used by the AuthenticationInterceptor
 * to automatically refresh expired access tokens.
 */
public class TokenRefresher {
    private static final Logger LOG = LoggerFactory.getLogger(TokenRefresher.class);

    private final boolean insecure;

    public TokenRefresher(boolean insecure) {
        this.insecure = insecure;
    }

    /**
     * Result of a token refresh operation.
     */
    public static class RefreshResult {
        private final String accessToken;
        private final String refreshToken;
        private final long expiryEpochSeconds;

        public RefreshResult(String accessToken, String refreshToken, long expiryEpochSeconds) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiryEpochSeconds = expiryEpochSeconds;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public long getExpiryEpochSeconds() {
            return expiryEpochSeconds;
        }
    }

    /**
     * Refreshes an access token using a refresh token.
     *
     * @param refreshTokenValue the refresh token value
     * @param authServerUrl the authentication server URL (e.g., http://localhost:8080)
     * @param clientId the OAuth2 client ID
     * @return the refresh result containing new tokens and expiry
     * @throws TokenRefreshException if the refresh fails
     */
    public RefreshResult refresh(String refreshTokenValue, String authServerUrl, String clientId) {
        try {
            URI tokenEndpoint = resolveTokenEndpointUri(authServerUrl);
            RefreshToken refreshToken = new RefreshToken(refreshTokenValue);
            AuthorizationGrant refreshTokenGrant = new RefreshTokenGrant(refreshToken);
            ClientID clientID = new ClientID(clientId);

            TokenRequest request = new TokenRequest(tokenEndpoint, clientID, refreshTokenGrant, null);

            LOG.debug("Sending token refresh request to {}", tokenEndpoint);
            HTTPRequest httpRequest = request.toHTTPRequest();

            if (insecure) {
                try {
                    httpRequest.setSSLSocketFactory(InsecureSSLHelper.getInsecureSSLSocketFactory());
                    httpRequest.setHostnameVerifier(InsecureSSLHelper.getInsecureHostnameVerifier());
                } catch (Exception e) {
                    throw new TokenRefreshException("Failed to configure insecure SSL: " + e.getMessage(), e);
                }
            }

            TokenResponse response = TokenResponse.parse(httpRequest.send());

            if (!response.indicatesSuccess()) {
                TokenErrorResponse errorResponse = response.toErrorResponse();
                String errorDescription = errorResponse.getErrorObject().getDescription();
                LOG.error("Token refresh failed: {}", errorDescription);
                throw new TokenRefreshException("Token refresh failed: " + errorDescription);
            }

            AccessTokenResponse successResponse = response.toSuccessResponse();
            AccessToken newAccessToken = successResponse.getTokens().getAccessToken();
            RefreshToken newRefreshToken = successResponse.getTokens().getRefreshToken();

            long expiryEpochSeconds = Instant.now().getEpochSecond() + newAccessToken.getLifetime();
            LOG.info("Token refreshed successfully, new token expires in {} seconds", newAccessToken.getLifetime());

            return new RefreshResult(
                    newAccessToken.getValue(),
                    newRefreshToken != null ? newRefreshToken.getValue() : refreshTokenValue,
                    expiryEpochSeconds);
        } catch (IOException e) {
            throw new TokenRefreshException("I/O error during token refresh: " + e.getMessage(), e);
        } catch (ParseException e) {
            throw new TokenRefreshException("Failed to parse token response: " + e.getMessage(), e);
        }
    }

    private Issuer resolveIssuer(String authServerUrl) {
        String discoveryUrl = authServerUrl + "/q/oidc/";
        Issuer issuer = new Issuer(discoveryUrl);

        try {
            OIDCProviderMetadata op = OIDCHttpUtils.performHttpRequest(issuer, insecure);
            return op.getIssuer();
        } catch (GeneralException e) {
            throw new TokenRefreshException("Unable to resolve token endpoint URI: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new TokenRefreshException("I/O error while resolving token endpoint URI: " + e.getMessage(), e);
        }
    }

    private URI resolveTokenEndpointUri(String authServerUrl) {
        Issuer issuer = new Issuer(resolveIssuer(authServerUrl));

        try {
            OIDCProviderMetadata resolvedOp = OIDCHttpUtils.performHttpRequest(issuer, insecure);
            return resolvedOp.getTokenEndpointURI();
        } catch (GeneralException e) {
            throw new TokenRefreshException("Unable to resolve token endpoint URI: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new TokenRefreshException("I/O error while resolving token endpoint URI: " + e.getMessage(), e);
        }
    }

    /**
     * Exception thrown when token refresh fails.
     */
    public static class TokenRefreshException extends RuntimeException {
        public TokenRefreshException(String message) {
            super(message);
        }

        public TokenRefreshException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
