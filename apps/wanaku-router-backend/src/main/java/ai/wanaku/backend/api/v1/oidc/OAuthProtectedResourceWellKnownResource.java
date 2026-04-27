package ai.wanaku.backend.api.v1.oidc;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.net.URI;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/.well-known/oauth-protected-resource")
@Produces(MediaType.APPLICATION_JSON)
public class OAuthProtectedResourceWellKnownResource {

    @ConfigProperty(name = "quarkus.oidc-proxy.root-path", defaultValue = "/q/oidc")
    String oidcProxyRootPath;

    @Context
    UriInfo uriInfo;

    @GET
    @Path("/{namespace}/mcp")
    public Response getProtectedResourceMetadata(@PathParam("namespace") String namespace) {
        URI baseUri = uriInfo.getBaseUri();
        String baseUrl = baseUri.toString();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        String resource = baseUrl + "/" + namespace + "/mcp";
        String authorizationServer = baseUrl + oidcProxyRootPath;

        String json =
                "{\"resource\":\"" + resource + "\"," + "\"authorization_servers\":[\"" + authorizationServer + "\"]}";

        return Response.ok(json, MediaType.APPLICATION_JSON).build();
    }
}
