package ai.wanaku.backend.api.v1.datastores;

import ai.wanaku.api.exceptions.WanakuException;
import ai.wanaku.api.types.DataStore;
import ai.wanaku.api.types.WanakuResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.jboss.logging.Logger;

/**
 * REST API resource for managing DataStore entries.
 * Base path: /api/v1/data-store
 */
@ApplicationScoped
@Path("/api/v1/data-store")
public class DataStoresResource {
    private static final Logger LOG = Logger.getLogger(DataStoresResource.class);

    @Inject
    DataStoresBean dataStoresBean;

    /**
     * Add a new data store entry.
     * POST /api/v1/data-store/add
     *
     * @param dataStore the data store to add
     * @return response with the created data store
     * @throws WanakuException if addition fails
     */
    @Path("/add")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public WanakuResponse<DataStore> add(DataStore dataStore) throws WanakuException {
        LOG.debugf("REST: Adding data store: %s", dataStore);
        DataStore result = dataStoresBean.add(dataStore);
        return new WanakuResponse<>(result);
    }

    /**
     * Update an existing data store entry.
     * PUT /api/v1/data-store/update
     *
     * @param dataStore the data store to update (must include ID)
     * @return HTTP 200 if updated successfully
     * @throws WanakuException if update fails
     */
    @Path("/update")
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response update(DataStore dataStore) throws WanakuException {
        LOG.debugf("REST: Updating data store: %s", dataStore);
        dataStoresBean.update(dataStore);
        return Response.ok().build();
    }

    /**
     * List all data stores.
     * GET /api/v1/data-store/list
     *
     * @return response with list of all data stores
     * @throws WanakuException if listing fails
     */
    @Path("/list")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public WanakuResponse<List<DataStore>> list() throws WanakuException {
        LOG.debug("REST: Listing all data stores");
        List<DataStore> dataStores = dataStoresBean.list();
        return new WanakuResponse<>(dataStores);
    }

    /**
     * Get a data store by ID or name.
     * GET /api/v1/data-store/get?id={id}
     * GET /api/v1/data-store/get?name={name}
     *
     * @param id the ID of the data store (optional)
     * @param name the name of the data store (optional)
     * @return response with the requested data store(s)
     * @throws WanakuException if retrieval fails
     */
    @Path("/get")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public WanakuResponse<?> get(@QueryParam("id") String id, @QueryParam("name") String name) throws WanakuException {
        if (id != null && !id.isEmpty()) {
            LOG.debugf("REST: Getting data store by ID: %s", id);
            DataStore dataStore = dataStoresBean.findById(id);
            if (dataStore == null) {
                throw new WanakuException("Data store not found with ID: " + id);
            }
            return new WanakuResponse<>(dataStore);
        } else if (name != null && !name.isEmpty()) {
            LOG.debugf("REST: Getting data stores by name: %s", name);
            List<DataStore> dataStores = dataStoresBean.findByName(name);
            return new WanakuResponse<>(dataStores);
        } else {
            throw new WanakuException("Either 'id' or 'name' query parameter must be provided");
        }
    }

    /**
     * Remove a data store by ID or name.
     * DELETE /api/v1/data-store/remove?id={id}
     * DELETE /api/v1/data-store/remove?name={name}
     *
     * @param id the ID of the data store to remove (optional)
     * @param name the name of the data store(s) to remove (optional)
     * @return HTTP 200 if removed, 404 if not found
     * @throws WanakuException if removal fails
     */
    @Path("/remove")
    @DELETE
    public Response remove(@QueryParam("id") String id, @QueryParam("name") String name) throws WanakuException {
        int deleteCount = 0;

        if (id != null && !id.isEmpty()) {
            LOG.debugf("REST: Removing data store by ID: %s", id);
            deleteCount = dataStoresBean.removeById(id);
        } else if (name != null && !name.isEmpty()) {
            LOG.debugf("REST: Removing data stores by name: %s", name);
            deleteCount = dataStoresBean.remove(name);
        } else {
            throw new WanakuException("Either 'id' or 'name' query parameter must be provided");
        }

        if (deleteCount > 0) {
            return Response.ok().build();
        } else {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }
}
