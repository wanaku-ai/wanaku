package ai.wanaku.core.security.auth;

/**
 * Represents an OAuth2/OIDC token response containing access and refresh tokens.
 */
public class TokenResponse {
    private final String accessToken;
    private final String refreshToken;
    private final Long expiresIn;
    private final String tokenType;

    public TokenResponse(String accessToken, String refreshToken, Long expiresIn, String tokenType) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
        this.tokenType = tokenType;
    }

    /**
     * Gets the access token.
     *
     * @return the access token
     */
    public String getAccessToken() {
        return accessToken;
    }

    /**
     * Gets the refresh token.
     *
     * @return the refresh token, or null if not provided
     */
    public String getRefreshToken() {
        return refreshToken;
    }

    /**
     * Gets the token expiration time in seconds.
     *
     * @return expiration time in seconds, or null if not provided
     */
    public Long getExpiresIn() {
        return expiresIn;
    }

    /**
     * Gets the token type (e.g., "Bearer").
     *
     * @return the token type
     */
    public String getTokenType() {
        return tokenType;
    }
}
