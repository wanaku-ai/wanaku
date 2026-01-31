package ai.wanaku.backend.api.v1.forwards;

import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.capabilities.sdk.api.types.ForwardReference;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
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

    @Path("/add")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addForward(ForwardReference reference) throws WanakuException {
        forwardsBean.forward(reference);
        return Response.ok().build();
    }

    @Path("/remove")
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response removeForward(ForwardReference reference) {
        int deleteCount = forwardsBean.remove(reference);
        if (deleteCount > 0) {
            return Response.ok().build();
        }

        return Response.status(Response.Status.NOT_FOUND).build();
    }

    @Path("/list")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public RestResponse<WanakuResponse<List<ForwardReference>>> listForwards(
            @jakarta.ws.rs.QueryParam("labelFilter") String labelFilter) {
        return RestResponse.ok(new WanakuResponse<>(forwardsBean.listForwards(labelFilter)));
    }

    @Path("/update")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response update(ForwardReference resource) throws WanakuException {
        forwardsBean.update(resource);
        return Response.ok().build();
    }

    @Path("/refresh")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response refresh(ForwardReference reference) throws WanakuException {
        forwardsBean.refresh(reference);
        return Response.ok().build();
    }
}
