package ai.wanaku.backend.api.v1.resources;

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

import ai.wanaku.api.types.io.ProvisionAwarePayload;
import ai.wanaku.api.types.io.ResourcePayload;
import ai.wanaku.core.util.CollectionsHelper;
import ai.wanaku.backend.api.v1.forwards.ForwardsBean;

import java.util.List;

@ApplicationScoped
@Path("/api/v1/resources")
public class ResourcesResource {

    @Inject
    ResourcesBean resourcesBean;

    @Inject
    ForwardsBean forwardsBean;

    @Path("/exposeWithPayload")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public WanakuResponse<ResourceReference> exposeWithPayload(ResourcePayload resource) throws WanakuException {
        ResourceReference ret = resourcesBean.expose(resource);
        validatePayload(resource);
        return new WanakuResponse<>(ret);
    }

    private void validatePayload(ProvisionAwarePayload<?> resource) throws WanakuException {
        if (resource == null) {
            throw new WanakuException("The request itself must not be null");
        }
        if (resource.getPayload() == null) {
            throw new WanakuException("The 'payload' is required for this request");
        }
    }

    @Path("/expose")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public WanakuResponse<ResourceReference> expose(ResourceReference resource) throws WanakuException {
        ResourceReference ret = resourcesBean.expose(resource);
        return new WanakuResponse<>(ret);
    }

    @Path("/list")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public WanakuResponse<List<ResourceReference>> list() throws WanakuException {
        List<ResourceReference> list = resourcesBean.list();
        List<ResourceReference> resourceReferences = forwardsBean.listAllResources();

        List<ResourceReference> ret = CollectionsHelper.join(list, resourceReferences);

        return new WanakuResponse<>(ret);
    }

    @Path("/remove")
    @PUT
    public Response remove(@QueryParam("resource") String resource) throws WanakuException {
        int deleteCount = resourcesBean.remove(resource);
        if (deleteCount > 0) {
            return Response.ok().build();
        }else{
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    @Path("/update")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response update(ResourceReference resource) throws WanakuException {
        resourcesBean.update(resource);
        return Response.ok().build();
    }
}
