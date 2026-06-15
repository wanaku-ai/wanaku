package ai.wanaku.backend.api.v1.oidc;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.net.http.HttpClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/.well-known")
@Produces(MediaType.APPLICATION_JSON)
public class OAuthAuthorizationServerWellKnownResource {
    @ConfigProperty(name = "quarkus.oidc-proxy.root-path", defaultValue = "/q/oidc")
    String oidcProxyRootPath;

    @ConfigProperty(name = "auth.proxy", defaultValue = "http://localhost:8080")
    String authProxy;

    private final OpenIdConfigurationForwarder forwarder = new OpenIdConfigurationForwarder(HttpClient.newHttpClient());

    @GET
    @Path("/oauth-authorization-server")
    public Response getAuthorizationServerMetadata() {
        return forwardToOpenIdConfiguration();
    }

    @GET
    @Path("/oauth-authorization-server/{tenant}")
    public Response getAuthorizationServerMetadataForTenant(@PathParam("tenant") String tenant) {
        return forwardToOpenIdConfiguration();
    }

    private Response forwardToOpenIdConfiguration() {
        return forwarder.forward(proxyBaseUri(), oidcProxyRootPath);
    }

    /**
     * The base URI the OIDC metadata is fetched from. It is pinned to the configured local OIDC
     * proxy ({@code auth.proxy}) rather than derived from the inbound request, so a caller cannot
     * steer the server's outbound request to an arbitrary host via the {@code Host} /
     * {@code X-Forwarded-*} headers (SSRF).
     *
     * @return the trusted base URI (always ending with {@code /})
     */
    URI proxyBaseUri() {
        String base = (authProxy != null && !authProxy.isBlank()) ? authProxy.trim() : "http://localhost:8080";
        return URI.create(base.endsWith("/") ? base : base + "/");
    }
}
