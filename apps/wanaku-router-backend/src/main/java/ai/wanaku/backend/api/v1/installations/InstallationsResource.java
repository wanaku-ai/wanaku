package ai.wanaku.backend.api.v1.installations;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;
import org.jboss.logging.Logger;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;

/**
 * REST API resource for the capability launcher.
 *
 * <p>Provides endpoints to launch, stop, and query the status of capability
 * processes. Executable paths are resolved from server-side configuration
 * properties, not from HTTP input.
 *
 * <p>Base path: {@code /api/v1/capabilities/launcher}
 */
@ApplicationScoped
@Path("/api/v1/capabilities/launcher")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InstallationsResource {
    private static final Logger LOG = Logger.getLogger(InstallationsResource.class);

    @Inject
    Launcher launcher;

    /**
     * Launches a capability process for the given catalog and system.
     * POST /api/v1/capabilities/launcher/{catalogName}/{systemName}/launch
     *
     * @param catalogName the service catalog name
     * @param systemName  the system identifier within the catalog
     * @return response containing the process status after launch
     */
    @POST
    @Path("/{catalogName}/{systemName}/launch")
    public WanakuResponse<ProcessStatus> launch(
            @PathParam("catalogName") String catalogName, @PathParam("systemName") String systemName) {
        LOG.infof("REST: Launching %s/%s", catalogName, systemName);
        ProcessStatus status = launcher.launch(catalogName, systemName);
        return new WanakuResponse<>(status);
    }

    /**
     * Stops a running capability process.
     * POST /api/v1/capabilities/launcher/{catalogName}/{systemName}/stop
     *
     * @param catalogName the service catalog name
     * @param systemName  the system identifier within the catalog
     * @return HTTP 200 if stopped successfully
     */
    @POST
    @Path("/{catalogName}/{systemName}/stop")
    public Response stop(@PathParam("catalogName") String catalogName, @PathParam("systemName") String systemName) {
        LOG.infof("REST: Stopping %s/%s", catalogName, systemName);
        launcher.stop(catalogName, systemName);
        return Response.ok().build();
    }

    /**
     * Returns the process status for a specific catalog and system.
     * GET /api/v1/capabilities/launcher/{catalogName}/{systemName}/status
     *
     * @param catalogName the service catalog name
     * @param systemName  the system identifier within the catalog
     * @return response containing the current process status
     */
    @GET
    @Path("/{catalogName}/{systemName}/status")
    public WanakuResponse<ProcessStatus> getStatus(
            @PathParam("catalogName") String catalogName, @PathParam("systemName") String systemName) {
        LOG.debugf("REST: Getting status for %s/%s", catalogName, systemName);
        ProcessStatus status = launcher.getStatus(catalogName, systemName);
        return new WanakuResponse<>(status);
    }

    /**
     * Returns the status of all managed capability processes.
     * GET /api/v1/capabilities/launcher/status
     *
     * @return response containing a map of process keys to their statuses
     */
    @GET
    @Path("/status")
    public WanakuResponse<Map<String, ProcessStatus>> getAllStatuses() {
        LOG.debug("REST: Getting all launcher statuses");
        return new WanakuResponse<>(launcher.getAllStatuses());
    }
}
