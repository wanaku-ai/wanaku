package ai.wanaku.core.services.api;

import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.capabilities.sdk.api.types.discovery.ActivityRecord;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
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
     * Lists all available capabilities.
     *
     * @return a {@link WanakuResponse} containing a list of tool service targets
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<List<ServiceTarget>> list();

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

    /**
     * Lists all stale capabilities based on the provided criteria.
     * <p>
     * A capability is considered stale if it hasn't been seen within the specified
     * time threshold. Optionally, only inactive capabilities can be included.
     *
     * @param maxAgeSeconds the maximum age in seconds since last seen (default: 86400 = 1 day)
     * @param inactiveOnly if true, only return capabilities that are also marked as inactive
     * @return a {@link WanakuResponse} containing a list of stale capability information
     */
    @Path("/stale")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<List<StaleCapabilityInfo>> listStale(
            @QueryParam("maxAgeSeconds") Long maxAgeSeconds, @QueryParam("inactiveOnly") Boolean inactiveOnly);

    /**
     * Cleans up (removes) all stale capabilities based on the provided criteria.
     * <p>
     * This operation is destructive and cannot be undone. Each removed capability
     * will trigger a deregistration SSE event.
     *
     * @param maxAgeSeconds the maximum age in seconds since last seen (default: 86400 = 1 day)
     * @param inactiveOnly if true, only remove capabilities that are also marked as inactive
     * @return a {@link WanakuResponse} containing the count of removed capabilities
     */
    @Path("/stale")
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<Integer> cleanupStale(
            @QueryParam("maxAgeSeconds") Long maxAgeSeconds, @QueryParam("inactiveOnly") Boolean inactiveOnly);
}
