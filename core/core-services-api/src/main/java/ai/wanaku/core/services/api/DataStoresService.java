package ai.wanaku.core.services.api;

import ai.wanaku.api.exceptions.WanakuException;
import ai.wanaku.api.types.DataStore;
import ai.wanaku.api.types.WanakuResponse;
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
     * List all data stores.
     *
     * @return response with list of all data stores
     */
    @Path("/list")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<List<DataStore>> list();

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
}
