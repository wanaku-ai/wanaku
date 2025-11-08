package ai.wanaku.backend.api.v1.forwards;

import ai.wanaku.api.exceptions.WanakuException;
import ai.wanaku.api.types.ForwardReference;
import ai.wanaku.api.types.WanakuResponse;
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
import org.jboss.resteasy.reactive.RestResponse;

@ApplicationScoped
@Path("/api/v1/forwards")
public class ForwardsResource {
    @Inject
    ForwardsBean forwardsBean;

    /**
     * Creates a new forward reference.
     *
     * @param reference the forward reference to create
     * @return HTTP 200 OK if created successfully
     * @throws WanakuException if creation fails
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addForward(ForwardReference reference) throws WanakuException {
        forwardsBean.forward(reference);
        return Response.ok().build();
    }

    /**
     * Deletes a forward reference by name.
     *
     * @param forwardName the name of the forward to delete
     * @return HTTP 200 OK if deleted successfully, HTTP 404 NOT FOUND if forward doesn't exist
     */
    @Path("/{forwardName}")
    @DELETE
    public Response removeForward(@PathParam("forwardName") String forwardName) {
        ForwardReference reference = new ForwardReference();
        reference.setName(forwardName);

        int deleteCount = forwardsBean.remove(reference);
        if (deleteCount > 0) {
            return Response.ok().build();
        }

        return Response.status(Response.Status.NOT_FOUND).build();
    }

    /**
     * Lists all forward references.
     *
     * @return a response containing a list of all forward references
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public RestResponse<WanakuResponse<List<ForwardReference>>> listForwards() {
        return RestResponse.ok(new WanakuResponse<>(forwardsBean.listForwards()));
    }

    /**
     * Updates an existing forward reference.
     *
     * @param forwardName the name of the forward to update
     * @param resource the updated forward reference
     * @return HTTP 200 OK if updated successfully
     * @throws WanakuException if update fails
     */
    @Path("/{forwardName}")
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response update(@PathParam("forwardName") String forwardName, ForwardReference resource)
            throws WanakuException {
        // Ensure the resource has the correct name
        resource.setName(forwardName);
        forwardsBean.update(resource);
        return Response.ok().build();
    }
}
