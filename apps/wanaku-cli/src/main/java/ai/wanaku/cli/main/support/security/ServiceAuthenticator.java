package ai.wanaku.cli.main.support.security;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.nimbusds.oauth2.sdk.AccessTokenResponse;
import com.nimbusds.oauth2.sdk.AuthorizationGrant;
import com.nimbusds.oauth2.sdk.GeneralException;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.RefreshTokenGrant;
import com.nimbusds.oauth2.sdk.ResourceOwnerPasswordCredentialsGrant;
import com.nimbusds.oauth2.sdk.TokenErrorResponse;
import com.nimbusds.oauth2.sdk.TokenRequest;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.oauth2.sdk.auth.ClientAuthentication;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.oauth2.sdk.token.AccessToken;
import com.nimbusds.oauth2.sdk.token.RefreshToken;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;

/**
 * Handles OAuth2 authentication with the Wanaku.
 * Manages access tokens, refresh tokens, and automatic token renewal.
 */
public class ServiceAuthenticator {
    private static final Logger LOG = LoggerFactory.getLogger(ServiceAuthenticator.class);
    private final SecurityServiceConfig config;
    private final boolean insecure;
    private AccessToken accessToken;
    private RefreshToken refreshToken;
    private Instant creationTime;

    /**
     * Creates a new authenticator and obtains an initial access token.
     *
     * @param config The security service configuration containing OAuth2 credentials.
     */
    public ServiceAuthenticator(SecurityServiceConfig config) {
        this(config, false);
    }

    /**
     * Creates a new authenticator and obtains an initial access token.
     *
     * @param config The security service configuration containing OAuth2 credentials.
     * @param insecure Whether to skip SSL certificate verification.
     */
    public ServiceAuthenticator(SecurityServiceConfig config, boolean insecure) {
        this.config = config;
        this.insecure = insecure;

        renewToken(config);

        LOG.info("Received token with a lifetime of {} seconds", accessToken.getLifetime());
    }

    /**
     * Renews the access token using either client credentials or refresh token grant.
     *
     * @param config The security service configuration.
     */
    private void renewToken(SecurityServiceConfig config) {
        final TokenRequest request = createTokenRequest(config);
        requestToken(request);
    }

    /**
     * Creates an OAuth2 token request using appropriate grant type.
     *
     * @param config The security service configuration.
     * @return The configured token request.
     */
    private TokenRequest createTokenRequest(SecurityServiceConfig config) {
        final URI tokenEndpoint = resolveTokenEndpointUri(config);
        ClientID clientID = new ClientID(config.getClientId());

        TokenRequest request;
        if (refreshToken == null) {
            // Construct the password grant from the username and password
            AuthorizationGrant passwordGrant =
                    new ResourceOwnerPasswordCredentialsGrant(config.getUsername(), new Secret(config.getPassword()));

            request = new TokenRequest(tokenEndpoint, clientID, passwordGrant, null);
        } else {
            AuthorizationGrant refreshTokenGrant = new RefreshTokenGrant(refreshToken);
            request = new TokenRequest(tokenEndpoint, clientID, refreshTokenGrant, null);
        }

        return request;
    }

