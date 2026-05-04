package ai.wanaku.backend.api.v1.toolsetrepos;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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
import org.jboss.logging.Logger;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.capabilities.sdk.api.types.ToolReference;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;

/**
 * REST API resource for toolset repository operations.
 * Base path: /api/v1/toolset-repos
 */
@ApplicationScoped
@Path("/api/v1/toolset-repos")
public class ToolsetReposResource {
    private static final Logger LOG = Logger.getLogger(ToolsetReposResource.class);

    @Inject
    ToolsetReposBean toolsetReposBean;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public WanakuResponse<List<Map<String, String>>> list() {
        LOG.debug("REST: Listing toolset repositories");
        return new WanakuResponse<>(toolsetReposBean.list());
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public WanakuResponse<Map<String, String>> add(Map<String, String> repo) throws WanakuException {
        LOG.debugf("REST: Adding toolset repository: %s", repo.get("name"));
        Map<String, String> result =
                toolsetReposBean.add(repo.get("name"), repo.get("url"), repo.get("description"), repo.get("icon"));
        return new WanakuResponse<>(result);
    }

    @Path("/{name}")
    @DELETE
    public Response remove(@PathParam("name") String name) throws WanakuException {
        LOG.debugf("REST: Removing toolset repository: %s", name);
        int removed = toolsetReposBean.remove(name);
        if (removed > 0) {
            return Response.ok().build();
        }
        return Response.status(Response.Status.NOT_FOUND).build();
    }

    @Path("/{name}/browse")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public WanakuResponse<Map<String, Object>> browse(@PathParam("name") String name) throws WanakuException {
        LOG.debugf("REST: Browsing toolset repository: %s", name);
        return new WanakuResponse<>(toolsetReposBean.browse(name));
    }

    @Path("/{name}/toolsets/{toolsetName}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public WanakuResponse<List<ToolReference>> fetchToolset(
            @PathParam("name") String name, @PathParam("toolsetName") String toolsetName) throws WanakuException {
        LOG.debugf("REST: Fetching toolset '%s' from repository: %s", toolsetName, name);
        return new WanakuResponse<>(toolsetReposBean.fetchToolset(name, toolsetName));
    }
}
