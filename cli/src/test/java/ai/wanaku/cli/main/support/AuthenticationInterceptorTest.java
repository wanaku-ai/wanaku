package ai.wanaku.cli.main.support;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import java.net.URI;
import java.nio.file.Path;
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
}
