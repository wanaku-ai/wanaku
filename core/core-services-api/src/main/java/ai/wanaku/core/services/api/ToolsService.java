package ai.wanaku.core.services.api;

import ai.wanaku.api.exceptions.WanakuException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import ai.wanaku.api.types.ToolReference;
import ai.wanaku.api.types.WanakuResponse;
import ai.wanaku.api.types.io.ToolPayload;
import java.util.List;

@Path("/api/v1/tools")
public interface ToolsService {

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/add")
    WanakuResponse<ToolReference> add(ToolReference toolReference);

    @Path("/addWithPayload")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<ToolReference> addWithPayload(ToolPayload resource) throws WanakuException;

    @Path("/list")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<List<ToolReference>> list();

    @Path("/remove")
    @PUT
    Response remove(@QueryParam("tool") String tool);

    @Path("/update")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    Response update(ToolReference resource) throws WanakuException;


    @Path("/")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    WanakuResponse<ToolReference> getByName(@QueryParam("name") String name) throws WanakuException;

}
