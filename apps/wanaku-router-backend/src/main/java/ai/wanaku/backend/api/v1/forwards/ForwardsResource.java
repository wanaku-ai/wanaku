package ai.wanaku.backend.api.v1.forwards;

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

import java.util.List;
import java.util.stream.Collectors;
import ai.wanaku.capabilities.sdk.api.exceptions.ResourceNotFoundException;
import ai.wanaku.capabilities.sdk.api.types.ForwardReference;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.core.services.api.ForwardWithRootsRequest;
import ai.wanaku.core.util.StringHelper;

@ApplicationScoped
@Path("/api/v1/forwards")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ForwardsResource {
    @Inject
    ForwardsBean forwardsBean;

    @POST
    public WanakuResponse<Void> addForward(ForwardReference reference) {
        forwardsBean.forward(reference);
        return new WanakuResponse<>();
    }

    @Path("/with-roots")
    @POST
    public WanakuResponse<Void> addForwardWithRoots(ForwardWithRootsRequest request) {
        List<ForwardRequest.RootEntry> rootEntries = null;
        if (request.getRoots() != null) {
            rootEntries = request.getRoots().stream()
                    .map(r -> new ForwardRequest.RootEntry(r.getUri(), r.getName()))
                    .collect(Collectors.toList());
        }
        forwardsBean.forward(request.getForward(), rootEntries);
        return new WanakuResponse<>();
    }

    @Path("/{name}")
    @DELETE
    public WanakuResponse<Void> removeForward(@PathParam("name") String name) {
        ForwardReference reference = new ForwardReference();
        reference.setName(name);
        int deleteCount = forwardsBean.remove(reference);
        if (deleteCount > 0) {
            return new WanakuResponse<>();
        }

        throw new ResourceNotFoundException(name);
    }

    @GET
    public WanakuResponse<List<ForwardReference>> listForwards(
            @jakarta.ws.rs.QueryParam("labelFilter") String labelFilter) {
        return new WanakuResponse<>(forwardsBean.listForwards(labelFilter));
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
        throw new ResourceNotFoundException("Forward not found with name: %s".formatted(name));
    }

    @Path("/{name}")
    @PUT
    public WanakuResponse<Void> update(@PathParam("name") String name, ForwardReference resource) {
        if (resource != null && StringHelper.isBlank(resource.getName())) {
            resource.setName(name);
        }
        forwardsBean.update(resource);
        return new WanakuResponse<>();
    }

    @Path("/{name}/refreshes")
    @POST
    public WanakuResponse<Void> refresh(@PathParam("name") String name) {
        ForwardReference reference = new ForwardReference();
        reference.setName(name);
        forwardsBean.refresh(reference);
        return new WanakuResponse<>();
    }
}
