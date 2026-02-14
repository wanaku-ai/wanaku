package ai.wanaku.backend.api.v1.datastores;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import org.jboss.logging.Logger;
import ai.wanaku.capabilities.sdk.api.exceptions.DataStoreResourceNotFoundException;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.capabilities.sdk.api.types.DataStore;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;

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
     * POST /api/v1/data-store/update
     *
     * @param dataStore the data store to update (must include ID)
     * @return HTTP 200 if updated successfully
     * @throws WanakuException if update fails
     */
    @Path("/update")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response update(DataStore dataStore) throws WanakuException {
        LOG.debugf("REST: Updating data store: %s", dataStore);
        dataStoresBean.update(dataStore);
        return Response.ok().build();
    }

    /**
     * List all data stores, optionally filtered by label expression.
     * GET /api/v1/data-store/list
     * GET /api/v1/data-store/list?labelFilter={expression}
     *
     * @param labelFilter optional label expression to filter data stores
     * @return response with list of data stores
     * @throws WanakuException if listing or label expression parsing fails
     */
    @Path("/list")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public WanakuResponse<List<DataStore>> list(@QueryParam("labelFilter") String labelFilter) throws WanakuException {
        if (labelFilter != null && !labelFilter.isBlank()) {
            LOG.debugf("REST: Listing data stores with label filter: %s", labelFilter);
        } else {
            LOG.debug("REST: Listing all data stores");
        }
        List<DataStore> dataStores = dataStoresBean.list(labelFilter);
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
                throw new DataStoreResourceNotFoundException("Data store not found with ID: " + id);
            }
            return new WanakuResponse<>(dataStore);
        } else if (name != null && !name.isEmpty()) {
            LOG.debugf("REST: Getting data stores by name: %s", name);
            List<DataStore> dataStores = dataStoresBean.findByName(name);
            if (dataStores == null || dataStores.isEmpty()) {
                throw new DataStoreResourceNotFoundException("Data store not found with name: " + name);
            }
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

    /**
     * Remove data stores matching a label expression.
     * DELETE /api/v1/data-store/removeByLabel?labelExpression={expression}
     *
     * @param labelExpression the label expression to match data stores for removal
     * @return response with count of removed data stores
     * @throws WanakuException if label expression is invalid or removal fails
     */
    @Path("/removeByLabel")
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    public WanakuResponse<Integer> removeIf(@QueryParam("labelExpression") String labelExpression)
            throws WanakuException {
        LOG.debugf("REST: Removing data stores by label expression: %s", labelExpression);
        int removed = dataStoresBean.removeIf(labelExpression);
        return new WanakuResponse<>(removed);
    }
}
