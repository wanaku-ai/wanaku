package ai.wanaku.backend.api.v1.resources;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import ai.wanaku.backend.api.v1.common.PayloadValidator;
import ai.wanaku.backend.api.v1.forwards.ForwardsBean;
import ai.wanaku.capabilities.sdk.api.exceptions.ResourceNotFoundException;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.capabilities.sdk.api.types.io.ResourcePayload;
import ai.wanaku.core.util.CollectionsHelper;
import ai.wanaku.core.util.StringHelper;

/**
 * JAX-RS REST resource implementation for resource management endpoints.
 * <p>
 * This class implements the {@code /api/v1/resources} endpoints for managing
 * resource capabilities in the Wanaku router. It delegates business logic to
 * {@link ResourcesBean} and integrates with {@link ForwardsBean} to provide
 * a unified view of both locally exposed resources and resources available
 * through forward proxies.
 * <p>
 * This is an application-scoped CDI bean that serves as the entry point for
 * HTTP requests related to resource management.
 */
@ApplicationScoped
@Path("/api/v1/resources")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ResourcesResource {

    @Inject
    ResourcesBean resourcesBean;

    @Inject
    ForwardsBean forwardsBean;

    /**
     * Exposes a new resource capability with provisioning payload.
     * <p>
     * This endpoint accepts a resource reference along with configuration and secrets
     * data, enabling complete provisioning of the resource capability.
     *
     * @param resource the resource payload containing the resource reference and provisioning data
     * @return a response containing the exposed resource reference
     */
    @Path("/payloads")
    @POST
    public WanakuResponse<ResourceReference> exposeWithPayload(ResourcePayload resource) {
        PayloadValidator.validate(resource);
        if (resource.getPayload() instanceof ResourceReference resourceReference
                && StringHelper.isEmpty(resourceReference.getName())) {
            throw new WanakuException("The 'payload.name' is required for this request");
        }
        ResourceReference ret = resourcesBean.expose(resource);
        return new WanakuResponse<>(ret);
    }

    /**
     * Exposes a new resource capability.
     *
     * @param resource the resource reference to expose
     * @return a response containing the exposed resource reference
     */
    @POST
    public WanakuResponse<ResourceReference> expose(ResourceReference resource) {
        ResourceReference ret = resourcesBean.expose(resource);
        return new WanakuResponse<>(ret);
    }

    /**
     * Lists all available resource capabilities.
     * <p>
     * This endpoint returns a combined list of both locally exposed resources
     * and resources available through forward proxies to remote MCP servers.
     *
     * @param labelFilter optional label expression to filter resources by labels
     * @return a response containing a list of all resource references
     */
    @GET
    public WanakuResponse<List<ResourceReference>> list(@QueryParam("labelFilter") String labelFilter) {
        List<ResourceReference> list = resourcesBean.list(labelFilter);
        List<ResourceReference> resourceReferences = forwardsBean.listAllResources();

        List<ResourceReference> ret = CollectionsHelper.join(list, resourceReferences);

        return new WanakuResponse<>(ret);
    }

    /**
     * Retrieves a resource capability by name.
     *
     * @param name the name of the resource to retrieve
     * @return a response containing the resource reference
     * @throws ResourceNotFoundException if the resource is not found
     */
    @Path("/{name}")
    @GET
    public WanakuResponse<ResourceReference> getByName(@PathParam("name") String name) {
        ResourceReference resource = resourcesBean.getByName(name);
        if (resource == null) {
            throw new ResourceNotFoundException(name);
        }
        return new WanakuResponse<>(resource);
    }

    /**
     * Removes a resource capability by name.
     *
     * @param name the name of the resource to remove
     * @return HTTP 200 OK if removed successfully, HTTP 404 NOT FOUND if the resource doesn't exist
     */
    @Path("/{name}")
    @DELETE
    public Response remove(@PathParam("name") String name) {
        int deleteCount = resourcesBean.remove(name);
        if (deleteCount > 0) {
            return Response.ok().build();
        } else {
            throw new ResourceNotFoundException(name);
        }
    }

    /**
     * Updates an existing resource capability.
     *
     * @param resource the updated resource reference
     * @return HTTP 200 OK if updated successfully
     */
    @Path("/{name}")
    @PUT
    public Response update(@PathParam("name") String name, ResourceReference resource) {
        if (resource != null && StringHelper.isEmpty(resource.getName())) {
            resource.setName(name);
        }
        resourcesBean.update(resource);
        return Response.ok().build();
    }
}
