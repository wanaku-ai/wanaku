package ai.wanaku.backend.api.v1.tools;

import ai.wanaku.api.exceptions.ToolNotFoundException;
import ai.wanaku.api.exceptions.WanakuException;
import ai.wanaku.api.types.ToolReference;
import ai.wanaku.api.types.WanakuResponse;
import ai.wanaku.api.types.io.ProvisionAwarePayload;
import ai.wanaku.api.types.io.ToolPayload;
import ai.wanaku.backend.api.v1.forwards.ForwardsBean;
import ai.wanaku.core.util.CollectionsHelper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

/**
 * JAX-RS REST resource implementation for tool management endpoints.
 * <p>
 * This class implements the {@code /api/v1/tools} endpoints for managing tool
 * capabilities in the Wanaku router. It delegates business logic to {@link ToolsBean}
 * and integrates with {@link ForwardsBean} to provide a unified view of both
 * locally registered tools and tools available through forward proxies.
 * <p>
 * This is an application-scoped CDI bean that serves as the entry point for
 * HTTP requests related to tool management.
 */
@ApplicationScoped
@Path("/api/v1/tools")
public class ToolsResource {
    @Inject
    ToolsBean toolsBean;

    @Inject
    ForwardsBean forwardsBean;

    /**
     * Registers a new tool capability.
     *
     * @param resource the tool reference to register
     * @return a response containing the registered tool reference
     * @throws WanakuException if registration fails
     */
    @Path("/add")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public WanakuResponse<ToolReference> add(ToolReference resource) throws WanakuException {
        var ret = toolsBean.add(resource);
        return new WanakuResponse<>(ret);
    }

    /**
     * Registers a new tool capability with provisioning payload.
     * <p>
     * This endpoint accepts a tool reference along with configuration and secrets
     * data, enabling complete provisioning of the tool capability.
     *
     * @param resource the tool payload containing the tool reference and provisioning data
     * @return a response containing the registered tool reference
     * @throws WanakuException if registration fails or payload validation fails
     */
    @Path("/addWithPayload")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public WanakuResponse<ToolReference> addWithPayload(ToolPayload resource) throws WanakuException {
        var ret = toolsBean.add(resource);
        validatePayload(resource);
        return new WanakuResponse<>(ret);
    }

    /**
     * Validates that a provision-aware payload is not null and contains a payload object.
     *
     * @param resource the payload to validate
     * @throws WanakuException if the payload or its nested payload object is null
     */
    private void validatePayload(ProvisionAwarePayload<?> resource) throws WanakuException {
        if (resource == null) {
            throw new WanakuException("The request itself must not be null");
        }
        if (resource.getPayload() == null) {
            throw new WanakuException("The 'payload' is required for this request");
        }
    }

    /**
     * Lists all available tool capabilities.
     * <p>
     * This endpoint returns a combined list of both locally registered tools
     * and tools available through forward proxies to remote MCP servers.
     *
     * @return a response containing a list of all tool references
     * @throws WanakuException if listing fails
     */
    @Path("/list")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public WanakuResponse<List<ToolReference>> list(@QueryParam("labelFilter") String labelFilter)
            throws WanakuException {
        List<ToolReference> forwardTools = forwardsBean.listAllAsTools(labelFilter);
        List<ToolReference> tools = toolsBean.list(labelFilter);
        List<ToolReference> ret = CollectionsHelper.join(tools, forwardTools);
        return new WanakuResponse<>(ret);
    }

    /**
     * Removes a tool capability by name.
     *
     * @param tool the name of the tool to remove
     * @return HTTP 200 OK if removed successfully, HTTP 404 NOT FOUND if the tool doesn't exist
     * @throws WanakuException if removal fails
     */
    @Path("/remove")
    @PUT
    public Response remove(@QueryParam("tool") String tool) throws WanakuException {
        int deleteCount = toolsBean.remove(tool);
        if (deleteCount > 0) {
            return Response.ok().build();
        } else {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    /**
     * Updates an existing tool capability.
     *
     * @param resource the updated tool reference
     * @return HTTP 200 OK if updated successfully
     * @throws WanakuException if update fails
     */
    @Path("/update")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response update(ToolReference resource) throws WanakuException {
        toolsBean.update(resource);
        return Response.ok().build();
    }

    /**
     * Retrieves a tool capability by name.
     *
     * @param name the name of the tool to retrieve
     * @return a response containing the tool reference
     * @throws ToolNotFoundException if the tool is not found
     * @throws WanakuException if retrieval fails
     */
    @Path("/")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public WanakuResponse<ToolReference> getByName(@QueryParam("name") String name) throws WanakuException {
        ToolReference tool = toolsBean.getByName(name);
        if (tool == null) {
            throw new ToolNotFoundException(name);
        }
        return new WanakuResponse<>(tool);
    }

    /**
     * Removes all tools from the system that match the label expression
     *
     * @param labelExpression the name of the tool to remove
     * @return a {@link Response} indicating the number of the tools removed.
     */
    @Path("/")
    @DELETE
    public WanakuResponse<Integer> removeIf(@QueryParam("labelExpression") String labelExpression)
            throws WanakuException {

        int deleteCount = toolsBean.removeIf(labelExpression);
        return new WanakuResponse<>(deleteCount);
    }
}
