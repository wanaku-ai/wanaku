package ai.wanaku.core.services.api;

import ai.wanaku.api.types.WanakuResponse;
import ai.wanaku.api.types.discovery.ActivityRecord;
import ai.wanaku.api.types.providers.ServiceTarget;
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
import java.util.Map;

/**
 * JAX-RS service interface for managing and monitoring capabilities in the Wanaku system.
 * <p>
 * This service provides REST endpoints for discovering available capability providers
 * (both tools and resources), monitoring their health and activity state, and configuring
 * their runtime settings. It acts as a central point for capability lifecycle management
 * and service discovery.
 * <p>
 * All endpoints are available under the {@code /api/v1/capabilities} base path.
 */
@Path("/api/v1/capabilities")
public interface CapabilitiesService {

    /**
     * Lists all available tool capability providers.
     *
     * @return a {@link WanakuResponse} containing a list of tool service targets
     */
    @Path("/tools/list")
    @GET
    @Consumes(MediaType.TEXT_PLAIN)
    WanakuResponse<List<ServiceTarget>> toolsList();

    /**
     * Retrieves the health and activity state of all tool providers.
     * <p>
     * Returns a map where keys are service identifiers and values are lists of
     * activity records tracking the health and availability of each tool provider.
     *
     * @return a {@link WanakuResponse} containing the state map of tool providers
     */
    @Path("/tools/state")
    @GET
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<Map<String, List<ActivityRecord>>> toolsState();

    /**
     * Configures runtime settings for a specific tool provider service.
     *
     * @param service the identifier of the service to configure
     * @param option the configuration option name
     * @param value the configuration value to set
     * @return a {@link Response} indicating the result of the configuration operation
     */
    @Path("/tools/configure/{service}")
    @PUT
    @Consumes(MediaType.TEXT_PLAIN)
    Response toolsConfigure(
            @PathParam("service") String service,
            @QueryParam("option") String option,
            @QueryParam("value") String value);

    /**
     * Lists all available resource capability providers.
     *
     * @return a {@link WanakuResponse} containing a list of resource service targets
     */
    @Path("/resources/list")
    @GET
    @Consumes(MediaType.TEXT_PLAIN)
    WanakuResponse<List<ServiceTarget>> resourcesList();

    /**
     * Configures runtime settings for a specific resource provider service.
     *
     * @param service the identifier of the service to configure
     * @param option the configuration option name
     * @param value the configuration value to set
     * @return a {@link Response} indicating the result of the configuration operation
     */
    @Path("/resources/configure/{service}")
    @PUT
    @Consumes(MediaType.TEXT_PLAIN)
    Response resourcesConfigure(
            @PathParam("service") String service,
            @QueryParam("option") String option,
            @QueryParam("value") String value);

    /**
     * Retrieves the health and activity state of all resource providers.
     * <p>
     * Returns a map where keys are service identifiers and values are lists of
     * activity records tracking the health and availability of each resource provider.
     *
     * @return a {@link WanakuResponse} containing the state map of resource providers
     */
    @Path("/resources/state")
    @GET
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<Map<String, List<ActivityRecord>>> resourcesState();
}
