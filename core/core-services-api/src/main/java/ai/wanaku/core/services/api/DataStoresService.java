package ai.wanaku.core.services.api;

import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.capabilities.sdk.api.types.DataStore;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

/**
 * Service interface for DataStore operations via REST API.
 */
@Path("/api/v1/data-store")
public interface DataStoresService {

    /**
     * Add a new data store entry.
     *
     * @param dataStore the data store to add
     * @return response with the created data store
     * @throws WanakuException if addition fails
     */
    @Path("/add")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<DataStore> add(DataStore dataStore) throws WanakuException;

    /**
     * List all data stores, optionally filtered by label expression.
     *
     * @param labelFilter optional label expression to filter data stores by labels
     * @return response with list of data stores
     */
    @Path("/list")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<List<DataStore>> list(@QueryParam("labelFilter") String labelFilter);

    /**
     * List all data stores without filtering.
     *
     * @return response with list of all data stores
     */
    default WanakuResponse<List<DataStore>> list() {
        return list(null);
    }

    /**
     * Get a data store by ID.
     *
     * @param id the ID of the data store
     * @return response with the data store
     */
    @Path("/get")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<DataStore> getById(@QueryParam("id") String id);

    /**
     * Get data stores by name.
     *
     * @param name the name of the data stores
     * @return response with list of data stores
     */
    @Path("/get")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<List<DataStore>> getByName(@QueryParam("name") String name);

    /**
     * Remove a data store by ID.
     *
     * @param id the ID of the data store to remove
     * @return HTTP response
     */
    @Path("/remove")
    @DELETE
    Response remove(@QueryParam("id") String id);

    /**
     * Remove data stores by name.
     *
     * @param name the name of the data stores to remove
     * @return HTTP response
     */
    @Path("/remove")
    @DELETE
    Response removeByName(@QueryParam("name") String name);

    /**
     * Remove data stores matching a label expression.
     *
     * @param labelExpression the label expression to match data stores for removal
     * @return response with count of removed data stores
     * @throws WanakuException if removal fails
     */
    @Path("/removeByLabel")
    @DELETE
    WanakuResponse<Integer> removeIf(@QueryParam("labelExpression") String labelExpression) throws WanakuException;

    /**
     * Update an existing data store entry.
     *
     * @param dataStore the data store to update
     * @return HTTP response
     * @throws WanakuException if update fails
     */
    @Path("/update")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    Response update(DataStore dataStore) throws WanakuException;
}
