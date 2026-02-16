package ai.wanaku.cli.main.support.keycloak;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KeycloakAdminClientTest {

    private static final String KEYCLOAK_URL = "http://localhost:8543";
    private static final String ACCESS_TOKEN = "test-access-token";
    private static final String REALM = "wanaku";

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private KeycloakAdminClient adminClient;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        adminClient = new KeycloakAdminClient(httpClient, KEYCLOAK_URL, ACCESS_TOKEN);
    }

    @SuppressWarnings("unchecked")
    private void mockResponse(int statusCode, String body) throws IOException, InterruptedException {
        when(httpResponse.statusCode()).thenReturn(statusCode);
        when(httpResponse.body()).thenReturn(body);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
    }

    @Test
    void createUserShouldPostToCorrectUrl() throws Exception {
        mockResponse(201, "");

        adminClient.createUser(REALM, "testuser", "password123", "test@example.com");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());

        HttpRequest request = captor.getValue();
        assertEquals("POST", request.method());
        assertEquals(
                KEYCLOAK_URL + "/admin/realms/" + REALM + "/users",
                request.uri().toString());
        assertTrue(request.headers().firstValue("Authorization").orElse("").contains(ACCESS_TOKEN));
        assertEquals(
                "application/json", request.headers().firstValue("Content-Type").orElse(""));
    }

    @Test
    void createUserShouldThrowOnNon201() throws Exception {
        mockResponse(409, "{\"errorMessage\":\"User exists\"}");

        KeycloakAdminClient.KeycloakAdminException ex = assertThrows(
                KeycloakAdminClient.KeycloakAdminException.class,
                () -> adminClient.createUser(REALM, "testuser", "pass", null));

        assertTrue(ex.getMessage().contains("testuser"));
    }

    @Test
    void listUsersShouldGetFromCorrectUrl() throws Exception {
        mockResponse(200, "[{\"username\":\"admin\",\"email\":\"admin@test.com\",\"enabled\":true}]");

        List<Map<String, Object>> users = adminClient.listUsers(REALM);

        assertEquals(1, users.size());
        assertEquals("admin", users.get(0).get("username"));

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());
        assertEquals("GET", captor.getValue().method());
        assertEquals(
                KEYCLOAK_URL + "/admin/realms/" + REALM + "/users",
                captor.getValue().uri().toString());
    }

    @SuppressWarnings("unchecked")
    @Test
    void deleteUserShouldResolveIdThenDelete() throws Exception {
        // First call resolves user ID, second call deletes
        HttpResponse<String> resolveResponse = mock(HttpResponse.class);
        when(resolveResponse.statusCode()).thenReturn(200);
        when(resolveResponse.body()).thenReturn("[{\"id\":\"user-uuid-123\",\"username\":\"testuser\"}]");

        HttpResponse<String> deleteResponse = mock(HttpResponse.class);
        when(deleteResponse.statusCode()).thenReturn(204);
        when(deleteResponse.body()).thenReturn("");

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(resolveResponse, deleteResponse);

        adminClient.deleteUser(REALM, "testuser");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, times(2)).send(captor.capture(), any());

        List<HttpRequest> requests = captor.getAllValues();
        assertEquals("GET", requests.get(0).method());
        assertTrue(requests.get(0).uri().toString().contains("username=testuser"));
        assertEquals("DELETE", requests.get(1).method());
        assertTrue(requests.get(1).uri().toString().contains("user-uuid-123"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void setPasswordShouldResolveIdThenPut() throws Exception {
        HttpResponse<String> resolveResponse = mock(HttpResponse.class);
        when(resolveResponse.statusCode()).thenReturn(200);
        when(resolveResponse.body()).thenReturn("[{\"id\":\"user-uuid-123\",\"username\":\"testuser\"}]");

        HttpResponse<String> putResponse = mock(HttpResponse.class);
        when(putResponse.statusCode()).thenReturn(204);
        when(putResponse.body()).thenReturn("");

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(resolveResponse, putResponse);

        adminClient.setPassword(REALM, "testuser", "newpassword");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, times(2)).send(captor.capture(), any());

        List<HttpRequest> requests = captor.getAllValues();
        assertEquals("PUT", requests.get(1).method());
        assertTrue(requests.get(1).uri().toString().contains("reset-password"));
    }

    @Test
    void createClientShouldPostToCorrectUrl() throws Exception {
        mockResponse(201, "");

        adminClient.createClient(REALM, "my-service", "My service client");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());

        HttpRequest request = captor.getValue();
        assertEquals("POST", request.method());
        assertEquals(
                KEYCLOAK_URL + "/admin/realms/" + REALM + "/clients",
                request.uri().toString());
    }

    @Test
    void listClientsShouldGetFromCorrectUrl() throws Exception {
        mockResponse(200, "[{\"clientId\":\"my-service\",\"enabled\":true}]");

        List<Map<String, Object>> clients = adminClient.listClients(REALM);

        assertEquals(1, clients.size());
        assertEquals("my-service", clients.get(0).get("clientId"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void getClientSecretShouldResolveIdThenGet() throws Exception {
        HttpResponse<String> resolveResponse = mock(HttpResponse.class);
        when(resolveResponse.statusCode()).thenReturn(200);
        when(resolveResponse.body()).thenReturn("[{\"id\":\"client-uuid-456\",\"clientId\":\"my-service\"}]");

        HttpResponse<String> secretResponse = mock(HttpResponse.class);
        when(secretResponse.statusCode()).thenReturn(200);
        when(secretResponse.body()).thenReturn("{\"type\":\"secret\",\"value\":\"super-secret-123\"}");

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(resolveResponse, secretResponse);

        String secret = adminClient.getClientSecret(REALM, "my-service");

        assertEquals("super-secret-123", secret);

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, times(2)).send(captor.capture(), any());

        List<HttpRequest> requests = captor.getAllValues();
        assertTrue(requests.get(1).uri().toString().contains("client-secret"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void regenerateClientSecretShouldPostToSecretEndpoint() throws Exception {
        HttpResponse<String> resolveResponse = mock(HttpResponse.class);
        when(resolveResponse.statusCode()).thenReturn(200);
        when(resolveResponse.body()).thenReturn("[{\"id\":\"client-uuid-456\",\"clientId\":\"my-service\"}]");

        HttpResponse<String> secretResponse = mock(HttpResponse.class);
        when(secretResponse.statusCode()).thenReturn(200);
        when(secretResponse.body()).thenReturn("{\"type\":\"secret\",\"value\":\"new-secret-789\"}");

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(resolveResponse, secretResponse);

        String secret = adminClient.regenerateClientSecret(REALM, "my-service");

        assertEquals("new-secret-789", secret);

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, times(2)).send(captor.capture(), any());

        assertEquals("POST", captor.getAllValues().get(1).method());
    }

    @Test
    void deleteUserShouldThrowWhenUserNotFound() throws Exception {
        mockResponse(200, "[]");

        KeycloakAdminClient.KeycloakAdminException ex = assertThrows(
                KeycloakAdminClient.KeycloakAdminException.class, () -> adminClient.deleteUser(REALM, "nonexistent"));

        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    void listUsersShouldThrowOnNon200() throws Exception {
        mockResponse(403, "{\"error\":\"Forbidden\"}");

        KeycloakAdminClient.KeycloakAdminException ex =
                assertThrows(KeycloakAdminClient.KeycloakAdminException.class, () -> adminClient.listUsers(REALM));

        assertTrue(ex.getMessage().contains("Failed to list users"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldHandleIOException() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Connection refused"));

        KeycloakAdminClient.KeycloakAdminException ex =
                assertThrows(KeycloakAdminClient.KeycloakAdminException.class, () -> adminClient.listUsers(REALM));

        assertTrue(ex.getMessage().contains("Failed to communicate"));
    }

    @Test
    void shouldStripTrailingSlashFromUrl() throws Exception {
        KeycloakAdminClient clientWithSlash = new KeycloakAdminClient(httpClient, KEYCLOAK_URL + "/", ACCESS_TOKEN);
        mockResponse(200, "[]");

        clientWithSlash.listUsers(REALM);

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());

        assertFalse(captor.getValue().uri().toString().contains("//admin"));
    }
}
