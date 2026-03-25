package ai.wanaku.backend.api.v1.oidc;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.jboss.logging.Logger;

public class OpenIdConfigurationForwarder {
    private static final Logger LOG = Logger.getLogger(OpenIdConfigurationForwarder.class);

    private final HttpClient httpClient;

    public OpenIdConfigurationForwarder(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public Response forward(URI baseUri, String oidcProxyRootPath) {
        URI target = resolveTarget(baseUri, oidcProxyRootPath);
        HttpRequest request = HttpRequest.newBuilder(target).GET().build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return Response.ok(response.body(), MediaType.APPLICATION_JSON).build();
            }

            LOG.warnf("OIDC metadata request to %s returned %d", target, response.statusCode());
            return Response.status(response.statusCode())
                    .type(MediaType.APPLICATION_JSON)
                    .entity(response.body())
                    .build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.errorf(e, "Failed to fetch OIDC metadata from %s", target);
            return Response.status(Response.Status.BAD_GATEWAY).build();
        } catch (IOException e) {
            LOG.errorf(e, "Failed to fetch OIDC metadata from %s", target);
            return Response.status(Response.Status.BAD_GATEWAY).build();
        }
    }

    private static URI resolveTarget(URI baseUri, String oidcProxyRootPath) {
        URI safeBase = baseUri != null ? baseUri : URI.create("/");
        String root = oidcProxyRootPath != null ? oidcProxyRootPath.trim() : "/q/oidc";
        if (root.startsWith("/")) {
            root = root.substring(1);
        }
        if (!root.endsWith("/")) {
            root = root + "/";
        }
        return safeBase.resolve(root + ".well-known/openid-configuration");
    }
}
