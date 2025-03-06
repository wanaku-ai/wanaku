package ai.wanaku.server.quarkus.api.v1.resources;

import ai.wanaku.api.types.ResourceReference;
import ai.wanaku.server.quarkus.api.v1.models.WanakuResponse;
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
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestResponse;

import java.util.List;

@ApplicationScoped
@Path("/api/v1/resources")
public class ResourcesResource {
    private static final Logger LOG = Logger.getLogger(ResourcesResource.class);

    @Inject
    ResourcesBean resourcesBean;

    @Path("/expose")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response expose(ResourceReference resource) {
        try {
            resourcesBean.expose(resource);
            return Response.ok().build();
        } catch (Exception e) {
            LOG.errorf(e, "Failed to expose resource %s: %s", resource.getName(), e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Failed to expose resource").build();
        }
    }

    @Path("/list")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public RestResponse<WanakuResponse<List<ResourceReference>>> list() {
        try {
            List<ResourceReference> list = resourcesBean.list();
            return RestResponse.ok(new WanakuResponse<>(list));
        } catch (Exception e) {
            LOG.errorf(e, "Failed to list resources: %s", e.getMessage());
            throw new RuntimeException("Failed to list resources", e);
        }
    }

    @Path("/remove")
    @PUT
    public Response remove(@QueryParam("resource") String resource) {
        try {
            resourcesBean.remove(resource);
            return Response.ok().build();
        } catch (Exception e) {
            LOG.errorf(e, "Failed to remove resource %s: %s", resource, e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Failed to remove tool").build();
        }
    }
}
