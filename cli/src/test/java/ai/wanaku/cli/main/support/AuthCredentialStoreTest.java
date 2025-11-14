package ai.wanaku.cli.main.support;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuthCredentialStoreTest {

    @TempDir
    Path tempDir;

    private AuthCredentialStore credentialStore;

    @BeforeEach
    void setUp() {
        Path credentialsFile = tempDir.resolve("test-credentials");
        URI credentialsUri = credentialsFile.toUri();
        credentialStore = new AuthCredentialStore(credentialsUri);
    }

    @Test
    void shouldStoreAndRetrieveApiToken() {
        String token = "test-api-token-12345";

        credentialStore.storeApiToken(token);
        String retrievedToken = credentialStore.getApiToken();

        assertEquals(token, retrievedToken);
    }

    @Test
    void shouldStoreAndRetrieveRefreshToken() {
        String refreshToken = "test-refresh-token-67890";

        credentialStore.storeRefreshToken(refreshToken);
        String retrievedToken = credentialStore.getRefreshToken();

        assertEquals(refreshToken, retrievedToken);
    }

    @Test
    void shouldStoreAndRetrieveAuthMode() {
        String authMode = "oauth2";

        credentialStore.storeAuthMode(authMode);
        String retrievedMode = credentialStore.getAuthMode();

        assertEquals(authMode, retrievedMode);
    }

    @Test
    void shouldReturnDefaultAuthModeWhenNotSet() {
        String authMode = credentialStore.getAuthMode();
        assertEquals("none", authMode);
    }

    @Test
    void shouldStoreAndRetrieveAuthServerUrl() {
        String serverUrl = "https://auth.example.com";

        credentialStore.storeAuthServerUrl(serverUrl);
        String retrievedUrl = credentialStore.getAuthServerUrl();

        assertEquals(serverUrl, retrievedUrl);
    }

    @Test
    void shouldReturnTrueWhenCredentialsExist() {
        assertFalse(credentialStore.hasCredentials());

        credentialStore.storeApiToken("test-token");
        assertTrue(credentialStore.hasCredentials());
    }

    @Test
    void shouldReturnTrueWhenRefreshTokenExists() {
        assertFalse(credentialStore.hasCredentials());

        credentialStore.storeRefreshToken("test-refresh-token");
        assertTrue(credentialStore.hasCredentials());
    }

    @Test
    void shouldClearAllCredentials() {
        credentialStore.storeApiToken("test-token");
        credentialStore.storeRefreshToken("test-refresh");
        credentialStore.storeAuthMode("oauth2");
        credentialStore.storeAuthServerUrl("https://auth.example.com");

        assertTrue(credentialStore.hasCredentials());

        credentialStore.clearCredentials();

        assertFalse(credentialStore.hasCredentials());
        assertNull(credentialStore.getApiToken());
        assertNull(credentialStore.getRefreshToken());
        assertEquals("none", credentialStore.getAuthMode());
        assertNull(credentialStore.getAuthServerUrl());
    }

    @Test
    void shouldReturnCorrectCredentialsFileUri() {
        URI credentialsUri = credentialStore.getCredentialsFile();
        assertNotNull(credentialsUri);
        assertTrue(credentialsUri.getPath().endsWith("test-credentials"));
    }

    @Test
    void shouldPersistCredentialsAcrossInstances() {
        String token = "persistent-token";
        URI credentialsUri = credentialStore.getCredentialsFile();

        credentialStore.storeApiToken(token);

        AuthCredentialStore newStore = new AuthCredentialStore(credentialsUri);
        assertEquals(token, newStore.getApiToken());
    }
}
