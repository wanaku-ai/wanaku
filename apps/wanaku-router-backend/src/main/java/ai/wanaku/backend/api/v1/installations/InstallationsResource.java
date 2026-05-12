package ai.wanaku.backend.api.v1.installations;

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
import org.jboss.logging.Logger;
import ai.wanaku.capabilities.sdk.api.types.DataStore;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;

/**
 * REST API resource for managing capability installations and their process lifecycle.
 *
 * <p>Provides CRUD operations for installation entries (stored as labeled DataStore entities)
 * and process management endpoints (launch, stop, status) for running capabilities locally.
 *
 * <p>Base path: {@code /api/v1/capabilities/installations}
 */
@ApplicationScoped
@Path("/api/v1/capabilities/installations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InstallationsResource {
    private static final Logger LOG = Logger.getLogger(InstallationsResource.class);

    @Inject
    InstallationsBean installationsBean;

    /**
     * Lists all capability installations.
     * GET /api/v1/capabilities/installations
     *
     * @return response containing the list of installations
     */
    @GET
    public WanakuResponse<List<DataStore>> list() {
        LOG.debug("REST: Listing all installations");
        List<DataStore> installations = installationsBean.list();
        return new WanakuResponse<>(installations);
    }

    /**
     * Creates a new capability installation.
     * POST /api/v1/capabilities/installations
     *
     * @param dataStore the installation data to create
     * @return response containing the created installation with generated ID
     */
    @POST
    public WanakuResponse<DataStore> create(DataStore dataStore) {
        LOG.debugf("REST: Creating installation: %s", dataStore.getName());
        DataStore result = installationsBean.create(dataStore);
        return new WanakuResponse<>(result);
    }

    /**
     * Updates an existing capability installation.
     * PUT /api/v1/capabilities/installations/{id}
     *
     * @param id        the installation ID
     * @param dataStore the updated installation data
     * @return HTTP 200 if updated successfully
     */
    @PUT
    @Path("/{id}")
    public Response update(@PathParam("id") String id, DataStore dataStore) {
        LOG.debugf("REST: Updating installation: %s", id);
        dataStore.setId(id);
        installationsBean.update(dataStore);
        return Response.ok().build();
    }

    /**
     * Deletes a capability installation. If the process is running, it is stopped first.
     * DELETE /api/v1/capabilities/installations/{id}
     *
     * @param id the installation ID to delete
     * @return HTTP 200 if deleted successfully
     */
    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") String id) {
        LOG.debugf("REST: Deleting installation: %s", id);
        installationsBean.delete(id);
        return Response.ok().build();
    }

    /**
     * Launches the capability process for an installation.
     * POST /api/v1/capabilities/installations/{id}/launch
     *
     * @param id the installation ID to launch
     * @return response containing the process status after launch
     */
    @POST
    @Path("/{id}/launch")
    public WanakuResponse<ProcessStatus> launch(@PathParam("id") String id) {
        LOG.infof("REST: Launching installation: %s", id);
        ProcessStatus status = installationsBean.launch(id);
        return new WanakuResponse<>(status);
    }

    /**
     * Stops the capability process for an installation.
     * POST /api/v1/capabilities/installations/{id}/stop
     *
     * @param id the installation ID to stop
     * @return HTTP 200 if stopped successfully
     */
    @POST
    @Path("/{id}/stop")
    public Response stop(@PathParam("id") String id) {
        LOG.infof("REST: Stopping installation: %s", id);
        installationsBean.stop(id);
        return Response.ok().build();
    }

    /**
     * Returns the process status for an installation.
     * GET /api/v1/capabilities/installations/{id}/status
     *
     * @param id the installation ID
     * @return response containing the current process status
     */
    @GET
    @Path("/{id}/status")
    public WanakuResponse<ProcessStatus> getStatus(@PathParam("id") String id) {
        LOG.debugf("REST: Getting status for installation: %s", id);
        ProcessStatus status = installationsBean.getStatus(id);
        return new WanakuResponse<>(status);
    }
}
