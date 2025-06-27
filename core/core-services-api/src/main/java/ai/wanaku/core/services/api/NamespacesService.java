package ai.wanaku.core.services.api;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import ai.wanaku.api.types.Namespace;
import ai.wanaku.api.types.WanakuResponse;
import java.util.List;

@Path("/api/v1/namespaces")
public interface NamespacesService {

    @Path("/list")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<List<Namespace>> list();
}
