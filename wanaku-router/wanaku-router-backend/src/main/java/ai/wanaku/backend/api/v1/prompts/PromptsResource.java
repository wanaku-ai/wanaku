package ai.wanaku.backend.api.v1.prompts;

import ai.wanaku.capabilities.sdk.api.exceptions.PromptNotFoundException;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.capabilities.sdk.api.types.PromptReference;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.capabilities.sdk.api.types.io.PromptPayload;
import ai.wanaku.capabilities.sdk.api.types.io.ProvisionAwarePayload;
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
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

@ApplicationScoped
@Path("/api/v1/prompts")
public class PromptsResource {
    @Inject
    PromptsBean promptsBean;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public WanakuResponse<PromptReference> add(PromptReference resource) throws WanakuException {
        var ret = promptsBean.add(resource);
        return new WanakuResponse<>(ret);
    }

    @Path("/with-payload")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public WanakuResponse<PromptReference> addWithPayload(PromptPayload resource) throws WanakuException {
        validatePayload(resource);
        var ret = promptsBean.add(resource);
        return new WanakuResponse<>(ret);
    }

    private void validatePayload(ProvisionAwarePayload<?> resource) throws WanakuException {
        if (resource == null) {
            throw new WanakuException("The request itself must not be null");
        }
        if (resource.getPayload() == null) {
            throw new WanakuException("The 'payload' is required for this request");
        }
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public WanakuResponse<List<PromptReference>> list() throws WanakuException {
        List<PromptReference> prompts = promptsBean.list();
        return new WanakuResponse<>(prompts);
    }

    @DELETE
    public Response remove(@QueryParam("prompt") String prompt) throws WanakuException {
        int deleteCount = promptsBean.remove(prompt);
        if (deleteCount > 0) {
            return Response.ok().build();
        } else {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response update(PromptReference resource) throws WanakuException {
        promptsBean.update(resource);
        return Response.ok().build();
    }

    @Path("/{name}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public WanakuResponse<PromptReference> getByName(@PathParam("name") String name) throws WanakuException {
        PromptReference prompt = promptsBean.getByName(name);
        if (prompt == null) {
            throw new PromptNotFoundException(name);
        }
        return new WanakuResponse<>(prompt);
    }
}
