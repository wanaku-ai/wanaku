package ai.wanaku.backend.api.v1.resources;

import ai.wanaku.api.exceptions.WanakuException;
import ai.wanaku.api.types.ResourceReference;
import ai.wanaku.api.types.WanakuResponse;
import ai.wanaku.api.types.io.ProvisionAwarePayload;
import ai.wanaku.api.types.io.ResourcePayload;
import ai.wanaku.backend.api.v1.forwards.ForwardsBean;
import ai.wanaku.core.util.CollectionsHelper;
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
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

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
     * @throws WanakuException if exposure fails or payload validation fails
     */
    @Path("/with-payload")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public WanakuResponse<ResourceReference> exposeWithPayload(ResourcePayload resource) throws WanakuException {
        ResourceReference ret = resourcesBean.expose(resource);
        validatePayload(resource);
        return new WanakuResponse<>(ret);
    }

    /**
     * Validates that a provision-aware payload is not null and contains a payload object.
     *
     * @param resource the payload to validate
     * @throws WanakuException if the payload or its nested payload object is null
     */
    private void validatePayload(ProvisionAwarePayload<?> resource) throws WanakuException {
        if (resource == null) {
            throw new WanakuException("The request itself must not be null");
        }
        if (resource.getPayload() == null) {
            throw new WanakuException("The 'payload' is required for this request");
        }
    }

    /**
     * Exposes a new resource capability.
     *
     * @param resource the resource reference to expose
     * @return a response containing the exposed resource reference
     * @throws WanakuException if exposure fails
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public WanakuResponse<ResourceReference> expose(ResourceReference resource) throws WanakuException {
        ResourceReference ret = resourcesBean.expose(resource);
        return new WanakuResponse<>(ret);
    }

    /**
     * Lists all available resource capabilities.
     * <p>
     * This endpoint returns a combined list of both locally exposed resources
     * and resources available through forward proxies to remote MCP servers.
     *
     * @return a response containing a list of all resource references
     * @throws WanakuException if listing fails
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public WanakuResponse<List<ResourceReference>> list() throws WanakuException {
        List<ResourceReference> list = resourcesBean.list();
        List<ResourceReference> resourceReferences = forwardsBean.listAllResources();

        List<ResourceReference> ret = CollectionsHelper.join(list, resourceReferences);

        return new WanakuResponse<>(ret);
    }

    /**
     * Removes a resource capability by name.
     *
     * @param resourceName the name of the resource to remove
     * @return HTTP 200 OK if removed successfully, HTTP 404 NOT FOUND if the resource doesn't exist
     * @throws WanakuException if removal fails
     */
    @Path("/{resourceName}")
    @DELETE
    public Response remove(@PathParam("resourceName") String resourceName) throws WanakuException {
        int deleteCount = resourcesBean.remove(resourceName);
        if (deleteCount > 0) {
            return Response.ok().build();
        } else {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    /**
     * Updates an existing resource capability.
     *
     * @param resourceName the name of the resource to update
     * @param resource the updated resource reference
     * @return HTTP 200 OK if updated successfully
     * @throws WanakuException if update fails
     */
    @Path("/{resourceName}")
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response update(@PathParam("resourceName") String resourceName, ResourceReference resource)
            throws WanakuException {
        // Ensure the resource has the correct name
        resource.setName(resourceName);
        resourcesBean.update(resource);
        return Response.ok().build();
    }
}
