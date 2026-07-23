package ai.wanaku.cli.main.support;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Properties;
import org.jboss.logging.Logger;
import ai.wanaku.core.util.WanakuHome;

/**
 * A credential store specifically designed for CLI authentication.
 * Handles secure storage and retrieval of authentication credentials
 * such as API tokens, refresh tokens, and authentication configuration.
 */
public class AuthCredentialStore {

    private static final Logger LOG = Logger.getLogger(AuthCredentialStore.class);

    private static final String API_TOKEN_KEY = "api.token";
    private static final String REFRESH_TOKEN_KEY = "refresh.token";
    private static final String AUTH_MODE_KEY = "auth.mode";
    private static final String AUTH_SERVER_URL_KEY = "auth.server.url";
    private static final String TOKEN_EXPIRY_KEY = "token.expiry";
    private static final String CLIENT_ID_KEY = "client.id";
    private static final String REALM_KEY = "auth.realm";

    private final URI credentialsUri;

    public AuthCredentialStore() {
        this(resolveCredentialsPath(getDefaultCredentialsFile()));
    }

    private static String getDefaultCredentialsFile() {
        String envOverride = System.getenv("WANAKU_CREDENTIALS");
        if (envOverride != null && !envOverride.isBlank()) {
            return envOverride.trim();
        }
        return WanakuHome.get() + File.separator + "credentials";
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
     * Stores the Keycloak realm used for OIDC discovery.
     *
     * @param realm the realm value, or null to clear
     */
    public void storeRealm(String realm) {
        if (realm != null) {
            storeCredential(REALM_KEY, realm);
        } else {
            clearCredential(REALM_KEY);
        }
    }

    /**
     * Retrieves the stored Keycloak realm, or null if not found.
     *
     * @return the realm, or null
     */
    public String getRealm() {
        return get(REALM_KEY);
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
                try (InputStream in = Files.newInputStream(credentialsPath)) {
                    props.load(in);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load credentials", e);
        }
        return props;
    }

    private void clearCredential(String key) {
        try {
            Path credentialsPath = Paths.get(credentialsUri);
            if (!Files.exists(credentialsPath)) {
                return;
            }
            Properties props = loadProperties();
            props.remove(key);
            try (OutputStream out = Files.newOutputStream(credentialsPath)) {
                props.store(out, "Wanaku CLI Authentication Credentials");
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to clear credential: " + key, e);
        }
    }

    private void storeCredential(String key, String value) {
        try {
            Path credentialsPath = Paths.get(credentialsUri);
            Properties props = loadProperties();

            props.setProperty(key, value);

            // Make sure the file exists with owner-only permissions BEFORE writing secrets to it,
            // so access and refresh tokens are never momentarily world-readable.
            ensureSecureFile(credentialsPath);
            try (OutputStream out = Files.newOutputStream(credentialsPath)) {
                props.store(out, "Wanaku CLI Authentication Credentials");
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to store credential: " + key, e);
        }
    }

    private void ensureCredentialsDirectoryExists() {
        try {
            Path credentialsPath = Paths.get(credentialsUri);
            Path parentDir = credentialsPath.getParent();
            if (parentDir != null) {
                if (!Files.exists(parentDir)) {
                    Files.createDirectories(parentDir);
                }
                // Enforce owner-only access even if the directory already existed with broader
                // permissions.
                restrictPermissions(parentDir, "rwx------");
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create credentials directory", e);
        }
    }

    /**
     * Ensures the credentials file exists with owner-only ({@code 0600}) permissions. On a POSIX
     * filesystem the file is created atomically with the restricted permissions; on a non-POSIX
     * filesystem it falls back to a best-effort restriction via the {@link File} API.
     *
     * @param path the credentials file path
     * @throws IOException if the file cannot be created
     */
    private static void ensureSecureFile(Path path) throws IOException {
        try {
            // Create atomically with owner-only permissions: no exists()-then-create() race.
            Files.createFile(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            return;
        } catch (FileAlreadyExistsException ignored) {
            // Already present: fall through and re-assert the restricted permissions below.
        } catch (UnsupportedOperationException e) {
            // Non-POSIX filesystem (e.g. Windows): create if needed, then restrict best-effort below.
            if (!Files.exists(path)) {
                Files.createFile(path);
            }
        }
        restrictPermissions(path, "rw-------");
    }

    /**
     * Best-effort restriction of a path to owner-only permissions, tolerating non-POSIX
     * filesystems where POSIX permissions are unavailable.
     *
     * @param path the file or directory to restrict
     * @param posixPermissions the desired POSIX permission string (e.g. {@code rw-------})
     */
    private static void restrictPermissions(Path path, String posixPermissions) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(posixPermissions));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Non-POSIX filesystem (e.g. Windows): best-effort owner-only via java.io.File. These
            // calls can legitimately return false on such platforms (e.g. Windows cannot revoke
            // read for "everyone"), so we surface a warning rather than failing the command.
            boolean ownerExecutable =
                    PosixFilePermissions.fromString(posixPermissions).contains(PosixFilePermission.OWNER_EXECUTE);
            File file = path.toFile();
            boolean restricted = file.setReadable(false, false);
            restricted = file.setReadable(true, true) && restricted;
            restricted = file.setWritable(false, false) && restricted;
            restricted = file.setWritable(true, true) && restricted;
            if (ownerExecutable) {
                restricted = file.setExecutable(false, false) && restricted;
                restricted = file.setExecutable(true, true) && restricted;
            }
            if (!restricted) {
                LOG.warnf(
                        "Could not fully restrict permissions on %s; it may be accessible to other users "
                                + "on this filesystem",
                        path);
            }
        }
    }

    private static URI resolveCredentialsPath(String credentialsPath) {
        String resolvedPath = WanakuHome.expandPlaceholders(credentialsPath);
        if (resolvedPath.startsWith("~/")) {
            resolvedPath = System.getProperty("user.home") + resolvedPath.substring(1);
        }
        return Paths.get(resolvedPath).toUri();
    }
}
