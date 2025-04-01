package ai.wanaku.server.quarkus.api.v1.forwards;

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

import ai.wanaku.api.exceptions.WanakuException;
import ai.wanaku.api.types.ForwardReference;
import ai.wanaku.api.types.WanakuResponse;
import java.util.List;
import org.jboss.resteasy.reactive.RestResponse;

@ApplicationScoped
@Path("/api/v1/forwards")
public class ForwardsResource {
    @Inject
    ForwardsBean forwardsBean;

    @Path("/add")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addForward(ForwardReference reference) throws WanakuException {
        forwardsBean.forward(reference);
        return Response.ok().build();
    }

    @Path("/remove")
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response removeForward(ForwardReference reference) {
        forwardsBean.remove(reference);
        return Response.ok().build();
    }


    @Path("/list")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public RestResponse<WanakuResponse<List<ForwardReference>>> listForwards() {
        return RestResponse.ok(new WanakuResponse<>(forwardsBean.listForwards()));
    }
}
