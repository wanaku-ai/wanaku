package ai.wanaku.backend.health;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.jboss.logging.Logger;

/**
 * OIDC readiness health check that verifies the OIDC provider is reachable.
 * When OIDC is enabled, this check attempts to connect to the OIDC discovery endpoint
 * to confirm the provider is available and responding.
 */
@Readiness
@ApplicationScoped
public class OidcReadinessCheck implements HealthCheck {

    private static final Logger LOG = Logger.getLogger(OidcReadinessCheck.class);
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 5000;
    private static final int HTTP_OK = 200;

    @ConfigProperty(name = "quarkus.oidc.enabled", defaultValue = "false")
    boolean oidcEnabled;

    @ConfigProperty(name = "quarkus.oidc.auth-server-url")
    String authServerUrl;

    @ConfigProperty(name = "quarkus.oidc.discovery-enabled", defaultValue = "true")
    boolean discoveryEnabled;

    @Inject
    HttpConnectionProvider httpConnectionProvider;

    @Override
    public HealthCheckResponse call() {
        if (!oidcEnabled) {
            return disabledResponse();
        }

        if (authServerUrl == null || authServerUrl.isBlank()) {
            return configurationErrorResponse();
        }

        return checkOidcReachability();
    }

    private HealthCheckResponse disabledResponse() {
        return HealthCheckResponse.builder()
                .name("oidc")
                .up()
                .withData("status", "disabled")
                .build();
    }

    private HealthCheckResponse configurationErrorResponse() {
        return HealthCheckResponse.builder()
                .name("oidc")
                .down()
                .withData("status", "error")
                .withData("message", "OIDC is enabled but auth-server-url is not configured")
                .build();
    }

    private HealthCheckResponse checkOidcReachability() {
        try {
            String discoveryUrl = buildDiscoveryUrl(authServerUrl);
            HttpURLConnection connection = httpConnectionProvider.createConnection(discoveryUrl);
            int responseCode = connection.getResponseCode();
            return handleDiscoveryResponse(discoveryUrl, responseCode);
        } catch (URISyntaxException e) {
            return invalidUrlError(e);
        } catch (IOException e) {
            return connectionError(e);
        }
    }

    private HealthCheckResponse handleDiscoveryResponse(String discoveryUrl, int responseCode) {
        if (responseCode == HTTP_OK) {
            return successResponse();
        }

        LOG.warnf("OIDC discovery endpoint returned HTTP %d for URL: %s", responseCode, discoveryUrl);
        return unreachableResponse(responseCode);
    }

    private HealthCheckResponse successResponse() {
        return HealthCheckResponse.builder()
                .name("oidc")
                .up()
                .withData("status", "enabled")
                .build();
    }

    private HealthCheckResponse unreachableResponse(int responseCode) {
        return HealthCheckResponse.builder()
                .name("oidc")
                .down()
                .withData("status", "unreachable")
                .withData("httpStatus", String.valueOf(responseCode))
                .build();
    }

    private HealthCheckResponse invalidUrlError(URISyntaxException e) {
        LOG.errorf(e, "Invalid OIDC auth-server-url: %s", authServerUrl);
        return HealthCheckResponse.builder()
                .name("oidc")
                .down()
                .withData("status", "invalid-url")
                .withData("error", e.getMessage())
                .build();
    }

    private HealthCheckResponse connectionError(IOException e) {
        LOG.warnf("Failed to connect to OIDC discovery endpoint: %s", e.getMessage());
        return HealthCheckResponse.builder()
                .name("oidc")
                .down()
                .withData("status", "unreachable")
                .withData("error", e.getMessage())
                .build();
    }

    /**
     * Builds the OIDC discovery URL from the auth-server-url.
     * The discovery endpoint is typically at: {auth-server-url}/.well-known/openid-configuration
     */
    private String buildDiscoveryUrl(String authServerUrl) throws URISyntaxException {
        URI uri = new URI(authServerUrl);
        String path = uri.getPath();

        // If discovery is disabled, the path may already contain the full URL to the realm endpoint
        if (!discoveryEnabled && path != null && !path.isEmpty()) {
            return authServerUrl;
        }

        // Default discovery endpoint
        String basePath = path != null && !path.isEmpty() ? path.replaceAll("/$", "") : "";
        return uri.getScheme() + "://" + uri.getAuthority() + basePath + "/.well-known/openid-configuration";
    }

    /**
     * Provider interface for HTTP connections to allow mocking in tests.
     */
    public interface HttpConnectionProvider {
        HttpURLConnection createConnection(String urlString) throws IOException;
    }

    @ApplicationScoped
    public static class DefaultHttpConnectionProvider implements HttpConnectionProvider {
        @Override
        public HttpURLConnection createConnection(String urlString) throws IOException {
            try {
                HttpURLConnection connection =
                        (HttpURLConnection) new URI(urlString).toURL().openConnection();
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);
                connection.setRequestMethod("GET");
                return connection;
            } catch (URISyntaxException e) {
                throw new IOException("Invalid URL: " + urlString, e);
            }
        }
    }
}
