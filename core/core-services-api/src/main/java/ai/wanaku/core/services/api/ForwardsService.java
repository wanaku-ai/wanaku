package ai.wanaku.core.services.api;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import ai.wanaku.api.types.ForwardReference;
import ai.wanaku.api.types.WanakuResponse;
import java.util.List;

@Path("/api/v1/forwards")
public interface ForwardsService {
    @Path("/add")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    Response addForward(ForwardReference reference);

    @Path("/remove")
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    Response removeForward(ForwardReference reference);

    @Path("/list")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<List<ForwardReference>> listForwards();
}
