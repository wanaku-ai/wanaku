package ai.wanaku.backend.health;

import java.io.IOException;
import java.net.HttpURLConnection;
import org.eclipse.microprofile.health.HealthCheckResponse;
import ai.wanaku.backend.health.OidcReadinessCheck.HttpConnectionProvider;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OidcReadinessCheckTest {

    private static final String KEYCLOAK = "keycloak";
    private static final String AUTH_SERVER_URL = "http://localhost:8543/realms/wanaku";
    private static final String DISCOVERY_URL = AUTH_SERVER_URL + "/.well-known/openid-configuration";

    @Test
    void call_returnsUp_whenOidcIsDisabled() {
        OidcReadinessCheck check = new OidcReadinessCheck();
        check.oidcEnabled = false;
        check.httpAuth = KEYCLOAK;

        HealthCheckResponse response = check.call();

        assertEquals("oidc", response.getName());
        assertSame(HealthCheckResponse.Status.UP, response.getStatus());
        assertTrue(response.getData().isPresent());
        assertEquals("disabled", getDataValue(response, "status"));
    }

    @Test
    void call_returnsUp_whenHttpAuthIsNone() {
        OidcReadinessCheck check = new OidcReadinessCheck();
        check.oidcEnabled = true;
        check.httpAuth = "none";

        HealthCheckResponse response = check.call();

        assertEquals("oidc", response.getName());
        assertSame(HealthCheckResponse.Status.UP, response.getStatus());
        assertTrue(response.getData().isPresent());
        assertEquals("disabled", getDataValue(response, "status"));
    }

    @Test
    void call_returnsUp_whenOidcIsEnabledAndDiscoverySucceeds() throws IOException {
        HttpURLConnection connection = mock(HttpURLConnection.class);
        when(connection.getResponseCode()).thenReturn(200);

        HttpConnectionProvider connectionProvider = mock(HttpConnectionProvider.class);
        when(connectionProvider.createConnection(DISCOVERY_URL)).thenReturn(connection);

        OidcReadinessCheck check = new OidcReadinessCheck();
        check.oidcEnabled = true;
        check.httpAuth = KEYCLOAK;
        check.authServerUrl = AUTH_SERVER_URL;
        check.discoveryEnabled = true;
        check.httpConnectionProvider = connectionProvider;

        HealthCheckResponse response = check.call();

        assertEquals("oidc", response.getName());
        assertSame(response.getStatus(), HealthCheckResponse.Status.UP);
        assertEquals("enabled", getDataValue(response, "status"));
        assertFalse(response.getData().get().containsKey("authServerUrl"));
    }

    @Test
    void call_returnsDown_whenDiscoveryReturnsNon200() throws IOException {
        HttpURLConnection connection = mock(HttpURLConnection.class);
        when(connection.getResponseCode()).thenReturn(503);

        HttpConnectionProvider connectionProvider = mock(HttpConnectionProvider.class);
        when(connectionProvider.createConnection(DISCOVERY_URL)).thenReturn(connection);

        OidcReadinessCheck check = new OidcReadinessCheck();
        check.oidcEnabled = true;
        check.httpAuth = KEYCLOAK;
        check.authServerUrl = AUTH_SERVER_URL;
        check.discoveryEnabled = true;
        check.httpConnectionProvider = connectionProvider;

        HealthCheckResponse response = check.call();

        assertEquals("oidc", response.getName());
        assertSame(HealthCheckResponse.Status.DOWN, response.getStatus());
        assertEquals("unreachable", getDataValue(response, "status"));
        assertEquals("503", getDataValue(response, "httpStatus"));
        assertFalse(response.getData().get().containsKey("authServerUrl"));
    }

    @Test
    void call_returnsDown_whenAuthServerUrlIsInvalid() throws IOException {
        // For an invalid URL, buildDiscoveryUrl will construct a malformed URL like
        // "null://nullnot-a-valid-url/.well-known/openid-configuration"
        // which will cause an IOException when trying to open the connection
        HttpConnectionProvider connectionProvider = mock(HttpConnectionProvider.class);
        when(connectionProvider.createConnection("null://nullnot-a-valid-url/.well-known/openid-configuration"))
                .thenThrow(new IOException("Invalid URL"));

        OidcReadinessCheck check = new OidcReadinessCheck();
        check.oidcEnabled = true;
        check.httpAuth = KEYCLOAK;
        check.authServerUrl = "not-a-valid-url";
        check.discoveryEnabled = true;
        check.httpConnectionProvider = connectionProvider;

        HealthCheckResponse response = check.call();

        assertEquals("oidc", response.getName());
        assertSame(HealthCheckResponse.Status.DOWN, response.getStatus());
        assertEquals("unreachable", getDataValue(response, "status"));
        assertFalse(response.getData().get().containsKey("authServerUrl"));
    }

    @Test
    void call_doesNotExposeAuthServerUrl_inDownResponses() throws IOException {
        HttpURLConnection connection = mock(HttpURLConnection.class);
        when(connection.getResponseCode()).thenReturn(500);

        HttpConnectionProvider connectionProvider = mock(HttpConnectionProvider.class);
        when(connectionProvider.createConnection(DISCOVERY_URL)).thenReturn(connection);

        OidcReadinessCheck check = new OidcReadinessCheck();
        check.oidcEnabled = true;
        check.httpAuth = KEYCLOAK;
        check.authServerUrl = AUTH_SERVER_URL;
        check.discoveryEnabled = true;
        check.httpConnectionProvider = connectionProvider;

        HealthCheckResponse response = check.call();

        assertSame(HealthCheckResponse.Status.DOWN, response.getStatus());
        assertFalse(response.getData().get().containsKey("authServerUrl"));
    }

    @Test
    void call_returnsDown_whenAuthServerUrlIsNull() {
        OidcReadinessCheck check = new OidcReadinessCheck();
        check.oidcEnabled = true;
        check.httpAuth = KEYCLOAK;
        check.authServerUrl = null;
        check.discoveryEnabled = true;

        HealthCheckResponse response = check.call();

        assertEquals("oidc", response.getName());
        assertSame(HealthCheckResponse.Status.DOWN, response.getStatus());
        assertEquals("error", getDataValue(response, "status"));
    }

    @Test
    void call_returnsDown_whenAuthServerUrlIsEmpty() {
        OidcReadinessCheck check = new OidcReadinessCheck();
        check.oidcEnabled = true;
        check.httpAuth = KEYCLOAK;
        check.authServerUrl = " ";
        check.discoveryEnabled = true;

        HealthCheckResponse response = check.call();

        assertEquals("oidc", response.getName());
        assertSame(HealthCheckResponse.Status.DOWN, response.getStatus());
        assertEquals("error", getDataValue(response, "status"));
    }

    @Test
    void call_usesDirectUrl_whenDiscoveryIsDisabled() throws IOException {
        HttpURLConnection connection = mock(HttpURLConnection.class);
        when(connection.getResponseCode()).thenReturn(200);

        HttpConnectionProvider connectionProvider = mock(HttpConnectionProvider.class);
        when(connectionProvider.createConnection(AUTH_SERVER_URL)).thenReturn(connection);

        OidcReadinessCheck check = new OidcReadinessCheck();
        check.oidcEnabled = true;
        check.httpAuth = KEYCLOAK;
        check.authServerUrl = AUTH_SERVER_URL;
        check.discoveryEnabled = false;
        check.httpConnectionProvider = connectionProvider;

        HealthCheckResponse response = check.call();

        assertEquals("oidc", response.getName());
        assertSame(HealthCheckResponse.Status.UP, response.getStatus());
    }

    private String getDataValue(HealthCheckResponse response, String key) {
        return response.getData()
                .map(data -> data.get(key))
                .map(Object::toString)
                .orElse(null);
    }
}
