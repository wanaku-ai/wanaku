package ai.wanaku.cli.main.support;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import java.io.IOException;

/**
 * A JAX-RS client request filter that automatically adds authentication headers
 * to outgoing requests based on stored credentials.
 */
public class AuthenticationInterceptor implements ClientRequestFilter {

    private final AuthCredentialStore credentialStore;

    public AuthenticationInterceptor() {
        this.credentialStore = new AuthCredentialStore();
    }

    public AuthenticationInterceptor(AuthCredentialStore credentialStore) {
        this.credentialStore = credentialStore;
    }

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        String authMode = credentialStore.getAuthMode();

        if ("none".equals(authMode) || !credentialStore.hasCredentials()) {
            return;
        }

        String apiToken = credentialStore.getApiToken();
        if (apiToken != null && !apiToken.trim().isEmpty()) {
            requestContext.getHeaders().add(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken);
        }
    }
}
