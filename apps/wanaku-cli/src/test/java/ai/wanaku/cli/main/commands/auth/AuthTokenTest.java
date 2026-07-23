package ai.wanaku.cli.main.commands.auth;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import ai.wanaku.cli.main.support.AuthCredentialStore;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.cli.main.support.security.TokenRefresher;
import ai.wanaku.cli.main.support.security.TokenRefresher.RefreshResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisabledOnOs(OS.WINDOWS)
class AuthTokenTest {

    @TempDir
    Path tempDir;

    private AuthCredentialStore credentialStore;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        Path credentialsFile = tempDir.resolve("test-credentials");
        URI credentialsUri = credentialsFile.toUri();
        credentialStore = new AuthCredentialStore(credentialsUri);
    }

    @Test
    void shouldRefreshExpiredTokenOnGet() throws Exception {
        String oldToken = "old-expired-token";
        String newToken = "new-refreshed-token";
        String refreshToken = "my-refresh-token";
        String authServerUrl = "http://localhost:8080";
        String clientId = "admin-cli";

        credentialStore.storeApiToken(oldToken);
        credentialStore.storeRefreshToken(refreshToken);
        credentialStore.storeAuthServerUrl(authServerUrl);
        credentialStore.storeClientId(clientId);
        credentialStore.storeAuthMode("token");
        // Token expired 60 seconds ago
        credentialStore.storeTokenExpiry(Instant.now().getEpochSecond() - 60);

        TokenRefresher mockRefresher = mock(TokenRefresher.class);
        long newExpiry = Instant.now().getEpochSecond() + 300;
        when(mockRefresher.refresh(refreshToken, authServerUrl, clientId, null))
                .thenReturn(new RefreshResult(newToken, refreshToken, newExpiry));

        AuthToken authToken = new AuthToken(credentialStore, mockRefresher);
        authToken.operation = new AuthToken.TokenOperation();
        authToken.operation.getOptions = new AuthToken.GetOptions();
        authToken.operation.getOptions.getToken = true;
        authToken.operation.getOptions.unmask = true;

        WanakuPrinter.setPlainMode(true);
        try (Terminal terminal = WanakuPrinter.terminalInstance()) {
            WanakuPrinter printer = new WanakuPrinter(null, terminal);
            authToken.doCall(terminal, printer);
        } finally {
            WanakuPrinter.setPlainMode(false);
        }

        verify(mockRefresher).refresh(refreshToken, authServerUrl, clientId, null);
        assertEquals(newToken, credentialStore.getApiToken());
        assertEquals(newExpiry, credentialStore.getTokenExpiry());
    }

    @Test
    void shouldNotRefreshValidTokenOnGet() throws Exception {
        String token = "valid-token";

        credentialStore.storeApiToken(token);
        credentialStore.storeAuthMode("token");
        // Token expires in 5 minutes
        credentialStore.storeTokenExpiry(Instant.now().getEpochSecond() + 300);

        TokenRefresher mockRefresher = mock(TokenRefresher.class);

        AuthToken authToken = new AuthToken(credentialStore, mockRefresher);
        authToken.operation = new AuthToken.TokenOperation();
        authToken.operation.getOptions = new AuthToken.GetOptions();
        authToken.operation.getOptions.getToken = true;
        authToken.operation.getOptions.unmask = true;

        WanakuPrinter.setPlainMode(true);
        try (Terminal terminal = WanakuPrinter.terminalInstance()) {
            WanakuPrinter printer = new WanakuPrinter(null, terminal);
            authToken.doCall(terminal, printer);
        } finally {
            WanakuPrinter.setPlainMode(false);
        }

        verify(mockRefresher, never()).refresh(any(), any(), any(), any());
        assertEquals(token, credentialStore.getApiToken());
    }

    @Test
    void shouldReturnExistingTokenWhenRefreshFails() throws Exception {
        String oldToken = "old-token";
        String refreshToken = "my-refresh-token";
        String authServerUrl = "http://localhost:8080";
        String clientId = "admin-cli";

        credentialStore.storeApiToken(oldToken);
        credentialStore.storeRefreshToken(refreshToken);
        credentialStore.storeAuthServerUrl(authServerUrl);
        credentialStore.storeClientId(clientId);
        credentialStore.storeAuthMode("token");
        // Token expired
        credentialStore.storeTokenExpiry(Instant.now().getEpochSecond() - 60);

        TokenRefresher mockRefresher = mock(TokenRefresher.class);
        when(mockRefresher.refresh(refreshToken, authServerUrl, clientId, null))
                .thenThrow(new TokenRefresher.TokenRefreshException("Refresh failed"));

        AuthToken authToken = new AuthToken(credentialStore, mockRefresher);
        authToken.operation = new AuthToken.TokenOperation();
        authToken.operation.getOptions = new AuthToken.GetOptions();
        authToken.operation.getOptions.getToken = true;
        authToken.operation.getOptions.unmask = true;

        WanakuPrinter.setPlainMode(true);
        try (Terminal terminal = WanakuPrinter.terminalInstance()) {
            WanakuPrinter printer = new WanakuPrinter(null, terminal);
            authToken.doCall(terminal, printer);
        } finally {
            WanakuPrinter.setPlainMode(false);
        }

        // Should still have old token as fallback
        assertEquals(oldToken, credentialStore.getApiToken());
    }

    @Test
    void shouldRefreshTokenWithRealmOnGet() throws Exception {
        String oldToken = "old-token";
        String newToken = "new-token";
        String refreshToken = "my-refresh-token";
        String authServerUrl = "http://keycloak-host";
        String clientId = "admin-cli";
        String realm = "wanaku";

        credentialStore.storeApiToken(oldToken);
        credentialStore.storeRefreshToken(refreshToken);
        credentialStore.storeAuthServerUrl(authServerUrl);
        credentialStore.storeClientId(clientId);
        credentialStore.storeRealm(realm);
        credentialStore.storeAuthMode("token");
        // Token expired
        credentialStore.storeTokenExpiry(Instant.now().getEpochSecond() - 60);

        TokenRefresher mockRefresher = mock(TokenRefresher.class);
        long newExpiry = Instant.now().getEpochSecond() + 300;
        when(mockRefresher.refresh(refreshToken, authServerUrl, clientId, realm))
                .thenReturn(new RefreshResult(newToken, refreshToken, newExpiry));

        AuthToken authToken = new AuthToken(credentialStore, mockRefresher);
        authToken.operation = new AuthToken.TokenOperation();
        authToken.operation.getOptions = new AuthToken.GetOptions();
        authToken.operation.getOptions.getToken = true;
        authToken.operation.getOptions.unmask = true;

        WanakuPrinter.setPlainMode(true);
        try (Terminal terminal = WanakuPrinter.terminalInstance()) {
            WanakuPrinter printer = new WanakuPrinter(null, terminal);
            authToken.doCall(terminal, printer);
        } finally {
            WanakuPrinter.setPlainMode(false);
        }

        verify(mockRefresher).refresh(refreshToken, authServerUrl, clientId, realm);
        assertEquals(newToken, credentialStore.getApiToken());
    }

    @Test
    void shouldNotRefreshWhenNoExpiryStored() throws Exception {
        String token = "legacy-token";

        credentialStore.storeApiToken(token);
        credentialStore.storeAuthMode("token");
        // No expiry stored (legacy token)

        TokenRefresher mockRefresher = mock(TokenRefresher.class);

        AuthToken authToken = new AuthToken(credentialStore, mockRefresher);
        authToken.operation = new AuthToken.TokenOperation();
        authToken.operation.getOptions = new AuthToken.GetOptions();
        authToken.operation.getOptions.getToken = true;
        authToken.operation.getOptions.unmask = true;

        WanakuPrinter.setPlainMode(true);
        try (Terminal terminal = WanakuPrinter.terminalInstance()) {
            WanakuPrinter printer = new WanakuPrinter(null, terminal);
            authToken.doCall(terminal, printer);
        } finally {
            WanakuPrinter.setPlainMode(false);
        }

        verify(mockRefresher, never()).refresh(any(), any(), any(), any());
        assertEquals(token, credentialStore.getApiToken());
    }

    @Test
    void shouldOutputTokenToWriterInPlainMode() throws Exception {
        String token = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.test-payload.signature";

        credentialStore.storeApiToken(token);
        credentialStore.storeAuthMode("token");
        // Token expires in 5 minutes (not expired)
        credentialStore.storeTokenExpiry(Instant.now().getEpochSecond() + 300);

        TokenRefresher mockRefresher = mock(TokenRefresher.class);

        AuthToken authToken = new AuthToken(credentialStore, mockRefresher);
        authToken.operation = new AuthToken.TokenOperation();
        authToken.operation.getOptions = new AuthToken.GetOptions();
        authToken.operation.getOptions.getToken = true;
        authToken.operation.getOptions.unmask = true;

        // Simulate what BaseCommand.call() does in plain mode: create a terminal
        // with system(false) + explicit streams so output is capturable.
        WanakuPrinter.setPlainMode(true);
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try (Terminal terminal = TerminalBuilder.builder()
                .system(false)
                .streams(System.in, captured)
                .jni(false)
                .color(false)
                .build()) {
            WanakuPrinter printer = new WanakuPrinter(null, terminal);
            authToken.doCall(terminal, printer);
        } finally {
            WanakuPrinter.setPlainMode(false);
        }

        String output = captured.toString().trim();
        assertTrue(output.contains(token), "Plain-mode output must contain the full token value, got: " + output);
    }

    @Test
    void shouldRefreshTokenAboutToExpire() throws Exception {
        String oldToken = "old-token";
        String newToken = "new-token";
        String refreshToken = "my-refresh-token";
        String authServerUrl = "http://localhost:8080";
        String clientId = "admin-cli";

        credentialStore.storeApiToken(oldToken);
        credentialStore.storeRefreshToken(refreshToken);
        credentialStore.storeAuthServerUrl(authServerUrl);
        credentialStore.storeClientId(clientId);
        credentialStore.storeAuthMode("token");
        // Token expires in 20 seconds (within 30 second buffer)
        credentialStore.storeTokenExpiry(Instant.now().getEpochSecond() + 20);

        TokenRefresher mockRefresher = mock(TokenRefresher.class);
        long newExpiry = Instant.now().getEpochSecond() + 300;
        when(mockRefresher.refresh(refreshToken, authServerUrl, clientId, null))
                .thenReturn(new RefreshResult(newToken, refreshToken, newExpiry));

        AuthToken authToken = new AuthToken(credentialStore, mockRefresher);
        authToken.operation = new AuthToken.TokenOperation();
        authToken.operation.getOptions = new AuthToken.GetOptions();
        authToken.operation.getOptions.getToken = true;
        authToken.operation.getOptions.unmask = true;

        WanakuPrinter.setPlainMode(true);
        try (Terminal terminal = WanakuPrinter.terminalInstance()) {
            WanakuPrinter printer = new WanakuPrinter(null, terminal);
            authToken.doCall(terminal, printer);
        } finally {
            WanakuPrinter.setPlainMode(false);
        }

        verify(mockRefresher).refresh(refreshToken, authServerUrl, clientId, null);
        assertEquals(newToken, credentialStore.getApiToken());
    }
}
