package ai.wanaku.backend.api.v1.forwards;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import org.jboss.resteasy.reactive.RestResponse;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.capabilities.sdk.api.types.ForwardReference;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.core.util.StringHelper;

@ApplicationScoped
@Path("/api/v1/forwards")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ForwardsResource {
    @Inject
    ForwardsBean forwardsBean;

    @POST
    public Response addForward(ForwardReference reference) throws WanakuException {
        forwardsBean.forward(reference);
        return Response.ok().build();
    }

    @Path("/{name}")
    @DELETE
    public Response removeForward(@PathParam("name") String name) {
        ForwardReference reference = new ForwardReference();
        reference.setName(name);
        int deleteCount = forwardsBean.remove(reference);
        if (deleteCount > 0) {
            return Response.ok().build();
        }

        return Response.status(Response.Status.NOT_FOUND).build();
    }

    @GET
    public RestResponse<WanakuResponse<List<ForwardReference>>> listForwards(
            @jakarta.ws.rs.QueryParam("labelFilter") String labelFilter) {
        return RestResponse.ok(new WanakuResponse<>(forwardsBean.listForwards(labelFilter)));
    }

    @Path("/{name}")
    @GET
    public WanakuResponse<ForwardReference> getByName(@PathParam("name") String name) {
        List<ForwardReference> references = forwardsBean.listForwards();
        for (ForwardReference reference : references) {
            if (name.equals(reference.getName())) {
                return new WanakuResponse<>(reference);
            }
        }
        throw new NotFoundException("Forward not found with name: %s".formatted(name));
    }

    @Path("/{name}")
    @PUT
    public Response update(@PathParam("name") String name, ForwardReference resource) throws WanakuException {
        if (resource != null && StringHelper.isBlank(resource.getName())) {
            resource.setName(name);
        }
        forwardsBean.update(resource);
        return Response.ok().build();
    }

    @Path("/{name}/refreshes")
    @POST
    public Response refresh(@PathParam("name") String name) throws WanakuException {
        ForwardReference reference = new ForwardReference();
        reference.setName(name);
        forwardsBean.refresh(reference);
        return Response.ok().build();
    }
}
