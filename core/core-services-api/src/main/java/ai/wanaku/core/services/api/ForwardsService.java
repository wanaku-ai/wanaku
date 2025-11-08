package ai.wanaku.core.services.api;

import ai.wanaku.api.types.ForwardReference;
import ai.wanaku.api.types.WanakuResponse;
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
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    Response addForward(ForwardReference reference);

    /**
     * Removes a forward reference from the system.
     *
     * @param forwardName the name of the forward reference to remove
     * @return a {@link Response} indicating the result of the remove operation
     */
    @Path("/{forwardName}")
    @DELETE
    Response removeForward(@PathParam("forwardName") String forwardName);

    /**
     * Lists all registered forward references.
     *
     * @return a {@link WanakuResponse} containing a list of all forward references
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<List<ForwardReference>> listForwards();

    /**
     * Updates an existing forward reference.
     *
     * @param forwardName the name of the forward reference to update
     * @param reference the updated forward reference
     * @return a {@link Response} indicating the result of the update operation
     */
    @Path("/{forwardName}")
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    Response update(@PathParam("forwardName") String forwardName, ForwardReference reference);
}
