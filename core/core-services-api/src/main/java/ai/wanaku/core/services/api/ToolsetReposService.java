package ai.wanaku.core.services.api;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.capabilities.sdk.api.types.ToolReference;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;

/**
 * Service interface for managing toolset repositories via REST API.
 * <p>
 * Toolset repositories are remote sources (typically git repositories) that contain
 * collections of MCP tools organized by category. Users can register repository URLs,
 * browse their catalogs, and selectively import individual tools.
 */
@Path("/api/v1/toolset-repos")
public interface ToolsetReposService {

    /**
     * List all registered toolset repositories.
     *
     * @return response with list of repository summaries
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<List<Map<String, String>>> list();

    /**
     * Add a new toolset repository.
     *
     * @param repo map containing: name, url, description (optional), icon (optional)
     * @return response with the created repository
     * @throws WanakuException if registration fails
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<Map<String, String>> add(Map<String, String> repo) throws WanakuException;

    /**
     * Remove a toolset repository by name.
     *
     * @param name the repository name
     * @return HTTP response
     * @throws WanakuException if removal fails
     */
    @Path("/{name}")
    @DELETE
    Response remove(@PathParam("name") String name) throws WanakuException;

    /**
     * Browse a toolset repository's catalog by fetching and parsing its index.
     *
     * @param name the repository name
     * @return response with the parsed catalog (toolset entries with descriptions and icons)
     * @throws WanakuException if the repository is not found or fetching fails
     */
    @Path("/{name}/browse")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<Map<String, Object>> browse(@PathParam("name") String name) throws WanakuException;

    /**
     * Fetch a specific toolset's tools from a repository.
     *
     * @param name the repository name
     * @param toolsetName the toolset name within the repository
     * @return response with the list of tool references
     * @throws WanakuException if fetching fails
     */
    @Path("/{name}/toolsets/{toolsetName}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<List<ToolReference>> fetchToolset(
            @PathParam("name") String name, @PathParam("toolsetName") String toolsetName) throws WanakuException;
}
