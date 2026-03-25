package ai.wanaku.cli.main.support;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * A credential store specifically designed for CLI authentication.
 * Handles secure storage and retrieval of authentication credentials
 * such as API tokens, refresh tokens, and authentication configuration.
 */
public class AuthCredentialStore {

    private static final String DEFAULT_CREDENTIALS_FILE = "~/.wanaku/credentials";
    private static final String API_TOKEN_KEY = "api.token";
    private static final String REFRESH_TOKEN_KEY = "refresh.token";
    private static final String AUTH_MODE_KEY = "auth.mode";
    private static final String AUTH_SERVER_URL_KEY = "auth.server.url";
    private static final String TOKEN_EXPIRY_KEY = "token.expiry";
    private static final String CLIENT_ID_KEY = "client.id";

    private final URI credentialsUri;

    public AuthCredentialStore() {
        this(resolveCredentialsPath(DEFAULT_CREDENTIALS_FILE));
    }

    public AuthCredentialStore(String credentialsPath) {
        this(resolveCredentialsPath(credentialsPath));
    }

    public AuthCredentialStore(URI credentialsUri) {
        this.credentialsUri = credentialsUri;
        ensureCredentialsDirectoryExists();
    }

    private String get(String name) {
        Properties props = loadProperties();
        return props.getProperty(name);
    }

    /**
     * Stores an API token for authentication.
     *
     * @param token the API token to store
     */
    public void storeApiToken(String token) {
        storeCredential(API_TOKEN_KEY, token);
    }

    /**
     * Retrieves the stored API token.
     *
     * @return the API token, or null if not found
     */
    public String getApiToken() {
        return get(API_TOKEN_KEY);
    }

    /**
     * Stores a refresh token for token renewal.
     *
     * @param refreshToken the refresh token to store
     */
    public void storeRefreshToken(String refreshToken) {
        storeCredential(REFRESH_TOKEN_KEY, refreshToken);
    }

    /**
     * Retrieves the stored refresh token.
     *
     * @return the refresh token, or null if not found
     */
    public String getRefreshToken() {
        return get(REFRESH_TOKEN_KEY);
    }

    /**
     * Stores the authentication mode.
     *
     * @param authMode the authentication mode (e.g., "token", "oauth2")
     */
    public void storeAuthMode(String authMode) {
        storeCredential(AUTH_MODE_KEY, authMode);
    }

    /**
     * Retrieves the authentication mode.
     *
     * @return the authentication mode, or "none" if not found
     */
    public String getAuthMode() {
        String mode = get(AUTH_MODE_KEY);
        return mode != null ? mode : "none";
    }

    /**
     * Stores the authentication server URL.
     *
     * @param serverUrl the authentication server URL
     */
    public void storeAuthServerUrl(String serverUrl) {
        storeCredential(AUTH_SERVER_URL_KEY, serverUrl);
    }

    /**
     * Retrieves the authentication server URL.
     *
     * @return the authentication server URL, or null if not found
     */
    public String getAuthServerUrl() {
        return get(AUTH_SERVER_URL_KEY);
    }

    /**
     * Stores the token expiry time as epoch seconds.
     *
     * @param expiryEpochSeconds the expiry time in epoch seconds
     */
    public void storeTokenExpiry(long expiryEpochSeconds) {
        storeCredential(TOKEN_EXPIRY_KEY, String.valueOf(expiryEpochSeconds));
    }

    /**
     * Retrieves the token expiry time as epoch seconds.
     *
     * @return the expiry time in epoch seconds, or 0 if not found
     */
    public long getTokenExpiry() {
        String expiry = get(TOKEN_EXPIRY_KEY);
        return expiry != null ? Long.parseLong(expiry) : 0;
    }

    /**
     * Stores the OAuth2 client ID.
     *
     * @param clientId the client ID
     */
    public void storeClientId(String clientId) {
        storeCredential(CLIENT_ID_KEY, clientId);
    }

    /**
     * Retrieves the OAuth2 client ID.
     *
     * @return the client ID, or null if not found
     */
    public String getClientId() {
        return get(CLIENT_ID_KEY);
    }

    /**
     * Clears all stored credentials.
     */
    public void clearCredentials() {
        try {
            Path credentialsPath = Paths.get(credentialsUri);
            if (Files.exists(credentialsPath)) {
                Files.delete(credentialsPath);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to clear credentials", e);
        }
    }

    /**
     * Checks if any credentials are stored.
     *
     * @return true if credentials exist, false otherwise
     */
    public boolean hasCredentials() {
        return getApiToken() != null || getRefreshToken() != null;
    }

    /**
     * Gets the credentials file URI.
     *
     * @return the URI of the credentials file
     */
    public URI getCredentialsFile() {
        return credentialsUri;
    }

    private Properties loadProperties() {
        Properties props = new Properties();
        try {
            Path credentialsPath = Paths.get(credentialsUri);
            if (Files.exists(credentialsPath)) {
                props.load(Files.newInputStream(credentialsPath));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load credentials", e);
        }
        return props;
    }

    private void storeCredential(String key, String value) {
        try {
            Path credentialsPath = Paths.get(credentialsUri);
            Properties props = loadProperties();

            props.setProperty(key, value);
            props.store(Files.newOutputStream(credentialsPath), "Wanaku CLI Authentication Credentials");
        } catch (IOException e) {
            throw new RuntimeException("Failed to store credential: " + key, e);
        }
    }

    private void ensureCredentialsDirectoryExists() {
        try {
            Path credentialsPath = Paths.get(credentialsUri);
            Path parentDir = credentialsPath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create credentials directory", e);
        }
    }

    private static URI resolveCredentialsPath(String credentialsPath) {
        String resolvedPath = credentialsPath;
        if (resolvedPath.startsWith("~/")) {
            resolvedPath = System.getProperty("user.home") + resolvedPath.substring(1);
        }
        return Paths.get(resolvedPath).toUri();
    }
}
