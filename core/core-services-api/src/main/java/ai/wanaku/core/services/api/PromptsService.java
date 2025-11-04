package ai.wanaku.core.services.api;

import ai.wanaku.api.exceptions.WanakuException;
import ai.wanaku.api.types.PromptReference;
import ai.wanaku.api.types.WanakuResponse;
import ai.wanaku.api.types.io.PromptPayload;
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

@Path("/api/v1/prompts")
public interface PromptsService {

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<PromptReference> add(PromptReference prompt) throws WanakuException;

    @Path("/with-payload")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<PromptReference> addWithPayload(PromptPayload resource) throws WanakuException;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<List<PromptReference>> list();

    @DELETE
    Response remove(@QueryParam("prompt") String promptName);

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    Response update(PromptReference prompt);

    @Path("/{name}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<PromptReference> getByName(@PathParam("name") String name);
}