    /*
     * We cannot use OIDCProviderMetadata.resolve directly, because it validates the provided
     * config endpoint with the issuer endpoint. However, because Wanaku typically uses the
     * OIDC Proxy, they are not the same (which causes it to throw a GeneralException).
     * This mimics the resolve logic, but ignores the validation and other things we don't
     * need.
     */
    private Issuer resolveIssuer(SecurityServiceConfig config) {
        // The OpenID provider issuer URL (strip trailing slash to avoid malformed paths)
        String endpoint = config.getTokenEndpoint();
        if (endpoint != null && endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        Issuer issuer = new Issuer(endpoint);
        try {
            OIDCProviderMetadata op = OIDCHttpUtils.performHttpRequest(issuer, insecure);
            return op.getIssuer();
        } catch (GeneralException e) {
            throw new ServiceAuthException("Unable to resolve token endpoint URI: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new ServiceAuthException("I/O error while resolving token endpoint URI: " + e.getMessage(), e);
        }
    }

    private URI resolveTokenEndpointUri(SecurityServiceConfig config) {

        /* We cannot use Wanaku's base address because it's typically behind the OIDC
         * proxy. Therefore, we first need to resolve the issuer address, and then
         * use the issuer address to resolve the OIDC metadata.
         */
        Issuer issuer = new Issuer(resolveIssuer(config));
        try {
            OIDCProviderMetadata resolvedOp = OIDCHttpUtils.performHttpRequest(issuer, insecure);
            return resolvedOp.getTokenEndpointURI();
        } catch (GeneralException e) {
            throw new ServiceAuthException("Unable to resolve token endpoint URI: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new ServiceAuthException("I/O error while resolving token endpoint URI: " + e.getMessage(), e);
        }
    }

    /**
     * Creates client authentication for OAuth2 requests.
     *
     * @param config The security service configuration containing client credentials.
     * @return The client authentication object.
     */
    private static ClientAuthentication getClientAuthentication(SecurityServiceConfig config) {
        // The credentials to authenticate the client at the token endpoint

        ClientID clientID = new ClientID(config.getClientId());
        Secret clientSecret = new Secret(config.getSecret());
        return new ClientSecretBasic(clientID, clientSecret);
    }

    /**
     * Executes the token request and updates internal token state.
     *
     * @param request The OAuth2 token request to execute.
     * @throws ServiceAuthException If authentication fails.
     */
    private void requestToken(TokenRequest request) {
        TokenResponse response = null;
        try {
            HTTPRequest httpRequest = request.toHTTPRequest();

            if (insecure) {
                try {
                    httpRequest.setSSLSocketFactory(InsecureSSLHelper.getInsecureSSLSocketFactory());
                    httpRequest.setHostnameVerifier(InsecureSSLHelper.getInsecureHostnameVerifier());
                } catch (Exception e) {
                    throw new ServiceAuthException("Failed to configure insecure SSL: " + e.getMessage(), e);
                }
            }

            response = TokenResponse.parse(httpRequest.send());
        } catch (IOException | ParseException e) {
            throw new ServiceAuthException(e);
        }

        if (!response.indicatesSuccess()) {
            // We got an error response...
            TokenErrorResponse errorResponse = response.toErrorResponse();
            LOG.error(
                    "Unable to authenticate with service: {}",
                    errorResponse.getErrorObject().getDescription());
            throw new ServiceAuthException(errorResponse.getErrorObject().getDescription());
        }

        AccessTokenResponse successResponse = response.toSuccessResponse();

        // Get the access token
        accessToken = successResponse.getTokens().getAccessToken();
        refreshToken = successResponse.getTokens().getRefreshToken();
        creationTime = Instant.now();
    }

    /**
     * Returns a valid access token, renewing it if necessary.
     *
     * @return A valid access token value.
     */
    public String currentValidAccessToken() {
        final long elapsedSeconds =
                Duration.between(creationTime, Instant.now()).getSeconds();

        if (elapsedSeconds >= (accessToken.getLifetime() - 30)) {
            LOG.info("The token is about to expire. Renewing token to prevent that from happening ...");
            renewToken(config);
        }

        return accessToken.getValue();
    }

    public String currentValidRefreshToken() {
        return refreshToken.getValue();
    }

    /**
     * Returns the expiry time of the current access token as epoch seconds.
     *
     * @return The token expiry time in epoch seconds.
     */
    public long getTokenExpiryEpochSeconds() {
        return creationTime.getEpochSecond() + accessToken.getLifetime();
    }

    /**
     * Formats the access token as an Authorization header value.
     *
     * @return The formatted Bearer token header value.
     */
    public String toHeaderValue() {
        return String.format("Bearer %s", currentValidAccessToken());
    }
}
