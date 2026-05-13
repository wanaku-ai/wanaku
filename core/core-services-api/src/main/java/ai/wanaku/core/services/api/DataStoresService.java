package ai.wanaku.core.services.api;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import ai.wanaku.capabilities.sdk.api.types.DataStore;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;

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
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<DataStore> add(DataStore dataStore);

    /**
     * List all data stores, optionally filtered by label expression.
     *
     * @param labelFilter optional label expression to filter data stores by labels
     * @return response with list of data stores
     */
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
    @Path("/{id}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<DataStore> getById(@PathParam("id") String id);

    /**
     * Get data stores by name.
     *
     * @param name the name of the data stores
     * @return response with list of data stores
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<List<DataStore>> getByName(@QueryParam("name") String name);

    /**
     * Remove a data store by ID.
     *
     * @param id the ID of the data store to remove
     * @return a {@link WanakuResponse} indicating the result of the removal operation
     */
    @Path("/{id}")
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<Void> remove(@PathParam("id") String id);

    /**
     * Remove data stores by name.
     *
     * @param name the name of the data stores to remove
     * @return a {@link WanakuResponse} indicating the result of the removal operation
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<Void> removeByName(@QueryParam("name") String name);

    /**
     * Remove data stores matching a label expression.
     *
     * @param labelExpression the label expression to match data stores for removal
     * @return response with count of removed data stores
     */
    @Path("/labels")
    @DELETE
    WanakuResponse<Integer> removeIf(@QueryParam("labelExpression") String labelExpression);

    /**
     * Update an existing data store entry.
     *
     * @param dataStore the data store to update
     * @return a {@link WanakuResponse} indicating the result of the update operation
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<Void> update(DataStore dataStore);
}
