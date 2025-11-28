package ai.wanaku.core.services.api;

import ai.wanaku.capabilities.sdk.api.types.ResourceReference;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.capabilities.sdk.api.types.io.ResourcePayload;
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

/**
 * JAX-RS service interface for managing resource capabilities in the Wanaku system.
 * <p>
 * This service provides REST endpoints for exposing, listing, and removing resource
 * capabilities. Resources are data sources or content that can be accessed by AI agents,
 * such as files, databases, or external data sources.
 * <p>
 * All endpoints are available under the {@code /api/v1/resources} base path.
 */
@Path("/api/v1/resources")
public interface ResourcesService {

    /**
     * Exposes a new resource capability with configuration and secrets payload.
     * <p>
     * This endpoint allows exposing a resource along with its provisioning data,
     * including configuration settings and secrets required for resource access.
     *
     * @param resourceReference the resource payload containing the resource reference and provisioning data
     * @return a {@link Response} indicating the result of the expose operation
     */
    @Path("/exposeWithPayload")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Response exposeWithPayload(ResourcePayload resourceReference);

    /**
     * Exposes a new resource capability in the system.
     *
     * @param resourceReference the resource reference containing resource metadata
     * @return a {@link Response} indicating the result of the expose operation
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/expose")
    Response expose(ResourceReference resourceReference);

    /**
     * Lists all exposed resource capabilities.
     *
     * @param labelFilter optional label expression to filter resources by labels
     * @return a {@link WanakuResponse} containing a list of all resource references
     */
    @Path("/list")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<List<ResourceReference>> list(@QueryParam("labelFilter") String labelFilter);

    /**
     * Lists all exposed resource capabilities without filtering.
     *
     * @return a {@link WanakuResponse} containing a list of all resource references
     */
    default WanakuResponse<List<ResourceReference>> list() {
        return list(null);
    }

    /**
     * Removes a resource capability from the system.
     *
     * @param resource the name of the resource to remove
     * @return a {@link Response} indicating the result of the removal operation
     */
    @Path("/remove")
    @PUT
    Response remove(@QueryParam("resource") String resource);

    /**
     * Retrieves a resource capability by its name.
     *
     * @param name the name of the resource to retrieve
     * @return a {@link WanakuResponse} containing the resource reference
     */
    @Path("/")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    WanakuResponse<ResourceReference> getByName(@QueryParam("name") String name);

    /**
     * Updates an existing resource capability.
     *
     * @param resource the updated resource reference
     * @return a {@link Response} indicating the result of the update operation
     */
    @Path("/update")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    Response update(ResourceReference resource);
}
