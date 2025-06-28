package ai.wanaku.server.quarkus.api.v1.tools;

import ai.wanaku.api.exceptions.ToolNotFoundException;
import ai.wanaku.api.exceptions.WanakuException;
import ai.wanaku.api.types.ToolReference;
import ai.wanaku.api.types.WanakuResponse;
import ai.wanaku.api.types.io.ToolPayload;
import ai.wanaku.core.util.CollectionsHelper;
import ai.wanaku.server.quarkus.api.v1.forwards.ForwardsBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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

@ApplicationScoped
@Path("/api/v1/tools")
public class ToolsResource {
    @Inject
    ToolsBean toolsBean;

    @Inject
    ForwardsBean forwardsBean;

    @Path("/add")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public WanakuResponse<ToolReference> add(ToolReference resource) throws WanakuException {
        var ret = toolsBean.add(resource);
        return new WanakuResponse<>(ret);
    }

    @Path("/addWithPayload")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public WanakuResponse<ToolReference> addWithPayload(ToolPayload resource) throws WanakuException {
        var ret = toolsBean.add(resource);
        return new WanakuResponse<>(ret);
    }

    @Path("/list")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public WanakuResponse<List<ToolReference>> list() throws WanakuException {
        List<ToolReference> forwardTools = forwardsBean.listAllAsTools();
        List<ToolReference> tools = toolsBean.list();

        List<ToolReference> ret = CollectionsHelper.join(tools, forwardTools);

        return new WanakuResponse<>(ret);
    }

    @Path("/remove")
    @PUT
    public Response remove(@QueryParam("tool") String tool) throws WanakuException {
        int deleteCount = toolsBean.removeByName(tool);
        if (deleteCount > 0) {
            return Response.ok().build();
        }else{
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    @Path("/update")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response update(ToolReference resource) throws WanakuException {
        toolsBean.update(resource);
        return Response.ok().build();

    }

    @Path("/")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public  WanakuResponse<ToolReference> getByName(@QueryParam("name") String name) throws WanakuException {
        ToolReference tool = toolsBean.getByName(name);
        if (tool == null) {
            throw new ToolNotFoundException(name);
        }
        return new WanakuResponse<>(tool);
    }
}
