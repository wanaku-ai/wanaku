package ai.wanaku.backend.api.v1.oidc;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.net.http.HttpClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/.well-known")
@Produces(MediaType.APPLICATION_JSON)
public class OAuthAuthorizationServerWellKnownResource {
    @ConfigProperty(name = "quarkus.oidc-proxy.root-path", defaultValue = "/q/oidc")
    String oidcProxyRootPath;

    @Context
    UriInfo uriInfo;

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
        return forwarder.forward(uriInfo != null ? uriInfo.getBaseUri() : null, oidcProxyRootPath);
    }
}
