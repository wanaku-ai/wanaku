package ai.wanaku.core.services.api;

import ai.wanaku.api.types.Namespace;
import ai.wanaku.api.types.WanakuResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

/**
 * JAX-RS service interface for managing namespaces in the Wanaku system.
 * <p>
 * This service provides REST endpoints for working with namespaces. Namespaces
 * provide logical grouping and isolation for capabilities, tools, and resources
 * within the Wanaku system.
 * </p>
 * <p>
 * All endpoints are available under the {@code /api/v1/namespaces} base path.
 * </p>
 */
@Path("/api/v1/namespaces")
public interface NamespacesService {

    /**
     * Lists all registered namespaces in the system.
     *
     * @return a {@link WanakuResponse} containing a list of all namespaces
     */
    @Path("/list")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<List<Namespace>> list();
}
