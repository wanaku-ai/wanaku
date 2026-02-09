package ai.wanaku.cli.main.support;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ai.wanaku.cli.main.support.security.TokenRefresher;
import ai.wanaku.cli.main.support.security.TokenRefresher.RefreshResult;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class AuthenticationInterceptorTest {

    @TempDir
    Path tempDir;

    @Mock
    private ClientRequestContext requestContext;

    private MultivaluedMap<String, Object> headers;
    private AuthCredentialStore credentialStore;
    private AuthenticationInterceptor interceptor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        headers = new MultivaluedHashMap<>();
        when(requestContext.getHeaders()).thenReturn(headers);

        Path credentialsFile = tempDir.resolve("test-credentials");
        URI credentialsUri = credentialsFile.toUri();
        credentialStore = new AuthCredentialStore(credentialsUri);
        interceptor = new AuthenticationInterceptor(credentialStore);
    }

    @Test
    void shouldAddAuthorizationHeaderWhenTokenExists() throws Exception {
        String token = "test-api-token";
        credentialStore.storeApiToken(token);
        credentialStore.storeAuthMode("token");

        interceptor.filter(requestContext);

        verify(requestContext).getHeaders();
        assertEquals("Bearer " + token, headers.getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void shouldNotAddHeaderWhenAuthModeIsNone() throws Exception {
        credentialStore.storeApiToken("test-token");
        credentialStore.storeAuthMode("none");

        interceptor.filter(requestContext);

        assertNull(headers.getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void shouldNotAddHeaderWhenNoCredentialsExist() throws Exception {
        interceptor.filter(requestContext);

        assertNull(headers.getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void shouldNotAddHeaderWhenTokenIsEmpty() throws Exception {
        credentialStore.storeApiToken("");
        credentialStore.storeAuthMode("token");

        interceptor.filter(requestContext);

        assertNull(headers.getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void shouldNotAddHeaderWhenTokenIsWhitespace() throws Exception {
        credentialStore.storeApiToken("   ");
        credentialStore.storeAuthMode("token");

        interceptor.filter(requestContext);

        assertNull(headers.getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void shouldRefreshTokenWhenExpired() throws Exception {
        String oldToken = "old-token";
        String newToken = "new-token";
        String refreshToken = "refresh-token";
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
        when(mockRefresher.refresh(refreshToken, authServerUrl, clientId))
                .thenReturn(new RefreshResult(newToken, refreshToken, newExpiry));

        AuthenticationInterceptor interceptorWithMock = new AuthenticationInterceptor(credentialStore, mockRefresher);
        interceptorWithMock.filter(requestContext);

        verify(mockRefresher).refresh(refreshToken, authServerUrl, clientId);
        assertEquals("Bearer " + newToken, headers.getFirst(HttpHeaders.AUTHORIZATION));
        assertEquals(newToken, credentialStore.getApiToken());
        assertEquals(newExpiry, credentialStore.getTokenExpiry());
    }

    @Test
    void shouldNotRefreshTokenWhenNotExpired() throws Exception {
        String token = "valid-token";
        credentialStore.storeApiToken(token);
        credentialStore.storeAuthMode("token");
        // Token expires in 5 minutes
        credentialStore.storeTokenExpiry(Instant.now().getEpochSecond() + 300);

        TokenRefresher mockRefresher = mock(TokenRefresher.class);
        AuthenticationInterceptor interceptorWithMock = new AuthenticationInterceptor(credentialStore, mockRefresher);
        interceptorWithMock.filter(requestContext);

        verify(mockRefresher, never()).refresh(any(), any(), any());
        assertEquals("Bearer " + token, headers.getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void shouldUseExistingTokenWhenRefreshFails() throws Exception {
        String oldToken = "old-token";
        String refreshToken = "refresh-token";
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
        when(mockRefresher.refresh(refreshToken, authServerUrl, clientId))
                .thenThrow(new TokenRefresher.TokenRefreshException("Refresh failed"));

        AuthenticationInterceptor interceptorWithMock = new AuthenticationInterceptor(credentialStore, mockRefresher);
        interceptorWithMock.filter(requestContext);

        // Should still use old token as fallback
        assertEquals("Bearer " + oldToken, headers.getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void shouldNotRefreshWhenNoExpiryStored() throws Exception {
        String token = "legacy-token";
        credentialStore.storeApiToken(token);
        credentialStore.storeAuthMode("token");
        // No expiry stored (legacy token)

        TokenRefresher mockRefresher = mock(TokenRefresher.class);
        AuthenticationInterceptor interceptorWithMock = new AuthenticationInterceptor(credentialStore, mockRefresher);
        interceptorWithMock.filter(requestContext);

        verify(mockRefresher, never()).refresh(any(), any(), any());
        assertEquals("Bearer " + token, headers.getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void shouldRefreshTokenWhenAboutToExpire() throws Exception {
        String oldToken = "old-token";
        String newToken = "new-token";
        String refreshToken = "refresh-token";
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
        when(mockRefresher.refresh(refreshToken, authServerUrl, clientId))
                .thenReturn(new RefreshResult(newToken, refreshToken, newExpiry));

        AuthenticationInterceptor interceptorWithMock = new AuthenticationInterceptor(credentialStore, mockRefresher);
        interceptorWithMock.filter(requestContext);

        verify(mockRefresher).refresh(refreshToken, authServerUrl, clientId);
        assertEquals("Bearer " + newToken, headers.getFirst(HttpHeaders.AUTHORIZATION));
    }
}
