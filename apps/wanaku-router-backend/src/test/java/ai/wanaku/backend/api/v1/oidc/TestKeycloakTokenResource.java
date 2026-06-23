package ai.wanaku.backend.api.v1.oidc;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
@Path("/test-keycloak/realms/wanaku/protocol/openid-connect")
public class TestKeycloakTokenResource {

    @POST
    @Path("/token")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Response token(@HeaderParam("Authorization") String authorization, String formBody) {
        if (formBody != null && formBody.contains("grant_type=client_credentials")) {
            String json = "{\"access_token\":\"test-token\",\"token_type\":\"Bearer\",\"expires_in\":300}";
            return Response.ok(json, MediaType.APPLICATION_JSON).build();
        }

        return Response.status(Response.Status.BAD_REQUEST)
                .entity("{\"error\":\"unsupported_grant_type\"}")
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
