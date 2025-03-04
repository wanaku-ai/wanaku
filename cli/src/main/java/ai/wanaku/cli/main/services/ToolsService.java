package ai.wanaku.cli.main.services;

import java.util.List;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import ai.wanaku.api.types.ToolReference;

@Path("/api/v1/tools")
public interface ToolsService {

    @POST
    @Produces({ MediaType.APPLICATION_JSON})
    @Path("/add")
    Response add(ToolReference toolReference);

    @Path("/list")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    List<ToolReference> list();

    @Path("/remove")
    @PUT
    Response remove(@QueryParam("tool") String tool);
}
