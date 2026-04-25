package ai.wanaku.backend.health;

import java.io.IOException;
import java.net.HttpURLConnection;
import org.eclipse.microprofile.health.HealthCheckResponse;
import ai.wanaku.backend.health.OidcReadinessCheck.HttpConnectionProvider;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OidcReadinessCheckTest {

    @Test
    void call_returnsUp_whenOidcIsDisabled() {
        OidcReadinessCheck check = new OidcReadinessCheck();
        check.oidcEnabled = false;

        HealthCheckResponse response = check.call();

        assertEquals("oidc", response.getName());
        assertTrue(response.getStatus() == HealthCheckResponse.Status.UP);
        assertTrue(response.getData().isPresent());
        assertEquals("disabled", getDataValue(response, "status"));
    }

    @Test
    void call_returnsUp_whenOidcIsEnabledAndDiscoverySucceeds() throws IOException {
        HttpURLConnection connection = mock(HttpURLConnection.class);
        when(connection.getResponseCode()).thenReturn(200);

        HttpConnectionProvider connectionProvider = mock(HttpConnectionProvider.class);
        when(connectionProvider.createConnection(
                        "http://localhost:8543/realms/wanaku/.well-known/openid-configuration"))
                .thenReturn(connection);

        OidcReadinessCheck check = new OidcReadinessCheck();
        check.oidcEnabled = true;
        check.authServerUrl = "http://localhost:8543/realms/wanaku";
        check.discoveryEnabled = true;
        check.httpConnectionProvider = connectionProvider;

        HealthCheckResponse response = check.call();

        assertEquals("oidc", response.getName());
        assertTrue(response.getStatus() == HealthCheckResponse.Status.UP);
        assertEquals("enabled", getDataValue(response, "status"));
        assertEquals("http://localhost:8543/realms/wanaku", getDataValue(response, "authServerUrl"));
    }

    @Test
    void call_returnsDown_whenDiscoveryReturnsNon200() throws IOException {
        HttpURLConnection connection = mock(HttpURLConnection.class);
        when(connection.getResponseCode()).thenReturn(503);

        HttpConnectionProvider connectionProvider = mock(HttpConnectionProvider.class);
        when(connectionProvider.createConnection(
                        "http://localhost:8543/realms/wanaku/.well-known/openid-configuration"))
                .thenReturn(connection);

        OidcReadinessCheck check = new OidcReadinessCheck();
        check.oidcEnabled = true;
        check.authServerUrl = "http://localhost:8543/realms/wanaku";
        check.discoveryEnabled = true;
        check.httpConnectionProvider = connectionProvider;

        HealthCheckResponse response = check.call();

        assertEquals("oidc", response.getName());
        assertTrue(response.getStatus() == HealthCheckResponse.Status.DOWN);
        assertEquals("unreachable", getDataValue(response, "status"));
        assertEquals("503", getDataValue(response, "httpStatus"));
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
        check.authServerUrl = "not-a-valid-url";
        check.discoveryEnabled = true;
        check.httpConnectionProvider = connectionProvider;

        HealthCheckResponse response = check.call();

        assertEquals("oidc", response.getName());
        assertTrue(response.getStatus() == HealthCheckResponse.Status.DOWN);
        assertEquals("unreachable", getDataValue(response, "status"));
    }

    @Test
    void call_returnsDown_whenAuthServerUrlIsNull() {
        OidcReadinessCheck check = new OidcReadinessCheck();
        check.oidcEnabled = true;
        check.authServerUrl = null;
        check.discoveryEnabled = true;

        HealthCheckResponse response = check.call();

        assertEquals("oidc", response.getName());
        assertTrue(response.getStatus() == HealthCheckResponse.Status.DOWN);
        assertEquals("error", getDataValue(response, "status"));
    }

    @Test
    void call_returnsDown_whenAuthServerUrlIsEmpty() {
        OidcReadinessCheck check = new OidcReadinessCheck();
        check.oidcEnabled = true;
        check.authServerUrl = " ";
        check.discoveryEnabled = true;

        HealthCheckResponse response = check.call();

        assertEquals("oidc", response.getName());
        assertTrue(response.getStatus() == HealthCheckResponse.Status.DOWN);
        assertEquals("error", getDataValue(response, "status"));
    }

    @Test
    void call_usesDirectUrl_whenDiscoveryIsDisabled() throws IOException {
        HttpURLConnection connection = mock(HttpURLConnection.class);
        when(connection.getResponseCode()).thenReturn(200);

        HttpConnectionProvider connectionProvider = mock(HttpConnectionProvider.class);
        when(connectionProvider.createConnection("http://localhost:8543/realms/wanaku"))
                .thenReturn(connection);

        OidcReadinessCheck check = new OidcReadinessCheck();
        check.oidcEnabled = true;
        check.authServerUrl = "http://localhost:8543/realms/wanaku";
        check.discoveryEnabled = false;
        check.httpConnectionProvider = connectionProvider;

        HealthCheckResponse response = check.call();

        assertEquals("oidc", response.getName());
        assertTrue(response.getStatus() == HealthCheckResponse.Status.UP);
    }

    private String getDataValue(HealthCheckResponse response, String key) {
        return response.getData()
                .map(data -> data.get(key))
                .map(Object::toString)
                .orElse(null);
    }
}
