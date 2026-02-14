package ai.wanaku.core.services.api;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import ai.wanaku.capabilities.sdk.api.types.Namespace;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;

/**
 * JAX-RS service interface for managing namespaces in the Wanaku system.
 * <p>
 * This service provides REST endpoints for working with namespaces. Namespaces
 * provide logical grouping and isolation for capabilities, tools, and resources
 * within the Wanaku system.
 * <p>
 * All endpoints are available under the {@code /api/v1/namespaces} base path.
 */
@Path("/api/v1/namespaces")
public interface NamespacesService {

    /**
     * Lists all registered namespaces in the system.
     *
     * @param labelFilter optional label expression to filter namespaces by labels
     * @return a {@link WanakuResponse} containing a list of all namespaces
     */
    @Path("/list")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<List<Namespace>> list(@QueryParam("labelFilter") String labelFilter);

    /**
     * Lists all registered namespaces in the system without filtering.
     *
     * @return a {@link WanakuResponse} containing a list of all namespaces
     */
    default WanakuResponse<List<Namespace>> list() {
        return list(null);
    }

    /**
     * Retrieves a specific namespace by its ID.
     *
     * @param id the ID of the namespace to retrieve
     * @return a {@link WanakuResponse} containing the namespace details
     */
    @Path("/{id}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<Namespace> getById(@PathParam("id") String id);

    /**
     * Updates an existing namespace.
     *
     * @param namespace the namespace object with updated information
     * @return a {@link Response} indicating the result of the update operation
     */
    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    Response update(@PathParam("id") String id, Namespace namespace);
}
