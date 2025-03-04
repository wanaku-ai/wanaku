package ai.wanaku.server.quarkus.api.v1.tools;

import java.util.List;

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

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.jboss.logging.Logger;

import ai.wanaku.api.types.ToolReference;

@ApplicationScoped
@Path("/api/v1/tools")
public class ToolsResource {
    private static final Logger LOG = Logger.getLogger(ToolsResource.class);

    @Inject
    ToolsBean toolsBean;

    @Path("/add")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response add(ToolReference resource) {
        try {
            toolsBean.add(resource);
            return Response.ok().build();
        } catch (Exception e) {
            LOG.errorf(e, "Failed to add tools %s: %s", resource.getName(), e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Failed to expose tool").build();
        }
    }

    @Path("/list")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = ToolReference.class)))
    public Response list() {
        try {
            List<ToolReference> list = toolsBean.list();
            return Response.ok().entity(list).build();
        } catch (Exception e) {
            LOG.errorf(e, "Failed to list tools: %s", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Failed to list tools").build();
        }
    }

    @Path("/remove")
    @PUT
    public Response remove(@QueryParam("tool") String tool) {
        try {
            toolsBean.remove(tool);
            return Response.ok().build();
        } catch (Exception e) {
            LOG.errorf(e, "Failed to remove tool %s: %s", tool, e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Failed to remove tool").build();
        }
    }
}
