package ai.wanaku.core.services.api;

import ai.wanaku.api.types.ResourceReference;
import ai.wanaku.api.types.WanakuResponse;
import ai.wanaku.api.types.io.ResourcePayload;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

@Path("/api/v1/resources")
public interface ResourcesService {

    @Path("/exposeWithPayload")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Response exposeWithPayload(ResourcePayload resourceReference);

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/expose")
    Response expose(ResourceReference resourceReference);

    @Path("/list")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<List<ResourceReference>> list();

    @Path("/remove")
    @PUT
    Response remove(@QueryParam("resource") String resource);
}
