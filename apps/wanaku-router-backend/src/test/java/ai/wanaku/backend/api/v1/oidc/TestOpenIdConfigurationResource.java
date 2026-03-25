package ai.wanaku.backend.api.v1.oidc;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@ApplicationScoped
@Path("/test-oidc/.well-known")
@Produces(MediaType.APPLICATION_JSON)
public class TestOpenIdConfigurationResource {

    @GET
    @Path("/openid-configuration")
    public String openIdConfiguration() {
        return "{\"issuer\":\"http://test-issuer\"}";
    }
}
