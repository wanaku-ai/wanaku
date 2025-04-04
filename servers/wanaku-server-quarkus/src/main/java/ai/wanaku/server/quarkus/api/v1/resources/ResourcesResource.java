package ai.wanaku.server.quarkus.api.v1.resources;

import ai.wanaku.api.exceptions.WanakuException;
import ai.wanaku.api.types.ResourceReference;
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

import ai.wanaku.core.util.CollectionsHelper;
import ai.wanaku.server.quarkus.api.v1.forwards.ForwardsBean;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestResponse;

import java.util.List;

@ApplicationScoped
@Path("/api/v1/resources")
public class ResourcesResource {

    @Inject
    ResourcesBean resourcesBean;

    @Inject
    ForwardsBean forwardsBean;

    @Path("/expose")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response expose(ResourceReference resource) throws WanakuException {
        resourcesBean.expose(resource);
        return Response.ok().build();
    }

    @Path("/list")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public RestResponse<WanakuResponse<List<ResourceReference>>> list() throws WanakuException {
        List<ResourceReference> list = resourcesBean.list();
        List<ResourceReference> resourceReferences = forwardsBean.listAllResources();

        List<ResourceReference> ret = CollectionsHelper.join(list, resourceReferences);

        return RestResponse.ok(new WanakuResponse<>(ret));
    }

    @Path("/remove")
    @PUT
    public Response remove(@QueryParam("resource") String resource) throws WanakuException {
        resourcesBean.remove(resource);
        return Response.ok().build();
    }

    @Path("/update")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response update(ResourceReference resource) throws WanakuException {
        resourcesBean.update(resource);
        return Response.ok().build();
    }
}
