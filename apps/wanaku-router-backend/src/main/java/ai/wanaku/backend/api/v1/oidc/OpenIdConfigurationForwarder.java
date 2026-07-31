package ai.wanaku.backend.api.v1.oidc;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.jboss.logging.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class OpenIdConfigurationForwarder {
    private static final Logger LOG = Logger.getLogger(OpenIdConfigurationForwarder.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient;

    public OpenIdConfigurationForwarder(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public Response forward(URI baseUri, String oidcProxyRootPath) {
        return forward(baseUri, oidcProxyRootPath, null);
    }

    public Response forward(URI baseUri, String oidcProxyRootPath, String issuerOverride) {
        URI target = resolveTarget(baseUri, oidcProxyRootPath);
        HttpRequest request = HttpRequest.newBuilder(target).GET().build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String body = response.body();
                if (issuerOverride != null) {
                    body = rewriteIssuer(body, issuerOverride);
                }
                return Response.ok(body, MediaType.APPLICATION_JSON).build();
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

    private static String rewriteIssuer(String json, String issuerOverride) {
        try {
            ObjectNode node = (ObjectNode) MAPPER.readTree(json);
            node.put("issuer", issuerOverride);
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            LOG.debugf(e, "Failed to rewrite issuer in OIDC metadata, returning original");
            return json;
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
