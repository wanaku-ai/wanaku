package ai.wanaku.backend.api.v1.oidc;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/.well-known/oauth-protected-resource")
@Produces(MediaType.APPLICATION_JSON)
public class OAuthProtectedResourceWellKnownResource {

    @ConfigProperty(name = "quarkus.oidc-proxy.root-path", defaultValue = "/q/oidc")
    String oidcProxyRootPath;

    @Context
    UriInfo uriInfo;

    @Context
    HttpHeaders httpHeaders;

    @GET
    @Path("/{namespace}/mcp")
    public Response getProtectedResourceMetadata(@PathParam("namespace") String namespace) {
        String baseUrl = resolveBaseUrl();

        String resource = baseUrl + "/" + namespace + "/mcp";
        String authorizationServer = baseUrl + oidcProxyRootPath;

        Map<String, Object> metadata =
                Map.of("resource", resource, "authorization_servers", List.of(authorizationServer));

        return Response.ok(metadata, MediaType.APPLICATION_JSON).build();
    }

    private String resolveBaseUrl() {
        URI baseUri = uriInfo.getBaseUri();

        String scheme = baseUri.getScheme();
        String forwardedProto = httpHeaders.getHeaderString("X-Forwarded-Proto");
        if (forwardedProto != null && !forwardedProto.isBlank()) {
            scheme = forwardedProto.trim();
        }

        String authority = baseUri.getAuthority();
        String forwardedHost = httpHeaders.getHeaderString("X-Forwarded-Host");
        if (forwardedHost != null && !forwardedHost.isBlank()) {
            authority = forwardedHost.trim().split(",")[0].trim();
        }

        String path = baseUri.getPath();
        String forwardedPrefix = httpHeaders.getHeaderString("X-Forwarded-Prefix");
        if (forwardedPrefix != null && !forwardedPrefix.isBlank()) {
            path = forwardedPrefix.trim();
        }
        if (path != null && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        return scheme + "://" + authority + (path != null ? path : "");
    }
}
