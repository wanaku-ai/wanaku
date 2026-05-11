package ai.wanaku.core.services.api;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.capabilities.sdk.api.types.DataStore;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;

/**
 * Service interface for Service Catalog operations via REST API.
 */
@Path("/api/v1/service-catalog")
public interface ServiceCatalogService {

    /**
     * List all service catalog entries, optionally filtered by search term.
     *
     * @param search optional search term to filter catalogs by name or description
     * @return response with list of catalog summaries
     */
    @Path("/list")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<List<DataStore>> list(@QueryParam("search") String search);

    /**
     * List all service catalog entries without filtering.
     *
     * @return response with list of all catalog summaries
     */
    default WanakuResponse<List<DataStore>> list() {
        return list(null);
    }

    /**
     * Get a specific service catalog by name.
     *
     * @param name the catalog name
     * @return response with catalog details
     */
    @Path("/get")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<DataStore> get(@QueryParam("name") String name);

    /**
     * Deploy a service catalog ZIP package.
     *
     * @param dataStore the data store entry containing the Base64-encoded ZIP
     * @return response with the created data store entry
     */
    @Path("/deploy")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<DataStore> deploy(DataStore dataStore);

    /**
     * Download a service catalog by name, returning the raw DataStore with Base64-encoded ZIP data.
     *
     * @param name the catalog name
     * @return response with the DataStore containing the Base64-encoded ZIP
     */
    @Path("/download")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<DataStore> download(@QueryParam("name") String name);

    /**
     * Remove a service catalog by name.
     *
     * @param name the catalog name to remove
     * @return a {@link WanakuResponse} indicating the result of the removal operation
     */
    @Path("/remove")
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<Void> remove(@QueryParam("name") String name);

    /**
     * Get deployment instructions for a service catalog.
     *
     * @param name the catalog name
     * @param model the deployment model: local, docker, or kubernetes
     * @return response with deployment instructions
     */
    @Path("/instructions")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<DeploymentInstructions> getDeploymentInstructions(
            @QueryParam("name") String name, @QueryParam("model") String model);
}
