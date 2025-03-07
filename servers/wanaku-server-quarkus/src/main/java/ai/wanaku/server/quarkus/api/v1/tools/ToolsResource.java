package ai.wanaku.server.quarkus.api.v1.tools;

import ai.wanaku.api.exceptions.WanakuException;
import ai.wanaku.api.types.ToolReference;
import ai.wanaku.api.types.WanakuResponse;
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
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestResponse;

import java.util.List;

@ApplicationScoped
@Path("/api/v1/tools")
public class ToolsResource {
    private static final Logger LOG = Logger.getLogger(ToolsResource.class);

    @Inject
    ToolsBean toolsBean;

    @Path("/add")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response add(ToolReference resource) throws WanakuException {
        toolsBean.add(resource);
        return Response.ok().build();
    }

    @Path("/list")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public RestResponse<WanakuResponse<List<ToolReference>>> list() throws WanakuException {
        return RestResponse.ok(new WanakuResponse<>(toolsBean.list()));
    }

    @Path("/remove")
    @PUT
    public Response remove(@QueryParam("tool") String tool) throws WanakuException {
        toolsBean.remove(tool);
        return Response.ok().build();
    }
}
