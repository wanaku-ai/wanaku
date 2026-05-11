package ai.wanaku.backend.api.v1.prompts;

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
import ai.wanaku.backend.api.v1.common.PayloadValidator;
import ai.wanaku.capabilities.sdk.api.exceptions.PromptNotFoundException;
import ai.wanaku.capabilities.sdk.api.types.PromptReference;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.capabilities.sdk.api.types.io.PromptPayload;

@ApplicationScoped
@Path("/api/v1/prompts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PromptsResource {
    @Inject
    PromptsBean promptsBean;

    @POST
    public WanakuResponse<PromptReference> add(PromptReference resource) {
        var ret = promptsBean.add(resource);
        return new WanakuResponse<>(ret);
    }

    @Path("/payloads")
    @POST
    public WanakuResponse<PromptReference> addWithPayload(PromptPayload resource) {
        PayloadValidator.validate(resource);
        var ret = promptsBean.add(resource);
        return new WanakuResponse<>(ret);
    }

    @GET
    public WanakuResponse<List<PromptReference>> list() {
        List<PromptReference> prompts = promptsBean.list();
        return new WanakuResponse<>(prompts);
    }

    @Path("/{name}")
    @DELETE
    public WanakuResponse<Void> remove(@PathParam("name") String name) {
        int deleteCount = promptsBean.remove(name);
        if (deleteCount > 0) {
            return new WanakuResponse<>();
        } else {
            throw new PromptNotFoundException(name);
        }
    }

    @PUT
    public WanakuResponse<Void> update(PromptReference resource) {
        promptsBean.update(resource);
        return new WanakuResponse<>();
    }

    @Path("/{name}")
    @GET
    public WanakuResponse<PromptReference> getByName(@PathParam("name") String name) {
        PromptReference prompt = promptsBean.getByName(name);
        if (prompt == null) {
            throw new PromptNotFoundException(name);
        }
        return new WanakuResponse<>(prompt);
    }
}
