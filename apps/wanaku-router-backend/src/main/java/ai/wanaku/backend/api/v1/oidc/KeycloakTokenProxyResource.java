/*
 * Copyright 2026 Wanaku AI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.wanaku.backend.api.v1.oidc;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@Path("/realms/{realm}/protocol/openid-connect")
public class KeycloakTokenProxyResource {

    private static final Logger LOG = Logger.getLogger(KeycloakTokenProxyResource.class);

    @ConfigProperty(name = "auth.server", defaultValue = "http://localhost:8543")
    String authServer;

    @ConfigProperty(name = "auth.realm", defaultValue = "wanaku")
    String configuredRealm;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @POST
    @Path("/token")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Response proxyToken(
            @PathParam("realm") String realm, @HeaderParam("Authorization") String authorization, String formBody) {

        if (!configuredRealm.equals(realm)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        URI target = URI.create(authServer + "/realms/" + realm + "/protocol/openid-connect/token");
        HttpRequest.Builder builder = HttpRequest.newBuilder(target)
                .POST(HttpRequest.BodyPublishers.ofString(formBody != null ? formBody : ""))
                .header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED);

        if (authorization != null && !authorization.isBlank()) {
            builder.header("Authorization", authorization);
        }

        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return Response.ok(response.body(), MediaType.APPLICATION_JSON).build();
            }

            LOG.warnf("Keycloak token request to %s returned %d", target, response.statusCode());
            return Response.status(response.statusCode())
                    .type(MediaType.APPLICATION_JSON)
                    .entity(response.body())
                    .build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.errorf(e, "Interrupted while proxying token request to %s", target);
            return Response.status(Response.Status.BAD_GATEWAY).build();
        } catch (IOException e) {
            LOG.errorf(e, "Failed to proxy token request to %s", target);
            return Response.status(Response.Status.BAD_GATEWAY).build();
        }
    }
}
