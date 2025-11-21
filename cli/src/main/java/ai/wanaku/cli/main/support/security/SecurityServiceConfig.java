package ai.wanaku.cli.main.support.security;

/**
 * Common Security configuration
 */
public interface SecurityServiceConfig {

    /**
     * Returns the client ID used for authentication with the Discovery Service.
     *
     * @return The client ID as a {@code String}.
     */
    String getClientId();

    /**
     * Returns the secret used for authentication with the Discovery Service.
     *
     * @return The secret as a {@code String}.
     */
    String getSecret();

    /**
     * Returns the URL for the OpenID Connect token endpoint (e.g., http://address/.well-known/openid-configuration).
     *
     * @return The OpenID Connect token endpoint as a {@code String}.
     */
    String getTokenEndpoint();

    /**
     * The username for accessing the service
     * @return the user name
     */
    String getUsername();

    /**
     * The password for accessing the service
     * @return the password as a string
     */
    String getPassword();
}
