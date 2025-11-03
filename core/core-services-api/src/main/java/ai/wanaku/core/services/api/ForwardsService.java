package ai.wanaku.core.services.api;

import ai.wanaku.api.types.ForwardReference;
import ai.wanaku.api.types.WanakuResponse;
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
 * JAX-RS service interface for managing forward references in the Wanaku system.
 * <p>
 * This service provides REST endpoints for managing forwards, which enable the
 * Wanaku router to proxy requests to remote MCP (Model Context Protocol) servers
 * or other external capability providers.
 * <p>
 * All endpoints are available under the {@code /api/v1/forwards} base path.
 */
@Path("/api/v1/forwards")
public interface ForwardsService {
    /**
     * Registers a new forward reference in the system.
     * <p>
     * A forward reference configures the router to proxy requests for specific
     * capabilities to a remote server or service.
     *
     * @param reference the forward reference to register
     * @return a {@link Response} indicating the result of the add operation
     */
    @Path("/add")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    Response addForward(ForwardReference reference);

    /**
     * Removes a forward reference from the system.
     *
     * @param reference the forward reference to remove
     * @return a {@link Response} indicating the result of the remove operation
     */
    @Path("/remove")
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    Response removeForward(ForwardReference reference);

    /**
     * Lists all registered forward references.
     *
     * @param labelFilter optional label expression to filter forwards by labels
     * @return a {@link WanakuResponse} containing a list of all forward references
     */
    @Path("/list")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<List<ForwardReference>> listForwards(@QueryParam("labelFilter") String labelFilter);

    /**
     * Lists all registered forward references without filtering.
     *
     * @return a {@link WanakuResponse} containing a list of all forward references
     */
    default WanakuResponse<List<ForwardReference>> listForwards() {
        return listForwards(null);
    }

    /**
     * Retrieves a forward reference by its name.
     *
     * @param name the name of the forward to retrieve
     * @return a {@link WanakuResponse} containing the forward reference
     */
    @Path("/")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    WanakuResponse<ForwardReference> getByName(@QueryParam("name") String name);

    /**
     * Updates an existing forward reference.
     *
     * @param reference the updated forward reference
     * @return a {@link Response} indicating the result of the update operation
     */
    @Path("/update")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    Response updateForward(ForwardReference reference);
}
